# Trazabilidad Distribuida con Jaeger

## 1. Arquitectura de Observabilidad

```
┌─────────────────────────────────────────────────────────────────┐
│                        MICROSERVICIOS                            │
│  api-gateway (8080) ─── auth-service (8087)                     │
│       │                    │                                    │
│       ├── flight-service (8086)                                 │
│       ├── booking-service (8082) ─── payment-service (8083)     │
│       ├── checkin-service (8084)                                │
│       └── notification-service (8085)                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │ OTLP HTTP (puerto 4318)
                           ▼
                  ┌─────────────────┐
                  │     Jaeger      │
                  │   :16686 (UI)   │
                  │   :4317 (gRPC)  │
                  │   :4318 (HTTP)  │
                  └─────────────────┘
```

## 2. Stack Tecnológico

| Componente | Tecnología | Versión |
|------------|------------|---------|
| Framework | Spring Boot | 4.1.0 |
| Tracing Bridge | Micrometer → OpenTelemetry | via `spring-boot-starter-opentelemetry` |
| Export Protocol | OTLP HTTP | — |
| Backend | Jaeger all-in-one | 1.75.0 |
| Métricas | Prometheus + Micrometer | — |
| Logs | Loki + Promtail | — |
| Dashboards | Grafana | latest |

## 3. Configuración

### 3.1 Dependencia (pom.xml)

Cada microservicio incluye un único starter que agrupa bridge + exporter:

```xml
<!-- Spring Boot 4 OpenTelemetry starter (tracing + OTLP export) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

> **Nota:** En Spring Boot 4, este starter reemplaza las dependencias individuales
> `micrometer-tracing-bridge-otel` y `opentelemetry-exporter-otlp`.

### 3.2 Configuración YAML (application.yaml)

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # 100% en desarrollo
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://jaeger:4318/v1/traces
```

> **Clave:** La ruta correcta en Boot 4 es `management.opentelemetry.tracing.export.otlp.endpoint`.
> No usar `management.tracing.export.otlp.*` (es de Boot 3).

### 3.3 Docker Compose (jaeger)

```yaml
jaeger:
  image: jaegertracing/all-in-one:1.75.0
  container_name: jaeger
  ports:
    - "16686:16686"   # UI
    - "4317:4317"     # OTLP gRPC
    - "4318:4318"     # OTLP HTTP
  environment:
    COLLECTOR_OTLP_ENABLED: "true"
  networks:
    - observability
```

### 3.4 Prometheus → Servicios (prometheus.yml)

```yaml
- job_name: 'microservices'
  metrics_path: /actuator/prometheus
  static_configs:
    - targets:
        - 'api-gateway:8080'
        - 'booking-service:8082'
        - 'payment-service:8083'
        - 'checkin-service:8084'
        - 'flight-service:8086'
        - 'auth-service:8087'
```

> Los servicios corren dentro de Docker en la red `observability`.
> Se resuelven por nombre de contenedor, no por `host.docker.internal`.

## 4. Batería de Pruebas

### 4.1 Servicios Registrados en Jaeger

| Servicio | Puerto | Tipo | Estado |
|----------|--------|------|--------|
| api-gateway | 8080 | Gateway (Spring Cloud) | ✅ Registrado |
| auth-service | 8087 | REST + JWT | ✅ Registrado |
| flight-service | 8086 | REST + JPA | ✅ Registrado |
| booking-service | 8082 | REST + JPA + Kafka | ✅ Registrado |
| payment-service | 8083 | REST + JPA + Kafka | ✅ Registrado |
| checkin-service | 8084 | REST + JPA + Kafka | ✅ Registrado |
| notification-service | 8085 | Kafka Consumer | ✅ Registrado |

**Total: 7 servicios microservicio + 1 Jaeger = 8 en la UI**

URL Jaeger UI: **http://localhost:16686/search**

### 4.2 Pruebas Ejecutadas

#### Prueba 1: Búsqueda de Vuelos (flight-service)

```
GET /api/v1/flights?origin=BOG&destination=MIA&departureDate=2025-12-25
→ HTTP 200 | Respuesta: [] (sin datos de prueba)
```

Se ejecutaron 5 búsquedas con diferentes rutas:
- BOG → MIA ✅
- MIA → JFK ✅
- JFK → LAX ✅
- LAX → MEX ✅
- MEX → BOG ✅

#### Prueba 2: Registro de Usuario (auth-service)

```
POST /api/v1/auth/register
Body: {"username":"testuser","password":"testpass123","email":"test@example.com"}
→ HTTP 500 (Error de serialización Jackson 3 — preexistente)
```

> **Nota:** El auth-service devuelve 500 por un problema de compatibilidad
> con Jackson 3 en Spring Boot 4. Los DTOs usan records Java que requieren
> configuración adicional de Jackson 3. Esto es un bug preexistente del
> proyecto, NO de la configuración de tracing. Aun así, la petición genera
> traces correctamente en Jaeger.

#### Prueba 3: Login de Usuario (auth-service)

```
POST /api/v1/auth/login
Body: {"username":"testuser","password":"testpass123"}
→ HTTP 500 (mismo problema de Jackson 3)
```

#### Prueba 4: Obtener Reserva (booking-service)

```
GET /api/v1/bookings/1 → HTTP 500 (reserva inexistente)
GET /api/v1/bookings/2 → HTTP 200
GET /api/v1/bookings/3 → HTTP 200
```

#### Prueba 5: Check-in (checkin-service)

```
POST /api/v1/checkins
Body: {"bookingId":1,"flightId":1,"passengerId":1}
→ HTTP 400 (validación de negocio — esperado)
```

Se ejecutaron 3 intentos de check-in, todos generan traces.

#### Prueba 6: Health Checks (todos los servicios)

```
GET /actuator/health → HTTP 200 (cada servicio)
```

| Servicio | Estado |
|----------|--------|
| api-gateway | ✅ UP |
| auth-service | ✅ UP |
| flight-service | ✅ UP |
| booking-service | ✅ UP |
| payment-service | ✅ UP |
| checkin-service | ✅ UP |
| notification-service | ⚠️ 404 (actuator no expuesto) |

### 4.3 Resumen de Pruebas

| # | Endpoint | Servicio | Método | HTTP | Traces Generados |
|---|----------|----------|--------|------|------------------|
| 1 | /api/v1/flights | flight-service | GET | 200 | ✅ |
| 2 | /api/v1/auth/register | auth-service | POST | 500 | ✅ |
| 3 | /api/v1/auth/login | auth-service | POST | 500 | ✅ |
| 4 | /api/v1/bookings/1 | booking-service | GET | 500 | ✅ |
| 5 | /api/v1/bookings/2 | booking-service | GET | 200 | ✅ |
| 6 | /api/v1/bookings/3 | booking-service | GET | 200 | ✅ |
| 7 | /api/v1/checkins | checkin-service | POST | 400 | ✅ |
| 8 | /actuator/health | api-gateway | GET | 200 | ✅ |
| 9 | /actuator/health | auth-service | GET | 200 | ✅ |
| 10 | /actuator/health | booking-service | GET | 200 | ✅ |
| 11 | /actuator/health | flight-service | GET | 200 | ✅ |
| 12 | /actuator/health | payment-service | GET | 200 | ✅ |

## 5. Verificación en Jaeger UI

### Pasos para verificar

1. Abrir **http://localhost:16686/search**
2. En el dropdown **Service**, seleccionar cualquier microservicio
3. Hacer clic en **Find Traces**
4. Cada trace muestra:
   - **Trace ID** único
   - **Spans** por servicio involucrado
   - **Duration** total
   - **Timeline** con el flujo de la petición

### Qué buscar en la UI

- **api-gateway**: Trace de entrada con spans de security filter + routing
- **auth-service**: Spans de autenticación (aunque falle con 500)
- **flight-service**: Spans de búsqueda de vuelos + queries JPA
- **booking-service**: Spans de creación/consulta de reserva + Kafka events
- **checkin-service**: Spans de check-in + validación
- **payment-service**: Spans de procesamiento de pago
- **notification-service**: Spans de consumo de eventos Kafka

## 6. Troubleshooting

### Problema: Prometheus targets DOWN con `host.docker.internal`

**Causa:** Los servicios corren dentro de Docker, no en el host.

**Solución:** Usar nombres de contenedor Docker en `prometheus.yml`:
```yaml
- targets:
    - 'api-gateway:8080'    # No host.docker.internal:8080
```

### Problema: Jaeger no muestra traces

**Causa 1:** No se ha generado tráfico. Solución: hacer peticiones a los endpoints.

**Causa 2:** URL de endpoint incorrecta. Verificar:
```yaml
management.opentelemetry.tracing.export.otlp.endpoint: http://jaeger:4318/v1/traces
```

**Causa 3:** Dependencia incorrecta. Usar `spring-boot-starter-opentelemetry`, no las dependencias por separado.

### Problema: Actuator 401 en api-gateway

**Causa:** El `BearerTokenAuthenticationFilter` se ejecuta antes de `permitAll()`.

**Solución:** Dos `SecurityFilterChain` separadas:
- `@Order(1)` → Solo `/actuator/**` (sin JWT)
- `@Order(2)` → APIs protegidas (con JWT/OAuth2)

## 7. Endpoints de Verificación

| Herramienta | URL | Propósito |
|-------------|-----|-----------|
| Jaeger UI | http://localhost:16686 | Traces distribuidos |
| Prometheus | http://localhost:9090/targets | Estado de scrape targets |
| Grafana | http://localhost:3000 | Dashboards unificados |
| Kafdrop | http://localhost:19000 | Inspección de topics Kafka |

---

*Documento generado: 31 de agosto de 2026*
*Bootcamp: Bootcamp 1 2026 — Mestre, Enga & Saber*
