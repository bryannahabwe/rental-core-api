# RENTAL MANAGEMENT SAAS
## Technical Specification Document
**Version 4.0 — MVP Edition | April 2026 | CONFIDENTIAL**

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

- Landlord authentication (register, login, token refresh)
- Tenant management (CRUD + backend search + balance visibility)
- Rental unit management (CRUD + search + availability filter)
- Rental agreement management (NEW / EXISTING tenant type, opening balance, move-out)
- Payment recording (period-based, overpayment rollover, split payments, date filter)
- Reporting and analytics dashboard (collection progress, outstanding tenants)
- Progressive Web App (PWA) — installable on mobile

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
The platform uses a shared database, shared schema approach. Every table includes a `landlord_id` foreign key that scopes all data to the authenticated landlord.

- Simpler schema management — Flyway migrations run once against a single schema
- Lower operational overhead — no per-landlord provisioning required
- Scales comfortably to thousands of landlords on a single PostgreSQL instance
- Data leak risk mitigated by strict service-layer convention: all repository queries filter by `landlord_id` extracted from JWT token via `JwtUtils.getCurrentLandlordId()`

### 2.3 Technology Stack

#### Backend

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Backend Framework | Spring Boot 4.0.x | Structured, production-grade, team's primary stack |
| Language | Java 25 | Latest stable, modern features |
| Build Tool | Gradle | Faster builds, flexible DSL |
| ORM | Spring Data JPA + Hibernate | Native Spring integration |
| Database | PostgreSQL 16 | Relational model, robust, JSONB support |
| Migrations | Flyway | Version-controlled SQL migrations |
| Auth | Spring Security + JWT (jjwt 0.13.0) | Stateless auth for PWA and API clients |
| Validation | Jakarta Bean Validation | Declarative request validation |
| API Docs | Springdoc OpenAPI (Swagger UI) | Auto-generated interactive docs |

#### Frontend

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Framework | React + Vite 8 | Fast builds, component-based |
| Styling | Tailwind CSS v4 | Mobile-first, utility classes |
| Server State | React Query v5 | Caching, background sync, loading states |
| Forms | React Hook Form | Performant, minimal re-renders |
| Charts | Recharts | Lightweight, composable charts |
| State Management | Zustand v5 | Auth state, minimal boilerplate |
| HTTP Client | Axios | Interceptors for JWT attach + auto-refresh |
| Routing | React Router v7 | Client-side routing, protected routes |
| PWA | Vite PWA Plugin | Offline capability, installable on mobile |
| Icons | Lucide React | Consistent, tree-shakeable icon set |
| Fonts | DM Sans + DM Serif Display | Clean, professional typography |

#### Infrastructure

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Reverse Proxy | Nginx (Docker container) | Static file serving, API routing, SSL |
| Containerization | Docker + Docker Compose | Consistent deployment |
| SSL | Certbot / Let's Encrypt | Free automated HTTPS |
| CI/CD | GitLab CI | Automated build and deploy on push to main |
| Hosting | Contabo VPS | Existing VPS, more control |

---

## 3. Data Model

### 3.1 Entity Relationship Overview
The core data model consists of five entities. Every entity (except Users) carries a `landlord_id` to enforce tenant isolation. All entities extend `BaseEntity` which provides `id` (UUID) and `createdAt` (Timestamp) managed by Spring Data JPA Auditing.

### 3.2 Users (Landlords)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Primary key |
| name | VARCHAR(255) | NOT NULL | Full name of landlord |
| phone_number | VARCHAR(20) | UNIQUE, NOT NULL | Primary login identifier |
| email | VARCHAR(255) | UNIQUE, NULLABLE | Optional email address |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt hashed password |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Account creation timestamp |

### 3.3 Rental Units

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Primary key |
| landlord_id | UUID | FK → users.id, NOT NULL | Owning landlord |
| room_number | VARCHAR(50) | NOT NULL | Unit identifier (e.g. A1, Room 3) |
| description | TEXT | NULLABLE | Unit description |
| rent_amount | DECIMAL(12,2) | NOT NULL | Current listed monthly rent |
| is_available | BOOLEAN | NOT NULL, DEFAULT TRUE | Availability flag — auto-managed by agreements |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Record creation time |

### 3.4 Tenants

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Primary key |
| landlord_id | UUID | FK → users.id, NOT NULL | Owning landlord |
| name | VARCHAR(255) | NOT NULL | Full name of tenant |
| phone | VARCHAR(50) | NOT NULL | Phone number |
| email | VARCHAR(255) | NULLABLE | Email address |
| address | TEXT | NULLABLE | Home/previous address |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Record creation time |

> Note: Room number is NOT stored on the Tenant entity. The current unit is always derived from the tenant's active Rental Agreement.

### 3.5 Rental Agreements

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Primary key |
| landlord_id | UUID | FK → users.id, NOT NULL | Owning landlord |
| tenant_id | UUID | FK → tenants.id, NOT NULL | Associated tenant |
| unit_id | UUID | FK → rental_units.id, NOT NULL | Associated unit |
| start_date | DATE | NULLABLE | Move-in date — nullable for legacy/existing tenants |
| move_out_date | DATE | NULLABLE | Recorded when tenant actually leaves |
| rent_amount | DECIMAL(12,2) | NOT NULL | Agreed rent — defaults to unit rent if not specified |
| deposit_amount | DECIMAL(12,2) | NULLABLE | Deposit paid — nullable for existing tenants |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / TERMINATED |
| tenant_type | VARCHAR(20) | NOT NULL, DEFAULT 'NEW' | NEW / EXISTING |
| opening_balance | DECIMAL(12,2) | NOT NULL, DEFAULT 0 | Financial position at onboarding. Positive = credit, Negative = arrears |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Record creation time |

> **Tenant Type Logic:**
> - `NEW` — tenant moving in fresh. Move-in date required, full history from day one.
> - `EXISTING` — tenant already living there, being onboarded into the system mid-tenancy. Move-in date optional. Opening balance captures their financial position as of onboarding date.
>
> **Opening Balance:**
> - Positive (+) = tenant has paid ahead (credit)
> - Negative (−) = tenant owes arrears (debt)
> - Zero = fully up to date
> - Only applied to EXISTING tenants. NEW tenants always start at 0.
> - Used in outstanding balance calculation: `outstanding = rentAmount - totalPaid - max(0, openingBalance)`

### 3.6 Payments

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Primary key |
| landlord_id | UUID | FK → users.id, NOT NULL | Owning landlord |
| tenant_id | UUID | FK → tenants.id, NOT NULL | Paying tenant |
| unit_id | UUID | FK → rental_units.id, NOT NULL | Unit being paid for |
| agreement_id | UUID | FK → rental_agreements.id, NOT NULL | Agreement this payment belongs to |
| payment_date | DATE | NOT NULL | Date payment was received |
| amount | DECIMAL(12,2) | NOT NULL | Amount paid |
| method | VARCHAR(50) | NOT NULL, DEFAULT 'CASH' | Payment method (MVP: CASH only) |
| period_month | INT | NOT NULL | Month this payment covers (1–12) |
| period_year | INT | NOT NULL | Year this payment covers |
| expected_amount | DECIMAL(12,2) | NOT NULL | Rent due for this period — copied from agreement at time of payment |
| overpayment | DECIMAL(12,2) | NOT NULL, DEFAULT 0 | Excess above expected amount |
| source | VARCHAR(20) | NOT NULL, DEFAULT 'CASH' | CASH / ROLLOVER |
| reference | VARCHAR(255) | NULLABLE | Receipt or reference number |
| notes | TEXT | NULLABLE | Additional notes |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Record creation time |

> **Payment Period Logic:**
> - Each payment records the month and year it covers (`period_month`, `period_year`)
> - Multiple payments allowed per period per agreement (split payments)
> - `period_status` is computed dynamically: `PAID` / `PARTIAL` / `UNPAID` / `ROLLOVER`
>
> **Overpayment Rollover:**
> - When `amount > expectedAmount`, `overpayment = amount - expectedAmount`
> - System auto-creates a ROLLOVER payment for the next month
> - Rollover chains recursively if overpayment exceeds next month's rent too
> - Unique index prevents duplicate ROLLOVER records per period per agreement
>
> **Split Payments:**
> - Multiple CASH payments allowed per period — system sums them to determine period status
> - `PARTIAL` status shown when `SUM(payments for period) < expectedAmount`

### 3.7 Flyway Migrations

| File | Description |
|------|-------------|
| V1__create_users.sql | Users table |
| V2__create_rental_units.sql | Rental units table |
| V3__create_tenants.sql | Tenants table |
| V4__create_rental_agreements.sql | Rental agreements table |
| V5__create_payments.sql | Payments table |
| V7__make_agreement_fields_nullable.sql | Makes start_date and deposit_amount nullable |
| V8__add_tenant_type_and_opening_balance.sql | Adds tenant_type and opening_balance to agreements |
| V9__add_payment_period_fields.sql | Adds period_month, period_year, expected_amount, overpayment, source to payments |

---

## 4. API Design

### 4.1 Base URL & Versioning

```
https://<domain>/api/v1/...
```

### 4.2 Authentication
All endpoints except `/auth/register`, `/auth/login` and `/auth/refresh` require a Bearer JWT token:

```
Authorization: Bearer <access_token>
```

| Token Type | Expiry | Purpose |
|-----------|--------|---------|
| Access Token | 24 hours | API authentication |
| Refresh Token | 30 days | Obtain new access token |

### 4.3 Pagination

| Parameter | Default | Description |
|-----------|---------|-------------|
| page | 0 | Page number (zero-based) |
| size | 10 | Items per page |
| sortBy | createdAt | Field to sort by |
| sortDir | desc | Sort direction (asc / desc) |

### 4.4 Search Parameters

| Endpoint | Search Param | Additional Filters |
|----------|-------------|-------------------|
| GET /tenants | `search` (name, phone, email) | — |
| GET /units | `search` (roomNumber, description) | `isAvailable` (boolean) |
| GET /agreements | `search` (tenantName, roomNumber) | `status` (ACTIVE / TERMINATED) |
| GET /payments | `search` (tenantName, roomNumber, reference) | `from`, `to` (date range), `tenantId`, `agreementId` |

### 4.5 Endpoint Reference

#### Auth

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | /auth/register | Register a new landlord account | No |
| POST | /auth/login | Login and receive JWT tokens | No |
| POST | /auth/refresh | Refresh access token | No |

#### Tenants

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /tenants | List tenants with balance info (paginated + search) |
| POST | /tenants | Create a tenant |
| GET | /tenants/{id} | Get a tenant with balance info |
| PUT | /tenants/{id} | Update a tenant |
| DELETE | /tenants/{id} | Delete a tenant |

> Each tenant response now includes: `currentUnit`, `monthlyRent`, `currentBalance`, `periodStatus`, `currentPeriodMonth`, `currentPeriodYear` — all computed from their active agreement and current month's payments.

#### Rental Units

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /units | List all units (paginated + search + isAvailable filter) |
| POST | /units | Create a unit |
| GET | /units/{id} | Get a unit |
| PUT | /units/{id} | Update a unit |
| DELETE | /units/{id} | Delete a unit |

#### Agreements

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /agreements | List all agreements (paginated + search + status filter) |
| POST | /agreements | Create agreement (NEW or EXISTING tenant type) |
| GET | /agreements/{id} | Get an agreement |
| PUT | /agreements/{id} | Update an agreement |
| PATCH | /agreements/{id}/moveout | Record move-out and terminate |

#### Payments

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /payments | List all payments (paginated + search + date filter) |
| POST | /payments | Record a payment (with period month/year) |
| GET | /payments/{id} | Get a payment |

#### Reports

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /reports/summary | Dashboard summary stats |
| GET | /reports/payments | Payment report (date range) |
| GET | /reports/occupancy | Occupancy rate report |

### 4.6 Standard Error Response

```json
{
  "success": false,
  "error": "BAD_REQUEST",
  "message": "Unit A1 already has an active agreement"
}
```

---

## 5. Frontend Architecture

### 5.1 Design System

- **Primary color:** Deep Teal (`#0F6E56` / `#0a4a38`)
- **Font:** DM Sans (UI) + DM Serif Display (logo/display)
- **Style:** Clean white surfaces, dark teal sidebar, subtle borders
- **Layout:** Fixed sidebar (desktop) + bottom nav (mobile) — fully responsive PWA

### 5.2 Project Structure

```
rental-frontend/
├── src/
│   ├── components/
│   │   ├── layout/     ← Sidebar, BottomNav, PageWrapper, ProtectedRoute
│   │   └── ui/         ← Shared UI components
│   ├── pages/          ← One file per route
│   ├── hooks/          ← React Query hooks per module
│   ├── services/       ← Axios API calls per module
│   ├── store/          ← Zustand auth store (persisted to localStorage)
│   └── utils/          ← formatCurrency, formatDate
├── public/
│   ├── icons/          ← PWA icons (192x192, 512x512)
│   └── favicon.ico
├── Dockerfile
├── nginx.conf
├── .gitlab-ci.yml
├── .env.production
└── vite.config.js
```

### 5.3 Key Architecture Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Auth state | Zustand + localStorage persist | Survives page refresh, zero DB calls |
| Server state | React Query v5 | Caching, background refetch, loading states |
| HTTP client | Axios + interceptors | Auto-attach JWT, auto-refresh on 401 |
| Routing | React Router v7 + ProtectedRoute | Mirrors JwtAuthFilter on backend |
| Search | Backend search via query params + debounce | Works across all pages, not just current page |
| CSS approach | Inline styles | Tailwind v4 resets browser defaults aggressively |
| PWA | Vite PWA Plugin + Workbox NetworkFirst | Offline API caching, installable on mobile |
| Balance visibility | Enriched TenantResponse | One API call returns tenant + balance data |

### 5.4 Pages

| Page | Route | Features |
|------|-------|---------|
| Login | /login | Phone or email login, JWT storage, redirect to dashboard |
| Register | /register | Landlord registration, redirect to login |
| Dashboard | /dashboard | Summary stats, monthly collection progress, outstanding tenants, recent payments |
| Tenants | /tenants | CRUD, search, payment status filter (ALL/PAID/PARTIAL/UNPAID), balance columns |
| Units | /units | CRUD, search, available/occupied filter, toggle |
| Agreements | /agreements | Create (NEW/EXISTING toggle, opening balance), search, status filter, move-out |
| Payments | /payments | Record (period month/year, overpayment warning, partial warning), search, date filter |
| Reports | /reports | Summary cards, occupancy bar, payment chart |

### 5.5 Balance Computation Flow

```
GET /tenants (or /tenants/{id})
  → For each tenant, TenantService:
      1. Find active agreement via findFirstByTenantIdAndLandlordIdAndStatus()
      2. If none → return base tenant (balance fields = null)
      3. If found:
         a. SUM payments for current month/year via sumByAgreementAndPeriod()
         b. Apply opening balance credit: effectivePaid = totalPaid + max(0, openingBalance)
         c. outstanding = rentAmount - effectivePaid
         d. Compute status: PAID / PARTIAL / UNPAID
      4. Return enriched TenantResponse via withBalance()
```

### 5.6 Overpayment Rollover Flow

```
POST /payments (amount > agreement.rentAmount)
  → PaymentService:
      1. Save CASH payment with overpayment = amount - expectedAmount
      2. Call createRolloverPayment(agreement, overpayment, month, year)
      3. createRolloverPayment:
         a. Calculate next month/year
         b. Check if ROLLOVER already exists for that period (skip if yes)
         c. actualRollover = min(overpayment, rentAmount)
         d. remainingOverpayment = overpayment - rentAmount (if any)
         e. Save ROLLOVER payment for next period
         f. Recurse if remainingOverpayment > 0 (chains across months)
```

---

## 6. Security

### 6.1 JWT Strategy
- Access tokens signed with HS512, contain `userId` (landlordId) and `sub` (username)
- `JwtAuthFilter` intercepts every request, validates token, populates SecurityContext — no DB call
- `JwtUtils.getCurrentLandlordId()` reads landlordId from SecurityContext in any service method
- Frontend: Axios request interceptor attaches Bearer token to every request
- Frontend: Axios response interceptor auto-refreshes token on 401 and retries original request

### 6.2 AuthenticatedUser

```java
public record AuthenticatedUser(UUID landlordId, String username) implements UserDetails {}
```

### 6.3 CORS
Allowed origins: `http://localhost:5173` (dev), `https://yourdomain.com` (production)

### 6.4 Password Encoding
Passwords hashed using `BCryptPasswordEncoder`. Plain text never stored or logged.

---

## 7. Infrastructure & Deployment

### 7.1 Contabo VPS Layout

```
Contabo VPS
├── Nginx container (ports 80 / 443)
│   ├── Proxies / → rental-frontend (port 3000)
│   └── Proxies /api/ → rental-backend (port 8081)
├── Docker Compose
│   ├── rental-backend (port 8081)
│   ├── rental-frontend (port 3000)
│   └── postgres (port 5432, internal only)
├── cognix_network (external Docker network)
└── Certbot — auto-renew Let's Encrypt SSL
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

### 7.3 Frontend nginx.conf

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

### 7.4 Docker Compose

```yaml
services:
  rental-backend:
    build: .
    ports:
      - "8081:8081"
    environment:
      DB_URL: ${DB_URL}
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRY_MS: ${JWT_EXPIRY_MS}
      JWT_REFRESH_EXPIRY_MS: ${JWT_REFRESH_EXPIRY_MS}
    networks:
      - cognix_network
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: '0.5'

  rental-frontend:
    build:
      context: ./rental-frontend
      dockerfile: Dockerfile
    ports:
      - "3000:80"
    networks:
      - cognix_network
    restart: unless-stopped

networks:
  cognix_network:
    external: true
```

### 7.5 GitLab CI — Frontend

```yaml
stages:
  - deploy

deploy:
  stage: deploy
  image: alpine:latest
  before_script:
    - apk add --no-cache openssh-client
    - mkdir -p ~/.ssh
    - chmod 700 ~/.ssh
    - echo "$SERVER_SSH_KEY" | base64 -d > ~/.ssh/id_ed25519
    - chmod 600 ~/.ssh/id_ed25519
    - ssh-keyscan -H $SERVER_IP >> ~/.ssh/known_hosts
  script:
    - ssh root@$SERVER_IP "cd /opt/cognix/rental-frontend &&
        git pull &&
        docker compose -f /opt/cognix/docker-compose.yml up -d --build rental-frontend"
  only:
    - main
```

### 7.6 Environment Variables

| Variable | Description | Used By |
|----------|-------------|---------|
| DB_URL | PostgreSQL JDBC connection string | Backend |
| DB_USERNAME | Database user | Backend |
| DB_PASSWORD | Database password | Backend |
| JWT_SECRET | HS512 signing secret (min 256 bits) | Backend |
| JWT_EXPIRY_MS | Access token expiry in ms (default: 86400000) | Backend |
| JWT_REFRESH_EXPIRY_MS | Refresh token expiry in ms (default: 2592000000) | Backend |
| VITE_API_BASE_URL | Production API base URL | Frontend (.env.production) |
| SERVER_SSH_KEY | Base64 encoded SSH private key | GitLab CI |
| SERVER_IP | Contabo VPS IP address | GitLab CI |

---

## 8. Development Plan

### 8.1 Build Order — Status

| Phase | Task | Status |
|-------|------|--------|
| 1 | Spring Boot project setup + Gradle config | ✅ Done |
| 2 | Flyway migrations V1–V7 | ✅ Done |
| 3 | Auth module (register, login, JWT) | ✅ Done |
| 4 | Units CRUD + pagination | ✅ Done |
| 5 | Tenants CRUD + pagination | ✅ Done |
| 6 | Agreements CRUD + move-out endpoint | ✅ Done |
| 7 | Payments CRUD + pagination | ✅ Done |
| 8 | Reports endpoints | ✅ Done |
| 9 | Backend search — all four endpoints | ✅ Done |
| 10 | Docker deployment on Contabo VPS | ✅ Done |
| 11 | Frontend — Auth screens (Login, Register) | ✅ Done |
| 12 | Frontend — Layout shell (Sidebar, BottomNav, PageWrapper) | ✅ Done |
| 13 | Frontend — Dashboard | ✅ Done |
| 14 | Frontend — All CRUD pages | ✅ Done |
| 15 | Frontend — Reports page | ✅ Done |
| 16 | Frontend — Search & filter on all pages | ✅ Done |
| 17 | PWA configuration | ✅ Done |
| 18 | V8 migration — tenant_type + opening_balance on agreements | ✅ Done |
| 19 | V9 migration — period fields + overpayment + source on payments | ✅ Done |
| 20 | TenantType + PaymentSource enums | ✅ Done |
| 21 | Agreement NEW/EXISTING flow + opening balance | ✅ Done |
| 22 | Payment period recording + overpayment rollover | ✅ Done |
| 23 | Tenant balance enrichment in TenantService | ✅ Done |
| 24 | Frontend — Agreement modal NEW/EXISTING toggle | ✅ Done |
| 25 | Frontend — Payment modal period + overpayment warning | ✅ Done |
| 26 | Frontend — Tenants page balance columns + status filter | ✅ Done |
| 27 | Frontend — Dashboard collection progress + outstanding section | ✅ Done |
| 28 | GitLab CI for frontend | ⏳ In Progress |
| 29 | Nginx reverse proxy config + SSL | ⏳ In Progress |

---

## 9. Engineering Conventions

### 9.1 Backend

- Every service method calls `JwtUtils.getCurrentLandlordId()` — `landlord_id` never comes from request body
- All entities extend `BaseEntity` (id: UUID, createdAt: Timestamp via JPA Auditing)
- Use DTOs for all API request/response — JPA entities never exposed directly
- Flyway migration files follow naming: `V{n}__{description}.sql`
- Agreement termination always explicit — triggered by landlord recording move-out date
- All monetary values stored as `DECIMAL(12,2)` — no floating point
- JPQL search queries use `CAST(:param AS string)` to avoid PostgreSQL `bytea` type errors
- Nullable date params in JPQL use separate repository methods to avoid `cannot cast bytea to date`
- Move-out date validation guards against null `startDate` before comparing dates
- `@Builder.Default` required on Lombok builder fields with default values
- Opening balance only applied for `EXISTING` tenants — `NEW` tenants always start at zero
- Rollover payments use a unique index to prevent duplicates per period per agreement
- Rollover chains recursively across months if overpayment exceeds multiple months' rent
- `sumByAgreementAndPeriod()` uses `COALESCE(SUM(...), 0)` to return zero instead of null

### 9.2 Frontend

- All API calls go through the central Axios instance in `services/api.js`
- JWT token attached via request interceptor — never manually in individual calls
- Token refresh handled automatically via response interceptor on 401
- React Query query keys follow pattern: `['resource', params]`
- Search uses 400ms debounce to avoid excessive API calls while typing
- Inline styles used throughout to avoid Tailwind v4 CSS reset issues
- `invalidateQueries` calls prefixed with `void` to suppress ESLint promise warnings
- `isAvailable` toggle in UnitModal uses `useState` — never DOM manipulation
- Auth state persisted to `localStorage` via Zustand `persist` middleware
- Overpayment and underpayment warnings computed in real time from watched form values
- Balance columns show red for outstanding, green "Paid up" for zero balance
- Dashboard outstanding section only renders when at least one tenant has UNPAID/PARTIAL status
- Monthly collection progress bar computed from tenant list: `totalExpected - totalOutstanding`

---

## 10. Architecture Decisions Log

| Decision | Choice Made | Rejected Alternative | Reason |
|----------|------------|---------------------|--------|
| Multi-tenancy | Shared schema + landlord_id | Schema-per-landlord | Simpler ops, sufficient isolation for MVP |
| Backend | Spring Boot 4.0.x + Java 25 | Node.js + Express | Team's primary expertise |
| Build tool | Gradle | Maven | Faster builds, flexible DSL |
| Mobile strategy | PWA | Native iOS / Android | Single codebase, 90% of native UX |
| Auth | JWT + Refresh Tokens | Session-based auth | Stateless, works for PWA + API clients |
| JWT library | jjwt 0.13.0 | Spring OAuth | Lightweight, straightforward |
| Migrations | Flyway | Liquibase | Simpler SQL-first approach |
| Agreement lifecycle | Explicit move-out by landlord | Auto-expiry by end date | End dates not always known in advance |
| Agreement start date | Nullable | Required | Legacy tenants with unknown move-in dates |
| Deposit amount | Nullable | Required | Existing tenants already inside |
| Auth identifier | Phone number primary, email optional | Email only | Uganda market — phone more reliable |
| Login field | Single username field (phone or email) | Separate fields | Flexible, cleaner UX |
| SecurityContext principal | Lightweight AuthenticatedUser record | Full User entity | Zero DB calls per request |
| Pagination | Custom PagedResponse wrapper | Spring's Page directly | Consistent response structure for frontend |
| Payment methods | Cash/manual only | MTN / Airtel API | Removes 3rd-party complexity from MVP |
| Frontend framework | React + Vite 8 | Angular / Vue | Component-based, fast builds |
| CSS approach | Inline styles | Tailwind utility classes | Tailwind v4 resets browser defaults aggressively |
| Search implementation | Backend JPQL search | Client-side filtering | Works across all pages not just current page |
| JPQL search cast | CAST(:param AS string) | Direct :param | PostgreSQL cannot infer type of null params |
| JPQL date filter | Separate repo methods per condition | Single nullable query | PostgreSQL cannot cast null bytea to date |
| Deployment | GitLab CI + git pull + docker compose | Registry push/pull | Matches existing backend CI pattern |
| Frontend container | Nginx serving React build | Node.js serving | Lighter, faster, production standard |
| Tenant type | NEW / EXISTING flag on Agreement | Separate tenant tables | Same person can be new in one unit, existing in another |
| Opening balance | On Agreement entity | On Tenant entity | Scoped to a specific tenancy, not the person |
| Payment periods | period_month + period_year columns | Derived from payment_date | Explicit — payment date ≠ period covered |
| Split payments | Multiple payments per period allowed | One payment per period | Landlords often collect in instalments |
| Overpayment handling | Auto-create ROLLOVER for next period | Manual credit adjustment | Reduces landlord workload, mirrors real accounting |
| Rollover dedup | Unique index on (agreement_id, month, year, source=ROLLOVER) | Application-level check | Database-enforced, prevents race conditions |
| Balance visibility | Enriched TenantResponse | Separate /balances endpoint | One API call, simpler frontend, no data joining |
| Period status | Computed in PaymentResponse.from() | Stored column | Always accurate, no sync issues |
| Outstanding computation | TenantService enrichment on every GET | Scheduled batch job | Real-time accuracy, acceptable perf for MVP scale |

---

*Document updated to reflect payment period, overpayment rollover, tenant type, opening balance, and balance visibility features. Version 4.0 — All decisions are subject to revision as the project evolves.*
