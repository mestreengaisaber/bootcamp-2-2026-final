# Sistema de Reservas en una Aerolinea - Proyecto con Microservicios

## Descripción General

Este proyecto es un **sistema de reservas de aerolínea como parte del proyecto final del proyecto final del Bootcamp de Senior a Arquitecto - Programando En Java**, desarrollado con **Java + Spring Boot**.

El objetivo es simular cómo se construyen sistemas backend modernos en empresas:

* Arquitectura de microservicios
* Comunicación basada en eventos
* Procesamiento de pagos desacoplado (Strategy Pattern)
* Transacciones distribuidas (Saga Pattern)
* Seguridad con JWT y control de roles

> ⚠️ Este proyecto es **100% práctico**. Aquí no hay teoría innecesaria: construyes un sistema real de principio a fin.

---

## Alcance del MVP (2 semanas)

El proyecto está diseñado para enfocarse en **4 flujos principales**:

### Incluido

* 🔍 Búsqueda de vuelos
* 🧾 Reserva de vuelos
* 💳 Procesamiento de pagos
* 🛫 Check-in online

### Fuera de alcance

* Programas de fidelización
* Gestión de equipaje
* Analítica avanzada
* Arquitecturas avanzadas (DDD / Hexagonal)

---

## Arquitectura

El sistema está basado en **microservicios independientes**, donde cada servicio tiene su propia responsabilidad.

### Servicios

| Servicio             | Responsabilidad                   |
| -------------------- | --------------------------------- |
| Flight Service       | Gestión de vuelos e inventario    |
| Booking Service      | Gestión de reservas               |
| Payment Service      | Procesamiento de pagos            |
| Check-in Service     | Check-in y tarjeta de embarque    |
| Notification Service | Notificaciones basadas en eventos |

---

## Flujo Principal

```text id="m2a9fd"
Buscar vuelo → Reservar → Pagar → Check-in → Notificar
```

---

## Stack Tecnológico

* Java 17+
* Spring Boot
* Spring Data JPA
* Spring Security (JWT)
* H2 (desarrollo) / PostgreSQL (producción)
* Docker & Docker Compose
* GitHub Actions (CI/CD)

---

## 📁 Estructura del Proyecto

```text id="cd3nqp"
airline-system/
├── flight-service/
├── booking-service/
├── payment-service/
├── checkin-service/
├── notification-service/
└── docker-compose.yml
```

---

## Cómo ejecutar el proyecto

### 1. Clonar repositorio

```bash id="t5rw0j"
git clone <tu-repositorio>
cd airline-system
```

### 2. Ejecutar con Docker

```bash id="h8k4pd"
docker-compose up --build
```

### 3. Ejecutar en local (perfil dev)

```bash id="f7n0sa"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Autenticación

El sistema usa **JWT (JSON Web Tokens)** para autenticación stateless.

### Roles

* `PASSENGER` → puede reservar y hacer check-in de sus vuelos
* `AGENT` → puede gestionar reservas
* `ADMIN` → acceso completo

---

## Testing

* Tests unitarios (TDD)
* Tests de integración (MockMvc)
* Cobertura mínima recomendada: **70%**

Ejecutar tests:

```bash id="m2pr9k"
mvn test
```

---

## Arquitectura basada en eventos

El sistema usa **Spring Events** para desacoplar servicios.

### Eventos principales

* `BookingCreatedEvent`
* `PaymentProcessedEvent`
* `CheckInCompletedEvent`

---

## Transacciones distribuidas (Saga Pattern)

El flujo de reserva sigue el patrón Saga:

```text id="6z7g2p"
Crear reserva
   ↓
Bloquear asientos
   ↓
Procesar pago
   ↓
Confirmar reserva
```

Si el pago falla:

```text id="6a2qwv"
Liberar asientos (acción compensatoria)
```

---

## Flujo de trabajo (GitHub)

Se utiliza:

* **Issues** → User Stories
* **Epics** → funcionalidades grandes
* **GitHub Projects** → tablero Kanban

---

## Definition of Done

Una funcionalidad se considera terminada cuando:

* El código compila
* Los tests pasan
* Se cumplen los criterios de aceptación
* No hay estados inconsistentes
* Ha sido revisada

---

## Objetivo Final

Al completar este proyecto serás capaz de:

* Entender cómo se construyen sistemas backend reales
* Trabajar con microservicios y eventos
* Implementar patrones de arquitectura usados en producción
* Tener un proyecto sólido para tu portfolio
