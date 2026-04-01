# 🏠 Rental Core API

A multi-tenant SaaS backend for rental property management. Each landlord manages their own tenants, units, agreements,
and payments in a fully isolated data context.

Built with **Spring Boot 4.0.x**, **PostgreSQL**, and **Java 25**.

---

## Tech Stack

- Java 25 + Spring Boot 4.0.x
- PostgreSQL + Spring Data JPA + Flyway
- Spring Security + JWT
- Gradle
- Docker + Docker Compose

---

## Getting Started

### Prerequisites

- Java 25
- PostgreSQL 16+
- Gradle 8+

### 1. Clone the repository

```bash
git clone https://github.com/bryannahabwe/rental-core-api.git
cd rental-core-api
```

### 2. Create the database

```sql
CREATE DATABASE rental_db;
CREATE USER rental_user WITH PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE rental_db TO rental_user;
```

### 3. Set up environment variables

Create a `.env` file in the project root:

```env
DB_USERNAME=rental_user
DB_PASSWORD=yourpassword
JWT_SECRET=your-secret-generated-with-openssl-rand-base64-64
JWT_EXPIRY_MS=900000
JWT_REFRESH_EXPIRY_MS=604800000
```

> Generate a secure JWT secret: `openssl rand -base64 64`

### 4. Run the application

```bash
./gradlew bootRun
```

- API base URL: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`

---

## License

Proprietary — All rights reserved.

*Built by [Bryan Nahabwe](https://github.com/bryannahabwe)*