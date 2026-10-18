# broadcastmail-api

Spring Boot 4.1 HTTP API for BroadcastMail — handles authentication, campaign management, onboarding, billing, and audience preview.

## Overview

The API is the primary backend service. It serves the Next.js frontend, enforces plan limits, integrates with Stripe for billing, and connects to users' Supabase databases for audience resolution.

See [ARCHITECTURE.md](../ARCHITECTURE.md) for how this service fits into the full system.

## Stack

- Java 25 · Spring Boot 4.1
- PostgreSQL (via broadcastmail-common)
- Stripe SDK
- springdoc-openapi

## API docs

Swagger UI available at `/swagger-ui.html` when running locally.

## Getting started

See [DEVELOPERS.md](DEVELOPERS.md) for local setup and environment variables.

## Contributing

See [CONTRIBUTING.md](https://github.com/broadcastmail/.github/blob/main/CONTRIBUTING.md).

## License

MIT
