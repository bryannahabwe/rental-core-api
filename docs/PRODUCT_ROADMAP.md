# RentFlow — Product Roadmap

**Status:** living document · last reviewed 3 August 2026
**Audience:** whoever is deciding what to build next

---

## How to use this

This is **not a schedule**. The phases are ordered by dependency and leverage,
not by date, and nothing here is committed work. The intended use is: when
capacity frees up or a customer pushes on something, come here, read the
relevant entry, and pull it.

Every item follows the same shape:

- **Why it matters** — the business case, in one paragraph
- **Evidence today** — what the code actually does right now, with file paths,
  so nobody re-derives it
- **What to build** — enough to scope it, not a design doc
- **Depends on** — what must exist first
- **Size** — S / M / L / XL, relative to each other, not calendar time
- **Watch out for** — the thing that will bite

Sizes are deliberately coarse. `S` is days, `XL` is "this is a quarter and it
changes the data model".

---

## Where the product stands today

Eleven modules — `agreements`, `audit`, `auth`, `notification`, `payments`,
`properties`, `reports`, `settings`, `tenants`, `units`, `users` — under
`src/main/java/com/cognix/rentalcoreapi/modules/`.

**What is genuinely solid.** The rent ledger. Billing cycles
(`shared/util/BillingCycleUtils.java`), advance vs arrears billing, opening
balances for tenants inherited mid-tenancy, overpayment rollover, and a
per-tenant ledger that reconciles expected against paid per cycle. Multi-property
scoping, five roles resolved per property, and an immutable audit trail with a
Postgres-level immutability trigger. This part is well built and should be
treated as the foundation everything else hangs off.

**The shape of the gap.** The system is an excellent *record* of money that
already moved, and almost nothing else. It does not collect money, it never
speaks to a tenant, it has no concept of cost, and nothing in it has ever run on
a clock. Every number is computed on demand when a staff member opens a page.

That last point is the single most structural observation in this document, so
it gets its own section.

---

## P0 — The prerequisite: a scheduler

**There is no `@Scheduled` or `@EnableScheduling` anywhere in the codebase.**
Nothing in this application has ever executed without a human triggering it.

That one absence blocks, directly:

- rent reminders and arrears notices (Phase 1)
- late fees and penalties (Phase 2)
- any real invoicing or charge generation
- lease-expiry and renewal prompts (Phase 3)
- owner statements on a cycle (Phase 4)

So the first feature that needs a clock pays for the infrastructure, and three
or four later features get it free. Worth building deliberately at that point
rather than bolting a timer onto one feature and repeating it.

**What "deliberately" means here:** the app will eventually run more than one
instance, so an in-process `@Scheduled` will double-fire. Decide early between a
locking library (ShedLock is the low-ceremony option for Spring Boot) and a
job table with claim semantics. Also decide where job *outcomes* land — the
audit trail is immutable and currently only records human actions, so a failed
reminder batch has nowhere to go today.

**Size:** M on its own. Treat as part of whichever Phase 1 item lands first.

---

## Phase 1 — Make money move

The three items that change what the product *is*, rather than how complete it
is. If only one thing gets built this year, it is the first one.

### 1.1 Mobile money collection (MTN MoMo / Airtel Money)

**Why it matters.** This is the difference between a ledger and a rent platform.
Today a landlord collects rent somewhere else and then tells RentFlow it
happened. Collecting in-product removes the manual reconciliation that is
currently the landlord's actual daily work, makes the arrears data
self-maintaining rather than dependent on someone remembering to key it in, and
is the natural foundation for charging a transaction fee later.

**Evidence today.** `modules/payments/model/PaymentMethod.java` contains exactly
one value:

```java
public enum PaymentMethod {
    CASH
}
```

`PaymentSource` has `CASH` and `ROLLOVER` — rollover being the system's own
overpayment carry-forward, not a real tender. There is no gateway client, no
webhook endpoint, no reconciliation, and no idempotency handling anywhere in
`modules/payments`. Listed as pending in `RENTAL_MANAGEMENT_SAAS.md`.

**What to build.**

- Widen `PaymentMethod` (`MTN_MOMO`, `AIRTEL_MONEY`, keep `CASH`, likely
  `BANK_TRANSFER`) and add the matching `chk_` constraint — see
  `V19__add_caretaker_and_accountant_roles.sql` for the pattern, and note that
  forgetting the CHECK gives a raw 500 rather than a 400.
- A collection-request flow: initiate against a tenant's number, persist a
  pending payment with a provider reference, and expose its status.
- A **public** webhook endpoint for provider callbacks. This is the first
  unauthenticated write endpoint in the system — `SecurityConfig` currently
  permits only auth and docs paths — so it needs signature verification and its
  own threat model.
- Idempotency. Providers retry. A duplicate callback must not create a second
  payment, and must not re-trigger overpayment rollover in
  `PaymentService.recordPayment`.
- A reconciliation view: provider-settled versus recorded, with the unmatched.

**Depends on.** Nothing structural. Can ship before the scheduler, though a
scheduled poller is a common fallback for missed callbacks.

**Size:** XL.

**Watch out for.** Money plus retries plus webhooks is where correctness bugs
become financial ones. The existing overpayment-rollover logic assumes a payment
is created once, by a human, inside one transaction — re-read it against
concurrent callback delivery before extending it. Sandbox credentials from both
MTN and Airtel take real calendar time to obtain; start that before the build,
not during.

### 1.2 Tenant notifications — rent reminders and arrears notices

**Why it matters.** The system knows exactly who owes what, and tells nobody. A
reminder two days before rent is due, and a notice when a cycle goes unpaid, is
the cheapest available intervention against the arrears the ledger tracks so
carefully. In this market SMS materially outperforms email for tenants; email is
fine for staff.

**Evidence today.** `modules/notification/` contains four files —
`EmailService`, `EmailSender`, `BrevoEmailSender`, `LoggingEmailSender` — and
`EmailService` exposes exactly one method, `sendInvite`. There is no SMS sender,
no notification entity, no template concept, no delivery log, and no tenant has
ever received anything from this system.

**What to build.**

- An SMS sender behind the existing `EmailSender`-style interface, so the
  provider stays swappable the way Brevo already is. Local aggregators are the
  realistic choice over Twilio on price.
- A notification record: what was sent, to whom, when, delivery outcome. Needed
  for "did the tenant actually get told" during a dispute, and it is the natural
  landing place for scheduled-job outcomes.
- Templates: rent due, rent overdue, payment received (receipt by SMS is a
  strong small win), and later maintenance status.
- Per-landlord opt-out and quiet hours in `LandlordSettings`. Sending at 3am
  loses customers.

**Depends on.** P0 scheduler.

**Size:** L (M if the scheduler already exists).

**Watch out for.** Every message costs money and reaches a real person. Build
the kill switch and a dry-run mode before the first live send, and rate-limit
per tenant so a scheduling bug cannot text somebody forty times. Note the
existing invite email is deliberately best-effort and swallows failures
(`UserManagementService.sendInviteEmail`) — tenant billing notices likely
warrant retry rather than silent loss.

### 1.3 Tenant self-service

**Why it matters.** Every "what do I owe?" is currently a phone call to the
landlord or caretaker. The data to answer it is already computed and already
exposed — it is simply unreachable by the person it concerns. Once mobile money
exists, this is also the surface where a tenant pays.

**Evidence today.** `modules/tenants/model/Tenant.java` has `landlord`,
`property`, `name`, `phone`, `email`, `address` and nothing else — confirmed
against the live schema, the `tenants` table has no `user_id`. Tenants have no
login, no password, no status. `GET /tenants/{id}/ledger` and
`/tenants/{id}/transactions` already return everything a tenant portal would
show, scoped to landlord staff. Named as pending in `RENTAL_MANAGEMENT_SAAS.md`.

**What to build.**

- Link `Tenant` to a login. The cleanest fit with the existing model is a
  nullable `user_id` and a `TENANT` role, so one identity mechanism serves
  everybody — but note **every data row is anchored to `accountOwnerId`** and a
  tenant is not staff of that account. Getting that boundary right is the whole
  design problem; a tenant must reach their own ledger and nothing else.
- Consider passwordless. Tenants lose passwords; an SMS one-time code fits the
  market and reuses 1.2's infrastructure.
- Read-only first: balance, cycle history, receipts. Payment comes with 1.1.

**Depends on.** 1.2 for OTP delivery, if passwordless. 1.1 for paying.

**Size:** L.

**Watch out for.** This is the first actor outside the landlord's account
boundary, and the `landlord_id` scoping that has kept the system safe so far
does not express "this tenant, their own row only". Do not extend
`PropertyAccessGuard` to cover it by adding cases — it answers a different
question. Treat tenant access as its own authorization path.

---

## Phase 2 — Complete the money story

Nothing here changes the product category, but each closes a gap where a
landlord currently keeps a parallel notebook.

### 2.1 Expenses and net income

**Why it matters.** Reports today answer "what came in". A landlord's actual
question is "what did I clear". Without cost, the reporting module can never
produce the number that matters, and any future owner statement (4.1) is
impossible.

**Evidence today.** `modules/reports/dto/` contains exactly four DTOs —
`SummaryResponse`, `PaymentReportResponse`, `MonthlyCollectionResponse`,
`OccupancyReportResponse` — all revenue-side. Grepping `src/main/java` for
`expense` returns zero files.

**What to build.** An `Expense` entity scoped to landlord and property, with
categories (repairs, utilities, security, garbage, salaries, taxes), optional
receipt attachment (see 3.3), and a net-income report joining it against
collections. Property-level attribution matters — landlords want to know which
building is actually profitable.

**Depends on.** Nothing. Genuinely independent, which makes it good filler work.

**Size:** M.

**Watch out for.** Deciding whether an expense is cash-basis at payment date or
accrual against a period. Cash-basis is almost certainly right for this market;
decide once and write it down rather than discovering the ambiguity in a report.

### 2.2 Additional charges — utilities, service charge, late fees

**Why it matters.** Rent is the only chargeable thing the model knows about.
Water, service charge and garbage are near-universal in Ugandan rentals, so
they are being tracked outside the app — which means the balance RentFlow shows
is not the balance the tenant actually owes.

**Evidence today.** `RentalAgreement` carries `rentAmount` and `depositAmount`
and no other money field. Grepping for `charge`, `utility`, `penalty` and
`lateFee` returns zero files. Expected amounts are derived from `rentAmount`
alone in `BillingCycleUtils`.

**What to build.** A charge concept sitting alongside rent within a billing
cycle: recurring (fixed monthly service charge), metered (water, entered per
cycle), and one-off (a repair recharged to the tenant). Late fees are the same
mechanism on a schedule. The tenant ledger and every "expected" calculation must
then sum charges rather than reading `rentAmount`.

**Depends on.** P0 scheduler, for late fees only.

**Size:** L.

**Watch out for.** This is the most invasive change in Phase 2 — it touches the
cycle engine, the ledger, the reports, and receipts. `BillingCycleUtils` and the
ledger logic are among the better-tested parts of the system by usage; changing
what "expected" means will ripple. Sequence it when there is appetite for a
careful regression pass, not as a quick win.

### 2.3 Deposit settlement

**Why it matters.** Deposits are where landlord–tenant disputes actually happen,
and the system currently has no position on them.

**Evidence today.** `RentalAgreement.depositAmount` is captured at move-in and
never read again. `PATCH /agreements/{id}/moveout` terminates the tenancy and
frees the unit; it does nothing with the deposit.

**What to build.** A settlement step in the move-out flow: deposit held, itemised
deductions with reasons, amount refunded, and a settlement statement the tenant
can be sent. Deductions should be able to reference a maintenance job (3.1) once
that exists.

**Depends on.** Nothing. Better with 3.1.

**Size:** M.

**Watch out for.** Deposits are held money, not income. Do not let them fall into
the collections figures — a deposit landing in the monthly collection report
overstates revenue and will be noticed by the first landlord who reconciles.

---

## Phase 3 — Operations

### 3.1 Maintenance and work orders

**Why it matters.** Repairs are the second-largest thing a property manager
does after collecting rent, and the app has no opinion on them at all. Note that
**the reporter already exists**: the `CARETAKER` role added in `V19` is
precisely the person standing in front of the broken tap. The actor is in place
with nothing to report into.

**Evidence today.** Grepping `src/main/java` for `maintenance`, `repair`,
`workorder`, `vendor` and `contractor` returns zero files each.

**What to build.** A request with property, unit, optional tenant, category,
severity, photos, and a status lifecycle. Caretakers raise and update, managers
triage and close, tenants raise once 1.3 exists. Cost on completion should feed
2.1 rather than being a separate number.

**Depends on.** 3.3 for photos. 2.1 to be useful financially.

**Size:** L.

**Watch out for.** Scope creep into vendor management, quotes and scheduling.
Ship request → assign → resolve, and let real usage argue for the rest.

### 3.2 Lease term, expiry and renewal

**Why it matters.** A tenancy currently has no end. There is no way to ask
"whose lease expires next month", which is the question that drives renewal
conversations and occupancy planning.

**Evidence today.** `RentalAgreement` has `startDate` and `moveOutDate` — the
latter recorded when someone leaves, not agreed in advance. There is no
`endDate`, and grepping for `renewal` returns zero files.

**What to build.** An agreed end date, an expiring-soon view, a renewal that
supersedes an agreement while preserving ledger history, and — with the
scheduler — a reminder ahead of expiry.

**Depends on.** P0 for reminders.

**Size:** M.

**Watch out for.** Renewal must not orphan the payment history. Payments
reference `agreement`; a renewal that creates a fresh row silently splits a
tenant's ledger in two. Decide whether renewal is a new agreement linked to its
predecessor, or an extension in place.

### 3.3 Document storage

**Why it matters.** Signed leases and tenant IDs live in WhatsApp today.

**Evidence today.** `shared/storage/CloudinaryStorageService.java` and
`FileStorageService.java` already exist and work — and are wired to exactly one
caller, `LandlordSettingsService` for logo upload. There is no document entity.

**What to build.** A polymorphic attachment (owner type + id, so it serves
tenants, agreements, maintenance jobs and expense receipts), with type, upload
metadata, and access scoped the same way as its parent.

**Depends on.** Nothing — the storage layer is already built and proven.

**Size:** S–M. **The best effort-to-value ratio in this document**, precisely
because the hard part already exists.

**Watch out for.** Tenant IDs are personal data. Decide retention and who may
download before, not after.

---

## Phase 4 — Platform and business model

Items that change who the product can be sold to.

### 4.1 Property owner (investor) as a distinct actor

**Why it matters.** This is the item that changes the addressable market: from
landlords managing their own buildings, to agencies managing buildings for other
people. That is a larger and better-paying customer.

**Evidence today.** `Property.landlord` points at the account owner — "landlord"
throughout this codebase means *the operator of the account*, persisted as
`landlord_id` on every table. There is no way to say "this block belongs to
owner X, whom we manage it for". No commission, management fee, payout or
statement code exists.

**What to build.** An owner entity attached to properties, read-only owner
access to their own buildings, periodic owner statements (collections less
expenses less management fee), and payout tracking. Slots into the per-property
role architecture already built in `V19` — an owner is another scoped actor,
though account-wide read of *their* subset.

**Depends on.** 2.1 (an owner statement without expenses is meaningless).
Benefits from the role work already shipped.

**Size:** XL.

**Watch out for.** The `accountOwnerId` anchor assumes one operator owns all
data in the account. An investor is inside the account but must see only their
own buildings — the same boundary problem as 1.3, from the other direction.
Do these two with a common answer, not two ad hoc ones.

### 4.2 Platform support admin

**Why it matters.** Nobody at Cognix can currently look at a customer's account
to help them without asking for their password. That is both a support ceiling
and a security practice you do not want normalised.

**Evidence today.** Every query anchors to `accountOwnerId` via
`JwtUtils.getCurrentLandlordId()`. There is no cross-account role and no
mechanism to assume one.

**What to build.** A platform-level identity outside the customer role model,
with explicit, audited, time-boxed impersonation. It must land in the audit
trail as clearly as any other action.

**Depends on.** Nothing technically. Should not ship without the audit trail
capturing it.

**Size:** L.

**Watch out for.** This is a master key. Read-only first; consider requiring
customer consent per session.

---

## Standing debt

Not a phase. Small, known, and worth folding into whatever is being touched
nearby.

| Item | Where | Why it matters |
|---|---|---|
| **No ownership transfer** | `UserManagementService.assertCanAssignRole` blocks assigning `SUPER_ADMIN`; nothing can move it | If the original registrant leaves the company, the account is permanently stranded. There is no second owner and no promotion path. This is a real customer-support incident waiting to happen. |
| **Stale API spec** | `docs/rental-api-openapi.yaml` | Predates `/properties`, `/users`, `/activity`, `X-Property-Id`, the ledger endpoints and all five roles. Actively misleading — worse than absent. |
| **Receipt number race** | `LandlordSettingsService.getNextReceiptNumber()` | Read-modify-write on `next_receipt_no` with no lock or optimistic version. Two concurrent receipts can collide. Widened caller set as of `V19`. Fix is `@Lock(PESSIMISTIC_WRITE)` on `findByLandlordId`. |
| **500 instead of 415** | `GlobalExceptionHandler` | A request with a missing or wrong `Content-Type` returns 500 rather than 415. Cosmetic until a client hits it during integration. |
| **Read-only isn't literally read-only** | `LandlordSettingsService.getSettings()` | Auto-creates the settings row, so an `ACCOUNTANT` hitting `GET /settings` first can trigger an INSERT. Harmless, but the claim "writes nothing" is not strictly true. |
| **No currency concept** | All money is `DECIMAL(12,2)`; "UGX" appears in exactly one audit string in `PaymentService` and otherwise only in the frontend | Fine while single-market. Becomes a migration the day it isn't. |

---

## Choosing what's next

Rather than following the phase numbers blindly, let the signal pick the item:

| If you're hearing… | Build |
|---|---|
| "I still reconcile MoMo messages by hand" | 1.1 Mobile money |
| "Tenants pay late because they forget" | 1.2 Notifications |
| "My phone rings all day asking about balances" | 1.3 Tenant self-service |
| "Is this building actually making money?" | 2.1 Expenses |
| "The balance in the app isn't the real balance" | 2.2 Additional charges |
| "We argue about deposits every move-out" | 2.3 Deposit settlement |
| "Repairs get forgotten" | 3.1 Maintenance |
| "Where's the signed lease?" | 3.3 Documents — cheapest item here |
| An agency asks to manage owners' buildings | 4.1 Owner actor |
| Support can't help without a password | 4.2 Platform admin |

**Two standing recommendations.**

First, whichever Phase 1 item lands first, build the **P0 scheduler** properly as
part of it. Three later features depend on it and the cost is paid once.

Second, if the goal is to change what the product is rather than fill it in,
**1.1 Mobile money** is the only item on this list that does that. Everything
else makes RentFlow a more complete version of what it already is.
