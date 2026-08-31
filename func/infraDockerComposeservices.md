# Infraestructura Docker Compose - Sistema de Microservicios Airline

## Arquitectura General

```
                        ┌─────────────────────────────────────────────────┐
                        │           Puerto 8080 (pública)                 │
                        │              api-gateway                        │
                        └──────────┬──────────┬──────────┬───────────────┘
                                   │          │          │
                    ┌──────────────┘          │          └──────────────┐
                    ▼                         ▼                         ▼
            ┌──────────────┐        ┌──────────────┐        ┌──────────────┐
            │ auth-service │        │booking-service│        │flight-service│
            │    :8087     │        │    :8082      │        │    :8086     │
            └──────┬───────┘        └──────┬───────┘        └──────┬───────┘
                   │                       │                       │
                   │        ┌──────────────┼──────────────┐        │
                   │        ▼              ▼              ▼        │
                   │  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
                   │  │ payment  │  │ checkin  │  │notif-svc │    │
                   │  │  :8083   │  │  :8084   │  │  :8085   │    │
                   │  └────┬─────┘  └────┬─────┘  └──────────┘    │
                   │       │             │                         │
                   └───────┴──────┬──────┴─────────────────────────┘
                                  │
                    ┌─────────────┴─────────────────────────┐
                    │          RED "observability"           │
                    │                                       │
                    │  ┌─────────┐  ┌─────────┐  ┌────────┐│
                    │  │   db    │  │ broker  │  │ jaeger ││
                    │  │  :5432  │  │  :9092  │  │:16686  ││
                    │  └─────────┘  └─────────┘  └────────┘│
                    └───────────────────────────────────────┘
```

---

## Servicios - Puertos y Dependencias

### Infraestructura Base

| Servicio | Imagen | Puerto | Descripción |
|----------|--------|--------|-------------|
| **db** | postgres:17 | 5432 | PostgreSQL - BD principal |
| **broker** | apache/kafka:latest | 9092 (ext) / 29092 (int) | Kafka con KRaft |
| **kafdrop** | obsidiandynamics/kafdrop:latest | 19000 | UI para Kafka |
| **jaeger** | jaegertracing/all-in-one:1.75.0 | 16686 | UI de tracing |
| **prometheus** | prom/prometheus:latest | 9090 | Métricas |
| **grafana** | grafana/grafana:latest | 3000 | Dashboards |
| **loki** | grafana/loki:2.9.0 | 3100 | Logs |
| **promtail** | grafana/promtail:2.9.0 | - | Collector de logs |

### Microservicios

| Servicio | Puerto | Build | Profile | DB Host | Kafka |
|----------|--------|-------|---------|---------|-------|
| **api-gateway** | 8080 | ./api-gateway | - | - | - |
| **auth-service** | 8087 | ./auth-service | prod | db | - |
| **booking-service** | 8082 | ./booking-service | prod | db | broker:29092 |
| **flight-service** | 8086 | ./flight-service | prod | db | broker:29092 |
| **payment-service** | 8083 | ./payment-service | prod | db | broker:29092 |
| **checkin-service** | 8084 | ./checkin-service | prod | db | broker:29092 |
| **notification-service** | 8085 | ./notification-service | - | - | - |

---

## Flujo de Llamadas

```
Cliente → api-gateway:8080
              │
              ├── /api/v1/auth/*       → auth-service:8087
              ├── /api/v1/flights/*    → flight-service:8086
              ├── /api/v1/bookings/*   → booking-service:8082
              ├── /api/v1/payments/*   → payment-service:8083
              ├── /api/v1/checkins/*   → checkin-service:8084
              └── /api/v1/notifications/* → notification-service:8085
```

### Eventos Kafka

| Evento | Publicador | Consumidor | Topico |
|--------|-----------|------------|--------|
| booking.created | booking-service | flight-service | booking.created |
| booking.confirmed | booking-service | checkin-service | booking.confirmed |
| booking.cancelled | booking-service | flight-service, payment-service | booking.cancelled |
| payment.processed | payment-service | booking-service | payment.processed |
| checkin.completed | checkin-service | notification-service | checkin.completed |

---

## Comandos Docker

```bash
# Arrancar todo (construir si es necesario)
docker compose up -d --build

# Arrancar todo (sin reconstruir)
docker compose up -d

# Ver estado
docker compose ps

# Ver logs de un servicio
docker compose logs -f api-gateway
docker compose logs -f booking-service
docker compose logs -f checkin-service

# Ver todos los logs
docker compose logs -f

# Parar todo
docker compose down

# Parar todo y eliminar volumes
docker compose down -v

# Reconstruir un servicio específico
docker compose up -d --build api-gateway
```

---

## Variables de Entorno por Servicio

### api-gateway
```yaml
AUTH_SERVICE_URI: http://auth-service:8087
FLIGHT_SERVICE_URI: http://flight-service:8086
BOOKING_SERVICE_URI: http://booking-service:8082
PAYMENT_SERVICE_URI: http://payment-service:8083
CHECKIN_SERVICE_URI: http://checkin-service:8084
NOTIFICATION_SERVICE_URI: http://notification-service:8085
```

### booking-service / flight-service / payment-service / checkin-service
```yaml
SPRING_PROFILES_ACTIVE: prod
DB_HOST: db
SPRING_KAFKA_BOOTSTRAP_SERVERS: broker:29092
```

### auth-service
```yaml
SPRING_PROFILES_ACTIVE: prod
DB_HOST: db
```

---

## Dependencias (depends_on)

```
auth-service     → db (healthy)
booking-service  → db (healthy), kafka (started)
flight-service   → db (healthy), kafka (started)
payment-service  → db (healthy), kafka (started)
checkin-service  → db (healthy), kafka (started)
api-gateway      → auth-service, booking-service, flight-service,
                   payment-service, checkin-service, notification-service
```

---

## Red

Todos los servicios comparten la red `observability` (bridge).

---

## Volumes

| Volume | Uso |
|--------|-----|
| pgdata | Datos PostgreSQL |
| grafana-data | Dashboards Grafana |
| loki_data | Logs Loki |

---

## Estado Actual (2026-08-27) ✅

Todos los servicios corriendo correctamente.

---

## Errores Encontrados y Solucionados

### Error 1: booking-service — Método faltante en interfaz

**Error**: `method does not override or implement a method from a supertype`
**Archivo**: `PaymentProcessedKafkaListener.java` → `BookingUsecase.java`
**Causa**: La interfaz `BookingUsecase` no declaraba el método `applyPaymentResult()`, pero el listener lo invocaba.
**Solución**: Agregar el método `applyPaymentResult()` a la interfaz `BookingUsecase`.

### Error 2: payment-service — Jackson JSR310 no registrado en deserializer

**Error**: `Instant not supported by default`
**Archivo**: `BookingCreatedEvent.java`
**Causa**: El `JsonDeserializer` de Kafka usa un `ObjectMapper` (Jackson 2) que NO tiene registrado el `JavaTimeModule`. No sabe convertir `"2026-08-27T11:24:16.855Z"` a `java.time.Instant`.
**Solución**: Cambiar `Instant createdAt` → `String createdAt` en el DTO del evento. El JSON ya trae el timestamp como string.

### Error 3: payment-service — Kafka producer hardcoded a localhost

**Error**: Producer intenta conectarse a `localhost:9092` en vez de `broker:29092`
**Archivo**: `PaymentKafkaConfig.java` línea 20: `private static final String BOOTSTRAP_SERVERS = "localhost:9092";`
**Causa**: El `PaymentKafkaConfig` tenía la dirección del broker **hardcoded**. No leía la env var `SPRING_KAFKA_BOOTSTRAP_SERVERS`.
**Solución**: Cambiar a `@Value("${spring.kafka.bootstrap-servers:localhost:9092}")` para que se inyecte desde `application.yaml` / env var.

### Error 4: payment-service — application-prod.yaml sin config de Kafka

**Error**: El consumer intentaba conectarse a `localhost:9092` en vez de `broker:29092`
**Archivo**: `payment-service/src/main/resources/application-prod.yaml`
**Causa**: El profile `prod` NO sobreescribía `spring.kafka.bootstrap-servers`. Usaba el valor de `application.yaml` (dev): `localhost:9092`.
**Solución**: Agregar `spring.kafka.bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` en `application-prod.yaml`.

### Error 5: payment-service — driver class no encontrado

**Error**: `Cannot load JDBC driver class 'org.postgresql.Driver'`
**Archivo**: `application-prod.yaml`
**Causa**: Spring Boot 4.x no detecta automáticamente el driver de PostgreSQL en profiles externos.
**Solución**: Agregar explícitamente `driver-class-name: org.postgresql.Driver` en el datasource.

### Error 6: booking-service — booking.confirmed nunca se publica

**Error**: Check-in rechaza: "Booking is not confirmed" aunque booking-service tiene status CONFIRMED
**Archivo**: `BookingService.java` + `BookingEventPublisherPort.java` + `BookingEventOutboxAdapter.java`
**Causa**: El caso de uso `applyPaymentResult()` confirma la reserva en DB pero **NUNCA publica el evento `booking.confirmed`**. La interfaz `BookingEventPublisherPort` solo tenía `bookingCreated` y `bookingCancelled`. El outbox relay tampoco tenía mapping para `BOOKING_CONFIRMED`.
**Solución**:
1. Crear `BookingConfirmedEvent.java` (nuevo record)
2. Agregar `bookingConfirmed()` a `BookingEventPublisherPort`
3. Implementar en `BookingEventOutboxAdapter`
4. Llamar `eventPublisher.bookingConfirmed()` en `BookingService.applyPaymentResult()` cuando APPROVED
5. Agregar `"BOOKING_CONFIRMED", "booking.confirmed"` al mapa `TOPIC_BY_EVENT_TYPE` en `OutboxRelay`

### Error 7: flight-service — Jackson Instant (mismo patrón que error 2)

**Error**: `Instant not supported by default` al consumir `booking.created`
**Archivo**: `flight-service/.../event/BookingCreatedEvent.java`
**Causa**: Mismo patrón que error 2 — `Instant` sin módulo JSR310 en Jackson 2.
**Solución**: Cambiar `Instant createdAt` → `String createdAt`.

### Error 8: checkin-service — StringDeserializer no puede convertir a Map

**Error**: `Cannot convert from [java.lang.String] to [java.util.Map]`
**Archivo**: `checkin-service/src/main/resources/application.yaml`
**Causa**: El consumer global usaba `StringDeserializer` (devuelve String crudo), pero el `BookingConfirmedKafkaListener` espera un `Map<String, Object>`. Spring no puede convertir String → Map sin `JsonDeserializer`.
**Solución**: Cambiar `value-deserializer` a `JsonDeserializer` y agregar `spring.json.value.default.type: java.util.Map` + `spring.json.trusted.packages: "*"`.

### Error 9: checkin-service — application-prod.yaml sin config de Kafka

**Error**: Mismo patrón que error 4 — consumer usa `localhost:9092` en vez de `broker:29092`
**Archivo**: `checkin-service/src/main/resources/application-prod.yaml`
**Causa**: El profile `prod` no sobreescribía `spring.kafka.bootstrap-servers` ni el deserializer.
**Solución**: Agregar bloque completo de Kafka en `application-prod.yaml` (bootstrap-servers + consumer config).

### Error 10: checkin-service — passengerId String vs Long

**Error**: `NumberFormatException: For input string: "passenger"`
**Archivo**: `BookingConfirmedKafkaListener.java` + `ProcessedEventEntity.java`
**Causa**: El dominio envía `passengerId: "passenger"` (String), pero `ProcessedEventEntity` espera `Long` y la columna DB es `BIGINT`. El listener intenta `toLong("passenger")` y falla.
**Solución**: Pasar `null` para passengerId — no se necesita para la validación de check-in (solo `bookingId` + `eventType`).

### Error 11: checkin-service — seatNumber "Snull"

**Error**: `"seatNumber": "Snull"` en el response del check-in
**Archivo**: `CheckInService.java` línea 60: `String seat = "S" + checkIn.getId();`
**Causa**: `checkIn.getId()` es `null` porque el CheckIn **aún no se ha guardado** en la BD. El ID se genera al hacer `save()`. Al concatenar `"S" + null` se obtiene `"Snull"`.
**Solución**: Reordenar el flujo — guardar PRIMERO para obtener el ID, luego generar el boarding pass con el seat number correcto y volver a guardar.

### Error 12: checkin-service — NonUniqueResultException en validación de reserva

**Error**: `org.hibernate.NonUniqueResultException: Query did not return a unique result: 2 results were returned`
**Archivo**: `BookingValidationAdapter.java` + `ProcessedEventJpaRepository.java`
**Causa**: El método `existsByEventTypeAndBookingId` con valor de retorno `boolean` asumía un resultado único. Si por reintentos de Kafka o pruebas existían múltiples registros en `processed_events` para un mismo `booking_id`, Hibernate lanzaba excepción de resultado no único.
**Solución**: Cambiar a un enfoque defensivo devolviendo una `List` (`findByEventTypeAndBookingId`) y evaluando con `!list.isEmpty()`. Así se evitan sorpresas con eventos duplicados o reintentos distribuidos.

### Error 13: checkin-service — NonUniqueResultException en búsqueda de Check-In

**Error**: `org.hibernate.NonUniqueResultException` al buscar check-in por `bookingId`
**Archivo**: `CheckInPersistenceAdapter.java` + `CheckInJpaRepository.java`
**Causa**: El método `Optional<CheckInEntity> findByBookingId(Long bookingId)` le exige a Hibernate que devuelva estrictamente 0 o 1 resultado. Si existían múltiples intentos históricos de check-in para la misma reserva, explotaba con error 500.
**Solución**: Cambiar a `Optional<CheckInEntity> findFirstByBookingIdOrderByIdDesc(Long bookingId)`, lo que añade automáticamente un `ORDER BY id DESC LIMIT 1` asegurando que siempre se obtiene el intento más reciente de forma limpia y resiliente.

---

## Checklist de Configuración por Servicio

Cuando un microservicio usa **Kafka + PostgreSQL**, asegúrate de tener:

1. **application.yaml** (dev): config con H2 + `localhost:9092` + `JsonDeserializer` como value-deserializer
2. **application-prod.yaml** (prod): config con PostgreSQL + env vars + MISMA config de Kafka que dev
3. **docker-compose.yml**: env vars `SPRING_PROFILES_ACTIVE`, `DB_HOST`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`
4. **Clase de configuración**: NUNCA hardcodes `localhost` → usa `@Value` o Spring properties
5. **pom.xml**: `jackson-datatype-jsr310` si usas `Instant`, `LocalDateTime`, etc. en eventos Kafka
6. **Eventos DTO**: NUNCA uses `Instant` en records de Kafka → usa `String` para timestamps
7. **OutboxRelay**: Cada `eventType` DEBE tener un mapping en `TOPIC_BY_EVENT_TYPE`
8. **Consumer**: Usa `JsonDeserializer` + `spring.json.value.default.type` para recibir objetos Java

---

## Patrones de Errores Comunes (Guía de Prevención)

| Patrón | Ejemplo | Prevención |
|--------|---------|------------|
| Jackson 2 vs 3 | `Instant not supported` | Eventos DTO usan `String` para fechas |
| Hardcoded localhost | Producer/consumer apuntan a `localhost:9092` | Siempre `@Value` o env vars |
| Profile prod incompleto | `application-prod.yaml` sin Kafka config | Copiar config de Kafka de dev a prod |
| Evento faltante en outbox | `BOOKING_CONFIRMED` no publicado | Checklist: cada evento del diagrama DEBE tener publisher + consumer + outbox mapping |
| Deserializer equivocado | `String → Map` falla | Consumer usa `JsonDeserializer`, no `StringDeserializer` |

---

## Comandos Útiles de Debug

```bash
# Ver logs de un servicio
docker compose logs -f payment-service

# Buscar errores en logs
docker compose logs payment-service 2>&1 | Select-String "ERROR|Exception"

# Verificar que Kafka está recibiendo eventos
docker compose exec broker kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic booking.created --from-beginning

# Verificar conexión de un servicio a Kafka
docker compose logs payment-service | Select-String "broker:29092|partitions assigned"

# Verificar que un servicio está listening
curl http://localhost:8083/actuator/health
```

---

## Estado Final (2026-08-27) ✅ FLUJO COMPLETO FUNCIONANDO

### Todos los servicios corriendo (14/14)

| Servicio | Puerto | Estado | Kafka Topic |
|----------|--------|--------|-------------|
| api-gateway | 8080 | ✅ Up | - |
| auth-service | 8087 | ✅ Up | - |
| booking-service | 8082 | ✅ Up | payment.processed (consumer) |
| flight-service | 8086 | ✅ Up | booking.created + booking.cancelled (consumer) |
| payment-service | 8083 | ✅ Up | booking.created (consumer) |
| checkin-service | 8084 | ✅ Up | booking.confirmed (consumer) |
| notification-service | 8085 | ✅ Up | - |
| db | 5432 | ✅ Up (healthy) | - |
| broker | 9092/29092 | ✅ Up | - |
| kafdrop | 19000 | ✅ Up | - |
| jaeger | 16686 | ✅ Up | - |
| grafana | 3000 | ✅ Up | - |
| loki | 3100 | ✅ Up | - |
| prometheus | 9090 | ✅ Up | - |

### Flujo de Saga completo — Prueba exitosa

```
1. POST /api/v1/auth/login → Token JWT ✅
2. POST /api/v1/bookings → Booking ID=6, status=PENDING ✅
3. Kafka: booking.created → flight-service reserva asientos ✅
4. Kafka: booking.created → payment-service procesa pago MOCK ✅
5. Kafka: payment.processed → booking-service confirma reserva ✅
6. GET /api/v1/bookings/6 → status=CONFIRMED ✅
7. Kafka: booking.confirmed → checkin-service valida reserva ✅
8. POST /api/v1/checkins → 201 CREATED, boarding pass generado ✅
```

### Response del check-in (Booking ID=6)

```json
{
    "id": 1,
    "bookingId": 6,
    "flightId": 6,
    "passengerId": 1,
    "status": "COMPLETED",
    "seatNumber": "S1",
    "gate": "G6",
    "boardingTime": "2026-08-27T16:22:40.076355911",
    "completedAt": "2026-08-27T15:37:40.076426321Z"
}
```

### Errores totales encontrados y solucionados: 13

| # | Error | Servicio | Archivo | Solución |
|---|-------|----------|---------|----------|
| 1 | Jackson Instant not supported | payment-service | BookingCreatedEvent.java | Instant → String |
| 2 | Producer hardcoded localhost | payment-service | PaymentKafkaConfig.java | @Value injection |
| 3 | application-prod.yaml sin Kafka | payment-service | application-prod.yaml | Agregar config Kafka |
| 4 | Driver class not found | payment-service | application-prod.yaml | driver-class-name explícito |
| 5 | booking.confirmed nunca publicado | booking-service | BookingService.java + Port + Adapter | Crear evento + publicar |
| 6 | OutboxRelay sin mapping | booking-service | OutboxRelay.java | Agregar BOOKING_CONFIRMED |
| 7 | Jackson Instant (mismo patrón) | flight-service | BookingCreatedEvent.java | Instant → String |
| 8 | StringDeserializer vs Map | checkin-service | application.yaml | JsonDeserializer |
| 9 | application-prod.yaml sin Kafka | checkin-service | application-prod.yaml | Agregar config Kafka |
| 10 | passengerId String vs Long | checkin-service | BookingConfirmedKafkaListener.java | Pasar null |
| 11 | seatNumber "Snull" | checkin-service | CheckInService.java | Reordenar save → generateBoardingPass |
| 12 | NonUniqueResult en validación | checkin-service | ProcessedEventJpaRepository.java | Usar List + .isEmpty() |
| 13 | NonUniqueResult en findByBookingId | checkin-service | CheckInJpaRepository.java | Usar findFirst...OrderByIdDesc |

### Checklist de Prevención (aplicar a CADA microservicio)

1. **Eventos DTO**: NUNCA `Instant` → siempre `String` para timestamps
2. **Kafka config**: Siempre `@Value` o env vars, NUNCA hardcodear `localhost`
3. **application-prod.yaml**: SIEMPRE replicar config de Kafka de dev
4. **OutboxRelay**: Cada `eventType` DEBE tener mapping en `TOPIC_BY_EVENT_TYPE`
5. **Consumer deserializer**: `JsonDeserializer` + `spring.json.value.default.type` para objetos Java
6. **IDs de BD**: Generar seat number DESPUÉS del save, no antes
7. **Dominio vs DB**: Validar que los tipos coinciden (String vs Long) en los bordes
