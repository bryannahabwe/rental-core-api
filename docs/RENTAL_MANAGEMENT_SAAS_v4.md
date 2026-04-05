# RENTAL MANAGEMENT SAAS
## Technical Specification Document
**Version 5.0 — MVP Edition | April 2026 | CONFIDENTIAL**

---

## Table of Contents
1. Introduction
2. System Architecture
3. Data Model
4. API Design
5. Frontend Architecture
6. Security
7. Infrastructure & Deployment
8. Development Plan
9. Engineering Conventions
10. Architecture Decisions Log

---

## 1. Introduction

### 1.1 Purpose
This document describes the technical architecture, data model, API design, frontend implementation, and deployment approach for the Rental Management SaaS platform. It serves as the single source of truth for all engineering decisions made during the design and implementation phases.

### 1.2 Project Overview
The Rental Management SaaS is a multi-tenant web application that allows landlords to manage their rental properties, tenants, agreements, and payments from a single platform. Each landlord operates in their own isolated data context. The system is designed as a Progressive Web App (PWA) accessible via mobile and desktop browsers.

### 1.3 Scope — MVP
The MVP covers the following functional areas:

- Landlord authentication (register, login, token refresh, auto-logout on expiry)
- Tenant management (CRUD + backend search + cumulative balance visibility)
- Rental unit management (CRUD + search + availability filter)
- Rental agreement management (NEW/EXISTING tenant type, opening balance, billing model, billing day, move-out, edit)
- Payment recording (cycle-based periods, overpayment rollover, split payments, date filter)
- Reporting and analytics dashboard (collection progress, outstanding tenants)
- Progressive Web App (PWA) — installable on mobile
- Responsive layout — sidebar on desktop, bottom nav + FAB on mobile

### 1.4 Out of Scope (MVP)

- Mobile money integrations (MTN, Airtel) — post-MVP
- Native mobile applications (iOS / Android)
- Email / SMS notifications
- Multi-user roles per landlord account

---

## 2. System Architecture

### 2.1 Architecture Overview
The system follows a standard three-tier architecture: a React PWA frontend, a Spring Boot REST API backend, and a PostgreSQL relational database. All components are containerized with Docker and deployed on a single Contabo VPS, fronted by an Nginx reverse proxy container.

### 2.2 Multi-Tenancy Strategy
The platform uses a shared database, shared schema approach. Every table includes a `landlord_id` foreign key that scopes all data to the authenticated landlord. Data leak risk is mitigated by strict service-layer convention — all repository queries filter by `landlord_id` extracted from JWT via `JwtUtils.getCurrentLandlordId()`.

### 2.3 Technology Stack

#### Backend

| Layer             | Technology                          |
|-------------------|-------------------------------------|
| Backend Framework | Spring Boot 4.0.x                   |
| Language          | Java 25                             |
| Build Tool        | Gradle                              |
| ORM               | Spring Data JPA + Hibernate         |
| Database          | PostgreSQL 16                       |
| Migrations        | Flyway                              |
| Auth              | Spring Security + JWT (jjwt 0.13.0) |
| Validation        | Jakarta Bean Validation             |
| API Docs          | Springdoc OpenAPI (Swagger UI)      |

#### Frontend

| Layer            | Technology                                  |
|------------------|---------------------------------------------|
| Framework        | React + Vite 8                              |
| Styling          | Tailwind CSS v4 + inline styles             |
| Server State     | React Query v5                              |
| Forms            | React Hook Form                             |
| Charts           | Recharts                                    |
| State Management | Zustand v5 (persisted)                      |
| HTTP Client      | Axios (interceptors for JWT + auto-refresh) |
| Routing          | React Router v7 + ProtectedRoute            |
| PWA              | Vite PWA Plugin                             |
| Icons            | Lucide React                                |
| Fonts            | DM Sans + DM Serif Display                  |

#### Infrastructure

| Layer            | Technology               |
|------------------|--------------------------|
| Reverse Proxy    | Nginx (Docker container) |
| Containerization | Docker + Docker Compose  |
| SSL              | Certbot / Let's Encrypt  |
| CI/CD            | GitLab CI                |
| Hosting          | Contabo VPS              |

---

## 3. Data Model

### 3.1 Entity Relationship Overview
Five core entities. Every entity (except Users) carries a `landlord_id`. All extend `BaseEntity` (UUID id, createdAt).

### 3.2 Users (Landlords)

| Column        | Type         | Constraints      |
|---------------|--------------|------------------|
| id            | UUID         | PK               |
| name          | VARCHAR(255) | NOT NULL         |
| phone_number  | VARCHAR(20)  | UNIQUE, NOT NULL |
| email         | VARCHAR(255) | UNIQUE, NULLABLE |
| password_hash | VARCHAR(255) | NOT NULL         |
| created_at    | TIMESTAMP    | NOT NULL         |

### 3.3 Rental Units

| Column       | Type          | Constraints   |
|--------------|---------------|---------------|
| id           | UUID          | PK            |
| landlord_id  | UUID          | FK → users.id |
| room_number  | VARCHAR(50)   | NOT NULL      |
| description  | TEXT          | NULLABLE      |
| rent_amount  | DECIMAL(12,2) | NOT NULL      |
| is_available | BOOLEAN       | DEFAULT TRUE  |
| created_at   | TIMESTAMP     | NOT NULL      |

### 3.4 Tenants

| Column      | Type         | Constraints   |
|-------------|--------------|---------------|
| id          | UUID         | PK            |
| landlord_id | UUID         | FK → users.id |
| name        | VARCHAR(255) | NOT NULL      |
| phone       | VARCHAR(50)  | NOT NULL      |
| email       | VARCHAR(255) | NULLABLE      |
| address     | TEXT         | NULLABLE      |
| created_at  | TIMESTAMP    | NOT NULL      |

> Room number is NOT stored on Tenant. The current unit is always derived from the active agreement.

### 3.5 Rental Agreements

| Column          | Type          | Constraints                | Description                           |
|-----------------|---------------|----------------------------|---------------------------------------|
| id              | UUID          | PK                         |                                       |
| landlord_id     | UUID          | FK → users.id              |                                       |
| tenant_id       | UUID          | FK → tenants.id            |                                       |
| unit_id         | UUID          | FK → rental_units.id       |                                       |
| start_date      | DATE          | NULLABLE                   | Move-in / first billing cycle start   |
| move_out_date   | DATE          | NULLABLE                   | Set on termination                    |
| rent_amount     | DECIMAL(12,2) | NOT NULL                   | Agreed monthly rent                   |
| deposit_amount  | DECIMAL(12,2) | NULLABLE                   |                                       |
| status          | VARCHAR(20)   | NOT NULL                   | ACTIVE / TERMINATED                   |
| tenant_type     | VARCHAR(20)   | NOT NULL DEFAULT 'NEW'     | NEW / EXISTING                        |
| opening_balance | DECIMAL(12,2) | NOT NULL DEFAULT 0         | Positive = credit, Negative = arrears |
| billing_day     | INT           | NOT NULL DEFAULT 1         | Day of month rent is due (1–28)       |
| billing_model   | VARCHAR(20)   | NOT NULL DEFAULT 'ADVANCE' | ADVANCE / ARREARS                     |
| created_at      | TIMESTAMP     | NOT NULL                   |                                       |

> **billing_day** is derived automatically from `start_date` (day of month, capped at 28 to avoid February issues). For EXISTING tenants without a start_date, defaults to 1.
>
> **billing_model:**
> - `ADVANCE` — tenant pays at the START of each cycle. First payment is due on the move-in day.
> - `ARREARS` — tenant pays at the END of each cycle. First payment is due one full cycle after move-in.
>
> **opening_balance:**
> - Positive = tenant has paid ahead (credit reduces outstanding)
> - Negative = tenant owes historical arrears (increases outstanding)
> - Only meaningful for EXISTING tenants. NEW tenants always start at 0.
> - For EXISTING tenants: set `start_date` to the first cycle you want to track going forward, and set `openingBalance` to the total unpaid amount before that date.

### 3.6 Payments

| Column            | Type          | Constraints               | Description                        |
|-------------------|---------------|---------------------------|------------------------------------|
| id                | UUID          | PK                        |                                    |
| landlord_id       | UUID          | FK → users.id             |                                    |
| tenant_id         | UUID          | FK → tenants.id           |                                    |
| unit_id           | UUID          | FK → rental_units.id      |                                    |
| agreement_id      | UUID          | FK → rental_agreements.id |                                    |
| payment_date      | DATE          | NOT NULL                  | Date payment was received          |
| amount            | DECIMAL(12,2) | NOT NULL                  | Amount paid                        |
| method            | VARCHAR(50)   | NOT NULL DEFAULT 'CASH'   | CASH only (MVP)                    |
| period_start_date | DATE          | NOT NULL                  | Start of cycle this payment covers |
| period_end_date   | DATE          | NOT NULL                  | End of cycle this payment covers   |
| expected_amount   | DECIMAL(12,2) | NOT NULL                  | Rent due for this period           |
| overpayment       | DECIMAL(12,2) | NOT NULL DEFAULT 0        | Excess above expected              |
| source            | VARCHAR(20)   | NOT NULL DEFAULT 'CASH'   | CASH / ROLLOVER                    |
| reference         | VARCHAR(255)  | NULLABLE                  |                                    |
| notes             | TEXT          | NULLABLE                  |                                    |
| created_at        | TIMESTAMP     | NOT NULL                  |                                    |

> **period_start_date / period_end_date** replace the old `period_month` / `period_year` columns. Periods are now expressed as exact date ranges matching the billing cycle (e.g. Apr 15 – May 14 for a tenant with billing_day = 15).
>
> **Overpayment rollover:** when `amount > expectedAmount`, `overpayment = amount - expectedAmount`. A ROLLOVER payment is auto-created for the next cycle. Chains recursively across multiple months.
>
> **Split payments:** Multiple CASH payments per cycle are allowed. System sums them to determine period status.

### 3.7 Flyway Migrations

| File                                        | Description                                                                     |
|---------------------------------------------|---------------------------------------------------------------------------------|
| V1__create_users.sql                        | Users table                                                                     |
| V2__create_rental_units.sql                 | Rental units                                                                    |
| V3__create_tenants.sql                      | Tenants                                                                         |
| V4__create_rental_agreements.sql            | Rental agreements                                                               |
| V5__create_payments.sql                     | Payments                                                                        |
| V7__make_agreement_fields_nullable.sql      | start_date and deposit_amount nullable                                          |
| V8__add_tenant_type_and_opening_balance.sql | tenant_type, opening_balance on agreements                                      |
| V9__add_payment_period_fields.sql           | period_month, period_year, expected_amount, overpayment, source on payments     |
| V10__add_billing_model_and_day.sql          | billing_day, billing_model on agreements                                        |
| V11__add_payment_period_dates.sql           | period_start_date, period_end_date on payments; backfill from period_month/year |

---

## 4. API Design

### 4.1 Base URL

```
https://<domain>/api/v1/...
```

### 4.2 Authentication
All endpoints except `/auth/register`, `/auth/login`, `/auth/refresh` require:

```
Authorization: Bearer <access_token>
```

| Token         | Expiry   | Purpose                 |
|---------------|----------|-------------------------|
| Access Token  | 24 hours | API authentication      |
| Refresh Token | 30 days  | Obtain new access token |

### 4.3 Pagination

| Param   | Default   | Description            |
|---------|-----------|------------------------|
| page    | 0         | Zero-based page number |
| size    | 10        | Items per page         |
| sortBy  | createdAt | Sort field             |
| sortDir | desc      | asc or desc            |

### 4.4 Endpoint Reference

#### Auth

| Method | Endpoint       | Description          | Auth |
|--------|----------------|----------------------|------|
| POST   | /auth/register | Register landlord    | No   |
| POST   | /auth/login    | Login                | No   |
| POST   | /auth/refresh  | Refresh access token | No   |

#### Tenants

| Method | Endpoint      | Description                                       |
|--------|---------------|---------------------------------------------------|
| GET    | /tenants      | List with cumulative balance (paginated + search) |
| POST   | /tenants      | Create                                            |
| GET    | /tenants/{id} | Get with cumulative balance                       |
| PUT    | /tenants/{id} | Update                                            |
| DELETE | /tenants/{id} | Delete                                            |

#### Units

| Method | Endpoint    | Description                                    |
|--------|-------------|------------------------------------------------|
| GET    | /units      | List (paginated + search + isAvailable filter) |
| POST   | /units      | Create                                         |
| GET    | /units/{id} | Get                                            |
| PUT    | /units/{id} | Update                                         |
| DELETE | /units/{id} | Delete                                         |

#### Agreements

| Method | Endpoint                 | Description                                                                 |
|--------|--------------------------|-----------------------------------------------------------------------------|
| GET    | /agreements              | List (paginated + search + status filter)                                   |
| POST   | /agreements              | Create (with billingModel, billingDay derived from startDate)               |
| GET    | /agreements/{id}         | Get                                                                         |
| PUT    | /agreements/{id}         | Update (billingModel, startDate, rentAmount, depositAmount, openingBalance) |
| PATCH  | /agreements/{id}/moveout | Record move-out and terminate                                               |

#### Payments

| Method | Endpoint       | Description                                                    |
|--------|----------------|----------------------------------------------------------------|
| GET    | /payments      | List (paginated + search + date filter)                        |
| POST   | /payments      | Record (periodStartDate + periodEndDate instead of month/year) |
| GET    | /payments/{id} | Get                                                            |

#### Reports

| Method | Endpoint           | Description                 |
|--------|--------------------|-----------------------------|
| GET    | /reports/summary   | Dashboard stats             |
| GET    | /reports/payments  | Payment report (date range) |
| GET    | /reports/occupancy | Occupancy rate              |

---

## 5. Frontend Architecture

### 5.1 Design System

- **Primary:** Deep Teal (`#0F6E56` / `#0a4a38`)
- **Font:** DM Sans (UI) + DM Serif Display (logo)
- **Layout:** Fixed sidebar (desktop 768px+) + bottom nav + FAB (mobile)
- **Responsive:** CSS classes `desktop-table`, `desktop-topbar`, `mobile-cards`, `mobile-topbar`, `sidebar-desktop`, `bottom-nav`, `page-content`, `main-content`

### 5.2 Project Structure

```
src/
├── components/
│   ├── layout/   ← PageWrapper, Sidebar, BottomNav, ProtectedRoute
│   └── ui/       ← BottomSheet, TenantDetailSheet, UnitDetailSheet,
│                    AgreementDetailSheet, PaymentDetailSheet
├── pages/        ← Dashboard, Tenants, Units, Agreements, Payments, Reports
├── hooks/        ← useTenants, useUnits, useAgreements, usePayments, useReports
├── services/     ← api.js (Axios), authService, tenantsService, unitsService,
│                    agreementsService, paymentsService, reportsService
└── store/        ← authStore (Zustand + persist)
```

### 5.3 Key Architecture Decisions

| Decision          | Choice                           | Reason                               |
|-------------------|----------------------------------|--------------------------------------|
| Auth state        | Zustand + localStorage           | Survives page refresh                |
| Server state      | React Query v5                   | Caching, background refetch          |
| HTTP client       | Axios + interceptors             | Auto-attach JWT, auto-refresh on 401 |
| Token expiry      | Auto-logout + redirect to /login | No empty broken screens              |
| CSS approach      | Inline styles                    | Tailwind v4 resets browser defaults  |
| Search            | Backend JPQL + 400ms debounce    | Works across all pages               |
| Balance           | Cumulative across all cycles     | Unpaid months carry forward          |
| Mobile nav        | Bottom nav (6 items) + FAB       | Standard mobile PWA pattern          |
| Mobile drill-down | Bottom sheet on card tap         | Progressive disclosure               |

### 5.4 Pages

| Page       | Route       | Key Features                                                                                                      |
|------------|-------------|-------------------------------------------------------------------------------------------------------------------|
| Login      | /login      | Phone or email, JWT storage                                                                                       |
| Register   | /register   | Landlord registration                                                                                             |
| Dashboard  | /dashboard  | Stats, collection progress bar, outstanding tenants, recent payments. Desktop table + mobile cards                |
| Tenants    | /tenants    | CRUD, search, status filter (ALL/PAID/PARTIAL/UNPAID), cumulative balance, progress bar, mobile drill-down        |
| Units      | /units      | CRUD, search, available/occupied filter, mobile drill-down                                                        |
| Agreements | /agreements | Create/Edit (billing model toggle, billing day hint, opening balance), move-out, status filter, mobile drill-down |
| Payments   | /payments   | Record (cycle date picker), search, date filter, mobile drill-down                                                |
| Reports    | /reports    | Summary cards, occupancy bar, payment chart, date filter                                                          |

### 5.5 PageWrapper

`PageWrapper` accepts three props:
- `title` — shown in both desktop and mobile headers
- `actions` — desktop button(s) shown top-right on desktop
- `mobileAction` — FAB shown bottom-right on mobile (circular + button)

Mobile header shows title + avatar only. Actions never appear in mobile header.

### 5.6 Mobile Detail Sheets

Tapping any mobile card opens a `BottomSheet` with full record details fetched from the individual GET endpoint. Actions (Edit, Delete, Move-out) live inside the sheet — not on the card.

Components: `TenantDetailSheet`, `UnitDetailSheet`, `AgreementDetailSheet`, `PaymentDetailSheet`.

### 5.7 Billing Cycle Display

All payment periods are displayed as exact cycle date ranges:
```
Apr 15 – May 14   (billing_day = 15)
Apr 1 – Apr 30    (billing_day = 1)
```

Helper functions used across all pages:
```js
formatCycleDate(dateStr)  // "Apr 15"
formatCycle(start, end)   // "Apr 15 – May 14"
```

### 5.8 Cumulative Balance Computation Flow

```
GET /tenants (or /tenants/{id})
  → TenantService.enrichWithBalance():
      1. Find active agreement
      2. If none → return base tenant (balance fields = null)
      3. cyclesElapsed = BillingCycleUtils.cyclesElapsed(agreement)
      4. totalEverOwed = rentAmount × cyclesElapsed
      5. openingCredit  = max(0, openingBalance)
      6. openingArrears = abs(min(0, openingBalance))
      7. totalEverOwed  = totalEverOwed - openingCredit + openingArrears
      8. totalEverPaid  = sumAllByAgreement(agreementId)
      9. outstanding    = max(0, totalEverOwed - totalEverPaid)
     10. currentCycleStart/End from BillingCycleUtils
     11. Return enriched TenantResponse
```

### 5.9 BillingCycleUtils (Backend)

Located at `shared/util/BillingCycleUtils.java`.

Key methods:
- `currentCycleStart(agreement)` — finds the current cycle start based on billing_day and today
- `cycleEnd(cycleStart, billingDay)` — returns day before next cycle start
- `cyclesElapsed(agreement)` — counts due cycles:
    - ADVANCE: current cycle immediately due, includes current
    - ARREARS: only completed cycles are due
- Guard: if `startDate` is in the future, returns 0
- Max billing_day = 28 to avoid February issues

### 5.10 Overpayment Rollover Flow

```
POST /payments (amount > rentAmount)
  → PaymentService:
      1. Save CASH payment, overpayment = amount - rentAmount
      2. nextCycleStart = periodEndDate + 1 day
      3. createRolloverPayment(agreement, overpayment, nextCycleStart)
      4. createRolloverPayment:
         a. Check duplicate — existsByAgreementIdAndPeriodStartDateAndSource()
         b. actualRollover = min(overpayment, rentAmount)
         c. remainingOverpayment = overpayment - rentAmount (if any)
         d. Save ROLLOVER payment for next cycle
         e. Recurse if remainingOverpayment > 0
```

### 5.11 Token Expiry Handling

Axios response interceptor handles 401 responses:
1. Try to refresh using stored `refreshToken` via `/auth/refresh`
2. If refresh succeeds — store new `accessToken`, retry original request silently
3. If refresh fails (token expired or missing) — call `logout()`, redirect to `/login`
4. `ProtectedRoute` additionally redirects to `/login` if `accessToken` is null

---

## 6. Security

### 6.1 JWT Strategy
- Access tokens signed with HS512, contain `userId` (landlordId)
- `JwtAuthFilter` validates every request, populates SecurityContext — no DB call
- `JwtUtils.getCurrentLandlordId()` reads landlordId from SecurityContext
- Frontend: request interceptor attaches Bearer token
- Frontend: response interceptor auto-refreshes on 401, auto-logouts on refresh failure

### 6.2 Password Encoding
BCrypt. Plain text never stored or logged.

### 6.3 CORS
Allowed: `http://localhost:5173` (dev), `https://yourdomain.com` (prod)

---

## 7. Infrastructure & Deployment

### 7.1 VPS Layout

```
Contabo VPS
├── Nginx container (ports 80 / 443)
│   ├── / → rental-frontend (port 3000)
│   └── /api/ → rental-backend (port 8081)
├── rental-backend (port 8081)
├── rental-frontend (port 3000)
└── postgres (port 5432, internal only)
```

### 7.2 Frontend Dockerfile

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 7.3 Environment Variables

| Variable              | Used By   |
|-----------------------|-----------|
| DB_URL                | Backend   |
| DB_USERNAME           | Backend   |
| DB_PASSWORD           | Backend   |
| JWT_SECRET            | Backend   |
| JWT_EXPIRY_MS         | Backend   |
| JWT_REFRESH_EXPIRY_MS | Backend   |
| VITE_API_BASE_URL     | Frontend  |
| SERVER_SSH_KEY        | GitLab CI |
| SERVER_IP             | GitLab CI |

---

## 8. Development Plan — Status

| Phase | Task                                                    | Status        |
|-------|---------------------------------------------------------|---------------|
| 1–10  | Core backend (auth, CRUD, search, Docker)               | ✅ Done        |
| 11–17 | Frontend (auth, layout, all pages, PWA)                 | ✅ Done        |
| 18    | V8 migration — tenant_type + opening_balance            | ✅ Done        |
| 19    | V9 migration — payment period fields                    | ✅ Done        |
| 20    | Agreement NEW/EXISTING + opening balance flow           | ✅ Done        |
| 21    | Payment period recording + overpayment rollover         | ✅ Done        |
| 22    | Tenant cumulative balance enrichment                    | ✅ Done        |
| 23    | Frontend — agreement modal NEW/EXISTING + billing model | ✅ Done        |
| 24    | Frontend — payment cycle date picker                    | ✅ Done        |
| 25    | Frontend — tenant balance progress bar                  | ✅ Done        |
| 26    | Frontend — dashboard mobile optimisation                | ✅ Done        |
| 27    | Frontend — mobile card drill-down (bottom sheets)       | ✅ Done        |
| 28    | V10 migration — billing_day + billing_model             | ✅ Done        |
| 29    | V11 migration — period_start_date + period_end_date     | ✅ Done        |
| 30    | BillingCycleUtils — ADVANCE/ARREARS cycle calculation   | ✅ Done        |
| 31    | Frontend — cycle date display (Apr 15 – May 14)         | ✅ Done        |
| 32    | Frontend — edit agreement modal                         | ✅ Done        |
| 33    | Token expiry — auto-logout + redirect to login          | ✅ Done        |
| 34    | GitLab CI frontend deployment                           | ⏳ In Progress |
| 35    | Nginx reverse proxy + SSL                               | ⏳ In Progress |

---

## 9. Engineering Conventions

### Backend
- Every service method calls `JwtUtils.getCurrentLandlordId()` — never from request body
- All entities extend `BaseEntity` (id: UUID, createdAt via JPA Auditing)
- DTOs for all API request/response — entities never exposed directly
- Flyway migrations: `V{n}__{description}.sql`
- All monetary values: `DECIMAL(12,2)` — no floating point
- JPQL search: `CAST(:param AS string)` to avoid PostgreSQL bytea errors
- Nullable date params in JPQL: separate repository methods
- `@Builder.Default` on Lombok builder fields with default values
- Opening balance only applied for `EXISTING` tenants
- Rollover uses unique index: `(agreement_id, period_start_date, source=ROLLOVER)`
- `sumAllByAgreement()` uses `COALESCE(SUM(...), 0)` to return zero not null
- `billing_day` derived from `start_date`, capped at 28 — never stored as user input
- `billing_day` defaults to 1 for EXISTING tenants without a start_date
- ARREARS: only completed cycles are due — current in-progress cycle not counted
- ADVANCE: current cycle is due immediately from billing day
- Future `start_date` guard: cyclesElapsed returns 0 if startDate > today

### Frontend
- All API calls via central Axios instance in `services/api.js`
- JWT attached via request interceptor
- Token refresh on 401 via response interceptor — auto-logout on refresh failure
- `ProtectedRoute` redirects to `/login` if no accessToken
- React Query keys: `['resource', params]`
- 400ms debounce on all search inputs
- Inline styles throughout (Tailwind v4 resets)
- `nullIfEmpty` converts `""` to `null` for optional fields in all onSubmit handlers
- `void queryClient.invalidateQueries(...)` to suppress ESLint promise warnings
- Balance columns: red for outstanding, green "Paid up" for zero
- Period display: `formatCycle(periodStartDate, periodEndDate)` — never month/year
- `generateCycles(agreement)` produces last 6 billing cycles for the cycle picker
- `getOrdinal(n)` formats billing day as 1st, 2nd, 3rd, 4th, 15th etc.
- Mobile cards are tappable — bottom sheet opens on tap, not inline expand
- Edit/Delete/Move-out actions live inside detail sheets, not on cards
- FAB (circular +) for mobile create actions — never in mobile header
- Mobile header: title + avatar only — no action buttons

---

## 10. Architecture Decisions Log

| Decision                   | Choice                                                              | Rejected                           | Reason                                           |
|----------------------------|---------------------------------------------------------------------|------------------------------------|--------------------------------------------------|
| Multi-tenancy              | Shared schema + landlord_id                                         | Schema-per-landlord                | Simpler ops                                      |
| Auth                       | JWT + Refresh Tokens                                                | Session-based                      | Stateless, PWA-friendly                          |
| Token expiry UX            | Auto-logout + redirect                                              | Show empty screens / unlock button | Clean, standard, no confusion                    |
| Agreement lifecycle        | Explicit move-out                                                   | Auto-expiry by end date            | End dates not always known                       |
| start_date                 | Nullable                                                            | Required                           | Legacy tenants may not know move-in date         |
| Billing cycle              | Derived from move-in day                                            | Calendar month only                | Matches real-world tenant expectations           |
| Billing day cap            | Max 28                                                              | Max 31                             | Avoids February 29/30/31 issues                  |
| Billing model              | Per agreement (ADVANCE/ARREARS)                                     | Per landlord                       | Different tenants have different arrangements    |
| Payment period             | period_start_date + period_end_date                                 | period_month + period_year         | Exact dates needed for mid-month billing cycles  |
| Overpayment                | Auto-create ROLLOVER                                                | Manual credit                      | Reduces landlord workload                        |
| Rollover dedup             | Unique index on (agreement_id, period_start_date, source)           | App-level check                    | Database-enforced                                |
| Balance computation        | Cumulative all-time                                                 | Current month only                 | Unpaid months must carry forward                 |
| Opening balance            | On Agreement                                                        | On Tenant                          | Scoped to a specific tenancy                     |
| EXISTING tenant onboarding | startDate = first future cycle, openingBalance = historical arrears | startDate = first unpaid cycle     | Simpler for landlord — enter arrears manually    |
| Period status              | Computed in PaymentResponse.from()                                  | Stored column                      | Always accurate, no sync issues                  |
| Outstanding computation    | TenantService enrichment on every GET                               | Scheduled batch                    | Real-time accuracy                               |
| Mobile layout              | Cards + bottom sheet drill-down                                     | Horizontal scroll table            | Readable on small screens                        |
| Mobile create action       | FAB (floating action button)                                        | Button in header                   | Standard mobile PWA pattern, clean header        |
| Mobile sign-out            | Avatar dropdown in header                                           | Bottom nav item                    | Saves nav space, standard pattern                |
| Desktop edit agreements    | Edit button in table row                                            | Separate edit page                 | Inline, no navigation required                   |
| CSS approach               | Inline styles                                                       | Tailwind utility classes           | Tailwind v4 resets browser defaults aggressively |

---

*Document updated to v5.0 — reflects billing cycle (ADVANCE/ARREARS), cycle date periods, BillingCycleUtils, edit agreement, token expiry handling, mobile drill-down sheets, FAB pattern, and cumulative balance computation.*