# ✈️ Sistema de Reservas de Aerolínea — Microservicios con Java y Spring Boot

> Proyecto final del Bootcamp Senior a Arquitecto — Programando en Java.
> Backend de reservas de vuelos basado en **microservicios independientes**, aplicando **DDD**, **Arquitectura Hexagonal**, **Kafka** y **Saga Pattern**.

---

## 🎯 Objetivo

Construir un sistema de reservas de vuelos **distribuido, desacoplado y escalable**, donde cada microservicio gestiona un contexto funcional concreto con su propia base de datos.

El flujo principal del sistema es:

```
Buscar vuelo → Reservar → Pagar → Check-in → Notificar
```

---

### ✅ Incluido

- 🔍 Búsqueda de vuelos
- 🧾 Reserva de vuelos
- 💳 Procesamiento de pagos
- 🛫 Check-in online
- 📨 Notificaciones basadas en eventos
- 🔐 Autenticación JWT con control de roles
- 📡 Comunicación asíncrona con Apache Kafka
- 🔄 Transacciones distribuidas con Saga Pattern

### 🚫 Fuera de alcance

- Programas de fidelización
- Gestión de equipaje
- Analítica avanzada

---

## 🏗️ Arquitectura

El sistema está compuesto por **un API Gateway y seis microservicios independientes**. Cada servicio tiene su **propia base de datos** (no hay base de datos compartida) y se comunica de forma asíncrona mediante **Apache Kafka**.

### Diagrama de arquitectura

```
                           ┌─────────────────────────┐
                           │      API Gateway        │
                           │         :8080           │
                           │  Enrutamiento + JWT     │
                           └────────────┬────────────┘
                                        │
            ┌───────────────────────────┼───────────────────────────┐
            │                           │                           │
            ▼                           ▼                           ▼
  ┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
  │  Auth Service   │         │ Flight Service  │         │ Booking Service │
  │     :8087       │         │     :8086       │         │     :8082       │
  │     [DB]        │         │     [DB]        │         │     [DB]        │
  └─────────────────┘         └─────────────────┘         └────────┬────────┘
                                                                    │
                         ┌──────────────────────────────────────────┤
                         │ Outbox → Kafka                           │
                         ▼                                          ▼
                   ┌──────────┐                              ┌──────────┐
                   │  Kafka   │◄─────────────────────────────│  Kafka   │
                   │          │                              │          │
                   └────┬─────┘                              └────┬─────┘
                        │                                         │
         ┌──────────────┼──────────────────┐                      │
         │              │                  │                      │
         ▼              ▼                  ▼                      ▼
  ┌───────────┐  ┌──────────────┐  ┌───────────────────────────────┐
  │ Payment   │  │ Check-in     │  │ Notification                  │
  │ Service   │  │ Service      │  │ Service                       │
  │  :8083    │  │   :8084      │  │   :8085                       │
  │  [DB]     │  │   [DB]       │  │  (sin BD — solo consume)      │
  └───────────┘  └──────────────┘  └───────────────────────────────┘
```

### Principios arquitectónicos

- Separación de responsabilidades por contexto funcional
- Bajo acoplamiento entre microservicios
- Inversión de dependencias (Puertos y Adaptadores)
- Dominio independiente de la infraestructura
- Comunicación desacoplada mediante eventos
- Cada microservicio tiene su propia base de datos

---

## 🧩 Microservicios

| Servicio | Puerto | Base de Datos | Responsabilidad |
| --- | --- | --- | --- |
| API Gateway | 8080 | — | Enrutamiento, seguridad y JWT |
| Auth Service | 8087 | bootcamp_auth | Autenticación y generación de JWT |
| Flight Service | 8086 | bootcamp_flight | Gestión de vuelos e inventario de asientos |
| Booking Service | 8082 | bootcamp_booking | Gestión de reservas |
| Payment Service | 8083 | bootcamp_payment | Procesamiento de pagos (Strategy Pattern) |
| Check-in Service | 8084 | bootcamp_checkin | Gestión del check-in y tarjeta de embarque |
| Notification Service | 8085 | — | Consumo de eventos y envío de notificaciones |

> Todos los servicios se ejecutan independientemente y se escalan de forma individual.

---

## 🧱 Arquitectura Hexagonal (DDD)

Los microservicios funcionales implementan **Arquitectura Hexagonal (Puertos y Adaptadores)**, separando el dominio de la infraestructura.

### Estructura de cada servicio

```
service/
├── domain/              ← Núcleo del negocio (entidades, value objects, invariantes)
├── application/         ← Casos de uso y contratos
│   ├── usecases/
│   ├── ports/
│   │   ├── in/          ← Puertos de entrada (casos de uso)
│   │   └── out/         ← Puertos de salida (repositorios, eventos)
│   ├── commands/
│   ├── events/
│   └── exceptions/
└── infrastructure/      ← Adaptadores tecnológicos
    ├── persistence/
    ├── adapters/
    ├── controllers/
    └── config/
```

### Evidencia de implementación real

**Auth Service** — Textbook hexagonal:
- `domain/User.java` — record puro, sin anotaciones Spring
- `application/port/in/AuthUseCase.java` — interfaz de entrada
- `application/port/out/UserRepositoryPort.java` — interfaz de salida
- `infrastructure/config/AppAuthConfig.java` — wiring manual, sin `@Service`

**Booking Service** — Dominio rico con comportamiento:
- `domain/Booking.java` — aggregate root con `confirm()`, `cancel()`, `fail()`
- `domain/BookingStatus.java` — enum con invariantes
- `application/port/out/BookingEventPublisherPort.java` — publicación de eventos

**Payment Service** — Strategy Pattern con Value Objects:
- `domain/vo/Money.java` — value object inmutable
- `domain/vo/PaymentMethod.java` — enum de estrategias
- `infrastructure/adapter/StripePaymentAdapter.java` — adaptador concreto
- `infrastructure/adapter/PaypalPaymentAdapter.java` — adaptador concreto
- `infrastructure/adapter/MockPaymentAdapter.java` — adaptador para tests

---

## 📨 Comunicación basada en eventos (Kafka)

La comunicación asíncrona entre microservicios utiliza **Apache Kafka** con un patrón de **Choreography** (sin orquestador central).

### Topics

| Topic | Productor | Consumidores |
| --- | --- | --- |
| `booking.created` | Booking Service | Payment, Flight, Notification |
| `booking.confirmed` | Booking Service | Check-in Service |
| `booking.cancelled` | Booking Service | Flight Service |
| `payment.processed` | Payment Service | Booking, Notification |
| `checkin.completed` | Check-in Service | Notification Service |

### Transactional Outbox Pattern

Para garantizar la fiabilidad en la publicación de eventos, se implementa el patrón **Transactional Outbox**:

1. El microservicio guarda el evento en una tabla `outbox_events` dentro de la **misma transacción** que el negocio
2. Un `OutboxRelay` (`@Scheduled(fixedDelay = 3000)`) pobla  periódicamente la tabla y publica eventos pendientes en Kafka
3. Si Kafka no está disponible, los eventos permanecen en la tabla hasta que se puedan reintentar

```
┌─────────────────────────────────────────┐
│           Transacción de negocio        │
│  ┌──────────────┐  ┌────────────────┐   │
│  │ booking table │  │ outbox_events  │   │
│  │   (INSERT)    │  │   (INSERT)     │   │
│  └──────────────┘  └────────────────┘   │
└─────────────────────────────────────────┘
                    │
                    ▼
            ┌───────────────┐
            │  OutboxRelay  │  (cada 3 segundos)
            │  polls + sends│
            └───────┬───────┘
                    │
                    ▼
              ┌──────────┐
              │  Kafka   │
              └──────────┘
```

### Idempotencia

Todos los consumers de Kafka implementan una tabla `processed_events` con `eventId` como PK para deduplicar mensajes. Si un evento se procesa dos veces (por retry o race condition), se detecta por `DataIntegrityViolationException` y se ignora.

---

## 🔁 Saga Pattern (Choreography)

El proceso de reserva utiliza **Saga Pattern** sin orquestador central. Cada servicio reacciona a los eventos y ejecuta su lógica, con acciones compensatorias en caso de error.

### Flujo correcto

```
Booking Service          Kafka              Payment Service        Flight Service
     │                     │                       │                     │
     │── booking.created ──┼──────────────────────►│                     │
     │                     │                       │── procesar pago     │
     │                     │◄── payment.processed ─┤                     │
     │                     │                       │                     │
     │── confirmar reserva │                       │                     │
     │── booking.confirmed─┼──────────────────────────────────────────►│
     │                     │                       │                     │
     │                     │                       │         │── reservar asientos
```

### Flujo con error (acción compensatoria)

```
Booking Service          Kafka              Payment Service        Flight Service
     │                     │                       │                     │
     │── booking.created ──┼──────────────────────►│                     │
     │                     │                       │── procesar pago     │
     │                     │                       │    ✗ ERROR          │
     │                     │                       │                     │
     │── cancelar reserva  │                       │                     │
     │── booking.cancelled─┼──────────────────────────────────────────►│
     │                     │                       │         │── liberar asientos
```

> El objetivo es evitar estados inconsistentes entre servicios sin un orquestador central.

---

## 🎯 Strategy Pattern (Payment Service)

El Payment Service utiliza **Strategy Pattern** para desacoplar el proceso de pago de la implementación concreta:

```
          PaymentService
                │
                ▼
         ProcessPaymentUseCase
                │
                ▼
          PaymentGateway (puerto)
           /          \
          ▼            ▼
   StripeAdapter  PaypalAdapter
          │            │
          ▼            ▼
    Stripe API    Paypal API
```

Esto permite incorporar nuevas estrategias de pago sin modificar el flujo principal del caso de uso.

---

## 🔐 Seguridad

La comunicación con las APIs está protegida mediante **JWT (JSON Web Tokens)** con autenticación stateless.

### Configuración JWT

| Parámetro | Valor |
| --- | --- |
| Biblioteca | Nimbus JOSE+JWT |
| Algoritmo | HS256 (HmacSHA256) |
| Expiración | 24 horas |
| Emisor | `https://api.airline-system.dev` |

### Roles

| Rol | Permisos |
| --- | --- |
| `PASSENGER` | Reservar y realizar check-in de sus vuelos |
| `AGENT` | Gestionar reservas |
| `ADMIN` | Acceso completo |
| `USER` | Rol por defecto en registro |

### Flujo de autenticación

```
Cliente ── POST /api/v1/auth/login ──► Auth Service
                                           │
                                           ▼
                                    JWT Token (24h)
                                           │
Cliente ◄── { token } ────────────────────┘
    │
    │  Authorization: Bearer <token>
    ▼
API Gateway ──► Valida JWT ──► Extrae roles ──► Route to service
```

> El auth-service genera el token JWT con Nimbus. El API Gateway lo valida y extrae el `sub` claim para inyectarlo como header `X-User-Id` en el Booking Service.

---

## 🛠️ Stack Tecnológico

| Categoría | Tecnología                                  |
| --- |---------------------------------------------|
| Lenguaje | Java 21                                     |
| Framework | Spring Boot 4.1.0                           |
| API Gateway | Spring Cloud Gateway                        |
| Seguridad | Spring Security + JWT (Nimbus)              |
| Persistencia | Spring Data JPA + Flyway (versionado de BD) |
| Mensajería | Apache Kafka                                |
| Base de datos (dev) | H2 (embebida)                               |
| Base de datos (prod) | PostgreSQL 17                               |
| Contenedores | Docker + Docker Compose                     |
| Monitoreo | Prometheus + Grafana                        |
| Trazabilidad | Jaeger (OpenTelemetry)                      |
| Logs | Loki + Promtail                             |
| CI/CD | GitHub Actions (pendiente)                  |

---

## 🗄️ Persistencia

El proyecto dispone de **dos perfiles de ejecución**:

### Perfil `dev` (desarrollo)

- H2 embebida por cada microservicio
- Arranque rápido, sin dependencias externas
- Ideal para desarrollo local y tests

### Perfil `prod` (producción)

- PostgreSQL 17 ejecutado mediante Docker
- Base de datos independiente por servicio:

```
bootcamp_auth        → auth-service
bootcamp_booking     → booking-service
bootcamp_flight      → flight-service
bootcamp_payment     → payment-service
bootcamp_checkin     → check-in-service
```

> **Cada microservicio tiene su propia base de datos.** No hay conexión directa entre servicios ni bases de datos compartidas.

---

## 🔄 Flujos principales

### 1. 🔍 Búsqueda de vuelos

```
Cliente → API Gateway → Flight Service → Disponibilidad de vuelos
```

El cliente consulta vuelos disponibles indicando criterios de búsqueda (origen, destino, fecha).

### 2. 🧾 Reserva

```
Cliente → API Gateway → Booking Service → Creación de reserva → Evento booking.created
```

El Booking Service crea la reserva en estado `PENDING` y publica el evento `booking.created`.

### 3. 💳 Pago

```
Booking Service → Kafka → Payment Service → Strategy (Stripe/PayPal/Mock) → Evento payment.processed
```

El pago está desacoplado mediante eventos. El Payment Service utiliza Strategy Pattern para procesar el pago con la estrategia configurada.

### 4. 🛫 Check-in

```
Booking confirmed → Kafka → Check-in Service → Tarjeta de embarque → Evento checkin.completed
```

Una vez confirmada la reserva y el pago, el pasajero puede realizar el check-in.

---

## 🚀 Ejecución

### Requisitos

- Java 21
- Maven
- Docker
- Docker Compose

### 1. Clonar el repositorio

```bash
git clone <URL_DEL_REPOSITORIO>
cd airline-system
```

### 2. Ejecutar con Docker (recomendado)

```bash
docker-compose up --build
```

Esto levanta todos los servicios, Kafka, PostgreSQL, Kafdrop, Prometheus, Grafana y Jaeger.

### 3. Ejecutar un servicio localmente

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. Ejecución mínima

Para consumir las APIs a través del Gateway es necesario tener disponibles:

```
API Gateway (:8080)
    └── Auth Service (:8087)
            └── Servicio funcional requerido
```

El Gateway está disponible en: `http://localhost:8080`

### Servicios de infraestructura

| Servicio | Puerto | URL |
| --- | --- | --- |
| Kafdrop (UI Kafka) | 19000 | http://localhost:19000 |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3000 | http://localhost:3000 |
| Jaeger (trazas) | 16686 | http://localhost:16686 |
| Loki (logs) | 3100 | http://localhost:3100 |

---

## 🧪 Testing

El proyecto contempla diferentes niveles de testing:

- Tests unitarios (TDD)
- Tests de integración (MockMvc)
- Slice Tests
- Cobertura mínima: **70%**

Ejecutar tests:

```bash
mvn test
```

> 🚧 La implementación completa de testing y cobertura se encuentra en evolución.

---

## 📊 Observabilidad — 🚧 Pendiente

Se contempla incorporar:

- Métricas con **Prometheus** (ya configurado en docker-compose)
- Dashboards con **Grafana**
- Trazabilidad distribuida con **Jaeger** (OpenTelemetry)
- Logs centralizados con **Loki + Promtail**

> Actualmente Prometheus y Grafana están definidos en docker-compose pero la instrumentación de las aplicaciones está en progreso.

---

## ☁️ CI/CD y Cloud — 🚧 Pendiente

El proyecto contempla la configuración de:

- **GitHub Actions** para pipeline de integración continua
- Construcción automática y ejecución de tests
- Análisis de calidad
- Despliegue en cloud

---

## 🗺️ Roadmap

### ✅ Implementado

- Arquitectura de microservicios independientes
- API Gateway con enrutamiento y seguridad
- Arquitectura Hexagonal (Puertos y Adaptadores)
- Autenticación JWT (Nimbus)
- Control de roles (PASSENGER, AGENT, ADMIN, USER)
- Gestión de vuelos e inventario de asientos
- Gestión de reservas con dominio rico
- Procesamiento de pagos con Strategy Pattern
- Check-in y tarjeta de embarque
- Comunicación asíncrona con Kafka
- Transactional Outbox Pattern
- Idempotencia en consumers
- Saga Pattern (Choreography)
- H2 para desarrollo local
- PostgreSQL para producción
- Docker + Docker Compose
- Flyway para migraciones de BD
- OpenTelemetry → Jaeger
- Prometheus + Grafana (infraestructura definida)

### 🚧 En progreso

- Ampliación de tests (Slice Tests) y cobertura
- Instrumentación de métricas en aplicaciones (Prometheus, Grafana )
- Dashboards de Grafana
- Trazabilidad distribuida completa (Loki)

### 📋 Próximas mejoras

- GitHub Actions (CI/CD)
- Despliegue cloud automatizado
- Observabilidad completa
- Añadir base de datos  NO SQL y añadir Cache a consultas BD 
- Auto-descubrimiento de puertos entre servicios
- Revisar asignación de roles (actualmente solo `USER` se asigna en registro)
- Modificar usar @Scheduled (Porque no permite muchos hilos) para OutBoundcontext
- Aplicar patrones de Resilience4j (Retry, CircuitBreaker)
- Aplicar algun flujo Sincrono (OpenFeign)
- Añadir https a la app y aplicar Seguridad mTLS

---

## 🌿 Git Workflow

El desarrollo se organiza utilizando un flujo basado en Git:

```
main
│
└── feature/nueva-funcionalidad
        │
        ▼
    Pull Request
        │
        ▼
    Code Review
        │
        ▼
    Integración
```
- Tenemos el repositorio de GitHub configurado porque solo se puedan hacer commits a develop o main via Pull request.

---

## 📚 API Documentation

Las APIs REST están diseñadas siguiendo un enfoque **API First** y se documentan mediante **OpenAPI/Swagger**.

| Servicio | Swagger UI |
| --- | --- |
| Auth Service | `http://localhost:8087/swagger-ui.html` |
| Flight Service | `http://localhost:8086/swagger-ui.html` |
| Booking Service | `http://localhost:8082/swagger-ui.html` |
| Payment Service | `http://localhost:8083/swagger-ui.html` |
| Check-in Service | `http://localhost:8084/swagger-ui.html` |

> Las URLs exactas de Swagger UI están pendientes de confirmar con la configuración actual de cada servicio.

-

## 📁 Estructura del proyecto

```
airline-system/
├── api-gateway/
├── auth-service/
├── flight-service/
├── booking-service/
├── payment-service/
├── checkin-service/
├── notification-service/
├── db-init/
├── docker-compose.yml
└── README.md
```

Cada microservicio contiene su propio `pom.xml`, `Dockerfile` y configuración independiente.


## ✅ Definition of Done

Una funcionalidad se considera terminada cuando:

- El código compila correctamente
- Se cumplen los criterios de aceptación
- Los tests correspondientes pasan correctamente
- No se introducen estados inconsistentes
- Se respetan las responsabilidades de cada capa
- Se mantiene el desacoplamiento entre componentes
- Se realiza la correspondiente revisión del código
- La funcionalidad queda documentada cuando es necesario

---
