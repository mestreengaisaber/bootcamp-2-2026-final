# TO_DO Final — Saga Kafka

> **Actualizado 2026-08-25** tras completar los pasos 0-6 y 8 del bloque flight.
> El patrón de referencia es `payment-service` (outbox + relay + consumer idempotente), funcionando end-to-end.

## ✅ booking-service — lado Kafka (COMPLETADO)

Todas las tareas del bloque original están implementadas y verificadas en código:

| # | Tarea | Estado |
|---|-------|--------|
| 1 | `paymentMethod` en el flujo (request → command → dominio → DB) | ✅ Ampliado 2026-08-23: **enum `PaymentMethod` en el dominio** (`STRIPE`, `PAYPAL`, `MOCK`). Copia local del vocabulario — no se comparte la clase Java con payment-service. Traducción String→enum en el borde web (controller devuelve 400 si el valor no existe). |
| 2 | Migraciones — columna `payment_method` + tablas outbox/processed | ✅ `V2__add_payment_method.sql`, `V3__create_outbox_and_processed_tables.sql` |
| 3 | Dependencias Kafka en pom | ✅ `spring-boot-starter-kafka`, `spring-boot-starter-json`, `jackson-databind` (bridge Jackson 2 para spring-kafka 4.1 en Boot 4.1) |
| 4 | Config Kafka yaml | ✅ `application.yaml` (dev) + `application-prod.yaml` |
| 5 | Eventos BookingCreated / PaymentProcessed / BookingCancelled | ✅ Los tres |
| 6 | Outbox (entity + repo + port + adapter + relay) | ✅ Patrón replicado de payment-service |
| 7 | `@Transactional` en `createBookingUseCase` | ✅ Booking + outbox en la misma transacción |
| 8 | Consumidor `payment.processed` con idempotencia | ✅ `PaymentProcessedKafkaListener` + tabla `processed_events`; APPROVED → confirm(), DECLINED → cancel() + compensación |
| 9 | Productor `booking.cancelled` | ✅ Emitido cuando el pago es DECLINED |
| 10 | Wiring en `AppBookingConfig` | ✅ Todos los beans |

### Cambio de seguridad aplicado (2026-08-23)

Eliminado `spring-boot-starter-oauth2-resource-server` del pom de booking-service:
estaba sin configurar → activaba la cadena de seguridad por defecto de Spring Boot → 401 a todo.
Patrón vigente: **edge authentication** — el gateway valida el JWT e inyecta `X-User-Id`;
los servicios internos confían en la red (ver sección 8 del doc de arquitectura).

## 🔶 flight-service — consumidor (EN PROGRESO: pasos 0-6 y 8 de 9 hechos, actualizado 2026-08-25)

Plan por micro-pasos (0-9). Hecho y verificado con suite 20/20 verde:

| # | Paso | Estado |
|---|------|--------|
| 0 | Limpieza índice git (8 ficheros legacy "AD") | ✅ |
| 1 | Dependencias Kafka en `pom.xml` (`spring-boot-starter-kafka`, `spring-boot-starter-json`, `jackson-databind`) | ✅ |
| 2 | Migración `V4__create_processed_events.sql` (solo dedupe, sin outbox — flight no publica) | ✅ |
| 3 | Config Kafka `application.yaml` (group `flight-group`; SIN `default.type` global → se fija por-listener en el paso 7) | ✅ |
| 4 | Copias del contrato: `BookingCreatedEvent` (15 campos) + `BookingCancelledEvent` (11 campos) en `application/event/` | ✅ |
| 5 | `ReleaseSeatsUseCase` + `FlightService.release()` con `@Transactional` (reutiliza lock pesimista). Fix latente: `reserve()` había perdido su `@Transactional` en el refactor SOLID del PR #33 | ✅ |
| 6 | Wiring `AppFlightConfig` (un bean por puerto-in: ISP) + tests: 4 nuevos de release (2 unit FakeRepo + 2 slice persistencia). Suite 20/20 | ✅ |
| 7 | **Listeners idempotentes** `BookingCreatedKafkaListener`→reserve / `BookingCancelledKafkaListener`→release | ⬜ Incluye crear `ProcessedEventEntity` + `ProcessedEventJpaRepository` (la tabla existe pero NO las clases JPA) y fijar `spring.json.value.default.type` POR-LISTENER vía propiedad de `@KafkaListener` (los productores envían JSON plano sin cabeceras de tipo y flight consume DOS tipos). Política de errores decidida: catch+log+skip para no recuperables (vuelo inexistente nunca sanará con reintentos), igual que payment |
| 8 | docker-compose: `SPRING_KAFKA_BOOTSTRAP_SERVERS: broker:29092` + `depends_on: kafka` en flight-service | ✅ 2026-08-25 (mismo patrón que booking/payment) |
| 9 | Saga e2e completa: feliz (MOCK→CONFIRMED, asientos reservados) + compensación (DECLINED→asientos liberados), verificando topics en kafdrop | ⬜ |

## ⬜ Docker / infra

| # | Tarea | Detalle |
|---|-------|---------|
| 1 | ✅ ~~Env vars Kafka booking~~ — `SPRING_KAFKA_BOOTSTRAP_SERVERS: broker:29092` + `depends_on: kafka` presentes. |
| 2 | ✅ ~~Añadir lo mismo a flight-service~~ — hecho 2026-08-25, mismo patrón que booking/payment. |
| 3 | ⬜ Levantar stack completo y verificar servicios Running (`docker compose ps`). |
| 4 | ⬜ Probar saga end-to-end: POST booking (paymentMethod=MOCK) → kafdrop ve booking.created → payment procesa → payment.processed → booking CONFIRMED. Caso negativo: valor inválido → hoy se salta el evento con log.error y la reserva queda PENDING (mejora futura: validar en origen — ya hecho con el enum — y decidir política de eventos huérfanos). |

## ⬜ Pendientes fuera de la saga

| Servicio | Estado |
|---|---|
| **notification-service** (US-009/010/011) | Esqueleto solo (Application + yaml). Consumidor reactivo de `booking.created` + `payment.processed`. |
| **checkin-service** (US-011) | Esqueleto solo (Application + yaml). Bloqueado por flujo previo. |
| **Seguridad** (doc arquitectura §8.5) | ⬜ Probar matriz de roles login → 200/403. ⬜ Ownership en GET/cancel propios. |
| **GitHub Actions CI/CD** | No existe. Adaptar patrón del monolito a matriz por módulo (no usar el wrapper de la raíz). |

### Mejoras futuras de la saga

- ⬜ **`BookingRejectedEvent`** (`booking.rejected`) — cerrar el hueco de validación asíncrona del `flightId`: hoy booking acepta cualquier id sin comprobar que el vuelo existe (no puede sin llamada síncrona, prohibida). Si `reserveSeatsUseCase` falla en flight (vuelo inexistente / sin asientos), la reserva queda PENDING para siempre. Propuesta: el catch del listener de flight publica `booking.rejected {bookingId, reason}` y booking la consume → `cancel()`, dejando estado final limpio. Es el mismo patrón que ya se usa con el pago (PENDING → resultado por evento), NO request/reply por Kafka (anti-patrón: sincronía disfrazada). Detalle en sección 14 del doc de arquitectura.
- ⬜ **Política de eventos malformados**: hoy los listeners hacen log.error + skip (pago con paymentMethod inválido = saga colgada en silencio). Evaluar cola de muertos (DLT) o evento de rechazo análogo.

## Orden lógico restante

```
1. flight-service: listeners con idempotencia (paso 7): clases JPA processed_events
   + default.type por-listener + dedupe existsById -> proceso -> recordProcessed
2. Commitear rama US-006 (pasos 0-8 sin commit) y PR a main
3. Stack completo arriba + saga end-to-end probada (feliz + compensación)
4. notification-service (consumidor reactivo)
5. Matriz de roles probada + ownership
6. CI/CD
```
