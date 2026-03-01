# QForge Backend (Microservices)

This backend is designed as multiple microservices, not a monolith.

## Services

- `gateway-service`: real API gateway (Spring Cloud Gateway), unified entry, JWT validation, route forwarding.
- `auth-service`: username/password login and JWT token issuing.

## Shared Library

- `libs/common-contract`: shared DTO/event contracts to keep inter-service schemas consistent.

## Infrastructure

- Shared infrastructure for all services:
- MySQL (`qforge` single shared database)
- Redis
- RabbitMQ
- Nacos (service registry/discovery)
- `docker-compose.yml`: full stack for current MVP (infra + auth + gateway)
- `infra/docker-compose.yml`: infra-only stack

## Runtime Stack

- Spring Boot 3
- Java 17
- Shared MySQL
- RabbitMQ for async events
- Redis for cache/session needs
- Nacos for service discovery (Spring Cloud Alibaba)
- Swagger (springdoc) on each service

## Quick Start (Image Build Mode)

From `backend/`:

```bash
docker compose up --build
```

Before first startup, initialize MySQL schema manually (Flyway is not used):

```bash
mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uqforge -pqforge qforge < sql/init-schema.sql
```

This starts:
- `gateway-service` on `http://localhost:8080`
- `auth-service` on `http://localhost:8088`
- MySQL, Redis, RabbitMQ, Nacos

## JWT Login (MVP)

- Unified login endpoint (through gateway): `POST http://localhost:8080/api/auth/login`
- Default account:
- username: `admin`
- password: `admin123`
- Protected test endpoint:
- `GET http://localhost:8080/api/auth/me` (with `Authorization: Bearer <token>`)
- `GET http://localhost:8080/gateway/ping` (with `Authorization: Bearer <token>`)

Gateway routing rule:
- `/api/auth/**` -> `auth-service` (`StripPrefix=1`, so `/api/auth/login` forwards to `/auth/login`)

## Swagger

- Auth: `http://localhost:8088/swagger-ui.html`
- Gateway: `http://localhost:8080/swagger-ui.html`

In `dev` profile, swagger is public (no JWT required).

## Develop Debug Container (No Rebuild Loop)

Use the develop compose file:

```bash
docker compose -f docker-compose.dev.yml up -d
```

This starts:
- infra (`mysql`, `redis`, `rabbitmq`, `nacos`)
- one `dev-container` that runs `auth-service` + `gateway-service` from source with `spring-boot:run`

Characteristics:
- no app image rebuild on each debug run
- shared Maven cache volume for faster incremental startup
- startup script clears possible running Java service processes before booting both services

Useful commands:

```bash
docker compose -f docker-compose.dev.yml logs -f dev-container
docker compose -f docker-compose.dev.yml restart dev-container
docker compose -f docker-compose.dev.yml down
```
