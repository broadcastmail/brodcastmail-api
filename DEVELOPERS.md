# Developing broadcastmail-api

## Prerequisites

- Java 25
- Maven 3.9+
- Docker (for Testcontainers)
- A Railway PostgreSQL instance or local PostgreSQL

## Environment variables

```bash
# PostgreSQL
PGHOST=
PGPORT=5432
PGDATABASE=railway
PGUSER=postgres
PGPASSWORD=

# Encryption — 32 character random string
ENCRYPTION_KEY=

# Supabase OAuth
SUPABASE_CLIENT_ID=
SUPABASE_CLIENT_SECRET=

# Stripe
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=
STRIPE_PRO_PRICE_ID=

# Frontend
APP_FRONTEND_URL=http://localhost:3000
```

## Running locally

```bash
./mvnw spring-boot:run
```

API starts on `http://localhost:8080`.
Swagger UI available at `http://localhost:8080/swagger-ui.html`.

## Running tests

```bash
# All tests except local Supabase integration tests
./mvnw verify -Dgroups='!local'

# Including local Supabase integration tests (requires supabase start)
./mvnw verify
```

## Flyway migrations

Migrations run automatically on startup. Migration files live in `src/main/resources/db/migration`.

Only the API runs migrations — worker and webhooks have Flyway disabled.

## Architecture

See [ARCHITECTURE.md](https://github.com/broadcastmail/.github/blob/main/ARCHITECTURE.md) for how this service fits into the full system.

## Database schema

See [DATABASE.md](https://github.com/broadcastmail/.github/blob/main/DATABASE.md) for full schema documentation.
