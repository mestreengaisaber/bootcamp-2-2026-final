# AGENTS.md

## What is this repo

Java 21 + Spring Boot 4.1.0 airline reservation system — 6 independent microservices + an API gateway, each with its own `../pom.xml` and `mvnw`. **Not a Maven multi-module build.** The root `../pom.xml` and `../src` are a leftover monolith; do not build from root expecting multi-module resolution.

## Build & test — per service

Each service is built/tested in its own directory. There is no single command to build everything.

```bash
# inside any service directory (flight-service, booking-service, etc.)
./mvnw clean package -DskipTests   # build jar
./mvnw test                         # run tests
```

Services: `api-gateway`, `auth-service`, `booking-service`, `flight-service`, `payment-service`, `checkin-service`, `notification-service`.

## Run locally

Each service defaults to `dev` profile (H2 in-memory DB). To run a single service:

```bash
cd flight-service
./mvnw spring-boot:run
```

Kafka is required for booking, flight, and payment services. Start infra first:

```bash
docker compose up -d kafka db
```

`docker compose up --build` starts everything (services + Kafka + PostgreSQL + observability stack).

## Stack facts

- **Database per microservice** — shared PostgreSQL, separate schemas: `bootcamp_flight`, `bootcamp_booking`, `bootcamp_payment`, `bootcamp_checkin`, `bootcamp_auth` (created by `../db-init/01-init-databases.sql`)
- **Flyway** handles migrations in each service (`src/main/resources/db/migration/`)
- **JWT auth at the gateway** — api-gateway validates tokens; internal services trust `X-User-Id` header. Internal service poms do NOT include `spring-boot-starter-oauth2-resource-server` (adding it breaks everything — see `TO_DO_Final.md`)
- **Kafka** for async inter-service events. Booking and payment services use the **outbox pattern** (OutboxRelay → Kafka). flight-service is consumer-only (no outbox)
- **Spring Boot 4.1.0 + Jackson 2 bridge** — spring-kafka 4.1 requires `com.fasterxml.jackson.core:jackson-databind` explicitly because Boot 4.1 ships Jackson 3 only. Without this dependency, startup fails with `NoClassDefFoundError: com.fasterxml.jackson.databind.JavaType`

## Architecture pattern

flight-service and booking-service follow a **layered hexagonal-style layout**:

```
domain/          — pure domain models (no JPA annotations)
application/     — use cases, ports (interfaces), events, commands, exceptions
infrastructure/  — JPA entities, repositories, controllers, config
```

Other services (payment, auth, checkin, notification) use a simpler flat structure.

## Kafka gotchas

- flight-service consumes `booking.created` and `booking.cancelled` — two different event types on the same consumer. Do NOT set a global `spring.json.value.default.type`; set it per-listener via `@KafkaListener(properties = ...)`
- Producers send plain JSON without type headers (outbox relay). Consumer deserialization depends on the per-listener `default.type`
- Idempotency: each service has a `processed_events` table. Check `existsById` before processing

## Key ports

| Service            | Port |
|--------------------|------|
| api-gateway        | 8080 |
| auth-service       | 8087 |
| booking-service    | 8082 |
| flight-service     | 8086 |
| payment-service    | 8083 |
| checkin-service    | 8084 |
| notification-service | 8085 |

## Package naming

All services use `dakota.software.<servicename>` (e.g., `dakota.software.flightservice`, `dakota.software.bookingservice`).

## CI/CD

No GitHub Actions workflows exist yet. The `TO_DO_Final.md` notes that per-module CI should be adapted, not a root wrapper.

## Scope

MVP covers: flight search, booking, payment, check-in. Out of scope: loyalty programs, baggage, analytics, DDD/hexagonal on non-flight services.

## Key reference files

- `TO_DO_Final.md` — current task status and saga implementation plan
- `arquitectura-Pfinal-Saga-Hexagonal.md` — full architecture doc
- `../docker-compose.yml` — all services, infrastructure, and their env vars
- `LockPesimista-SeatAvailable.md` — seat reservation concurrency details
