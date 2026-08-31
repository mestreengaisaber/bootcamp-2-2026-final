# DDD Check-in Service — Guía de Dominio (para aprendizaje)

> Documento interno de aprendizaje. Explica el porqué de cada decisión en la clase `CheckIn` (agregado raíz) siguiendo principios DDD y arquitectura hexagonal.

---

## 1. El problema: Crear vs Restaurar

Cuando cargas un `CheckIn` de la base de datos, **NO quieres** que pase por las validaciones de "nuevo".

Ejemplo: un `CheckIn` guardado en BD con `status=COMPLETED`, `boardingList` ya asignado, `createdAt` hace 5 minutos.

Si usas el mismo constructor que para "crear nuevo", **rompes el estado real**:
- Le pondrías `status=PENDING` (incorrecto, ya está completado)
- Le pondrías `createdAt=now()` (pierdes el histórico)
- Perderías `boardingPass` y `completedAt`

**Por eso existen DOS constructores privados** con propósitos distintos.

---

## 2. Dos constructores privados

### Constructor de CREACIÓN (para `CheckIn.create()`)
```java
private CheckIn(Long bookingId, Long flightId, Long passengerId) {
    this.bookingId = bookingId;      // Vienen del request/evento
    this.flightId = flightId;
    this.passengerId = passengerId;
    this.status = CheckInStatus.PENDING;   // REGLA: siempre empieza PENDING
    this.createdAt = Instant.now();        // REGLA: ahora
    // completedAt = null, boardingPass = null (implícito)
}
```

**Impone reglas de "nuevo"**: estado inicial, timestamp de creación, campos nulos.

---

### Constructor de RESTAURACIÓN (para `CheckIn.restore()` — adapter JPA)
```java
private CheckIn(Long id, Long bookingId, Long flightId, Long passengerId,
                CheckInStatus status, Instant createdAt, Instant completedAt,
                BoardingPass boardingPass) {
    this.id = id;                    // La BD YA lo asignó
    this.bookingId = bookingId;      // Tal cual está en BD
    this.flightId = flightId;
    this.passengerId = passengerId;
    this.status = status;            // Puede ser COMPLETED (¡no tocamos!)
    this.createdAt = createdAt;      // Tal cual (histórico)
    this.completedAt = completedAt;  // Puede tener valor
    this.boardingPass = boardingPass; // Puede tener valor
}
```

**Confía en la BD**: carga el estado tal cual, sin imponer reglas de "nuevo".

---

## 3. Factory Methods: puertas de entrada controladas

### `CheckIn.create(bookingId, flightId, passengerId)`
```java
public static CheckIn create(Long bookingId, Long flightId, Long passengerId) {
    if (bookingId == null || flightId == null || passengerId == null) {
        throw new IllegalArgumentException("bookingId, flightId, passengerId son obligatorios");
    }
    return new CheckIn(bookingId, flightId, passengerId);  // Llama constructor CREACIÓN
}
```
**Cuándo**: El caso de uso recibe `POST /checkin`, valida que la reserva existe y está CONFIRMED (en cache local), **luego** crea el agregado.

---

### `CheckIn.restore(...)` — Adapter JPA
```java
public static CheckIn restore(Long id, Long bookingId, Long flightId, Long passengerId,
                               CheckInStatus status, Instant createdAt, Instant completedAt,
                               BoardingPass boardingPass) {
    return new CheckIn(id, bookingId, flightId, passengerId, status, createdAt, completedAt, boardingPass);
}
```
**Cuándo**: `CheckInJpaRepository.findById(42)` → `CheckInPersistenceAdapter` mapea entity → domain → llama `restore()`.

---

### `checkIn.complete(boardingPass)` — Mutación de dominio
```java
public void complete(BoardingPass boardingPass) {
    ensurePending();           // GUARDA: solo desde PENDING
    this.boardingPass = boardingPass;
    this.status = CheckInStatus.COMPLETED;
    this.completedAt = Instant.now();
}

private void ensurePending() {
    if (this.status != CheckInStatus.PENDING) {
        throw new IllegalStateException("Check-in ya completado: " + status);
    }
}
```
**Cuándo**: Caso de uso ya validó reserva CONFIRMED, genera `BoardingPass`, llama `complete()`.

---

## 4. Diagrama de ciclo de vida

```
PASAJERO POST /checkin          ADAPTER findById(42)
        │                              │
        ▼                              ▼
  CheckIn.create()              CheckIn.restore()
        │                              │
        ▼                              ▼
┌─────────────────┐           ┌─────────────────────┐
│ Constructor     │           │ Constructor         │
│ CREACIÓN        │           │ RESTAURACIÓN        │
│ - status=PENDING│           │ - status=COMPLETED  │
│ - createdAt=now │           │ - createdAt=histórico│
│ - completedAt=null          │ - completedAt=valor │
│ - boardingPass=null         │ - boardingPass=valor│
└────────┬────────┘           └──────────┬──────────┘
         │                               │
         │      checkIn.complete(bp)     │
         │              │                │
         ▼              ▼                ▼
┌─────────────────────────────────────────────────────┐
│  Mutación: status=COMPLETED, completedAt=now(),     │
│  boardingPass=valor → adapter.save() + evento       │
└─────────────────────────────────────────────────────┘
```

---

## 5. `final` vs NO `final` — La regla DDD

> **Identidad + Invariantes de negocio = `final`**  
> **Estado que muda en el ciclo de vida = NO `final`**

| Campo | ¿Cambia legítimamente? | `final`? | Tipo |
|-------|------------------------|----------|------|
| `id` | No (asignado una vez en BD) | **Sí** | Identidad técnica |
| `bookingId` | **Nunca** (invariante) | **Sí** | Regla de negocio |
| `flightId` | **Nunca** (invariante) | **Sí** | Regla de negocio |
| `passengerId` | **Nunca** (invariante) | **Sí** | Regla de negocio |
| `createdAt` | **Nunca** (histórico) | **Sí** | Hecho pasado |
| `status` | **Sí**: PENDING → COMPLETED | **No** | Estado ciclo de vida |
| `completedAt` | **Sí**: null → fecha | **No** | Momento transición |
| `boardingPass` | **Sí**: null → valor | **No** | Resultado operación |

**Pregunta mental**: *"¿Puede este campo tener un valor distinto mañana sin ser error de negocio?"*

- `bookingId` → ¿Mañana el check-in 42 pertenece a reserva 999? **NO** → `final`
- `status` → ¿Mañana pasa de PENDING a COMPLETED? **SÍ** → **NO `final`**

---

## 6. Por qué tu constructor de 4 campos no basta

```java
// Tu versión (snapshot inmutable, estilo record)
public CheckIn(Long bookingId, Long flightId, Long passengerId, Instant createdAt) { ... }
```

**Te faltan los campos mutables** que empiezan en estado inicial y cambian:

```java
// Constructor de CREACIÓN real
private CheckIn(Long bookingId, Long flightId, Long passengerId) {
    this.bookingId = bookingId;      // final
    this.flightId = flightId;        // final
    this.passengerId = passengerId;  // final
    this.createdAt = Instant.now();  // final (se fija AHORA)
    
    // Estado inicial del ciclo de vida (NO final)
    this.status = CheckInStatus.PENDING;
    this.completedAt = null;
    this.boardingPass = null;
}
```

---

## 7. Por qué ELIMINAR el constructor público de 4 parámetros

### El constructor problemático
```java
// ❌ ELIMINAR - Constructor público que rompe la encapsulación
public CheckIn(Long bookingId, Long flightId, Long passengerId, Instant createdAt) { ... }
```

### 7 Razones para eliminarlo

#### 1. **Rompe la creación controlada (Single Entry Point)**
El factory `create()` es la **única forma válida** de crear un `CheckIn` nuevo. Si dejas el constructor público, cualquiera puede saltarse las reglas:
```java
// MALO - salta validaciones y reglas de negocio
CheckIn checkIn = new CheckIn(1001L, 555L, 777L, Instant.now().minusDays(10)); // createdAt falso
CheckIn checkIn2 = new CheckIn(null, null, null, null); // sin validación
```

#### 2. **`createdAt` es un histórico inmutable, no un parámetro**
> **Regla de negocio**: *"Un check-in se crea AHORA, no 'cuando el cliente quiera'."*

Si permites pasar `createdAt`:
- Un test/bug pone `createdAt = ayer` → **dato histórico corrupto**
- Pierdes la garantía de que `createdAt` refleja **cuándo se pidió realmente**
- `Instant.now()` en `create()` es una **regla de dominio**, no una opción

#### 3. **Confunde "crear" con "restaurar"**
| Constructor | Propósito | `createdAt` |
|-------------|-----------|-------------|
| `create()` → constructor **CREACIÓN** | Nuevo check-in | `Instant.now()` (regla de negocio) |
| `restore()` → constructor **RESTAURACIÓN** | Cargar de BD | Valor **real de BD** (histórico) |

Tu constructor público **mezcla ambos mundos**: parece creación pero acepta `createdAt` como parámetro (comportamiento de restauración).

#### 4. **Encapsulación = invariantes protegidas**
En DDD, el agregado **protege sus invariantes**. Al crear `CheckIn`:
- `bookingId`, `flightId`, `passengerId` obligatorios ✓
- `status = PENDING` (siempre) ✓
- `createdAt = now()` (siempre) ✓
- `completedAt = null`, `boardingPass = null` (siempre) ✓

**Constructor público = invariantes desprotegidas.**

#### 5. **Consistencia con el proyecto**
Mira `Booking.java` (booking-service) o `SeatInventory.java` (flight-service):
- **Cero constructores públicos**
- Solo **factory methods** (`create()`, `restore()`)
- Constructores **privados**

#### 6. **Testabilidad y mantenimiento**
- Un solo punto de creación → fácil de testear, auditar, cambiar
- Múltiples constructores públicos → caminos de código no controlados, bugs sutiles

#### 7. **Principio de menor sorpresa**
Quien lea `CheckIn.create(...)` sabe que obtiene un agregado **válido según reglas de negocio**. Quien vea `new CheckIn(...)` no sabe qué estado tiene.

---

### Resumen visual

```
┌────────────────────────────────────────────────────────────┐
│                    CREAR CheckIn NUEVO                      │
├────────────────────────────────────────────────────────────┤
│  CheckIn.create(bookingId, flightId, passengerId)          │
│       │                                                     │
│       ▼                                                     │
│  private CheckIn(bookingId, flightId, passengerId)         │
│       │                                                     │
│       ├── this.bookingId = bookingId       (final)         │
│       ├── this.flightId = flightId         (final)         │
│       ├── this.passengerId = passengerId   (final)         │
│       ├── this.createdAt = Instant.now()   (final, REGLA)  │
│       ├── this.status = PENDING            (mutable)       │
│       ├── this.completedAt = null          (mutable)       │
│       └── this.boardingPass = null         (mutable)       │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│                    RESTAURAR de BD                          │
├────────────────────────────────────────────────────────────┤
│  CheckIn.restore(id, bookingId, flightId, passengerId,     │
│                  status, createdAt, completedAt, boardingPass)│
│       │                                                     │
│       ▼                                                     │
│  private CheckIn(id, bookingId, flightId, passengerId,     │
│                  status, createdAt, completedAt, boardingPass)│
│       │                                                     │
│       └── Carga TODO tal cual (confía en BD)               │
└────────────────────────────────────────────────────────────┘
```

**Conclusión**: El constructor público de 4 params **no tiene cabida** en ninguno de los dos flujos válidos. Elimínalo y usa solo los dos constructores privados + factories.

---

## 8. Analogía del mundo real

| Campo `final` (invariante) | Campo NO `final` (estado) |
|---------------------------|---------------------------|
| Tu DNI (nunca cambia) | Tu estado civil (soltero → casado) |
| Tu fecha de nacimiento | Tu dirección actual |
| El vuelo para el que compraste el billete | Si ya has facturado o no |
| El pasajero que eres tú | Tu tarjeta de embarque (te la dan al facturar) |

---

## 9. Validación en la frontera correcta

| Frontera | Qué valida | Ejemplo |
|----------|------------|---------|
| **Factory `create()`** | Datos de entrada mínimos (not null) | `bookingId != null` |
| **Caso de uso (Application)** | Reglas de **aplicación** (existencia, estado externo) | "¿Reserva 1001 existe y está CONFIRMED en cache local?" |
| **Método dominio `complete()`** | Reglas de **negocio** (invariantes del agregado) | "¿Está en PENDING? ¿BoardingPass válido?" |
| **Factory `restore()`** | **Nada** (confía en BD) | — |

---

## 10. Record vs Class — Cuándo usar cada uno

| Tipo | Cuándo | Ejemplos proyecto |
|------|--------|-------------------|
| **`record`** | Datos inmutables en tránsito (commands, DTOs, eventos, VOs **sin lógica**) | `BookingCreatedEvent`, `Passenger`, `PaymentMethod`, `BoardingPass` |
| **`enum`** | Conjunto cerrado de valores | `BookingStatus`, `CheckInStatus`, `PaymentStatus` |
| **`class`** | Entidades/agregados con **identidad + estado mutable + reglas** (métodos que validan transiciones) | `Booking`, `CheckIn`, `SeatInventory`, `Flight`, `Payment` |

**Clave en flight-service**: `Flight` es `record` porque **delega mutación** a `SeatInventory` (class). `CheckIn` tiene **mutación directa** (`status`, `completedAt`, `boardingPass`) → **class**.

---

## 11. Constructor compacto de records (para `BoardingPass`)

```java
public record BoardingPass(
        Long id, Long checkInId, String seatNumber, String gate, LocalDateTime boardingTime
) {
    // Constructor compacto (sin parámetros) — validaciones ANTES de asignar campos final
    public BoardingPass {
        if (seatNumber == null || seatNumber.isBlank()) throw new IllegalArgumentException("seatNumber obligatorio");
        if (gate == null || gate.isBlank()) throw new IllegalArgumentException("gate obligatorio");
        if (boardingTime == null) throw new IllegalArgumentException("boardingTime obligatorio");
        // id y checkInId pueden ser null (se asignan al persistir)
    }
}
```

**Qué hace Java internamente:**
```java
// Tu compact constructor
public BoardingPass { validaciones... }

// Lo que Java genera (constructor canónico REAL)
public BoardingPass(Long id, Long checkInId, String seatNumber, String gate, LocalDateTime boardingTime) {
    // 1. PRIMERO: tu compact constructor (validaciones)
    if (seatNumber == null) throw...
    // 2. LUEGO: asignación automática de campos final
    this.id = id; this.checkInId = checkInId; this.seatNumber = seatNumber; ...
}
```

---

## 12. Checklist para tu `CheckIn.java`

- [x] `CheckInStatus` enum: `PENDING`, `COMPLETED`
- [x] `BoardingPass` record: 5 campos + constructor compacto con validaciones
- [x] `CheckIn` class:
  - [x] 5 campos `final` (id, bookingId, flightId, passengerId, createdAt)
  - [x] 3 campos NO `final` (status, completedAt, boardingPass)
  - [x] Constructor privado CREACIÓN (3 params + fija estado inicial)
  - [x] Constructor privado RESTAURACIÓN (8 params + carga todo)
  - [x] Factory `create()` → valida entrada → llama constructor CREACIÓN
  - [x] Factory `restore()` → sin validaciones → llama constructor RESTAURACIÓN
  - [x] Método `complete(BoardingPass)` → `ensurePending()` → muta estado
  - [x] `ensurePending()` privado → lanza si no PENDING
  - [x] **Cero anotaciones Spring/JPA** en dominio
  - [x] **Cero setters públicos** — solo getters

---

## 13. Estado de capas

1. **DOMAIN** ✅ → `CheckIn`, `BoardingPass`, `CheckInStatus`
2. **APPLICATION** ✅ → Puertos in/out, command, evento, excepción, service
3. **INFRASTRUCTURE** ⬜ → Pendiente (JPA, Kafka, REST, Flyway, config)

---

## 14. Arquitectura Hexagonal — Conceptos clave

### Qué es la arquitectura hexagonal

Un **hexágono** con el **dominio** en el centro. Alrededor tiene **puertos** (interfaces). Fuera viven los **adaptadores** (implementaciones reales).

```
        ┌──────────────────────────────────┐
        │         INFRAESTRUCTURA           │
        │  (JPA, Kafka, REST controller)    │
        │                                   │
        │    ┌─────────────────────────┐    │
        │    │      APPLICATION         │    │
        │    │  (casos de uso + puertos)│    │
        │    │                          │    │
        │    │    ┌──────────────┐      │    │
        │    │    │   DOMINIO    │      │    │
        │    │    │  (negocio)   │      │    │
        │    │    └──────────────┘      │    │
        │    └─────────────────────────┘    │
        └──────────────────────────────────┘
```

**Regla de oro**: las flechas van **hacia dentro**. El dominio no sabe nada del mundo exterior. La aplicación conoce el dominio pero no la infraestructura.

### Qué hace cada capa

| Capa | Qué hace | Ejemplo en checkin-service |
|---|---|---|
| **Dominio** | Define las **reglas del negocio** | "Solo puedes completar un check-in si está PENDING" |
| **Application** | **Orquesta** los pasos, delega al dominio y a los puertos-out | "Primero verifica, luego crea, luego guarda y publica" |
| **Infrastructure** (futura) | **Implementa** los puertos-out con tecnología real | JPA para guardar, Kafka para publicar |

---

## 15. Dominio de checkin-service — Cómo funciona

### CheckIn.java — El agregado raíz

Una **entidad** con identidad (tiene `id`) y estado que cambia:

```
Booking + Flight + Passenger
        │
        ▼
   CheckIn.create()  →  PENDING  →  complete()  →  COMPLETED
   (siempre)                                    (una sola vez)
```

**Qué protege (invariantes):**
- `bookingId`, `flightId`, `passengerId` → nunca cambian (son `final`)
- `status` → solo puede ir de PENDING a COMPLETED (nunca al revés)
- `createdAt` → se fija al crear, no se puede cambiar

**Cómo se crea:** solo vía `CheckIn.create()` (factory method, constructor privado). Nadie puede hacer `new CheckIn(...)` desde fuera.

### BoardingPass.java — Un valor

Un **record** (inmutable). No tiene identidad, no cambia. Es el resultado de facturar: asiento, puerta, hora.

### CheckInStatus.java — Un enum

Solo dos estados: `PENDING` y `COMPLETED`. No hay CANCELADO, no hay RECHAZADO. Simple.

---

## 16. Application de checkin-service — Cómo se usa el dominio

La aplicación **orquesta**. Sabe qué pasos dar, pero no sabe cómo se ejecutan los pasos externos (guardar en BD, publicar evento).

### Los puertos (interfaces)

Los puertos son **enchufes**. El caso de uso dice "necesito enchufar esto" pero no sabe qué hay al otro lado.

```
PUERTOS DE ENTRADA (lo que el caso de uso EXPONE)
─────────────────────────────────────────────────
PerformCheckInUseCase
  └── performCheckIn(command) → CheckIn
      "Alguien me pide facturar. Yo sé QUÉ hacer,
       no sé dónde guardar ni cómo publicar."

PUERTOS DE SALIDA (lo que el caso de uso NECESITA)
─────────────────────────────────────────────────
CheckInRepositoryPort
  ├── save(checkIn)          → "Guárdame esto en la BD"
  └── findByBookingId(id)    → "¿Ya tiene check-in esta reserva?"

CheckInEventPublisherPort
  └── checkInCompleted(checkIn) → "Publica que se completó el check-in"
```

**Por qué interfaces y no clases concretas?** Porque hoy el adaptador de `CheckInRepositoryPort` será JPA (H2/PostgreSQL). Mañana podría ser Mongo, o un fichero, o un mock en un test. El caso de uso **no cambia**.

### El caso de uso (CheckInService)

Es el **director de orquesta**. Recibe una orden y ejecuta pasos:

```
PerformCheckInCommand (bookingId, flightId, passengerId)
        │
        ▼
CheckInService.performCheckIn()
        │
        ├── "¿Ya tiene check-in esta reserva?"
        │      → repositoryPort.findByBookingId()
        │      → Si sí: CheckInAlreadyExistsException
        │
        ├── "Crear el check-in"
        │      → CheckIn.create()  ← DOMINIO
        │
        ├── "Generar tarjeta de embarque"
        │      → generateBoardingPass()  ← helper privado
        │
        ├── "Marcar como completado"
        │      → checkIn.complete()  ← DOMINIO
        │
        ├── "Guardar en BD"
        │      → repositoryPort.save()  ← PUERTO-OUT
        │
        └── "Publicar evento"
               → eventPublisherPort.checkInCompleted()  ← PUERTO-OUT
```

**Lo que hace el caso de uso:**
1. Decide **qué** pasar (orchestration)
2. Delega al **dominio** las reglas de negocio
3. Delega a los **puertos-out** las operaciones externas

**Lo que NO hace:**
- No sabe qué BD hay detrás (JPA, Mongo, ¿qué?)
- No sabe cómo se publica el evento (Kafka, RabbitMQ, ¿qué?)
- No tiene `@Autowired` ni `@Repository` — solo recibe puertos por constructor

### El command (PerformCheckInCommand)

Un **record** que transporta datos del controller al caso de uso. Es un DTO de entrada. Nada más.

### El evento (CheckInCompletedEvent)

El **contrato de integración** con otros servicios (notification-service lo consumirá). Tiene un envelope estándar (`eventId`, `eventType`, `version`, `occurredAt`...) igual que `BookingCreatedEvent` y `PaymentProcessedEvent`.

### La excepción (CheckInAlreadyExistsException)

Una sola. Igual que `booking-service` tiene `BookingNotFoundException` y `flight-service` tiene `SeatInventoryNotFoundException`. Protege el **único invariante cross-aggregate**: un check-in por reserva.

---

## 17. El flujo completo

```
PASSAJERO                         CHECKIN-SERVICE                    DOMINIO
    │                                   │                              │
    │  POST /api/v1/checkins            │                              │
    │  {bookingId, flightId, passengerId}                              │
    │──────────────────────────────────►│                              │
    │                                   │                              │
    │                          ┌────────┴────────┐                    │
    │                          │ ¿Ya tiene        │                    │
    │                          │ check-in?        │                    │
    │                          │ repositoryPort   │                    │
    │                          └────────┬────────┘                    │
    │                                   │                              │
    │                          ┌────────┴────────┐                    │
    │                          │ CheckIn.create() │──────────────────►│
    │                          └────────┬────────┘                    │
    │                                   │              PENDING         │
    │                          ┌────────┴────────┐                    │
    │                          │ generateBP()    │                    │
    │                          │ checkIn.complete()│─────────────────►│
    │                          └────────┬────────┘                    │
    │                                   │          COMPLETED           │
    │                          ┌────────┴────────┐                    │
    │                          │ save()          │                    │
    │                          │ publish()       │                    │
    │                          └────────┬────────┘                    │
    │                                   │                              │
    │  ← CheckIn (COMPLETED)           │                              │
    │◄──────────────────────────────────│                              │
```

---

## 18. Infrastructure — Paquetes y patrón

### Estructura estándar

```
infrastructure/
├── adapter/           → adapters de EVENTOS (outbox, kafka)
├── persistence/       → entities, repositories, adapters de BD
├── outbox/            → OutboxRelay (@Scheduled)
├── kafka/             → listeners (consumidores de eventos)
├── web/               → controllers + dto/
└── config/            → @Configuration (ensamblador final)
```

### Qué va en cada paquete

| Paquete | Qué va | Ejemplo |
|---|---|---|
| `adapter/` | Solo adapters de **eventos/mensajes** | `CheckInEventOutboxAdapter` |
| `persistence/` | Entities, repositories, y adapters de **BD** | `CheckInEntity`, `CheckInPersistenceAdapter` |
| `outbox/` | `@Scheduled` que publica eventos pendientes a Kafka | `OutboxRelay` |
| `kafka/` | `@KafkaListener` que consume eventos de otros servicios | `BookingConfirmedKafkaListener` |
| `web/` | Controllers REST + DTOs (request/response) | `CheckInController` |
| `config/` | `@Configuration` que ensambla todos los beans manualmente | `AppCheckInConfig` |

### Regla de separación adapter vs persistence

```
adapter/     → implementa puertos de EVENTOS (CheckInEventPublisherPort)
persistence/ → implementa puertos de BD (CheckInRepositoryPort, BookingValidationPort)
```

**Por qué separar?** Porque el adapter de eventos usa `OutboxJpaRepository` (tabla outbox), mientras que el adapter de BD usa `CheckInJpaRepository` (tabla check-ins). Son responsabilidades distintas.

---

## 19. Infrastructure — Persistencia

### Patrón: Entity → Repository → Adapter

```
CheckInRepositoryPort (application)         CheckInPersistenceAdapter (infrastructure)
─────────────────────────────               ─────────────────────────────────────────
save(CheckIn)                       →       CheckIn → CheckInEntity → jpaRepo.save() → CheckIn
findByBookingId(Long)               →       jpaRepo.findByBookingId() → CheckInEntity → CheckIn
```

### Entity (CheckInEntity)

Una clase con anotaciones `@Entity` que Spring JPA sabe leer y escribir en una tabla. **No tiene lógica de negocio**, solo mapeo.

```java
@Entity
@Table(name = "check_ins")
public class CheckInEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;
    // ... campos mapeados a columnas
}
```

### Repository (CheckInJpaRepository)

Spring Data genera automáticamente las queries (`save`, `findById`, `findByBookingId`). Sin esto, tendrías que escribir SQL a mano.

```java
public interface CheckInJpaRepository extends JpaRepository<CheckInEntity, Long> {
    Optional<CheckInEntity> findByBookingId(Long bookingId);
}
```

### Adapter (CheckInPersistenceAdapter)

**Implementa** `CheckInRepositoryPort` (el puerto-out). Es el **puente** entre dominio y JPA.

```java
public class CheckInPersistenceAdapter implements CheckInRepositoryPort {
    private final CheckInJpaRepository jpaRepo;

    @Override
    public CheckIn save(CheckIn checkIn) {
        CheckInEntity entity = toEntity(checkIn);      // dominio → entity
        CheckInEntity saved = jpaRepo.save(entity);     // guardar en BD
        return toDomain(saved);                         // entity → dominio
    }
}
```

### Conversión toDomain/toEntity

Los métodos privados traducen entre dominio y entity:

```
toEntity(CheckIn):
  → new CheckInEntity(
      checkIn.getBookingId(),
      checkIn.getFlightId(),
      checkIn.getPassengerId(),
      checkIn.getStatus(),
      checkIn.getCreatedAt(),
      checkIn.getCompletedAt(),
      bp != null ? bp.seatNumber() : null,
      bp != null ? bp.gate() : null,
      bp != null ? bp.boardingTime() : null
    )

toDomain(CheckInEntity):
  → CheckIn.restore(          ← usa factory del dominio
      entity.getId(),
      entity.getBookingId(),
      // ... todos los campos
    )
```

**Importante**: usar `CheckIn.restore()` (factory del dominio) para reconstruir el agregado. Nunca hacer `new CheckIn(...)` desde el adapter.

---

## 20. Infrastructure — Outbox

### Por qué existe el outbox

Si el service guarda el check-in Y publica el evento en la misma transacción, pero Kafka falla, **pierdes el evento**. Con el outbox:

1. Guardas check-in + evento en la **misma transacción** (BD)
2. Un `@Scheduled` (OutboxRelay) lee los pendientes y los publica a Kafka
3. Si Kafka falla, el evento queda pendiente y se reintenta

```
MISMA TRANSACCIÓN:
  ├── save(checkIn)           → tabla check_ins
  └── save(outboxEvent)       → tabla outbox_events

DESPUÉS (cada 3s):
  OutboxRelay lee pendientes → KafkaTemplate.send() → Kafka
```

### OutboxEvent (entity JPA)

```java
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String eventType;     // "CHECKIN_COMPLETED"
    private String payload;       // JSON serializado del evento
    private Instant createdAt;
    private Instant publishedAt;  // null = pendiente, con valor = publicado

    public void markAsPublished() {
        this.publishedAt = Instant.now();
    }
}
```

### OutboxRelay (@Scheduled)

```java
public class OutboxRelay {
    private static final Map<String, String> TOPIC_BY_EVENT_TYPE = Map.of(
            "CHECKIN_COMPLETED", "checkin.completed"
    );

    @Scheduled(fixedDelay = 3000)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            String topic = TOPIC_BY_EVENT_TYPE.get(event.getEventType());
            kafkaTemplate.send(topic, event.getPayload()).whenComplete((result, ex) -> {
                if (ex == null) {
                    event.markAsPublished();
                    outboxRepo.save(event);
                }
            });
        }
    }
}
```

---

## 21. Infrastructure — Kafka

### Listener (consuming events)

Un `@KafkaListener` recibe eventos de otros servicios y los procesa:

```java
@Component
public class BookingConfirmedKafkaListener {
    @KafkaListener(topics = "booking.confirmed")
    public void handleBookingConfirmed(Map<String, Object> rawEvent) {
        // 1. Idempotencia: ¿ya procesé este evento?
        if (isDuplicate(eventId)) return;

        // 2. Procesar: guardar datos en processed_events
        ProcessedEventEntity entity = new ProcessedEventEntity(...);
        repo.save(entity);

        // 3. Registrar como procesado
        recordProcessed(eventId);
    }
}
```

### Idempotencia con processed_events

**Problema**: Kafka puede enviar el mismo evento 2 veces (redelivery). Si procesas 2 veces, creas duplicados.

**Solución**: tabla `processed_events` con `eventId` como PK. Antes de procesar, consultas si ya existe.

```
1. ¿Existe eventId en processed_events? → SÍ: ignorar (duplicado)
2. ¿No existe? → procesar → guardar en processed_events
```

```java
private boolean isDuplicate(String eventId) {
    return processedEventRepository.existsById(eventId);
}

private void recordProcessed(String eventId) {
    try {
        processedEventRepository.save(new ProcessedEventEntity(eventId, Instant.now()));
    } catch (DataIntegrityViolationException e) {
        // Otra entrega ya registró este eventId (race condition)
        log.warn("Processed event {} already recorded (race), ignoring", eventId);
    }
}
```

---

## 22. Infrastructure — Web

### Controller

Un `@RestController` que recibe HTTP, traduce a command, delega al caso de uso, y devuelve HTTP response:

```java
@RestController
@RequestMapping("/api/v1/checkins")
public class CheckInController {
    private final PerformCheckInUseCase performCheckInUseCase;

    @PostMapping
    public ResponseEntity<?> performCheckIn(@Valid @RequestBody PerformCheckInRequest request) {
        try {
            PerformCheckInCommand command = new PerformCheckInCommand(
                    request.bookingId(), request.flightId(), request.passengerId());
            CheckIn checkIn = performCheckInUseCase.performCheckIn(command);
            return ResponseEntity.status(HttpStatus.CREATED).body(CheckInResponse.from(checkIn));
        } catch (CheckInAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (BookingNotConfirmedException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
```

### DTOs (records)

```
PerformCheckInRequest (entrada):
  → bookingId, flightId, passengerId

CheckInResponse (salida):
  → id, bookingId, flightId, passengerId, status, seatNumber, gate, boardingTime, completedAt
  → factory method from(CheckIn) para convertir dominio → response
```

### Tabla de HTTP status

| Status | Cuándo |
|---|---|
| `201 Created` | Happy path: check-in creado |
| `400 Bad Request` | Datos inválidos o reserva no confirmada |
| `409 Conflict` | Ya existe check-in para esa reserva |
| `500 Internal Server Error` | Error no manejado |

---

## 23. Infrastructure — Config

### Por qué va al final

La config es el **ensamblador de beans**. Necesita que **TODOS los componentes existan** antes de poder ensamblarlos.

```java
@EnableKafka
@Configuration
public class AppCheckInConfig {

    // Puerto-out → Adapter → JPA
    @Bean
    public CheckInRepositoryPort checkInRepositoryPort(CheckInJpaRepository jpa) {
        return new CheckInPersistenceAdapter(jpa);
    }

    // Event publisher → Outbox adapter
    @Bean
    public CheckInEventPublisherPort eventPublisherPort(OutboxJpaRepository outbox, ObjectMapper mapper) {
        return new CheckInEventOutboxAdapter(outbox, mapper);
    }

    // Booking validation → Adapter
    @Bean
    public BookingValidationPort bookingValidationPort(ProcessedEventJpaRepository repo) {
        return new BookingValidationAdapter(repo);
    }

    // Outbox relay → Kafka
    @Bean
    public OutboxRelay outboxRelay(OutboxJpaRepository outbox, KafkaTemplate<String, String> kafka) {
        return new OutboxRelay(outbox, kafka);
    }

    // Caso de uso → inyecta puertos
    @Bean
    public PerformCheckInUseCase performCheckInUseCase(CheckInRepositoryPort repo,
                                                        CheckInEventPublisherPort publisher,
                                                        BookingValidationPort validator) {
        return new CheckInService(repo, publisher, validator);
    }

    // Controller → inyecta caso de uso
    @Bean
    public CheckInController checkInController(PerformCheckInUseCase useCase) {
        return new CheckInController(useCase);
    }
}
```

**Es como un rompecabezas**: cada pieza encaja con la anterior. Si haces la config primero, no tienes piezas que ensamblar.

---

## 24. Caso de uso — Reserva confirmada

### El problema

"No se puede hacer check-in sin reserva confirmada." — Pero booking-service no tiene un endpoint REST para validar esto.

### La solución: evento + tabla local

```
booking-service                          checkin-service
──────────────                          ──────────────
1. Reserva se confirma
   └─ eventPublisher.bookingConfirmed()
      └─ guarda en outbox

2. OutboxRelay publica
   └─ "booking.confirmed" → Kafka

                                                    3. BookingConfirmedKafkaListener recibe
                                                       └─ guarda en processed_events

                                                    4. POST /api/v1/checkins
                                                       └─ CheckInService.performCheckIn()
                                                          └─ ¿Existe en processed_events?
                                                             ├─ SÍ → crea check-in
                                                             └─ NO → lanza BookingNotConfirmedException
```

### Puerto: BookingValidationPort

```java
public interface BookingValidationPort {
    boolean existsConfirmedBooking(Long bookingId);
}
```

### Adapter: BookingValidationAdapter

```java
public class BookingValidationAdapter implements BookingValidationPort {
    private final ProcessedEventJpaRepository repo;

    @Override
    public boolean existsConfirmedBooking(Long bookingId) {
        return repo.existsByEventTypeAndBookingId("BOOKING_CONFIRMED", bookingId);
    }
}
```

### Validación en CheckInService

```java
// Caso de uso: No se puede hacer check-in sin reserva confirmada.
if (!bookingValidationPort.existsConfirmedBooking(command.bookingId())) {
    throw new BookingNotConfirmedException(command.bookingId());
}
```

---

## 25. Diagrama de flujo completo

```
POST /api/v1/checkins
  → CheckInController
    → PerformCheckInCommand
      → CheckInService.performCheckIn()
        │
        ├── 1. ¿Ya tiene check-in esta reserva?
        │      → checkInRepositoryPort.findByBookingId()
        │      → Si sí: CheckInAlreadyExistsException → 409
        │
        ├── 2. ¿Reserva confirmada?
        │      → bookingValidationPort.existsConfirmedBooking()
        │      → Si no: BookingNotConfirmedException → 400
        │
        ├── 3. Crear check-in
        │      → CheckIn.create()  ← DOMINIO
        │
        ├── 4. Generar tarjeta de embarque
        │      → generateBoardingPass()  ← helper privado
        │
        ├── 5. Marcar como completado
        │      → checkIn.complete()  ← DOMINIO
        │
        ├── 6. Guardar en BD
        │      → checkInRepositoryPort.save()  ← PUERTO-OUT
        │
        └── 7. Publicar evento
               → checkInEventPublisherPort.checkInCompleted()
               → OutboxEvent → OutboxRelay → Kafka
```

---

## 26. Testing — Slice Tests

### Qué son los Slice Tests

En vez de arrancar toda la aplicación (`@SpringBootTest`), arrancas **solo una capa**:

| Slice | Qué arranca | Para qué |
|---|---|---|
| `@WebMvcTest` | Solo Controller + MockMvc | Testear HTTP |
| `@DataJpaTest` | Solo JPA + BD (Testcontainers) | Testear repositories |
| `@JsonTest` | Solo Jackson (serialización) | Testear DTOs |

### Tests por capa

| Capa | Qué testear | Cuántos |
|---|---|---|
| **Domain** | `CheckIn.create()`, `complete()`, `BoardingPass` validaciones | 5 |
| **Application** | `CheckInService` happy path, duplicate, not confirmed | 3 |
| **Controller** | HTTP status 201, 409, 400 | 4 |
| **Infrastructure** | Listener idempotencia, outbox publish | 4 |

### Testcontainers

Para `@DataJpaTest` con PostgreSQL real:

```java
@Testcontainers
@DataJpaTest
class CheckInJpaSliceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("bootcamp_checkin_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

### Dependencia en pom.xml

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 27. Cada servicio es distinto

### Qué se reutiliza (patrón común)

| Patrón | Cuándo se usa |
|---|---|
| Paquetes (adapter/persistence/outbox/kafka/web/config) | Siempre |
| Transactional outbox (OutboxRelay + OutboxEvent) | Si publicas eventos a Kafka |
| Idempotencia (processed_events) | Si consume eventos de otros servicios |
| @Configuration manual (sin @Autowired) | Siempre |
| Factory methods en dominio (create/restore) | Siempre que haya entidad con estado |

### Qué cambia según el servicio

| Aspecto | Qué varía |
|---|---|
| **Dominio** | Modelos distintos, invariantes distintos |
| **Casos de uso** | Más o menos puertos, más o menos validaciones |
| **Eventos** | Tipos distintos, topics distintos |
| **Controller** | Endpoints distintos, HTTP status distintos |
| **Adaptadores** | JPA entities distintas, queries distintas |

### Ejemplo: comparación booking vs checkin

| Aspecto | booking-service | checkin-service |
|---|---|---|
| Puerto-in | `BookingUsecase` (create, getById) | `PerformCheckInUseCase` (performCheckIn) |
| Puerto-out | `BookingRepositoryPort`, `BookingEventPublisherPort` | `CheckInRepositoryPort`, `CheckInEventPublisherPort`, `BookingValidationPort` |
| Eventos publicados | `BookingCreatedEvent`, `BookingCancelledEvent` | `CheckInCompletedEvent` |
| Eventos consumidos | Ninguno | `BookingConfirmedEvent` |
| Outbox | Sí | Sí |
| Idempotencia | No (no consume) | Sí (consume booking.confirmed) |

---

## 28. Checklist infraestructura

- [ ] `V1__create_checkins_table.sql`
- [ ] `V2__create_outbox_table.sql`
- [ ] `V3__create_processed_events_table.sql`
- [ ] `V4__amplify_processed_events_for_booking_validation.sql`
- [ ] `CheckInEntity.java`
- [ ] `CheckInJpaRepository.java`
- [ ] `CheckInPersistenceAdapter.java`
- [ ] `OutboxEvent.java`
- [ ] `OutboxJpaRepository.java`
- [ ] `CheckInEventOutboxAdapter.java`
- [ ] `OutboxRelay.java`
- [ ] `ProcessedEventEntity.java`
- [ ] `ProcessedEventJpaRepository.java`
- [ ] `BookingConfirmedKafkaListener.java`
- [ ] `BookingValidationPort.java`
- [ ] `BookingValidationAdapter.java`
- [ ] `BookingConfirmedEvent.java`
- [ ] `BookingNotConfirmedException.java`
- [ ] `PerformCheckInRequest.java`
- [ ] `CheckInResponse.java`
- [ ] `CheckInController.java`
- [ ] `AppCheckInConfig.java`
- [ ] `application.yaml`
- [ ] `pom.xml` (Testcontainers)

---

## 29. Cómo probar el microservicio

### Opción 1 — Unit tests (rápidos, sin levantar app)

```bash
cd checkin-service
./mvnw test
```

Tests que cubren:
- **Domain**: `CheckIn.create()`, `complete()`, `BoardingPass` validaciones
- **Application**: `CheckInService` con mocks de puertos
- **Controller**: MockMvc con respuestas mock

**Velocidad**: ~2 segundos. **Cuándo**: siempre antes de commit.

---

### Opción 2 — Integration test con Slice Test

`@WebMvcTest` levanta solo el controller + MockMvc. No necesitas BD real, ni Kafka.

```java
@WebMvcTest(CheckInController.class)
class CheckInControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PerformCheckInUseCase useCase;

    @Test
    void POST_returns_201() throws Exception {
        mockMvc.perform(post("/api/v1/checkins")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bookingId": 1001, "flightId": 2001, "passengerId": 3001}
                """))
                .andExpect(status().isCreated());
    }
}
```

**Velocidad**: ~3 segundos. **Cuándo**: desarrollo rápido de endpoints.

---

### Opción 3 — App completa con H2 (desarrollo)

```bash
cd checkin-service
./mvnw spring-boot:run
```

Luego con curl o Postman:

```bash
curl -X POST http://localhost:8084/api/v1/checkins \
  -H "Content-Type: application/json" \
  -d '{"bookingId": 1001, "flightId": 2001, "passengerId": 3001}'
```

**Nota**: sin Kafka arrancado, el outbox relay no publica (pero el check-in se guarda en H2).

**Velocidad**: ~5 segundos. **Cuándo**: probar flujo manual rápido.

---

### Opción 4 — App completa con docker compose (producción local)

```bash
cd ..
docker compose up --build
```

Levanta: PostgreSQL + Kafka + todos los servicios. El flujo completo funciona:
- POST check-in → guarda en BD
- Publica evento → Kafka
- notification-service consume el evento

**Velocidad**: ~30 segundos. **Cuándo**: validar integración real entre servicios.

---

### Resumen

| Opción | Qué testea | Velocidad | Cuándo usar |
|---|---|---|---|
| `./mvnw test` | Todos los unit tests | ⚡ 2s | Siempre antes de commit |
| `@WebMvcTest` | Controller + HTTP | ⚡ 3s | Desarrollo rápido |
| `spring-boot:run` + curl | App completa con H2 | 🐢 5s | Probar flujo manual |
| `docker compose` | Todo junto + Kafka + BD | 🐢 30s | Validar integración real |

---

## 30. Errores comunes y debugging

### 30.1 El viaje del `passenger_id`: por qué no funciona sin el gateway

El campo `passenger_id` en la tabla `bookings` **NO viene en el body del request**. Viene del JWT, extraído por el api-gateway:

```
Cliente envía:  POST /api/v1/bookings
                Authorization: Bearer eyJ...

api-gateway:
  1. Decodifica el JWT → extrae "sub": "passenger"
  2. Inyecta header:    X-User-Id: passenger
  3. Reenvía a:         booking-service:8082

booking-service:
  @RequestHeader("X-User-Id") String userId
  → userId = "passenger" → se guarda como passengerId
```

**Si llamas directo a `:8082` sin el header**, el controller recibe `userId = null` → la BD lanza `NOT NULL constraint violation`.

```bash
# ❌ FALLA — falta el header
curl -X POST http://localhost:8082/api/v1/bookings -d '{...}'

# ✅ FUNCIONA — con el header manual
curl -X POST http://localhost:8082/api/v1/bookings \
  -H "X-User-Id: passenger" -d '{...}'
```

### 30.2 JSON escape en PowerShell

PowerShell interpreta `\"` dentro de comillas dobles como literal `\"`, no como comilla JSON. Jackson recibe `\"` y falla con:

```
JSON parse error: Unexpected character ('\' (code 92))
```

**Solución**: usar archivo temporal o comillas simples:

```powershell
# ❌ FALLA — PowerShell envía barra invertida literal
curl -d "{\"flightId\": 1}"

# ✅ FUNCIONA — archivo temporal
'{"flightId": 1}' | Out-File -Encoding utf8 body.json
curl -d "@body.json"

# ✅ FUNCIONA — comillas simples (PowerShell no las interpreta)
curl -d '{"flightId": 1}'
```

### 30.3 Flyway: checksum mismatch después de modificar una migración

Si modificas un archivo de migración (`V3__xxx.sql`) después de que ya fue aplicado a la BD, Flyway lanza:

```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 3
```

**Solución rápida** (borra y recrea la BD del servicio):

```sql
DROP DATABASE bootcamp_booking;
CREATE DATABASE bootcamp_booking;
```

Luego reinicia el servicio para que Flyway reaplique las migraciones.

**Solución productiva** (reparar el checksum):

```sql
UPDATE flyway_schema_history SET checksum = 0 WHERE version = '3';
```

### 30.4 Jackson: `java.time.Instant` no soportado (payment-service)

Si un servicio consume eventos de Kafka y falla con:

```
Java 8 date/time type `java.time.Instant` not supported by default:
add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310"
```

**Causa**: Jackson 3 (Spring Boot 4.1) no incluye JSR310 por defecto.

**Solución**: agregar al `pom.xml` del servicio:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

O registrando el módulo en un `@Configuration`:

```java
@Bean
public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
}
```

### 30.5 Resumen de errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `NOT NULL constraint: passenger_id` | Header `X-User-Id` no llega | Llamar vía api-gateway o agregar header manual |
| `Unexpected character ('\' (code 92))` | PowerShell escape de comillas | Usar archivo JSON o comillas simples |
| `Flyway checksum mismatch` | Migración modificada después de aplicada | Recrear BD o actualizar checksum |
| `Instant not supported` | Falta módulo Jackson JSR310 | Agregar `jackson-datatype-jsr310` |
| `Booking not found with id: X` | Reserva no existe en BD | Crear reserva primero |
| `Booking not confirmed` | Status ≠ CONFIRMED | Procesar pago o confirmar manualmente |

---

*Documento vivo — actualizar conforme implementes y descubras edge cases.*