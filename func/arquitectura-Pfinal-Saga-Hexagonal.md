# Arquitectura Proyecto Final — Saga + Hexagonal

> Sistema de Reservas de Aerolínea · Bootcamp Senior a Arquitecto · Java + Spring Boot
> Documento vivo: define los dominios por servicio, la construcción de eventos y el flujo Saga.

## 1. Objetivo
Simular un sistema backend de reservas aéreas con microservicios **desacoplados 100% por eventos (Kafka)**.
Comunicación síncrona entre servicios (REST/OpenFeign) **queda fuera de alcance**: todo acoplamiento se resuelve con eventos.

## 2. Estado actual del código (`airline-system/`)
| Servicio | Estado | Puerto |
|---|---|---|
| `auth-service` | ✅ Hexagonal completo (patrón de referencia) | 8087 |
| `api-gateway` | ✅ Spring Cloud Gateway (rutas declarativas webmvc) + filtro | 8080 |
| `flight-service` | ✅ Hexagonal + búsqueda + tests slices (11/11 verdes) | 8086 |
| `booking-service` | 🚧 Dominio + aplicación completos · infraestructura en curso (persistencia + web) · lado Kafka pendiente (`booking.created` / `payment.processed` / `booking.cancelled`) | 8082 |
| `payment-service` | ✅ Hexagonal adaptado del proyecto de referencia → saga aerolínea (consume `booking.created` → publica `payment.processed`) · sin web | 8083 |
| `checkin-service` | ⬜ Esqueleto | 8084 |
| `notification-service` | ⬜ Esqueleto | 8085 |
| Raíz `src/` (monolito) | 🅿️ Restos, se dejan intactos | 8090 |
| Infra (Docker Compose) | ✅ Broker KRaft + Kafdrop + PostgreSQL (healthy, 6 BD) + Prometheus + Grafana + Jaeger + Loki + Promtail + 7 micros (auth/flight/payment/checkin/notification Up; booking bloqueado por java local en 8082; gateway en Created) | — |

## 3. Decisiones de arquitectura (ADR resumido)
1. **Hexagonal por servicio**, replicando la estructura de `auth-service`.
2. **Eventos como records en `application/event/` de CADA servicio** (Caso B, patrón del proyecto de referencia). NO hay módulo compartido: cada servicio que produce o consume un evento mantiene su propia copia con el mismo JSON. El contrato real es el schema JSON + el nombre del topic (sección 6).
3. **Comunicación solo por eventos.** Nada de OpenFeign/REST entre servicios.
4. **Saga en modo choreography** (orquestación implícita por eventos).
5. **Sin anotaciones Spring en domain/application** (`@Service`/`@Repository`): wiring manual en `config/XxxConfig`.
6. **`notification-service` es consumidor reactivo** (arquetipo choreography): no decide pasos del flujo, solo reacciona a hechos y despacha notificaciones.
7. **Seguridad centralizada en el gateway** (Spring Cloud Gateway, puerto 8089): el gateway es el punto único de entrada y decide la autorización por rol. Los servicios detrás solo autentican (`.anyRequest().authenticated()`), sin duplicar reglas de rol (sección 8).

## 4. Convención de capas por servicio
```
com.software.<servicio>.domain          → entidades de negocio (sin infraestructura)
com.software.<servicio>.application
  ├─ command/                           → comandos de entrada (records)
  ├─ event/                             → records de eventos Kafka (copias propias, Caso B)
  ├─ exception/                         → excepciones de negocio
  ├─ port/in/                           → casos de uso (interfaces)
  ├─ port/out/                          → puertos: *RepositoryPort, EventPublisherPort
  └─ service/                           → implementación de casos de uso
com.software.<servicio>.config          → wiring manual de beans
com.software.<servicio>.infrastructure
  ├─ persistence/                       → Entity + JpaRepository + Adapter
  ├─ events/                            → Kafka producer/consumer (adapter del port)
  └─ web/                               → REST público (dto + controller) — solo API del servicio
```

## 5. Definición de dominios por servicio

### 5.1 flight-service (catálogo + inventario)
- `Flight` — flightNumber, origin, destination, departure, arrival, price
- `SeatInventory` — flightId, totalSeats, availableSeats
- Casos de uso: buscar vuelos, reservar asientos (por evento), liberar asientos (compensación por evento)
- Consume: `BookingCreatedEvent` (bloquea), `BookingCancelledEvent` (libera)
- Web: `GET /api/v1/flights?origin&destination&date`

### 5.2 booking-service (núcleo de la saga)
- `Booking` — **agregado raíz (clase, sin setters)**: id, `Passenger` (VO compositado), `flightId` (referencia por ID), seats, amount, status
- `Passenger` — **Value Object (record)**: `passengerId` (claim `sub` del JWT), `name`, `email` (del request). NO es un usuario de login (sección 8.6)
- `BookingStatus` — **enum**: `PENDING, CONFIRMED, CANCELLED, FAILED`
- **Máquina de estados (reglas de dominio)**: `confirm()`, `cancel()`, `fail()` — solo válidas desde `PENDING` (guarda `ensurePending()` → `IllegalStateException`). Los eventos de la saga invocarán estos métodos
- **Dos constructores** (patrón `SeatInventory`): creación (valida + fija `PENDING`) y restauración (reproduce estado persistido con id + status). Campos `final` para invariantes (`passenger`, `flightId`, `seats`, `amount`); `id` y `status` mutables
- Casos de uso (aplicación): `createBookingUseCase` (devuelve `Booking` con id), `getBookingById` (lanza `BookingNotFoundException`)
- Puertos: `BookingUsecase` (in) · `BookingRepositoryPort` (out: `Booking save(Booking)`, `Optional<Booking> findById(Long)`)
- Publica: `BookingCreatedEvent`, `BookingCancelledEvent`, `BookingConfirmedEvent`
- Consume: `PaymentProcessedEvent` (status `APPROVED` → CONFIRMED; status `DECLINED` → CANCELLED) — evento único, modelo sección 5.3
- **Decisión (2026-08-19)**: `BookingCreatedEvent` y `CreateBookingRequest` incluyen `passengerEmail` (ya vive en `Passenger`) y `paymentMethod` (el pasajero elige método de pago al reservar; alimenta a payment-service, que no tiene canal REST propio).
- Web: `POST /api/v1/bookings`, `GET /api/v1/bookings/{id}`
- **Pendiente analizado**: `amount` no se puede calcular del precio del vuelo (sin sync REST) → por ahora lo manda el cliente en el request (MVP); se consolidará con la saga

#### 5.2.1 Diseño didáctico del dominio de booking (decisiones tomadas en sesión)

> El siguiente detalle es la **traza de decisiones** que llevaron al diseño actual del dominio. No es especificación aspiracional: es lo que se discutió y por qué.

**Escenario que gobierna el diseño**
```
1. El usuario selecciona un vuelo        → contra flight-service (sale el flightId)
2. Se crea una reserva asociada al usuario → POST /api/v1/bookings + JWT
3. La reserva queda en estado PENDIENTE   → factory de creación lo fija
```

**¿Cómo relacionamos usuario, vuelo y reserva? (la pregunta de los "dos dominios asociados")**

La intuición inicial es "la reserva tiene un usuario y un vuelo → son dos dominios asociados". La respuesta de DDD es más precisa:

| Concepto | ¿Se modela en booking? | Por qué |
|---|---|---|
| **Usuario** | ❌ NO | Lo posee auth-service. La identidad viaja en el JWT (`sub`) — doc 8.6. Duplicar `users` aquí = 5 copias de credenciales |
| **Vuelo** | ❌ NO | Lo posee flight-service. Un agregado externo se **referencia por ID**, no se importa su objeto |
| **Pasajero** | ✅ Como **Value Object** | Es un snapshot del request/JWT que viaja con la reserva: `passengerId` (sub) + `name`/`email` (request) |
| **Reserva** | ✅ Como **agregado raíz** | Es la unidad que garantiza las reglas: máquina de estados, validaciones |

**Regla de oro de DDD aplicada:** *un agregado composita sus Value Objects y referencia a otros agregados por ID*. No arrastras el `Flight` completo ni el `User` completo dentro de `Booking`.

**¿Por qué `Booking` es clase y `Passenger` es record? (la tabla de decisión)**

| Criterio | `Booking` | `Passenger` |
|---|---|---|
| ¿Tiene identidad? | ✅ (id) | ❌ (es snapshot) |
| ¿Estado mutable? | ✅ (status) | ❌ |
| ¿Reglas de negocio? | ✅ (máquina de estados) | ❌ |
| Forma | **clase** | **record** |

Regla práctica que usamos: **record para datos inmutables en tránsito (commands, DTOs, eventos, VOs sin lógica); clase para entidades/agregados con identidad + estado + reglas.** `BookingStatus` es enum porque es un conjunto cerrado de valores.

**¿Por qué NO hay setters?**

Dos razones, según el campo:
1. **`status`** — cambia SOLO por métodos de dominio (`confirm()`, `cancel()`, `fail()`), que validan la transición con `ensurePending()`. Un `setStatus()` permitiría saltarse la regla y crear un `Booking` inconsistente (p. ej. `CONFIRMED → PENDING`).
2. **`passenger`, `flightId`, `seats`, `amount`** — son `final`: **nunca cambian**. Un setter no solo es mala práctica: el compilador lo rechaza. Son invariantes fijados en el constructor.

Regla que resume la decisión: **setter libre = cualquiera puede dejar el objeto inconsistente. Método de dominio = el cambio solo ocurre si la regla lo permite. Y si un valor nunca debe cambiar, ni siquiera hay setter que discutir: es `final`.** (Mismo patrón que `SeatInventory.reserve()`/`release()` en flight-service.)

**¿Por qué DOS constructores? (patrón de `SeatInventory`)**

| Constructor | Lo llama | Qué impone |
|---|---|---|
| `Booking(passenger, flightId, seats, amount)` | caso de uso | Valida invariantes + fija `status = PENDING` |
| `Booking(id, passenger, flightId, seats, amount, status)` | adapter de persistencia | Reproduce el estado guardado tal cual |

*Crear* y *restaurar* son operaciones distintas: crear impone las reglas de "nuevo" (siempre PENDING); restaurar rehidrata lo que había en BD (puede ser CONFIRMED). El adapter necesita el segundo para no pasar por la regla de creación. La validación se comparte en `private static void validate(...)` para no duplicarla.

**¿Por qué `ensurePending()` y no un `if` en cada método?**

`confirm()`, `cancel()` y `fail()` comparten la misma guarda. Centralizarla en un método privado evita triplicar la condición y el mensaje; si la regla cambia, se toca en un solo sitio. El nombre sigue la convención **`ensure` + condición** = "asegúrate de que se cumpla esto o falla" (guard clause / fail-fast). Se invoca como primera línea, antes de mutar.

**¿Cuándo puede NO estar PENDING si ya lo creamos así? (duda resuelta)**

1. **Restauración desde BD**: el adapter rehidrata un `Booking` con `status = CONFIRMED`. Un `cancel()` sobre esa instancia debe rechazarse → `ensurePending()` lo impide.
2. **Ciclo de vida del objeto**: tras `confirm()`, el objeto ya no es PENDING; un `fail()` posterior debe fallar.

El constructor de creación solo garantiza el estado inicial. La guarda garantiza que **nunca más** deje de ser válido.

**¿Cómo se valida? (guard clause por exclusión)**

```java
if (seats < 1)   // equivalente a: if (seats <= 0)
    throw new IllegalArgumentException(...);
```
La condición representa el caso **inválido**: si es verdadera, falla. Los válidos son la negación (`seats >= 1`). Se validó que `seats < 1` y `seats <= 0` son funcionalmente idénticos con enteros; se eligió `seats < 1` por legibilidad.

**Estructura final del agregado**

```
┌─────────────────────────────────────────┐
│ Booking (AGREGADO RAÍZ — clase)          │
│   id: Long            (no final, BD)     │
│   passenger: Passenger (final, VO record)│
│   flightId: Long      (final, ref por ID)│
│   seats: int          (final)            │
│   amount: BigDecimal  (final)            │
│   status: BookingStatus (no final, muta) │
└─────────────────────────────────────────┘
```

### 5.3 payment-service (Strategy Pattern)
- `Payment` — id, bookingId, amount, method, status (PENDING→APPROVED/DECLINED)
- `PaymentProcessor` — interfaz estrategia; impls: `CreditCardProcessor`, `PayPalProcessor`, `MockProcessor`
- Consume: `BookingCreatedEvent`
- Publica: **un único evento** `PaymentProcessedEvent` con campo `status` (`APPROVED` / `DECLINED`); `reason` opcional solo en `DECLINED`
- **Decisión (2026-08-19)**: se adopta el modelo de esta sección (UN evento, UN topic `payment.processed`) frente al del proyecto de referencia (dos eventos `PaymentSucceeded`/`PaymentFailed` en topics separados). Beneficio: un solo contrato → booking con un listener que ramifica por `status`; notification (US-010) filtra `status == APPROVED`.
- **Implementado (adaptación 2026-08-19)**: consume `booking.created` (sección 6) · publica `payment.processed` · puerto **8083** · **sin REST** (eliminados `PaymentController` + DTOs web) · corregidos dos bugs de la copia: faltaban `@EnableScheduling` (el OutboxRelay nunca publicaba) y la config de consumer Kafka en `application.yaml`.

### 5.4 checkin-service
- `CheckIn` — id, bookingId, flightId, passengerId
- `BoardingPass` — id, checkInId, seatNumber, gate, boardingTime
- Consume: `BookingConfirmedEvent` → genera boarding pass
- Publica: `CheckInCompletedEvent`
- Web: `POST /api/v1/checkin`

### 5.5 notification-service (consumidor reactivo — arquetipo choreography)
- **Requisito funcional (EPIC-05)**: *"Informar al usuario sobre el estado de sus acciones"* — traduce los eventos de la saga en mensajes dirigidos al pasajero. No decide pasos del flujo: solo reacciona a hechos (ADR 6).
- `Notification` — type, recipient, subject, body, channel, timestamp
- `NotificationType` — enum (BOOKING_CREATED, PAYMENT_PROCESSED, CHECKIN_COMPLETED)
- Casos de uso (EPIC-05):
  - **US-009 (issue #14) Notificar reserva creada** → consume `BookingCreatedEvent` → despacha confirmación de reserva
  - **US-010 (issue #15) Notificar pago completado** → consume `PaymentProcessedEvent` (status `APPROVED`) → despacha
  - **US-011 (issue #16) Notificar check-in completado** → consume `CheckInCompletedEvent` → despacha
- **Matriz de impacto por dependencia**: US-009 y US-010 son implementables ya (booking y payment funcionales) y se construyen contra el contrato de la sección 6 con productores simulados en tests (patrón Fase 1). US-011 queda **bloqueado** hasta que `checkin-service` esté desarrollado (hoy esqueleto, sección 5.4) — el evento `CheckInCompletedEvent` no tiene productor real aún.
- `NotificationSenderPort` — despacho (impl mock en `infrastructure/sender`)
- Consume: todos los eventos relevantes → despacho mock (log)
- Sin web · Sin BD (mínimo viable)

## 6. Catálogo de eventos (topics Kafka)
| Evento | Topic | Productor | Consumidores |
|---|---|---|---|
| BookingCreatedEvent | `booking.created` | booking | flight, payment, notification |
| PaymentProcessedEvent | `payment.processed` | payment | booking, notification |
| BookingConfirmedEvent | `booking.confirmed` | booking | checkin, notification |
| BookingCancelledEvent | `booking.cancelled` | booking | flight, notification |
| CheckInCompletedEvent | `checkin.completed` | checkin | notification |

**Modelo de eventos (Caso B — cada servicio independiente):**
Cada evento es un `record` en `application/event/` de cada servicio que lo produce o consume. No hay módulo compartido: cada servicio mantiene su propia copia, y solo productor y consumidores de un mismo evento deben coincidir en el JSON (el contrato va documentado aquí, sección 6.2). El adapter Kafka serializa a JSON (`infrastructure/events`).

> Ejemplo de record (idéntico en booking-service y notification-service):
> `public record BookingCreatedEvent(String bookingId, String passengerId, String flightId, int seats, BigDecimal amount, String passengerEmail, String paymentMethod, Instant occurredAt) {}`

### 6.1 Contrato de topics por servicio (constantes)
Cada servicio que publique o consuma define su propio conjunto de constantes de topic en `infrastructure/events`
(siguiendo el patrón `TOPIC_BY_EVENT_TYPE` del proyecto de referencia). La **publicación** mapea tipo de evento → topic:

```java
public final class Topics {
    private Topics() {}

    public static final String BOOKING_CREATED = "booking.created";
    public static final String PAYMENT_PROCESSED = "payment.processed";
    public static final String BOOKING_CONFIRMED = "booking.confirmed";
    public static final String BOOKING_CANCELLED = "booking.cancelled";
    public static final String CHECKIN_COMPLETED = "checkin.completed";

    static final Map<String, String> TOPIC_BY_EVENT_TYPE = Map.of(
        BookingCreatedEvent.class.getName(), BOOKING_CREATED,
        PaymentProcessedEvent.class.getName(), PAYMENT_PROCESSED,
        // ...
    );
}
```

> **Dónde viven las constantes de topic**: en el adapter de eventos de cada servicio (`infrastructure/events/`), NUNCA en `domain`. Los `record` de eventos viven en `application/event/`; las constantes de topic son transporte y se quedan en `infrastructure`.

### 6.2 Contrato JSON de eventos (fuente de verdad acordada)
El shape JSON de cada evento se documenta aquí al implementarse, y debe ser idéntico en productor y consumidores:

| Evento | JSON |
|---|---|
| `BookingCreatedEvent` | `{"bookingId": "...", "passengerId": "...", "flightId": "...", "seats": 1, "amount": 199.90, "passengerEmail": "...", "paymentMethod": "MOCK", "occurredAt": "..."}` — `passengerEmail` y `paymentMethod` alimentan a payment-service (D1/D2, decisión 2026-08-19) |
| `PaymentProcessedEvent` | `{"paymentId": "...", "bookingId": "...", "status": "APPROVED"\|"DECLINED", "reason": "..."}` — `reason` solo si `status=DECLINED` |
| `BookingConfirmedEvent` | `{"bookingId": "...", "status": "CONFIRMED"}` |
| `BookingCancelledEvent` | `{"bookingId": "...", "flightId": "...", "seats": 1, "reason": "..."}` |
| `CheckInCompletedEvent` | `{"checkInId": "...", "bookingId": "...", "passengerId": "...", "flightId": "..."}` |

> Los shapes se consolidan al implementar cada productor; la deriva de schema se detecta con tests de integración que publican al topic real y verifican el parseo.

## 7. Flujo Saga (choreography)
```
POST /bookings
  │  booking-service publica BookingCreatedEvent
  ├─► flight-service  consume → bloquea asientos
  ├─► payment-service consume → procesa pago
  │      ├─ success → PaymentProcessedEvent → booking CONFIRMED → BookingConfirmedEvent
  │      │            └─► checkin consume → BoardingPass → CheckInCompletedEvent
  │      │            └─► notification consume (todos)
  │      └─ fail    → PaymentProcessedEvent (status DECLINED) → booking CANCELLED → BookingCancelledEvent
  │                   └─► flight consume → libera asientos (compensación)
  └─► notification consume BookingCreatedEvent (confirmación de reserva)
```

**Definición de Done:** código compila · tests pasan · criterios de aceptación cumplidos · sin estados inconsistentes · revisado.

## 8. Seguridad — roles centralizados en el gateway

### 8.1 Decisión
El `api-gateway` (Spring Cloud Gateway webmvc, puerto 8080) es el **punto único de entrada**. Centraliza la autorización por rol; los servicios detrás no duplican la matriz.

> **REVISIÓN (2026-08-23): se sustituye la opción C por edge authentication.**
> La opción C (oauth2-resource-server en cada servicio) quedó aplicada a medias en booking-service: el starter estaba en el pom pero SIN ninguna configuración de seguridad → Spring Boot activaba su cadena por defecto → 401 a todas las peticiones aunque el gateway validara bien el JWT.
>
> **Decisión final: edge authentication.** El gateway es la única frontera de confianza:
> 1. Valida firma + rol del JWT (matriz 8.2).
> 2. Inyecta `X-User-Id` (claim `sub`) en la petición reenviada.
> 3. Los servicios internos NO llevan Spring Security: confían en la red interna y leen el header.
>
> **Aplicado:** eliminado `spring-boot-starter-oauth2-resource-server` del pom de booking-service (comentario en el pom documenta el patrón). El controller recibe `@RequestHeader("X-User-ID")`. La inyección del header vive en el bean `routes()` de `SecurityConfig.java` del gateway (sección 8.4).
>
> **Condición del patrón:** nadie puede saltarse el gateway. En local y docker-compose se cumple; en producción, los puertos internos (8082, 8086...) no se exponen al exterior. Si algún día un servicio necesita validar identidad propia (defensa en profundidad), se vuelve a la opción C — el análisis de 8.1.1 sigue siendo válido para ese escenario.

**flight-service NO lleva Spring Security**: solo expone GET de búsqueda y no necesita identidad del usuario. Con edge authentication esto ya no es deuda: es el patrón.

> **Por qué la identidad NO viaja por evento:** el JWT está firmado por auth-service y validado por el gateway; Kafka no firma nada. Pasar `passengerId` por un topic rompería el canal de confianza (spoofing). La identidad nace del request autenticado, nunca de un evento.

#### 8.1.1 Diseño didáctico de la seguridad (alternativas analizadas)

> Traza de decisiones de la sesión: las tres formas de conseguir el `passengerId` en booking-service, por qué se eligió la opción C y por qué booking SÍ lleva seguridad mientras flight no.

**El problema que dispara la decisión**

`POST /api/v1/bookings` necesita saber **QUIÉN crea la reserva** para armar el `Passenger(passengerId, ...)`. El doc 8.6 prohíbe que `passengerId` venga en el body del request (identidad NO la aporta el cliente; la aporta el token). Entonces, ¿de dónde sale? Tres caminos posibles:

| Opción | Cómo obtiene `passengerId` | Código custom | Dependencias nuevas | Valida firma en el servicio |
|---|---|---|---|---|
| **A** | Decodificar el payload del JWT del header `Authorization` a mano (`Base64.getUrlDecoder()` + Jackson) | ~30 líneas (`JwtSubExtractor`) | 0 | ❌ (confía en el gateway) |
| **B** | Posponer: `passengerId` provisional por header/param | 0 | 0 | ❌ |
| **C** | Spring Security `oauth2-resource-server`: `authentication.getName()` (claim `sub`) | 0 (solo configuración declarativa) | 1 | ✅ (revalida) |

**Análisis de cada opción**

- **Opción A (extractor manual):** pragmática para desbloquear ya, pero deuda consciente: no valida firma (confía en que el gateway la validó) y deja ~30 líneas custom que se tiran al llegar la fase de seguridad. Además introduce parsing de tokens a mano — algo que Spring Security ya hace, mejor.
- **Opción B (posponer):** genera deuda funcional: el POST crearía reservas sin identidad real.
- **Opción C (resource server):** **cero lógica de negocio custom** — solo configuración declarativa (`SecurityFilterChain`, `JwtDecoder`, `JwtAuthenticationConverter`). Spring valida la firma (defensa en profundidad) y expone el principal autenticado. Es, además, la interpretación **literal del doc 8.1**: "los servicios detrás **solo autentican** y no duplican la matriz de roles".

**¿Por qué se eligió C?** Porque el criterio decisivo fue *no escribir código que Spring ya hace*: la opción C es la única con 0 lógica custom (el "hay Java" de `SecurityConfig` es configuración declarativa, no razonamiento de negocio). Es la arquitectura que el doc 8 describe como meta.

**¿Por qué booking SÍ y flight NO llevan seguridad? (la duda resuelta)**

La simetría NO es el criterio; el **caso de uso** lo es:

| Servicio | Necesita saber QUIÉN invoca | Consecuencia |
|---|---|---|
| `flight-service` (GET búsqueda) | ❌ — un GET de catálogo no asocia nada a nadie | El gateway valida "hay alguien autenticado" y basta → **sin Spring Security en el servicio** (correcto, no es deuda) |
| `booking-service` (POST crear) | ✅ — el `passengerId` se asocia a la reserva | El servicio **debe** leer la identidad del token → `oauth2-resource-server` (opción C) |

La seguridad se aplica donde hay **identidad que asociar**, no "a todos por igual". La decisión NO significa que el gateway sea prescindible: el gateway sigue siendo el punto único de entrada y quien decide la **autorización por rol** (matriz 8.2); el servicio solo **autentica** (firma + quién es). Son dos responsabilidades distintas y complementarias.

**Cobertura de la decisión C en el código**

```
pom.xml                    → spring-boot-starter-oauth2-resource-server
application.yaml           → app.jwt-secret (compartido con auth-service y gateway)
SecurityConfig.java        → SecurityFilterChain (.anyRequest().authenticated(), oauth2ResourceServer.jwt)
                            + JwtDecoder (HS256, mismo secreto)
                            + JwtAuthenticationConverter (mapea claim "roles" → authorities)
BookingController          → public ... create(@Valid @RequestBody ..., Authentication authentication)
                            → String passengerId = authentication.getName();  // claim "sub"
```

**Flujo con C (cómo viaja la identidad de extremo a extremo)**

```
Cliente → POST /api/v1/bookings + Authorization: Bearer <jwt>
  └─► api-gateway (8089): valida firma (JwtDecoder) + decide rol (matriz 8.2) → reenvía
        └─► booking-service (8082):
              SecurityFilterChain → JwtDecoder valida firma OTRA VEZ (defensa en profundidad)
              → JwtAuthenticationConverter → Authentication (principal = sub)
              → BookingController: passengerId = authentication.getName()
                → CreateBookingCommand → Passenger(passengerId, name, email) → Booking
```

**Matices de seguridad que conviene registrar**

1. **`app.jwt-secret` debe ser idéntico** en auth-service (firma), gateway y booking-service (verifica). Es `9a4f2c8d...` en dev con override por variable de entorno `JWT_SECRET`.
2. **booking-service NO duplica la matriz de roles** — solo `.anyRequest().authenticated()`. La autorización por rol sigue siendo exclusiva del gateway (doc 8.2).
3. **`authentication.getName()` devuelve el `sub`** porque Spring Security usa el `subject` del JWT como principal — es la vía canónica, sin parseo manual.
4. **No pasamos la identidad por evento** (nota anterior): Kafka no firma, no hay cadena de confianza. La identidad nace del request autenticado.
5. **Deuda anotada**: extender el patrón C a `flight-service` cuando necesite identidad (doc 8.5.9) y a los endpoints de cancelar/consultar con ownership (8.2 \*).

### 8.2 Matriz de rutas por rol
| Ruta | ADMIN | AGENT | PASSENGER |
|---|:---:|:---:|:---:|
| `/api/v1/auth/login` · `/register` | permitAll | permitAll | permitAll |
| `GET /api/v1/flights` · `/{id}` | ✅ | ✅ | ✅ |
| `POST /api/v1/bookings` | ✅ | ✅ | ✅ |
| `GET /api/v1/bookings` (listar todas) | ✅ | ✅ | ❌ |
| `GET /api/v1/bookings/{id}` | ✅ | ✅ | ✅ (solo propio)\* |
| `POST /api/v1/bookings/{id}/cancel` | ✅ | ✅ | ✅ (solo propio)\* |
| `POST /api/v1/checkin` | ✅ | ✅ | ✅ (solo su vuelo)\* |
| `payment` · `notification` | — (event-driven, sin web) | — | — |

> \* Ownership (PASSENGER solo accede a sus recursos): comparar el `passengerId` del JWT con el recurso. Es un nivel más fino que el rol — se implementa en un paso posterior.

### 8.3 Implementación en `api-gateway/config/SecurityConfig.java`
El JWT ya se valida con `oauth2ResourceServer.jwt`. Se añaden las reglas de rol:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers(HttpMethod.GET,  "/api/v1/flights/**").hasAnyRole("PASSENGER", "AGENT", "ADMIN")
    .requestMatchers(HttpMethod.POST, "/api/v1/bookings").hasAnyRole("PASSENGER", "AGENT", "ADMIN")
    .requestMatchers(HttpMethod.GET,  "/api/v1/bookings").hasAnyRole("AGENT", "ADMIN")
    .requestMatchers(HttpMethod.POST, "/api/v1/bookings/**/cancel").hasAnyRole("PASSENGER", "AGENT", "ADMIN")
    .requestMatchers(HttpMethod.POST, "/api/v1/checkin").hasAnyRole("PASSENGER", "AGENT", "ADMIN")
    .anyRequest().authenticated()
)
```

**Requisito crítico:** el gateway necesita un `JwtAuthenticationConverter` que lea el claim `roles` (mismo patrón que `auth-service`). El converter por defecto mapea `scopes`, no `roles` → sin él, `hasRole()` falla siempre.

**Por qué:** `auth-service` firma el token con el claim `roles` como **String** (`"ROLE_PASSENGER"`), no en `scope`. El `JwtAuthenticationConverter` por defecto lee `scope` → authorities vacías → `hasAnyRole(...)` es siempre false → 403 aunque el rol sea correcto. Spring Security 7 (Boot 4) documenta el mapeo de un claim custom como la vía canónica. El converter es un `@Bean`; Spring lo detecta y usa automáticamente en `oauth2ResourceServer`.

**Bean a añadir** (opción recomendada: idéntico al de `auth-service/infrastructure/security/SecurityConfig.java`, robusto porque el claim es String suelto, no lista):

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        Object roles = jwt.getClaim("roles");
        if (roles instanceof String role) {
            String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            authorities.add(new SimpleGrantedAuthority(authority));
        }
        return authorities;
    };
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
    return converter;
}
```

**Alternativa declarativa** (mostrada en la doc de Spring Security 7) si el claim fuera una lista:
```java
JwtGrantedAuthoritiesConverter a = new JwtGrantedAuthoritiesConverter();
a.setAuthoritiesClaimName("roles");
a.setAuthorityPrefix("ROLE_");
// -> usar como jwtGrantedAuthoritiesConverter
```
El converter declarativo debe confirmarse para claim tipo String; por eso se prefiere el custom.

> **Nota de trazabilidad:** el proyecto de referencia NO define este bean en sus servicios (usaba `hasRole` sin converter). El patrón sí existe en este proyecto en `auth-service`. Por consistencia y por ser probado con claim String, se reutiliza el mismo converter en el gateway.

### 8.4 Rutas del gateway (`application.yaml`)
Las rutas actuales apuntan al proyecto antiguo (`hexa/products`, `hexa/payments`, `orders`) y se corrigen a los servicios reales:

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: auth-service          # 8087
              uri: http://localhost:8087
              predicates: [Path=/api/v1/auth/**]
            - id: flight-service        # 8086
              uri: http://localhost:8086
              predicates: [Path=/api/v1/flights/**]
            - id: booking-service       # 8082
              uri: http://localhost:8082
              predicates: [Path=/api/v1/bookings/**]
            - id: payment-service       # 8083 (ruta inerte tras la adaptación)
              uri: http://localhost:8083
              predicates: [Path=/api/v1/payments/**]
            - id: checkin-service       # 8084
              uri: http://localhost:8084
              predicates: [Path=/api/v1/checkins/**]
            - id: notification-service  # 8085
              uri: http://localhost:8085
              predicates: [Path=/api/v1/notifications/**]
```

> **Nota (2026-08-19)**: el `api-gateway/application.yaml` real se mantiene **TAL CUAL** (decisión del usuario: no modificar el yaml del gateway). Las rutas a `payment` y `notification` quedan definidas aunque esos servicios no exponen REST público — un request a esas rutas devolvería 404 (rutas inertes). Este bloque es el estado actual real del yaml.

> **Actualización (2026-08-23): la ruta de bookings vive en Java, no en el yaml.**
> El bloque `booking-service` del yaml quedó **comentado con explicación**: la ruta la define ahora el bean `routes()` en `SecurityConfig.java`, porque además de enrutar debe **inyectar `X-User-Id` desde el `SecurityContext`**, y eso solo es posible con código Java (el yaml no puede leer el principal autenticado). Tener las dos fuentes dejaba dos `RouterFunction` compitiendo por el mismo path con ganador impredecible.
>
> ```java
> @Bean
> RouterFunction<ServerResponse> routes() {
>     return GatewayRouterFunctions.route("bookings")
>             .route(RequestPredicates.path("/api/v1/bookings/**"), HandlerFunctions.http())
>             // http() sin URI resuelve el destino desde GATEWAY_REQUEST_URL_ATTR,
>             // atributo que SOLO popula el before-filter uri(). Sin esta línea → 500.
>             .before(BeforeFilterFunctions.uri("http://localhost:8082"))
>             .before(request -> {
>                 JwtAuthenticationToken auth = (JwtAuthenticationToken)
>                         SecurityContextHolder.getContext().getAuthentication();
>                 return ServerRequest.from(request)
>                         .header("X-User-Id", auth.getToken().getSubject())
>                         .build();
>             })
>             .build();
> }
> ```
>
> **Lección registrada:** Spring Cloud Gateway MVC admite tres patrones de ruta — todo yaml, Java con `http("uri")` explícita, o Java con `http()` vacío + `.before(uri(...))`. Mezclarlos a medias compila pero revienta en runtime.

`payment-service` (8083) y `notification-service` (8085) **no exponen REST público** (rutas inertes en el gateway; ver nota del bloque anterior).

### 8.5 Pendientes de seguridad detectados (bloqueantes)
1. ✅ ~~`auth-service/SecurityConfig` (3 líneas muertas de `/api/v1/orders/**`)~~ — **aplicado**: limpiado.
2. ✅ ~~Impresion de users seeds...~~ — **aplicado**: `auth-service` (`db/migration/V3__inserts.sql`) ahora siembra `admin`, `user`, `agent` (`ROLE_AGENT`) y `passenger` (`ROLE_PASSENGER`).
3. ✅ ~~typo `ROLE_APASSENGER` en migración raíz~~ — **ya corregido** (monolito intacto, no afecta a los servicios).
4. ✅ ~~Ruta del gateway a auth `8081`~~ — **aplicado**: gateway apunta a `8087`.
5. ✅ ~~Rutas del gateway al proyecto viejo (`hexa/products`, `orders`)~~ — **aplicado**: rutas reales (auth 8087, flights 8080, bookings 8082, checkin 8084).
6. ✅ ~~Matriz de roles + `JwtAuthenticationConverter` en el gateway~~ — **aplicado** (opción B): converter custom del claim `roles` + matriz en `api-gateway/config/SecurityConfig.java`. Compila (`mvn compile`).
7. ⬜ **Pendiente**: levantar auth + gateway y probar la matriz (login admin/agent/passenger → 200/403).
8. ✅ ~~Seguridad en booking-service~~ — **REVISADO 2026-08-23**: la opción C quedó mal aplicada (starter sin configurar → 401 a todo). Sustituida por **edge authentication** (ver revisión en 8.1): starter eliminado del pom, gateway inyecta `X-User-Id`. El POST obtiene `passengerId` del header `X-User-ID` que SOLO puede venir del gateway.
9. ✅ ~~Extender patrón a flight-service~~ — **ya no aplica como deuda**: con edge authentication ningún servicio interno lleva Spring Security; el gateway es la única frontera.

### 8.6 Identidad: la tabla `users` solo vive en auth-service
- **Único dueño del dominio de usuarios**: `auth-service` (credenciales + roles). Solo él tiene la tabla `users` y su script de inserts (`db/migration/V3__inserts.sql`).
- **La identidad viaja en el JWT**, no se consulta en cada base de datos:
  ```
  auth-service → emite JWT (claims: sub + roles) → gateway valida y reenvía
  → el servicio destino extrae del token quién es el usuario (sub) y su rol (roles)
  ```
- Los servicios de negocio (flight/booking/checkin) **no tienen tabla `users`**. Si necesitan datos del pasajero, salen del request o del claim `sub` del token, no de una BD de usuarios propia.
- **`Passenger` (booking-service) NO es un usuario de login**: es un dato de la reserva (name/email del request; `passengerId` del claim `sub`). No hay tabla.
- Reforzado por el modelo de independencia: si cada servicio tuviera `users`, habría 5 copias de credenciales que sincronizar.

## 9. Fases de implementación
1. **Fase 0** — Deps en cada pom: `spring-boot-starter-kafka`, `spring-boot-starter-data-jpa`, `h2`, `spring-boot-starter-validation`. (Sin OpenFeign)
2. **Fase 1** — notification-service (consumidor reactivo: `booking.created` + `payment.processed`) — arranca contra el contrato de la sección 6 con productores simulados en tests
3. **Fase 2** — flight-service (dominio + listener de eventos + web búsqueda)
4. **Fase 3** — booking-service (productor de saga + consumidor de pagos) — consolida JSON de `booking.*` y `payment.*`
5. **Fase 4** — payment-service (Strategy + consumidor/productor) — consolida JSON de `payment.*` — ✅ **adaptación aplicada (2026-08-19)**: evento único `PaymentProcessedEvent` en `payment.processed`; consume `booking.created`
6. **Fase 5** — checkin-service (consumidor → boarding pass) — consolida JSON de `checkin.completed`
7. **Fase 6** — Verificación end-to-end con docker-compose + Kafdrop

## 10. Stack técnico
Java 17 · Spring Boot 4.1.0 · Spring Data JPA · Spring Security (JWT en auth + gateway) · **Spring Cloud Gateway (webmvc)** · Kafka · H2 (dev) / PostgreSQL (prod) · Flyway · Docker Compose · GitHub Actions

## 11. Glosario / Pendientes
- **Saga choreography**: coordinación implícita por eventos (sin orquestador central).
- **Caso B / eventos**: cada servicio define su copia del evento en `application/event/`; contrato = JSON + topic (sección 6).
- **Compensación**: `BookingCancelledEvent` → flight libera asientos.
- **Seguridad centralizada**: el gateway (8080) decide roles; los servicios solo autentican (sección 8).
- Pendiente: adaptar booking-service (productor de `booking.created` + consumidor de `payment.processed`, sección 5.2), implementar notification-service (US-009/010/011, sección 5.5), consolidar el shape JSON exacto de cada evento al implementar cada productor (sección 6.2), corregir los pendientes de seguridad de la sección 8.5, y perfiles de `application.yaml` por servicio.
- **Containerización ✅ completada** (2026-08-19): ver bitácora sección 12. Saga pendiente detallada en `funcional/TO_DO_Final.md`: booking-service y flight-service lado Kafka + desbloquear puerto 8082 + env vars Kafka en compose.

## 12. Bitácora de implementación

### 2026-08-25 — Bloque flight consumidor: pasos 0-6 y 8 completados

**Rama:** `feature/US-006-GestionarFalloPago-ReleaseSeat` (US-006 punto 2: liberar asientos si el pago es DECLINED).

**Hecho y verificado (suite 20/20 verde):**
- **Criteria + metamodelo**: `findByFlightIdWithLock` migrado de `@Lock/@Query` a fragmento Spring Data (`SeatInventoryJpaRepositoryCustom` + `SeatInventoryJpaRepositoryImpl`, Criteria con metamodelo generado por plugin `hibernate-jpamodelgen`). Regla aprendida: `@Lock/@Query` solo aplican a métodos declarados por Spring Data, no a implementaciones propias (ahí `setLockMode()` programático).
- **Paso 0**: índice git limpiado (8 ficheros legacy "AD").
- **Pasos 1-3**: deps Kafka en pom (`starter-kafka`, `starter-json`, puente `jackson-databind`), migración `V4__create_processed_events.sql` (solo dedupe — flight no publica, no necesita outbox), bloque `spring.kafka` en yaml con group `flight-group`.
- **Paso 4**: copias del contrato `BookingCreatedEvent`/`BookingCancelledEvent` en `application/event/` (JSON = contrato, sección 6.2; sin clases Java compartidas).
- **Paso 5**: puerto-in `ReleaseSeatsUseCase` + `FlightService.release()` con `@Transactional`. **Bug latente detectado y corregido**: el refactor SOLID del PR #33 dejó `reserve()` SIN `@Transactional` → el lock pesimista `FOR UPDATE` se liberaba antes del save (transacción implícita por statement). Lección: un refactor puede romper invariantes transaccionales que ningún test cubre si no hay concurrencia en pruebas.
- **Paso 6**: wiring `AppFlightConfig` (un bean por puerto-in, ISP) + 4 tests de release (2 unitarios con FakeFlightRepository estilo state-based + 2 slice `@DataJpaTest` espejando los de reserve). Web SliceTest NO aplica: release no tiene puerta HTTP, su puerta será el listener Kafka.
- **Paso 8 (docker-compose)**: env `SPRING_KAFKA_BOOTSTRAP_SERVERS: broker:29092` + `depends_on: kafka` en flight-service, mismo patrón que booking/payment.

**Decisiones para el paso 7 (listeners, pendiente):**
- `spring.json.value.default.type` se fija POR-LISTENER vía propiedad de `@KafkaListener` (los productores envían JSON plano sin cabeceras de tipo vía outbox-relay y flight consume DOS tipos; un default global no sirve).
- Orden dedupe→proceso→anotar en UNA transacción: si falla el proceso no se anota → Kafka reentrega → reintento limpio.
- Política de errores no recuperables (vuelo inexistente): catch+log+skip, igual que payment — un reintento nunca sanará ese evento.
- Falta además crear `ProcessedEventEntity` + `ProcessedEventJpaRepository` en flight (la tabla existe desde V4 pero no las clases JPA).

### 2026-08-23 — Edge authentication + fix 500 gateway + PaymentMethod enum en booking

**Síntoma:** `POST /api/v1/bookings` por el gateway (8080) devolvía **500** sin llegar a booking-service.

**Causa raíz (doble):**
1. **Gateway:** la ruta de bookings existía DOS veces — en el yaml y como bean `RouterFunction` en `SecurityConfig`. El bean ganaba y usaba `HandlerFunctions.http()` **sin URI**: ese overload resuelve el destino desde el atributo `GATEWAY_REQUEST_URL_ATTR`, que solo popula el before-filter `.before(uri(...))`, que no estaba → excepción → 500.
2. **Booking-service:** tenía `spring-boot-starter-oauth2-resource-server` en el pom SIN ninguna configuración (`SecurityFilterChain`/`JwtDecoder`) → cadena por defecto de Spring Boot → habría devuelto 401 a todo incluso pasando el gateway.

**Cambios aplicados:**
- `api-gateway/SecurityConfig.java`: añadido `.before(BeforeFilterFunctions.uri("http://localhost:8082"))` al bean `routes()`.
- `api-gateway/application.yaml`: bloque `booking-service` comentado con explicación (la ruta vive en Java porque inyecta `X-User-Id`; yaml no puede).
- `booking-service/pom.xml`: eliminado starter oauth2-resource-server, comentario documenta el patrón edge-authentication.

**Decisión de seguridad revisada:** opción C → **edge authentication** (detalle en 8.1). El gateway valida JWT + rol e inyecta `X-User-Id` (claim `sub`); booking lo lee con `@RequestHeader("X-User-ID")`. Condición: puertos internos no expuestos al exterior.

**PaymentMethod enum (fail-fast en origen):**
El valor viajaba como String libre hasta payment-service, donde `PaymentMethod.valueOf("VISA")` fallaba → el listener se saltaba el evento con log.error → reserva PENDING para siempre (saga colgada en silencio; solo valores válidos: `STRIPE`, `PAYPAL`, `MOCK`, case-sensitive).
- Nuevo enum `domain/PaymentMethod.java` en booking (copia local del vocabulario, NO se comparte la clase Java con payment — contrato = JSON del evento, sección 6.2).
- Dominio `Booking` tipado con el enum (invariante: estados inválidos irrepresentables); persistencia/eventos/response convierten con `.name()` / `valueOf()` en los bordes.
- Controller traduce String→enum y devuelve **400 con mensaje claro** si el valor no existe, en vez de dejar colgada la saga.
- Nota didáctica: `fromString` acepta case-insensitive y hace trim; el contrato Kafka sigue viajando en MAYÚSCULAS.

### 2026-08-19 — Adaptación de payment-service a la saga de aerolínea
- **Decisión de modelo**: evento único `PaymentProcessedEvent` con `status` (`APPROVED`/`DECLINED`) en `payment.processed` (sección 5.3). Se descarta el modelo de dos eventos (`PaymentSucceeded`/`PaymentFailed`) del proyecto de referencia.
- **Contrato `BookingCreatedEvent` ampliado**: + `passengerEmail` y `paymentMethod` (decisión D1/D2) → alimentan a payment-service, que no tiene canal REST propio.
- **Cambios en código** (`payment-service/`):
  - `application/event/BookingCreatedEvent.java` (nuevo) · eliminados `OrderCreatedEvent`, `OrderItemDto`, `PaymentSucceededEvent`, `PaymentFailedEvent`.
  - `ProcessPaymentCommand` y `ProcessPaymentService`: `orderId` → `bookingId`.
  - `PaymentEventPublisherPort` unificado a `paymentProcessed(Payment, bookingId, causationId)`; implementado en `PaymentEventOutboxAdapter` con `PaymentProcessedEvent`.
  - `OutboxRelay`: `PAYMENT_PROCESSED` → `payment.processed`.
  - `PaymentKafkaListener`: topic `booking.created`, tipo `BookingCreatedEvent`.
  - `application.yaml`: puerto **8083**, config de consumer Kafka (JsonDeserializer + `BookingCreatedEvent`).
  - Bug corregido: faltaba `@EnableScheduling` en el main (OutboxRelay no publicaba). Bug corregido: faltaba config de consumer Kafka.
  - Eliminado REST público: `PaymentController`, `PaymentRequest`, `PaymentResponse`; `PaymentMapper` conserva solo mapeo de entidad.
  - **Defectos pre-existentes de la copia detectados en verificación y corregidos**:
    - `PaymentEntity` sin imports JPA (`jakarta.persistence.*`) → no compilaba.
    - `ddl-auto=validate` + Flyway habilitado **sin migraciones ni `flyway-core`** → arranque fallaba con `missing table [outbox_events]`. Añadida `V1__create_payment_tables.sql` (`payments`, `outbox_events`, `processed_events`) y `spring-boot-starter-flyway` al pom.
    - `application.yaml` sin sección `app.*` (`stripe-key`, `paypal-key`, `fail-payments`) que `AppPaymentConfig` exige con `@Value`.
    - `spring-boot-starter-webmvc-test` duplicado en el pom (warning de Maven).
  - **Verificado**: `mvn compile` + `mvn test` → BUILD SUCCESS (contextLoads verde).
- **`api-gateway/application.yaml`: SIN cambios** (decisión del usuario: no modificar el yaml del gateway).

### 2026-08-19 — Containerización: Docker Compose (infra + observabilidad + 7 microservicios)
- **Java unificado a 21** en los 7 servicios + root pom. Dockerfiles multi-stage `maven:3.9.9-eclipse-temurin-21` (build) → `eclipse-temurin:21-jre-alpine` (runtime). 7 `Dockerfile` + 7 `.dockerignore`.
- **Estrategia de BD por microservicio**: `application-prod.yaml` por servicio (`bootcamp_auth`, `bootcamp_booking`, `bootcamp_flight`, `bootcamp_payment`) + `db-init/01-init-databases.sql` crea las 5 BD (incluye `bootcamp_checkin`).
- **Driver corregido**: eliminado `driver-class-name: org.h2.Driver` hardcodeado de los `application.yaml` base de auth/flight/booking/payment — fallaba con "Driver org.h2.Driver claims to not accept jdbcUrl" al correr el perfil prod. Spring Boot infiere el driver del URL (H2 en dev, PostgreSQL en prod).
- **Dependencias**: `org.postgresql:postgresql` en flight/booking/payment + `flyway-database-postgresql` en payment (y en booking/flight para las próximas migraciones PostgreSQL).
- **`docker-compose.yml` reescrito**: 15 servicios (broker KRaft, kafdrop, db, prometheus, grafana, jaeger, loki, promtail + 7 micros), todos en la red `observability`.
- **Configs de observabilidad creadas desde cero**: `prometheus.yml`, `loki-config.yml`, `promtail-config.yml` — en el proyecto de referencia eran carpetas vacías.
- **Gateway containerizado (cambio aprobado por el usuario)**: las URIs de ruta pasan a placeholders de entorno `${SERVICE_URI:http://localhost:808X}`; el compose inyecta `AUTH_SERVICE_URI=http://auth-service:8087`, etc.
- **Verificación del stack**:
  - Infra Up: broker, db (healthy, 6 BD creadas), kafdrop, prometheus, grafana, jaeger, loki, promtail.
  - Micros Up: auth-service ✅ (Flyway 3 migraciones en `bootcamp_auth`), flight-service ✅ (Flyway en `bootcamp_flight`), payment-service ✅ (87s, consumer conectado a `broker:29092`), checkin-service ✅, notification-service ✅.
  - **Bloqueo**: booking-service no arranca — el puerto 8082 lo ocupa un `java.exe` local (PID 28996) lanzado a las 10:04. Hay que pararlo (`taskkill /PID 28996`).
  - api-gateway en estado `Created` (depende de todos los servicios; arrancará cuando booking esté Up).
- **Pendiente de saga tras la containerización**: ver `funcional/TO_DO_Final.md` — booking-service (productor `booking.created` + consumidor `payment.processed` + compensación `booking.cancelled`) y flight-service (listeners para reservar/liberar asientos).

ORDEN

Por tanto, yo modificaría tus fases así
Fase	Qué haría
0	Infraestructura + dependencias + arranque
1	Flight: dominio + BD + búsqueda
2	Booking: dominio + BD + CRUD básico
3	Payment: dominio + Strategy
4	Check-in: dominio + BD
5	Notification: dominio + mock sender
6	Kafka básico + BookingCreatedEvent
7	Saga Booking → Payment
8	Saga de error + compensación Flight
9	Booking confirmado → Check-in
10	Notification de todos los eventos
11	End-to-End + Docker + Kafdrop
12	Seguridad/ownership + pruebas finales

LOGICA:

1. ¿Tengo el vuelo?              → Flight
2. ¿Puedo crear una reserva?     → Booking
3. ¿Puedo pagarla?               → Payment
4. ¿Puedo hacer check-in?        → Check-in
5. ¿Puedo notificar?             → Notification

6. ¿Puedo comunicar servicios?  → Kafka
7. ¿Puedo coordinar el proceso? → Saga
8. ¿Puedo compensar errores?     → Saga + eventos

## 13. MEJORAS — OutboxRelay multi-instancia (sustituir `@Scheduled`)

> Estado: **PLAN / ANALIZADO** (2026-08-19). No implementado. La opción recomendada es **B (ShedLock)**.
> Alcance: `payment-service`. Aplicable igual a cualquier otro relay de outbox futuro (booking, flight).

### 13.1 Problema

`OutboxRelay.publishPendingEvents()` usa `@Scheduled(fixedDelay = 3000)` (`infraestructure/outbox/OutboxRelay.java`).

- Dentro de UNA instancia no hay solapamiento: Spring Boot usa un pool de 1 hilo para `@Scheduled` y `fixedDelay` espera a que termine el ciclo anterior.
- El problema real es **multi-instancia**: con N réplicas, cada una ejecuta el mismo polling sobre la misma tabla `outbox_events` (`findTop100ByPublishedAtIsNullOrderByCreatedAtAsc`) y dispara `kafkaTemplate.send()` → **publicaciones duplicadas** del mismo evento (N sends).
- `markAsPublished()` es en memoria, no atómico: dos instancias leen el mismo evento con `publishedAt IS NULL`, ambas envían y ambas marcan.
- El consumidor ya absorbe duplicados vía `processed_events`, pero el tráfico duplicado y el riesgo de orden se mantienen.

### 13.2 Opciones analizadas

| Opción | Enfoque | Veredicto |
|---|---|---|
| **A. Claim en BD** | `SELECT ... FOR UPDATE SKIP LOCKED` (o `UPDATE ... WHERE id IN (SELECT ... LIMIT 100) RETURNING`) + columnas `state/lockedBy/lockedAt` + sweep de leases | Sin deps, pero portabilidad H2/Postgres dudosa (`SKIP LOCKED` limitado en H2), más código y riesgo de filas "colgadas" en `PROCESSING` si la instancia muere entre send y mark |
| **B. ShedLock** ✅ | Lock distribuido en tabla `app_shedlock`; solo una instancia ejecuta el job | **Recomendada**: mínimo cambio, agnóstico de BD, robusto |
| **C. Lock pesimista JPA** | `@Lock(PESSIMISTIC_WRITE)` + `@QueryHint("jakarta.persistence.lock.timeout","-2")` → `FOR UPDATE SKIP LOCKED` en Postgres | Mismo riesgo de portabilidad H2 que A |
| **D. Idempotencia sola** | No cambiar nada; el consumer deduplica | Cero esfuerzo pero no resuelve duplicados/orden |
| **E. CDC / pg_notify** | Debezium o `NOTIFY`/`LISTEN` | Descartado: depende de Postgres (rompe H2 dev) y añade infra (Kafka Connect) desproporcionada |

### 13.3 Detalle de la opción B (ShedLock)

#### Qué cambia (4 archivos)

**1. `payment-service/pom.xml`** — +2 dependencias:

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>7.8.0</version>
</dependency>

<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>7.8.0</version>
</dependency>
```

**2. `OutboxRelay.java`** — +1 anotación, la lógica NO cambia:

```java
@Scheduled(fixedDelay = 3000)
@SchedulerLock(
    name = "outboxRelay",     // id del lock (único global)
    lockAtMostFor = "60s",    // si la instancia muere, otra toma el lock tras expirar
    lockAtLeastFor = "2s"     // evita re-ejecución inmediata entre ciclos
)
public void publishPendingEvents() {
    // EXACTAMENTE IGUAL QUE HOY
}
```

| Parámetro | Función |
|---|---|
| `name` | Identificador del lock en la tabla `app_shedlock`; un `name` por job |
| `lockAtMostFor` | Tiempo máximo que se retiene el lock; si el dueño muere, otra instancia lo toma después de esto (lease). Para un job de ~3s, `60s` es correcto |
| `lockAtLeastFor` | Mínimo de retención aunque el job acabe antes; evita que otra instancia tome el lock por latencia de escritura |

**3. `infraestructure/config/ShedLockConfig.java`** — clase nueva:

```java
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("app_shedlock")
                .usingDbTime()   // reloj de la BD, no de cada JVM → sin clock skew
                .build()
        );
    }
}
```

**4. Migración Flyway** — `V2__create_shedlock.sql` (o la siguiente V del servicio):

```sql
CREATE TABLE app_shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

ShedLock gestiona la tabla entera; no se toca manualmente. Funciona idéntico en H2 (dev) y PostgreSQL (prod).

#### Mecanismo

Cada ciclo, ShedLock intenta `INSERT INTO app_shedlock ... WHERE name=? AND (lock_until IS NULL OR lock_until < now())`:
- Éxito → el job se ejecuta.
- Fracaso (otra instancia tiene el lock) → el método se omite silenciosamente.
- Si el dueño muere → el lock expira a los `lockAtMostFor` y otra instancia lo toma.

#### Ganancia funcional

| Escenario | Sin ShedLock | Con ShedLock |
|---|---|---|
| 1 instancia | OK | OK (idéntico) |
| 2+ instancias | Sends duplicados (N) | 1 sola instancia ejecuta → 0 duplicados |
| Instancia cae | Se reintenta al arrancar | Lock expira → otra instancia toma el relay |
| Latencia de red/DB | Sin efecto sobre solapamiento | `lockAtLeastFor` evita toma prematura entre ciclos |
| Clock skew entre nodos | — | `usingDbTime()` usa el reloj de la BD |

#### Límites (lo que NO resuelve)

- No reduce latencia: el evento sigue esperando al próximo ciclo (3s).
- No da exactly-once por sí solo: la garantía final la sigue dando el consumer idempotente (`processed_events`).
- Añade 1 tabla + 1 dependencia.

#### Orden de implementación

```
1. payment-service/pom.xml                        → +2 dependencias
2. infraestructure/config/ShedLockConfig.java     → clase nueva (~15 líneas)
3. db/migration/V2__create_shedlock.sql           → tabla nueva
4. OutboxRelay.java                               → +1 anotación @SchedulerLock
5. mvn compile + mvn test                         → verificación
```

## 14. MEJORA FUTURA — Validación asíncrona del flightId (`BookingRejectedEvent`)

### 14.1 El hueco

Booking NO valida que el `flightId` exista — no puede sin llamada síncrona a flight (prohibida por la arquitectura). El cliente aporta el id desde la búsqueda (US-001) y booking confía ciegamente. Si el id es inexistente o no hay asientos:

```
POST /bookings {flightId: 999} → 201 CREATED (PENDING)
  → booking.created → flight consume → reserveSeatsUseCase(999) falla
  → reserva PENDING para siempre (saga colgada en silencio)
```

Mismo patrón de fallo lejano y tardío que motivó el enum `PaymentMethod` en booking.

### 14.2 Por qué NO request/reply por Kafka

La alternativa "preguntar y esperar" (`flight.validate` + respuesta con correlationId) es un anti-patrón: booking quedaría bloqueado esperando para responder el POST — sincronía HTTP disfrazada de eventos, con coste extra (correlationIds, timeouts, peticiones pendientes). Se descarta.

### 14.3 La propuesta (patrón ya usado en la saga)

El pago YA es una validación asíncrona: PENDING → resultado por evento → confirm/cancel. La reserva de asientos es lo mismo; solo falta que **el fallo se comunique de vuelta**:

| Elemento | Detalle |
|---|---|
| Nuevo evento | `BookingRejectedEvent` en topic `booking.rejected` |
| Productor | flight-service: catch del listener de `booking.created` cuando `reserveSeatsUseCase` falle (vuelo inexistente / inventario insuficiente) |
| Consumidor | booking-service: `cancel()` sobre la reserva (requiere relajar `ensurePending` o reutilizar la transición existente) + notificación futura |
| Contrato JSON | `{"bookingId": "...", "flightId": "...", "reason": "..."}` (sección 6.2) |
| Alternativa descartada | Copia local del catálogo de vuelos en booking (rompe independencia de servicios) |

**Cuándo:** tras cerrar el bloque flight-service consumidor y probar la saga e2e básica. No bloquea nada actual.
