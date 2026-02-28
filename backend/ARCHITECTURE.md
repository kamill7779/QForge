# QForge Microservice Architecture

## Request Flow (Current MVP Scope)

1. Frontend calls `auth-service` login with username/password.
2. `auth-service` verifies account and signs JWT token.
3. Frontend calls protected APIs with `Authorization: Bearer <token>`.
4. `gateway-service` validates JWT and allows protected route access.

## Data Ownership

- `auth-service`: user account table and JWT issuing.
- `gateway-service`: JWT validation and protected API gateway entry.

Services are configured to use one shared MySQL database (`qforge`) for now.

## Integration Patterns

- Sync calls: REST between gateway and domain services.
- Async middleware: RabbitMQ is pre-integrated for next-stage events.
- Shared contracts: `libs/common-contract`.
