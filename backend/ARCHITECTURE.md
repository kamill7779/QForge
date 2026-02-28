# QForge Microservice Architecture

## Request Flow (Current MVP Scope)

1. Frontend calls `gateway-service` login endpoint `POST /api/auth/login`.
2. Gateway routes the request to `auth-service` (`/auth/login`) through service discovery.
3. `auth-service` verifies account and signs JWT token.
4. Frontend calls protected APIs with `Authorization: Bearer <token>` via gateway.
5. Gateway validates JWT first, then forwards request to target service.

## Data Ownership

- `auth-service`: user account table and JWT issuing.
- `gateway-service`: route entry, JWT validation, forwarding to discovered services.

Services are configured to use one shared MySQL database (`qforge`) for now.

## Integration Patterns

- Sync calls: Spring Cloud Gateway + REST routes between gateway and domain services.
- Service discovery: Nacos (Spring Cloud Alibaba).
- Async middleware: RabbitMQ is pre-integrated for next-stage events.
- Shared contracts: `libs/common-contract`.
