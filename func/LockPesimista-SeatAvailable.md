# Lock Pesimista — Disponibilidad de Asientos

> Requisito: "Como sistema, quiero evitar que se vendan más asientos de los disponibles. No se pueden reservar más asientos de los disponibles. El sistema gestiona correctamente múltiples reservas simultáneas."

## 1. Dónde vive el problema

La disponibilidad es `seat_inventory.available_seats` — **en flight-service**, no en booking-service. En la saga, flight-service consume `BookingCreatedEvent` y ejecuta `Flight.reserveSeats()`. Dos reservas simultáneas → dos transacciones escribiendo la **misma fila**.

El hotspot es **UNA fila por vuelo**: toda la contención se concentra ahí. Esto define el mecanismo de concurrencia.

**Regla de oro**: el que **posee el dato** es el que **aplica la regla**. booking-service solo crea la reserva `PENDING` con `flightId` y `seats`; flight-service decrementa `available_seats` y ahí se evita la sobreventa.

## 2. La invariante tiene DOS capas

| Capa | Qué hace | Dónde |
|---|---|---|
| **Regla de dominio** | `SeatInventory.reserve()`: si `availableSeats < seats` → `IllegalStateException` | `domain/SeatInventory.java` — ya existe |
| **Control de concurrencia** | Evita que dos transacciones lean el mismo valor "a la vez" y ambas pasen la regla | **falta** — es lo que resuelve este documento |

El locking **no sustituye la regla**: la hace *digna de confianza* bajo concurrencia.

## 3. Optimista vs Pesimista — análisis

### Optimista (`@Version`)

```
A: SELECT available=1, version=0
B: SELECT available=1, version=0
A: UPDATE ... SET available=0, version=1 WHERE id=X AND version=0 → 1 fila ✅ commit
B: UPDATE ... SET available=0, version=1 WHERE id=X AND version=0 → 0 filas ❌ ObjectOptimisticLockingFailureException
```

- ✅ Barato con contención **baja** (colisiones raras), sin bloqueos ni esperas.
- ✅ Sin riesgo de deadlock.
- ❌ **El perdedor debe REINTENTAR**: re-leer, re-validar, re-aplicar. En un consumidor Kafka de la saga = re-procesar el evento.
- ❌ En un hotspot, la probabilidad de colisión es alta → muchos reintentos → thrash.

### Pesimista (`@Lock(PESSIMISTIC_WRITE)`)

```
A: SELECT ... FOR UPDATE → BLOQUEA la fila
B: SELECT ... FOR UPDATE → espera (bloqueado)
A: reserve() + commit → libera
B: despierta, lee available=0 ACTUALIZADO → reserve() → IllegalStateException → fallo LIMPIO
```

- ✅ **El perdedor no reintenta**: espera, ve el valor fresco y la regla de dominio lo rechaza con un error de negocio claro.
- ✅ Correcto bajo contención ALTA; la transacción es corta (una fila, un UPDATE).
- ❌ Riesgo de deadlock si el orden de locks es inconsistente — aquí bajo: cada evento toca UNA fila.
- ❌ Cuello de botella si las transacciones son largas — aquí NO: una fila, un UPDATE.

### Conclusión

**Pesimista WRITE es la opción correcta**: hotspot garantizado, fallo limpio del perdedor sin reintentos (clave en flujo por eventos), caso canónico de "reserva de asientos / stock crítico".

> El optimista sería mejor con colisiones RARAS (p. ej. editar un perfil) donde el reintento es ocasional. Para un asiento de vuelo, la colisión no es la excepción: es el caso de uso.

## 4. Decisiones de diseño

### 4.1 `updateAvailableSeats` en la entidad NO es un método de negocio

Hay DOS objetos "disponibilidad":

| | `SeatInventory` (domain) | `SeatInventoryEntity` (infra) |
|---|---|---|
| Rol | Objeto de negocio | Objeto de persistencia |
| Métodos de negocio | `reserve()`, `release()` — SÍ | **NO debe tener** |
| Quién muta | `reserve()`/`release()` — con reglas | nadie — objeto "tonto" |

**Problema**: Hibernate solo persigue cambios en las ENTIDADES (managed objects). `reserve()` muta el `SeatInventory` de dominio (una copia) — la entidad original queda intacta. Sin un puente de vuelta, al cerrar la transacción Hibernate no ve cambios y **no emite el UPDATE** → la reserva se pierde en BD.

**Solución**: setter package-private en la entidad que **solo sincroniza** el valor ya validado por el dominio:

```java
// package-private: solo el adapter (mismo paquete) lo usa
void updateAvailableSeats(int availableSeats) {
    this.availableSeats = availableSeats;
}
```

| Método | Valida? | Es negocio? | Ubicación correcta |
|---|---|---|---|
| `SeatInventory.reserve(seats)` | Sí | ✅ Sí | **dominio** |
| `SeatInventoryEntity.updateAvailableSeats(n)` | No | ❌ No — sync de persistencia | **infra**, package-private |
| `updateAvailableSeats` que VALIDARA | Sí | ⚠️ negocio mal ubicado | **error**: regla duplicada en infra |

Regla de oro: *los métodos que DECIDEN van al dominio; los que solo SINCRONIZAN van a la entidad, y no pueden ser públicos.*

### 4.2 `@Transactional` en el servicio — excepción deliberada a la regla 3.5

El `SELECT FOR UPDATE` solo mantiene el lock **mientras la transacción esté abierta**. El caso de uso debe ser UNA transacción que abarque find→reserve→save:

| Opción | Dónde | Resultado |
|---|---|---|
| **A** | `@Transactional` en `FlightService` | ✅ La unidad de trabajo ES el caso de uso; el lock abarca los 3 pasos |
| **B** | `@Transactional` en el adapter | ❌ find y save son 2 llamadas → 2 transacciones → lock liberado entre ambas → colisión |

**Excepción documentada a la regla 3.5** ("sin anotaciones Spring en application"): `@Transactional` no registra beans (eso es `@Service`/`@Repository`), solo delimita la unidad de trabajo — y **no puede** vivir en el adapter porque ahí no abarcaría los 3 pasos. No es opinión: es la mecánica de JPA.

### 4.3 `saveSeatInventory` recibe `flightId`

El `SeatInventory` de dominio **no expone id** — el adapter lo necesita para recargar la misma entidad managed dentro de la transacción y aplicar `updateAvailableSeats`. Sin `save()` explícito: el *dirty checking* de Hibernate emite el UPDATE al commit.

## 5. Plan de implementación (orden hexagonal)

Las dependencias apuntan hacia dentro: **dominio → aplicación (puertos → servicio) → infraestructura**. El puerto define el QUÉ (contrato); el adapter llega al final solo para CUMPLIRLO.

| Paso | Capa | Qué |
|---|---|---|
| 0 | Dominio | ✅ ya está — `SeatInventory.reserve()` contiene la regla. Nada que cambiar |
| 1 | Application — puerto out | `FlightRepositoryPort`: `findSeatInventoryForUpdate` + `saveSeatInventory` |
| 2 | Application — puerto in | `FlightUsecase`: `reserveSeatsUseCase` |
| 3 | Application — excepción | `SeatInventoryNotFoundException` (patrón de booking) |
| 4 | Application — servicio | `FlightService`: orquestar find→reserve→save + `@Transactional` |
| 5 | Infra — repo | `SeatInventoryJpaRepository`: `findByFlightIdWithLock` (`@Lock PESSIMISTIC_WRITE`) |
| 6 | Infra — entidad | `SeatInventoryEntity`: `updateAvailableSeats` package-private |
| 7 | Infra — adapter | `FlightPersistenceAdapter`: implementar el contrato (recarga entidad en la misma tx) |
| 8 | Infra — config | `application.yaml`: `jakarta.persistence.lock.timeout: 5000` |
| 9 | Tests | unit (fake) + concurrencia (slice, 2 hilos) |

### 5.1 Código por paso

**Paso 1 — `FlightRepositoryPort`**
```java
SeatInventory findSeatInventoryForUpdate(Long flightId);           // carga con lock
void saveSeatInventory(Long flightId, SeatInventory seatInventory); // persiste el cambio
```

**Paso 2 — `FlightUsecase`**
```java
void reserveSeatsUseCase(Long flightId, int seats);
```

**Paso 3 — `SeatInventoryNotFoundException`**
```java
public class SeatInventoryNotFoundException extends RuntimeException {
    public SeatInventoryNotFoundException(Long flightId) {
        super("Seat inventory not found with id: " + flightId);
    }
}
```

**Paso 4 — `FlightService`** (la única anotación Spring en aplicación)
```java
@Override
@Transactional
public void reserveSeatsUseCase(Long flightId, int seats) {
    SeatInventory inventory = flightRepositoryPort.findSeatInventoryForUpdate(flightId);
    inventory.reserve(seats);   // regla de dominio; lanza IllegalStateException si no hay plazas
    flightRepositoryPort.saveSeatInventory(flightId, inventory);
}
```

**Paso 5 — `SeatInventoryJpaRepository`**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from SeatInventoryEntity s where s.flight.id = :flightId")
Optional<SeatInventoryEntity> findByFlightIdWithLock(@Param("flightId") Long flightId);
```

**Paso 6 — `SeatInventoryEntity`**
```java
void updateAvailableSeats(int availableSeats) {
    this.availableSeats = availableSeats;
}
```

**Paso 7 — `FlightPersistenceAdapter`** (hereda la tx del caso de uso; aquí NO va `@Transactional`)
```java
@Override
public SeatInventory findSeatInventoryForUpdate(Long flightId) {
    SeatInventoryEntity entity = seatInventoryJpaRepository.findByFlightIdWithLock(flightId)
            .orElseThrow(() -> new SeatInventoryNotFoundException(flightId));
    return toSeatInventory(entity);
}

@Override
public void saveSeatInventory(Long flightId, SeatInventory seatInventory) {
    SeatInventoryEntity entity = seatInventoryJpaRepository.findByFlightId(flightId)
            .orElseThrow(() -> new SeatInventoryNotFoundException(flightId));
    entity.updateAvailableSeats(seatInventory.getAvailableSeats());
}

private SeatInventory toSeatInventory(SeatInventoryEntity entity) {
    return new SeatInventory(entity.getTotalSeats(), entity.getAvailableSeats());
}
```

**Paso 8 — `application.yaml`**
```yaml
spring:
  jpa:
    properties:
      jakarta:
        persistence:
          lock:
            timeout: 5000
```

## 6. La prueba — test de ciclo de vida

### 6.1 El problema que prueba

Vuelo con **150 asientos disponibles**. Dos usuarios reservan a la vez. **Sin lock**:

```
Hilo A: SELECT availableSeats → 150     Hilo B: SELECT availableSeats → 150
Hilo A: 150 >= 5 → reserva OK           Hilo B: 150 >= 5 → reserva OK
Hilo A: UPDATE availableSeats=145       Hilo B: UPDATE availableSeats=145 (sobrescribe)
```

Ambos "ganaron" → la segunda reserva **pisa** la primera. El lock evita esa lectura concurrente del mismo valor.

### 6.2 La decisión: test de ciclo de vida, no de concurrencia

El proyecto de referencia (bootcamp-1-2026-mestreengaisaber) **no tenía test** del lock: el `OrderServiceImplTest.checkout()` estaba comentado y la verificación era manual — la `InsufficientStockException` escalaba como 500 con stack trace y esa era la "traza del error".

Siguiendo el principio de **máxima correlación con el requisito sin sobre-ingeniería**, en lugar de forzar una colisión con hilos + `REQUIRES_NEW` + `CountDownLatch`, se cubre el **ciclo de vida completo** de forma secuencial:

1. **Adquirir el lock** → `findSeatInventoryForUpdate` genera `SELECT ... FOR UPDATE`
2. **Aplicar la regla** → `reserve()` decrementa o lanza `IllegalStateException`
3. **Persistir** → `updateAvailableSeats` + dirty checking emite el `UPDATE`

### 6.3 Código del test

```java
@DataJpaTest
@Import(FlightPersistenceAdapter.class)
class SeatInventoryLifecycleTest {

    @Autowired FlightPersistenceAdapter adapter;
    @Autowired AirportJpaRepository airports;
    @Autowired FlightJpaRepository flights;
    @Autowired SeatInventoryJpaRepository seatInventory;

    private Long flightId;

    @BeforeEach
    void setUp() {
        seatInventory.deleteAllInBatch();
        flights.deleteAllInBatch();
        airports.deleteAllInBatch();
        AirportEntity mad = airports.save(new AirportEntity("MAD", "Adolfo Suarez", "Madrid", "Spain"));
        AirportEntity bcn = airports.save(new AirportEntity("BCN", "El Prat", "Barcelona", "Spain"));
        FlightEntity flight = new FlightEntity("IB1", mad, bcn,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1),
                new BigDecimal("199.90"));
        flight.setSeatInventory(new SeatInventoryEntity(flight, 180, 150));
        flightId = flights.save(flight).getId();
    }

    @Test
    void reserveSeatsAcquiresLockDecrementsAndPersists() {
        SeatInventory inventory = adapter.findSeatInventoryForUpdate(flightId);

        inventory.reserve(5);
        adapter.saveSeatInventory(flightId, inventory);

        SeatInventory reloaded = adapter.findSeatInventoryForUpdate(flightId);
        assertThat(reloaded.getAvailableSeats()).isEqualTo(145);
    }

    @Test
    void reserveMoreThanAvailableFailsCleanlyAndPersistsNothing() {
        SeatInventory inventory = adapter.findSeatInventoryForUpdate(flightId);

        assertThatThrownBy(() -> inventory.reserve(151))
                .isInstanceOf(IllegalStateException.class);

        SeatInventory reloaded = adapter.findSeatInventoryForUpdate(flightId);
        assertThat(reloaded.getAvailableSeats()).isEqualTo(150);
    }

    @Test
    void findSeatInventoryForUpdateThrowsWhenFlightDoesNotExist() {
        assertThatThrownBy(() -> adapter.findSeatInventoryForUpdate(999L))
                .isInstanceOf(SeatInventoryNotFoundException.class);
    }
}
```

### 6.4 Qué demuestra cada test

| Test | Fase del ciclo que cubre | Asserts |
|---|---|---|
| `reserveSeatsAcquiresLockDecrementsAndPersists` | Lock + regla + persistencia | `150 → 145` tras recargar (el `UPDATE` llegó a BD) |
| `reserveMoreThanAvailableFailsCleanlyAndPersistsNothing` | Regla ante stock insuficiente | `IllegalStateException` + disponible sigue en `150` |
| `findSeatInventoryForUpdateThrowsWhenFlightDoesNotExist` | Borde: vuelo inexistente | `SeatInventoryNotFoundException` |

### 6.5 Detalles del test

- **`deleteAllInBatch()` en setUp**: `@DataJpaTest` con Flyway gestiona el esquema (no hay `create-drop` entre tests) → sin limpieza, el segundo test heredaría filas del primero.
- **Sin hilos**: no hace falta `REQUIRES_NEW` — cada test corre en la transacción heredada del `@DataJpaTest`. Los hilos/latches solo tendrían sentido para *forzar* una colisión, que es exactamente lo que se ha decidido no probar (mismo nivel que el proyecto de referencia).
- **Los 2 tests unitarios de `FlightServiceTest`** (con fake, sin Spring) complementan probando la regla de dominio a nivel de caso de uso.

### 6.6 Optimista vs Pesimista

| | Optimista (el reto del material) | Pesimista (nuestro caso) |
|---|---|---|
| Cómo se fuerza la colisión | `Thread.sleep()` artificial entre find y save | No hace falta — `FOR UPDATE` bloquea de verdad |
| Cómo pierde el perdedor | `ObjectOptimisticLockingFailureException` + reintento | `IllegalStateException` de la regla de dominio, fallo limpio |
| Qué garantiza | "Cuidado, alguien lo cambió: reintenta" | "No hay plazas: rechazado" |

## 7. Notas de trazabilidad

- **Sin endpoint REST ni listener**: el disparo de la reserva será el consumidor de `BookingCreatedEvent` en la fase Kafka; el listener llamará al mismo caso de uso.
- **Sin cambios en booking-service**: los asientos no le pertenecen.
- **`@Transactional` en aplicación**: excepción documentada a la regla 3.5 (sección 4.2).
- El adapter recibe `flightId` en `save` porque el `SeatInventory` de dominio no expone id.