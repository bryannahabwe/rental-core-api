# RENTAL MANAGEMENT SAAS — RentFlow

## Technical Specification Document

**Version 6.0 — Post-MVP | May 2026 | CONFIDENTIAL**

---

## Table of Contents

1. Introduction
2. System Architecture
3. Data Model
4. API Design
5. Frontend Architecture
6. Security & Auth
7. Infrastructure & Deployment
8. Development Status
9. Engineering Conventions
10. Architecture Decisions Log
11. Pending Features

---

## 1. Introduction

### 1.1 Purpose

Single source of truth for all engineering decisions in the RentFlow platform. Use this document to onboard into a new
chat session or resume development from any point.

### 1.2 Project Overview

RentFlow is a multi-tenant SaaS PWA for Ugandan landlords to manage rental properties, tenants, agreements, and
payments. Each landlord operates in an isolated data context. Built as a Progressive Web App accessible via mobile and
desktop browsers.

### 1.3 Live Environment

- **API:** https://rental-api.askmoozo.com/api/v1
- **Stack:** Spring Boot 4 / Java 25 / PostgreSQL (backend) + React / Vite 8 / Tailwind v4 (frontend)
- **Deployment:** Contabo VPS, Docker + Docker Compose, GitLab CI, Nginx reverse proxy

---

## 2. System Architecture

### 2.1 Multi-Tenancy

Shared database, shared schema. Every table has `landlord_id` FK. All service methods call
`JwtUtils.getCurrentLandlordId()` — never from request body.

### 2.2 Technology Stack

#### Backend

| Layer        | Technology                          |
|--------------|-------------------------------------|
| Framework    | Spring Boot 4.0.x                   |
| Language     | Java 25                             |
| Build        | Gradle                              |
| ORM          | Spring Data JPA + Hibernate         |
| Database     | PostgreSQL 16                       |
| Migrations   | Flyway                              |
| Auth         | Spring Security + JWT (jjwt 0.13.0) |
| File Storage | Cloudinary (logo uploads)           |
| Validation   | Jakarta Bean Validation             |

#### Frontend

| Layer        | Technology                  |
|--------------|-----------------------------|
| Framework    | React + Vite 8              |
| Styling      | Tailwind v4 + inline styles |
| Server State | React Query v5              |
| Forms        | React Hook Form             |
| State        | Zustand v5 (persisted)      |
| HTTP         | Axios + interceptors        |
| Routing      | React Router v7             |
| PWA          | Vite PWA Plugin             |
| Icons        | Lucide React                |
| PDF          | jsPDF                       |
| Fonts        | DM Sans + DM Serif Display  |

#### Infrastructure

| Layer         | Technology              |
|---------------|-------------------------|
| Reverse Proxy | Nginx (Docker)          |
| Containers    | Docker + Docker Compose |
| SSL           | Certbot / Let's Encrypt |
| CI/CD         | GitLab CI               |
| Hosting       | Contabo VPS             |
| File Storage  | Cloudinary              |

---

## 3. Data Model

### 3.1 Users (Landlords)

| Column        | Type         | Constraints     |
|---------------|--------------|-----------------|
| id            | UUID         | PK              |
| name          | VARCHAR(255) | NOT NULL        |
| phone_number  | VARCHAR(20)  | UNIQUE NOT NULL |
| email         | VARCHAR(255) | UNIQUE NULLABLE |
| password_hash | VARCHAR(255) | NOT NULL        |
| created_at    | TIMESTAMP    | NOT NULL        |

### 3.2 Rental Units

| Column       | Type          | Constraints  |
|--------------|---------------|--------------|
| id           | UUID          | PK           |
| landlord_id  | UUID          | FK → users   |
| room_number  | VARCHAR(50)   | NOT NULL     |
| description  | TEXT          | NULLABLE     |
| rent_amount  | DECIMAL(12,2) | NOT NULL     |
| is_available | BOOLEAN       | DEFAULT TRUE |
| created_at   | TIMESTAMP     | NOT NULL     |

### 3.3 Tenants

| Column      | Type         | Constraints |
|-------------|--------------|-------------|
| id          | UUID         | PK          |
| landlord_id | UUID         | FK → users  |
| name        | VARCHAR(255) | NOT NULL    |
| phone       | VARCHAR(50)  | NOT NULL    |
| email       | VARCHAR(255) | NULLABLE    |
| address     | TEXT         | NULLABLE    |
| created_at  | TIMESTAMP    | NOT NULL    |

### 3.4 Rental Agreements

| Column          | Type          | Notes                                                    |
|-----------------|---------------|----------------------------------------------------------|
| id              | UUID          | PK                                                       |
| landlord_id     | UUID          | FK → users                                               |
| tenant_id       | UUID          | FK → tenants                                             |
| unit_id         | UUID          | FK → rental_units                                        |
| start_date      | DATE          | NULLABLE — move-in / first billing cycle start           |
| move_out_date   | DATE          | NULLABLE — set on termination                            |
| rent_amount     | DECIMAL(12,2) | NOT NULL                                                 |
| deposit_amount  | DECIMAL(12,2) | NULLABLE                                                 |
| status          | VARCHAR(20)   | ACTIVE / TERMINATED                                      |
| tenant_type     | VARCHAR(20)   | NEW / EXISTING                                           |
| opening_balance | DECIMAL(12,2) | Default 0. Positive=credit, Negative=arrears             |
| billing_day     | INT           | 1–28. Derived from start_date day of month, capped at 28 |
| billing_model   | VARCHAR(20)   | ADVANCE / ARREARS                                        |
| created_at      | TIMESTAMP     | NOT NULL                                                 |

> **billing_day** is derived from `start_date.getDayOfMonth()`, capped at 28. Never stored as user input.
> **ADVANCE** — pays at START of cycle. **ARREARS** — pays at END of cycle.
> **opening_balance** — only meaningful for EXISTING tenants. Positive = paid ahead, Negative = owes arrears.

### 3.5 Payments

| Column            | Type          | Notes                              |
|-------------------|---------------|------------------------------------|
| id                | UUID          | PK                                 |
| landlord_id       | UUID          | FK → users                         |
| tenant_id         | UUID          | FK → tenants                       |
| unit_id           | UUID          | FK → rental_units                  |
| agreement_id      | UUID          | FK → rental_agreements             |
| payment_date      | DATE          | NOT NULL — date received           |
| amount            | DECIMAL(12,2) | NOT NULL                           |
| method            | VARCHAR(50)   | CASH (MVP)                         |
| period_start_date | DATE          | NOT NULL — start of cycle covered  |
| period_end_date   | DATE          | NOT NULL — end of cycle covered    |
| expected_amount   | DECIMAL(12,2) | NOT NULL — rent due for this cycle |
| overpayment       | DECIMAL(12,2) | Default 0 — excess above expected  |
| source            | VARCHAR(20)   | CASH / ROLLOVER                    |
| reference         | VARCHAR(255)  | NULLABLE                           |
| notes             | TEXT          | NULLABLE                           |
| created_at        | TIMESTAMP     | NOT NULL                           |

> **period_month / period_year** — legacy columns, now nullable (V12 migration). Replaced by period_start_date /
> period_end_date.
> **Overpayment rollover** — when amount > expectedAmount, excess auto-creates ROLLOVER payment for next cycle. Chains
> recursively.

### 3.6 Landlord Settings

| Column            | Type         | Default                       | Notes                                       |
|-------------------|--------------|-------------------------------|---------------------------------------------|
| id                | UUID         | PK                            |                                             |
| landlord_id       | UUID         | UNIQUE FK                     | One row per landlord                        |
| company_name      | VARCHAR(255) | NULL                          | Shown in sidebar/header instead of RentFlow |
| address           | TEXT         | NULL                          | Shown on receipts                           |
| logo_url          | TEXT         | NULL                          | Cloudinary URL                              |
| receipt_prefix    | VARCHAR(10)  | 'RCP'                         | e.g. RCP-001                                |
| next_receipt_no   | INT          | 1                             | Auto-incremented on each receipt download   |
| receipt_numbering | VARCHAR(20)  | 'AUTO'                        | AUTO / MANUAL                               |
| receipt_footer    | TEXT         | 'Thank you for your business' | Bottom of receipt                           |
| receipt_style     | VARCHAR(20)  | 'DIGITAL'                     | DIGITAL / FORMAL                            |
| created_at        | TIMESTAMP    | NOW()                         |                                             |
| updated_at        | TIMESTAMP    | NOW()                         |                                             |

> Auto-created with defaults on first GET /settings. Logo uploaded to Cloudinary via
`fileStorageService.upload(file, "landlord/logo", landlordId)`.

### 3.7 Flyway Migrations

| File                                        | Description                                                     |
|---------------------------------------------|-----------------------------------------------------------------|
| V1__create_users.sql                        | Users table                                                     |
| V2__create_rental_units.sql                 | Rental units                                                    |
| V3__create_tenants.sql                      | Tenants                                                         |
| V4__create_rental_agreements.sql            | Rental agreements                                               |
| V5__create_payments.sql                     | Payments                                                        |
| V7__make_agreement_fields_nullable.sql      | start_date, deposit_amount nullable                             |
| V8__add_tenant_type_and_opening_balance.sql | tenant_type, opening_balance                                    |
| V9__add_payment_period_fields.sql           | period_month, period_year, expected_amount, overpayment, source |
| V10__add_billing_model_and_day.sql          | billing_day, billing_model on agreements                        |
| V11__add_payment_period_dates.sql           | period_start_date, period_end_date; backfill from month/year    |
| V12__drop_old_period_columns.sql            | Make period_month, period_year nullable                         |
| V13__create_landlord_settings.sql           | landlord_settings table                                         |

---

## 4. API Design

### 4.1 Base URL

```
https://rental-api.askmoozo.com/api/v1
```

### 4.2 Authentication

All endpoints except `/auth/**` require:

```
Authorization: Bearer <access_token>
```

| Token         | Expiry   | Purpose        |
|---------------|----------|----------------|
| Access Token  | 24 hours | API calls      |
| Refresh Token | 30 days  | Silent renewal |

### 4.3 Endpoints

#### Auth

| Method | Endpoint       | Auth | Description                                                         |
|--------|----------------|------|---------------------------------------------------------------------|
| POST   | /auth/register | No   | Register landlord                                                   |
| POST   | /auth/login    | No   | Login — returns accessToken, refreshToken, name, phoneNumber, email |
| POST   | /auth/refresh  | No   | Exchange refresh token for new access token                         |

#### Tenants

| Method | Endpoint      | Description                                                                                                                          |
|--------|---------------|--------------------------------------------------------------------------------------------------------------------------------------|
| GET    | /tenants      | Paginated + search. Each tenant enriched with cumulative balance, periodStatus, currentCycleStart, currentCycleEnd, currentCyclePaid |
| POST   | /tenants      | Create                                                                                                                               |
| GET    | /tenants/{id} | Get with balance                                                                                                                     |
| PUT    | /tenants/{id} | Update                                                                                                                               |
| DELETE | /tenants/{id} | Delete                                                                                                                               |

**TenantResponse fields:**
`id, name, phone, email, address, createdAt, currentUnit, monthlyRent, currentBalance, periodStatus (PAID/PARTIAL/UNPAID), currentCycleStart (LocalDate), currentCycleEnd (LocalDate), currentCyclePaid (Boolean)`

#### Units

| Method | Endpoint    | Description                             |
|--------|-------------|-----------------------------------------|
| GET    | /units      | Paginated + search + isAvailable filter |
| POST   | /units      | Create                                  |
| GET    | /units/{id} | Get                                     |
| PUT    | /units/{id} | Update                                  |
| DELETE | /units/{id} | Delete                                  |

#### Agreements

| Method | Endpoint                 | Description                                                                 |
|--------|--------------------------|-----------------------------------------------------------------------------|
| GET    | /agreements              | Paginated + search + status filter                                          |
| POST   | /agreements              | Create (billingDay derived from startDate)                                  |
| GET    | /agreements/{id}         | Get                                                                         |
| PUT    | /agreements/{id}         | Update (billingModel, startDate, rentAmount, depositAmount, openingBalance) |
| PATCH  | /agreements/{id}/moveout | Terminate + mark unit available                                             |

#### Payments

| Method | Endpoint       | Description                                       |
|--------|----------------|---------------------------------------------------|
| GET    | /payments      | Paginated + search + date range filter            |
| POST   | /payments      | Record (periodStartDate + periodEndDate required) |
| GET    | /payments/{id} | Get                                               |

#### Reports

| Method | Endpoint           | Description                                                                    |
|--------|--------------------|--------------------------------------------------------------------------------|
| GET    | /reports/summary   | totalUnits, totalTenants, activeAgreements, totalRevenueAllTime, occupancyRate |
| GET    | /reports/payments  | Date range payment totals                                                      |
| GET    | /reports/occupancy | occupiedUnits, availableUnits, occupancyRate                                   |

#### Settings

| Method | Endpoint                 | Description                                                 |
|--------|--------------------------|-------------------------------------------------------------|
| GET    | /settings                | Get landlord settings (auto-creates defaults on first call) |
| PUT    | /settings                | Update text fields                                          |
| POST   | /settings/logo           | Upload logo (multipart/form-data, file param) → Cloudinary  |
| POST   | /settings/receipt-number | Increment and return next receipt number (e.g. RCP-001)     |

---

## 5. Frontend Architecture

### 5.1 Design System

- **Primary:** Deep Teal `#0F6E56` / `#0a4a38`
- **Font:** DM Sans (UI) + DM Serif Display (brand/logo)
- **Inline styles** throughout (Tailwind v4 resets browser defaults aggressively)

### 5.2 Project Structure

```
src/
├── components/
│   ├── layout/   ← PageWrapper, Sidebar, BottomNav, ProtectedRoute
│   └── ui/       ← BottomSheet, TenantDetailSheet, UnitDetailSheet,
│                    AgreementDetailSheet, PaymentDetailSheet
├── pages/
│   ├── DashboardPage.jsx
│   ├── TenantsPage.jsx
│   ├── UnitsPage.jsx
│   ├── AgreementsPage.jsx
│   ├── PaymentsPage.jsx       ← includes RecordPaymentModal (Record + Manual tabs)
│   ├── ReportsPage.jsx
│   ├── SettingsPage.jsx       ← menu page
│   ├── BusinessProfilePage.jsx  ← company name, address, logo upload
│   └── ReceiptSettingsPage.jsx  ← prefix, numbering, footer, style
├── hooks/
│   ← useTenants, useUnits, useAgreements, usePayments, useReports, useSettings
├── services/
│   ← api.js, authService, tenantsService, unitsService,
│     agreementsService, paymentsService, reportsService, settingsService
├── store/
│   ← authStore.js, settingsStore.js
└── utils/
    └── receiptGenerator.js   ← jsPDF DIGITAL + FORMAL receipt generation
```

### 5.3 Routing (`App.jsx`)

```
/login              → LoginPage (public)
/register           → RegisterPage (public)
/dashboard          → DashboardPage (protected)
/tenants            → TenantsPage (protected)
/units              → UnitsPage (protected, showBack)
/agreements         → AgreementsPage (protected, showBack)
/payments           → PaymentsPage (protected)
/reports            → ReportsPage (protected, showBack)
/settings           → SettingsPage (protected)
/settings/business-profile  → BusinessProfilePage (protected, showBack)
/settings/receipt-settings  → ReceiptSettingsPage (protected, showBack)
```

TokenGuard component in App.jsx checks token expiry on mount and visibilitychange.

### 5.4 Bottom Navigation (Mobile)

4 items: Dashboard, Tenants, Payments, Settings

Desktop sidebar has all links: Dashboard, Tenants, Payments, Reports, Units, Agreements.

### 5.5 Stores

#### `authStore.js` (Zustand + persist)

```js
{
    accessToken, refreshToken, landlord
:
    {
        name, phoneNumber, email
    }
,
    setAuth(data),        // called after login
        setAccessToken(token), // called after silent refresh
        logout(),             // clears all auth state
        isTokenExpired(),     // decodes JWT exp, returns true if expired
        isRefreshTokenExpired(), // same for refresh token
}
```

#### `settingsStore.js` (Zustand + persist)

```js
{
    settings: {
        companyName, logoUrl, receiptPrefix, nextReceiptNo,
            receiptNumbering, receiptFooter, receiptStyle, address
    }
,
    setSettings(settings),  // called after login + after settings update
        clearSettings(),        // called on logout
}
```

Settings are fetched immediately after login in `LoginPage.jsx` and stored in `settingsStore`. Used by `Sidebar` and
`PageWrapper` for branding.

### 5.6 Token Expiry / PWA Resume

Three-layer protection:

1. **`ProtectedRoute`** — redirects to `/login` if refresh token expired on every route render
2. **`TokenGuard`** — checks on mount + `visibilitychange` (PWA resume from background)
3. **Axios interceptor** — on 401, checks refresh token expiry first; if valid silently refreshes; if expired calls
   `logout()` + `window.location.href = "/login"`

### 5.7 PageWrapper Props

```jsx
<PageWrapper
    title="Page Title"
    actions={<button>Desktop button</button>}   // desktop topbar right
    mobileAction={<button>+</button>}           // FAB bottom-right mobile
    showBack                                    // adds ← back arrow in mobile header
>
```

Mobile header: brand name (from settingsStore or "RentFlow") + page title as subtitle + avatar dropdown.
Desktop topbar: page title + actions.

### 5.8 BottomSheet Component

Renders as:

- **Mobile** — slides up from bottom (85vh, rounded top corners)
- **Desktop** — centered modal (520px wide, vertically centered)

Uses `mobile-cards` / `desktop-table` CSS classes for responsive switching.

### 5.9 Balance Computation (Backend — TenantService)

```
cyclesElapsed = BillingCycleUtils.cyclesElapsed(agreement)
totalEverOwed = rentAmount × cyclesElapsed
openingCredit  = max(0, openingBalance)
openingArrears = abs(min(0, openingBalance))
totalEverOwed  = totalEverOwed - openingCredit + openingArrears
totalEverPaid  = sumAllByAgreement(agreementId)
outstanding    = max(0, totalEverOwed - totalEverPaid)
currentCycleStart/End = BillingCycleUtils.currentCycleStart/cycleEnd
currentCyclePaid = sumByAgreementAndCycle(agreementId, cycleStart, cycleEnd) >= rentAmount
```

### 5.10 BillingCycleUtils (Backend)

- `currentCycleStart(agreement)` — finds current cycle start based on billingDay
- `cycleEnd(cycleStart, billingDay)` — day before next cycle start
- `cyclesElapsed(agreement)`:
    - ADVANCE: current cycle counts immediately
    - ARREARS: only completed cycles count
    - Guard: if startDate in future → returns 0
- Max billingDay = 28

### 5.11 Dashboard Metrics

```
totalMonthlyRent = sum of all active tenants' monthlyRent
totalOutstanding = sum of currentBalance for UNPAID + PARTIAL tenants (cumulative)
paidThisMonth    = sum of monthlyRent for tenants where currentCyclePaid === true
collectionPct    = paidThisMonth / totalMonthlyRent × 100

Progress bar uses collectionPct (current cycle paid / total monthly rent)
Desktop shows: Monthly Rent | Paid This Month | Total Outstanding
Mobile shows:  Monthly | Paid | Outstanding (3 boxes)
```

### 5.12 Receipt Generation (`src/utils/receiptGenerator.js`)

```js
generateReceipt(payment, settings, receiptNumber)
```

Two styles driven by `settings.receiptStyle`:

**DIGITAL** — Branded, dark green header, amount box, details grid, signature line.

**FORMAL** — Matches physical receipt book. RECEIPT title box, dotted lines, "Received from", "The sum of Shillings", "
Being payment of", amount box with border, signed line.

Both styles:

- Load logo from Cloudinary via `loadImageAsBase64()` (canvas conversion for jsPDF)
- Convert amount to words via `numberToWords()` (e.g. "One Hundred Eighty Thousand Shillings Only")
- A5 portrait format
- Filename: `{receiptNumber}-{tenantName}.pdf`

**Manual receipt** fields (when `payment.isManual === true`):

- `manualPeriod` — free text period string (e.g. "1 Apr – 30 Apr")
- `balance` — manually entered remaining balance
- Period display uses `manualPeriod` instead of `formatCycle()`
- Balance uses `payment.balance` instead of computing from expectedAmount

### 5.13 Record Payment Modal — Two Tabs

**Record Payment tab:**

- Select active agreement (tenant + unit auto-populated)
- Billing cycle picker (last 6 cycles, generated from billingDay)
- Amount, payment date, reference, notes
- On submit: `POST /payments` → success screen → download receipt option

**Manual Receipt tab:**

- Select tenant (name + currentUnit auto-populated from tenant list)
- Amount, period (free text), payment date, payment by (Cash/Mobile Money/Bank Transfer/Cheque)
- Balance remaining, reference, notes
- Style toggle (Digital / Formal) — overrides settings default
- On submit: fetches receipt number → generates PDF → downloads immediately → NO database write

### 5.14 Branding (White-label per landlord)

After login, settings are fetched and stored in `settingsStore`. All UI reads from store:

- **Sidebar (desktop):** shows `logoUrl` (img) or `companyName` (text) + "Property Management" subtitle
- **Mobile header:** shows `companyName` as brand line, page title as subtitle
- **Avatar dropdown:** shows logo/initial + companyName
- **Login page:** always shows "RentFlow" (before landlord identified)
- **Receipts:** show logo + companyName + address

### 5.15 Settings Pages

**BusinessProfilePage** (`/settings/business-profile`):

- Logo upload (file picker → preview → POST /settings/logo → Cloudinary)
- Company name, address
- Updates settingsStore immediately on save

**ReceiptSettingsPage** (`/settings/receipt-settings`):

- Numbering mode toggle (AUTO / MANUAL)
- Receipt prefix (e.g. RCP)
- Next receipt number (with live preview: RCP-001)
- Receipt style default (Digital / Formal)
- Footer message

---

## 6. Security

### 6.1 JWT Strategy

- HS512 signed, contain `userId` (landlordId)
- `JwtAuthFilter` validates every request, populates SecurityContext
- `JwtUtils.getCurrentLandlordId()` reads from SecurityContext — no DB call
- Frontend: request interceptor attaches Bearer token
- Frontend: response interceptor auto-refreshes on 401

### 6.2 Password

BCrypt. Never stored or logged as plain text.

### 6.3 CORS

Allowed origins: `http://localhost:5173` (dev), production domain.

---

## 7. Infrastructure & Deployment

### 7.1 VPS Layout

```
Contabo VPS
├── Nginx (80/443) → routes / to frontend, /api/ to backend
├── rental-backend (port 8082)
├── rental-frontend (port 3000)
└── postgres (port 5432, internal only)
```

### 7.2 Environment Variables

| Variable                                                         | Used By   |
|------------------------------------------------------------------|-----------|
| DB_URL, DB_USERNAME, DB_PASSWORD                                 | Backend   |
| JWT_SECRET, JWT_EXPIRY_MS, JWT_REFRESH_EXPIRY_MS                 | Backend   |
| CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET | Backend   |
| VITE_API_BASE_URL                                                | Frontend  |
| SERVER_SSH_KEY, SERVER_IP                                        | GitLab CI |

### 7.3 Cloudinary

Logo uploads via `fileStorageService.upload(file, "landlord/logo", landlordId)`.

- `public_id = landlordId` → re-upload overwrites previous logo
- Stored at `rentflow/logos/{landlordId}`
- URL stored in `landlord_settings.logo_url`

---

## 8. Development Status

| Feature                                                              | Status                         |
|----------------------------------------------------------------------|--------------------------------|
| Auth (register, login, refresh, auto-logout)                         | ✅ Done                         |
| Tenant CRUD + search                                                 | ✅ Done                         |
| Unit CRUD + search + availability                                    | ✅ Done                         |
| Agreement CRUD + billing model + billing day                         | ✅ Done                         |
| Payment recording + cycle picker + overpayment rollover              | ✅ Done                         |
| Cumulative balance computation                                       | ✅ Done                         |
| currentCyclePaid field on TenantResponse                             | ✅ Done                         |
| Reports (summary, payments, occupancy)                               | ✅ Done                         |
| Dashboard — stats + outstanding + recent payments                    | ✅ Done                         |
| Mobile PWA — bottom nav + FAB + bottom sheets                        | ✅ Done                         |
| Token expiry — TokenGuard + ProtectedRoute + interceptor             | ✅ Done                         |
| Settings — GET/PUT /settings (auto-create defaults)                  | ✅ Done                         |
| Logo upload — Cloudinary via POST /settings/logo                     | ✅ Done                         |
| Receipt numbering — POST /settings/receipt-number                    | ✅ Done                         |
| BusinessProfilePage                                                  | ✅ Done                         |
| ReceiptSettingsPage                                                  | ✅ Done                         |
| White-label branding (logo + company name in sidebar/header)         | ✅ Done                         |
| Receipt generation — DIGITAL style (jsPDF)                           | ✅ Done                         |
| Receipt generation — FORMAL style (jsPDF)                            | ✅ Done                         |
| Receipt download from PaymentDetailSheet                             | ✅ Done                         |
| Receipt download after recording payment                             | ✅ Done                         |
| Manual receipt tab (no DB write, tenant picker + manual fields)      | ✅ Done                         |
| Settings page — mobile menu with Business Profile + Receipt Settings | ✅ Done                         |
| GitLab CI frontend deployment                                        | ✅ Done                         |
| Multi-property support                                               | ✅ Done                         |
| User management & roles (SUPER_ADMIN / ADMIN / PROPERTY_MANAGER)     | ✅ Done                         |
| Audit trail & activity feed                                          | ✅ Done                         |
| MTN/Airtel mobile money integration                                  | ⏳ Post-MVP                     |
| Email/SMS notifications                                              | ⏳ Post-MVP                     |

---

## 9. Engineering Conventions

### Backend

- Every service method calls `JwtUtils.getCurrentLandlordId()` — never from request body
- All entities extend `BaseEntity` (UUID id, createdAt via JPA Auditing)
- DTOs for all API request/response — entities never exposed directly
- Flyway: `V{n}__{description}.sql`
- All monetary values: `DECIMAL(12,2)` — no floating point
- JPQL search: `CAST(:param AS string)` to avoid PostgreSQL bytea errors
- `@Builder.Default` on Lombok builder fields with defaults
- `sumAllByAgreement()` uses `COALESCE(SUM(...), 0)` — never returns null
- `billing_day` derived from `start_date`, capped at 28 — never user input
- ARREARS: only completed cycles counted. ADVANCE: current cycle counted immediately
- Future `start_date` guard: `cyclesElapsed` returns 0 if startDate > today
- Rollover dedup: unique index on `(agreement_id, period_start_date, source=ROLLOVER)`
- Settings auto-created with defaults on first GET — never 404
- Receipt number incremented atomically in `getNextReceiptNumber()`
- Logo re-upload uses same Cloudinary `public_id` (landlordId) — overwrites previous

### Frontend

- All API calls via central `src/services/api.js` (Axios instance)
- JWT attached via request interceptor
- 401 → try refresh → if refresh expired → logout() + redirect to /login
- `ProtectedRoute` checks `isRefreshTokenExpired()` on every render
- `TokenGuard` checks on mount + `visibilitychange`
- React Query keys: `['resource', params]`
- 400ms debounce on all search inputs
- `nullIfEmpty(val)` — converts `""` to `null` for optional fields in all onSubmit handlers
- `void queryClient.invalidateQueries(...)` to suppress ESLint promise warnings
- Inline styles throughout — Tailwind v4 resets aggressively
- `formatUGXShort`: `.toFixed(2).replace(/\.?0+$/, "")M` → `1.51M` not `1.5M`
- `getOrdinal(n)` → "1st", "2nd", "15th" for billing day display
- `generateCycles(agreement)` — produces last 6 billing cycles for cycle picker
- Mobile: cards tappable → BottomSheet opens. Edit/Delete/Move-out inside sheet.
- FAB (circular +) for mobile create. Never in mobile header.
- `settingsStore` loaded after login, cleared on logout
- Branding reads from `settingsStore` — fallback to "RentFlow" if not set

---

## 10. Architecture Decisions Log

| Decision            | Choice                                                              | Reason                                    |
|---------------------|---------------------------------------------------------------------|-------------------------------------------|
| Multi-tenancy       | Shared schema + landlord_id                                         | Simpler ops                               |
| Auth                | JWT + Refresh Tokens                                                | Stateless, PWA-friendly                   |
| Token expiry UX     | Auto-logout + redirect to /login                                    | No blank screens                          |
| Billing cycle       | Derived from move-in day (billingDay)                               | Real-world tenant expectation             |
| Billing day cap     | Max 28                                                              | Avoids Feb 29/30/31 issues                |
| Billing model       | Per agreement (ADVANCE/ARREARS)                                     | Different tenants, different arrangements |
| Payment period      | period_start_date + period_end_date                                 | Exact dates for mid-month cycles          |
| Overpayment         | Auto-create ROLLOVER                                                | Reduces landlord workload                 |
| Balance             | Cumulative all-time                                                 | Unpaid months carry forward               |
| Opening balance     | On Agreement                                                        | Scoped to a specific tenancy              |
| EXISTING onboarding | startDate = first future cycle, openingBalance = historical arrears | Simple for landlord                       |
| Period status       | Computed in TenantService enrichment                                | Always accurate, no sync issues           |
| currentCyclePaid    | sumByAgreementAndCycle >= rentAmount                                | Period-based not payment-date-based       |
| Dashboard progress  | paidThisMonth (currentCyclePaid=true) / totalMonthlyRent            | Avoids mixing cumulative + monthly        |
| Logo storage        | Cloudinary                                                          | CDN, auto-resize, no VPS disk management  |
| Receipt format      | A5 portrait, jsPDF                                                  | Works on mobile, WhatsApp-shareable       |
| Receipt numbering   | Atomic increment in DB                                              | No duplicate numbers                      |
| Manual receipt      | No DB write, tenant picker only                                     | Clean separation from payment records     |
| Settings branding   | settingsStore (Zustand persisted)                                   | Available everywhere without re-fetch     |
| Mobile nav          | 4 items (Dashboard, Tenants, Payments, Settings)                    | Clean, daily workflow items only          |
| Settings page       | Menu → sub-pages (Business Profile, Receipt Settings)               | Scalable, familiar iOS pattern            |
| BottomSheet         | Bottom on mobile, centered modal on desktop                         | Appropriate for each viewport             |
| CSS approach        | Inline styles                                                       | Tailwind v4 resets browser defaults       |

---

## 11. Pending Features

### Multi-Property Support ✅ Implemented

Landlords can create named properties (e.g. "Kamwokya Flats", "Nansana
Apartments"), assign units/tenants/agreements/payments to one, filter every view
by the active property, and switch between properties (or an "All properties"
aggregate) from a switcher in the sidebar/top bar.

**How it works:**

- New `properties` table (`landlord_id` FK). Migration `V14__add_properties.sql`
  creates it, seeds one default property per existing landlord (named from
  `landlord_settings.company_name`, else "My Property"), and backfills a
  NOT NULL `property_id` FK on `rental_units`, `tenants`, `rental_agreements`,
  and `payments`.
- `Property` CRUD lives in `modules/properties` (`/properties` endpoints);
  registration auto-creates a default property; a property can't be deleted
  while it still owns units or tenants.
- **Reads** (lists + reports) scope to the active property via an optional
  `X-Property-Id` header, resolved at `JwtUtils.getCurrentPropertyId()`. Absent
  header = landlord-wide aggregate ("All properties"). Repo queries use the
  `(:propertyId IS NULL OR x.property.id = :propertyId)` idiom.
- **Writes**: unit/tenant create require `propertyId`; agreements derive it from
  the chosen unit (and reject cross-property tenant/unit pairings); payments
  inherit it from their agreement.
- Frontend: `propertyStore` holds the selection; `services/api.js` injects the
  header; the selection is folded into React Query keys so switching refetches.
  `PropertySwitcher` sits in the sidebar/mobile top bar; a `Properties` page
  manages the list.

### User Management & Roles ✅ Implemented

Each account (anchored by the owner's user id, stored as `landlord_id`) can have
multiple users with roles:

Roles are either **account-wide** (they reach every property) or
**property-scoped** (they reach only the properties listed for them in
`user_properties`, and are held *per property*).

- **SUPER_ADMIN** — account-wide. The account owner; full control incl. user
  management. Can only be created by self-registration, and cannot be assigned,
  deactivated, or transferred.
- **ADMIN** — account-wide. Full-access staff (all properties, reports,
  settings); may manage every role except another admin.
- **ACCOUNTANT** — account-wide but **read-only**: reports, payments, tenant
  ledgers, and the activity feed. Writes nothing.
- **PROPERTY_MANAGER** — property-scoped; manages tenants/units/agreements/
  payments on assigned properties. No reports, no user management, no property
  CRUD, no deletes, and no "All properties" aggregate. Reads settings and draws
  receipt numbers so it can issue receipts, but cannot edit branding.
- **CARETAKER** — property-scoped and narrower still: records payments and reads
  tenants/units/agreements on assigned properties, and issues receipts. Cannot
  write tenants, units, or agreements, and sees no portfolio reports.

**Per-property roles.** The same person can be a Property Manager at one
property and a Caretaker at another. `users.role` holds the *account* role —
derived from the assignments for scoped staff, so it can never disagree with
what they actually hold somewhere — while `user_properties.role` holds the role
at each property.

**How it works:**

- `users` gains `role`, `status` (ACTIVE/INVITED/DEACTIVATED), and
  `account_owner_id`; `phone_number`/`password_hash` are nullable (invited staff
  join by email). Migration `V15__add_user_roles_and_assignments.sql` backfills
  every existing user as an active SUPER_ADMIN owner and creates `user_properties`.
  `V19__add_caretaker_and_accountant_roles.sql` widens `chk_users_role` and adds
  `user_properties.role`, backfilled to `PROPERTY_MANAGER` (every pre-existing
  assignment row belonged to a manager).
- The JWT filter reloads the `User` from the DB each request and builds the
  principal from the entity (`accountOwnerId`, `role`, `userId`), so role changes
  and deactivations take effect immediately. `getCurrentLandlordId()` still
  returns the account anchor, leaving all existing scoping untouched.
- **Effective role.** The filter parses `X-Property-Id` *before* it builds the
  principal, so for scoped staff it stores the role held at the active property
  (`PropertyAccessGuard.effectiveRoleFor`). Every `@PreAuthorize` check is
  therefore property-aware with no extra authorization layer, and a demotion at
  one property takes effect on the next request with no token reissue. With no
  header, the role resolves against the same default property
  `requireAccessibleProperty()` falls back to; a header naming an unassigned
  property falls back to that default too, so a bogus header can't promote a
  caretaker.
- Authorization: `@EnableMethodSecurity` + `@PreAuthorize` answer *may this role
  do this kind of thing*; `PropertyAccessGuard` answers *is this property one of
  theirs*. Account-wide roles pass straight through the guard.
- Login and `GET /users` return `assignedPropertyIds` plus a `propertyRoles` map;
  clients resolve the applicable role as
  `propertyRoles[activePropertyId] ?? role`.
- Invites carry `assignments: [{propertyId, role}]` for a scoped role. The older
  flat `propertyIds` list is still accepted — it is folded into assignments at the
  request's top-level role — so clients predating per-property roles keep working.
- Invites: `POST /users/invite` creates an INVITED user + a short-lived
  `purpose=INVITE` JWT emailed via the Brevo stack (`modules/notification`;
  logs the link when `app.mail.brevo.enabled=false`). `POST /auth/accept-invite`
  sets a password and activates. Login accepts phone **or** email.
- Frontend: role in `authStore`; role-aware sidebar/bottom-nav + route guards;
  managers land on Tenants and the switcher shows only assigned properties;
  `UsersPage` (invite/deactivate) and a public `AcceptInvitePage`. The React
  Query cache is cleared on login so a new user never sees a prior session's data.

### Audit Trail & Activity Feed ✅ Implemented

Every significant action writes an immutable record whose human-readable sentence
is built server-side and stored, so the UI just displays it (e.g. *"Ada recorded
a UGX 300,000 payment for Tom Okello (A1)."*). Pattern ported from
`greenlink-cargo-api`'s `audit/` package.

**How it works:**

- New `modules/audit` package: an INSERT-only `AuditTrail` entity
  (`account_id` + optional `property_id`, actor, `module`, `action`,
  `affected_record_id`, and the pre-built `statement`), two enums, an
  `AuditWriter` service, and a read `AuditController` at `/activity`.
- `auditWriter.record(...)` is called at the end of each state-changing service
  method (tenant/unit/agreement/payment/property/user creates, updates via a
  `field 'old' → 'new'` diff, deletes, move-outs, invites) inside the same
  `@Transactional`, so the action and its audit row commit together. Auth events
  (login, accept-invite) use an explicit-actor overload. The actor's name comes
  from `AuthenticatedUser.name`.
- Migration `V16__create_audit_trail.sql` creates the table + indexes and a
  Postgres **immutability trigger** (UPDATE/DELETE are rejected).
- Read: `GET /activity` (paged; `module`/`action`/`search`/`from`/`to` filters),
  `@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")`, scoped to the account —
  managers get 403.
- Frontend: an admin-only **Activity** page (`pages/ActivityPage.jsx`) — a
  chronological feed of sentences with a module icon, actor, and relative time,
  plus a module filter + search.

### Other Pending

- MTN / Airtel mobile money integration
- Email / SMS payment notifications
- Tenant portal (tenant-facing view of their balance + receipts)

> These three, and everything else known to be missing, are covered in detail in
> [PRODUCT_ROADMAP.md](PRODUCT_ROADMAP.md) — phased by dependency and leverage,
> with the current state of the code cited for each. Keep that document as the
> source of truth for what to build next; this section is only a summary.