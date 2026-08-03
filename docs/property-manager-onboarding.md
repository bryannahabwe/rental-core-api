# Onboarding a Property Manager

A step-by-step guide to inviting a Property Manager, getting them into RentFlow,
and managing their access afterwards. Property Managers are staff you scope to
specific properties — they can run day-to-day operations (tenants, units,
agreements, payments) but can't see account-wide settings, reports, or user
management.

> **Looking for what a manager actually does day-to-day?** See the companion
> **Property Manager Guide** — a screen-by-screen walkthrough of Tenants, Units,
> Agreements and Payments, with each action explained: [`property-manager-guide.html`](property-manager-guide.html)
> (open in a browser) or [`property-manager-guide.pdf`](property-manager-guide.pdf).
> This document covers the *onboarding* side; that one covers the *using* side.

---

## 1. Invite the manager  *(done by an Owner or Admin)*

1. In the sidebar, open **Users** (or **Settings → User Management**).
2. Click **Invite User** (top-right).
3. Fill in the invite form:
   | Field | Notes |
   |-------|-------|
   | **Full name** | The manager's name. |
   | **Phone number** | Required. Format `07XXXXXXXX` or `+2567XXXXXXXX`. |
   | **Email** | Required — the invitation link is sent here. |
   | **Role** | Choose **Property Manager (assigned properties only)**. |
   | **Assigned properties** | Tick at least one. A manager with no property can't see anything, so the form blocks an empty selection. |
4. Click **Send invite**.

The manager is created with status **INVITED** and an email with a secure,
single-use link is sent to them.

> **If the invite email doesn't arrive:** the user is still created regardless of
> email delivery. Open their card and use **Resend invite** to send a fresh link
> (this also invalidates any older link).

---

## 2. The manager accepts the invite  *(done by the manager)*

1. They open the **link in the invitation email** → the **Accept Invite** page.
2. They set a **password** (and confirm it).
3. On submit, their account switches to **ACTIVE** and they're **logged in
   automatically**.

They don't re-enter their name, phone, or email — those were set at invite time.

---

## 3. What the manager sees once inside

A Property Manager is automatically scoped to their **first assigned property**
(they never see the account-wide "All properties" view). They land on **Tenants**.

Their sidebar is intentionally limited to operational areas:

| Visible to a manager | Hidden (Owner / Admin only) |
|----------------------|------------------------------|
| **Tenants** (landing page) | Dashboard |
| **Units** | Reports |
| **Agreements** | Properties |
| **Payments** | Users |
| | Activity |
| | Settings |

If a manager is assigned more than one property, they switch between them using
the **property switcher** at the top of the sidebar.

---

## 4. Managing a manager afterwards

Open **Users**, then click the manager's card (the **›** chevron indicates it
opens a details view). From the **User Details** sheet you can:

- **Edit** — change their **phone number**, **role**, or **assigned properties**.
  (Name and email are fixed here.)
- **Resend invite** — only for users still in **INVITED** status; issues a new
  link and cancels the previous one.
- **Deactivate** — revokes access. The account owner can't be deactivated, and
  you can't deactivate yourself.

Status badges on each card tell you where things stand:

- **INVITED** — invited but hasn't set a password yet.
- **ACTIVE** — accepted and able to log in.
- **DEACTIVATED** — access revoked.

---

## Quick reference

```
Owner/Admin: Users → Invite User → (name, phone, email, then a role per assigned property) → Send invite
Manager:     Email link → set password → auto-logged in → lands on Tenants (scoped to assigned property)
Manage:      Users → open card → Edit / Resend invite / Deactivate
```

## Roles at a glance

| Role | Scope |
|------|-------|
| **Owner** (Super Admin) | Full access; the account anchor. Can't be deactivated. |
| **Admin** | Full access; can manage everyone except other admins. |
| **Accountant** | Every property, but read-only: reports, payments, ledgers, activity. |
| **Property Manager** | Operational access to assigned properties only. |
| **Caretaker** | Assigned properties, day-to-day only: records payments and issues receipts, but can't write tenants, units or agreements, and sees no reports. |

### Different roles at different properties

Roles are held **per property**, so one person can be a Property Manager at
Kireka and a Caretaker at Ntinda. When you invite or edit them, pick the role
alongside each property rather than once for the whole account. Whichever
property they have active in the switcher is the role that applies — switching
properties switches what they can do.

Owner, Admin and Accountant are account-wide: they reach every property, so they
carry no per-property assignments.
