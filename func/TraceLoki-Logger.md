# Trazabilidad de Logs con Logback + Loki + Jaeger

## 1. Objetivo

Correlacionar **logs** con **traces distribuidos**. Cada petición que traverse los microservicios lleva un `traceId` único que aparece:
- En los **logs** de cada microservicio (via Logback MDC)
- En los **traces** de Jaeger (via OpenTelemetry)

Resultado: buscar un `traceId` en Grafana/Loki te da los logs, buscarlo en Jaeger te da la traza completa.

## 2. Arquitectura de Logs

```
Microservicio
    │
    ├── Logback (genera logs con traceId/spanId via MDC)
    │       │
    │       ├── [Perfil local] → ConsoleAppender (texto legible)
    │       └── [Perfil docker/prod] → LogstashEncoder (JSON)
    │
    └── stdout (Docker captura los logs)
            │
            ▼
        Promtail (lee logs Docker, envía a Loki)
            │
            ▼
          Loki (almacena y indexa)
            │
            ▼
         Grafana (consulta y visualiza)
```

## 3. Cambios Realizados

### 3.1 Archivo nuevo: `logback-spring.xml` (7 servicios)

**Ruta:** `src/main/resources/logback-spring.xml`

Cada microservicio ahora tiene este archivo con dos appender:

#### Appender CONSOLE (perfil: local, default)
```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
  <encoder>
    <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level [%X{traceId},%X{spanId}] %logger{30} - %msg%n</pattern>
  </encoder>
</appender>
```

**Resultado en consola:**
```
14:23:45.102 [http-nio-8080-exec-1] INFO  [4bf92f35,a3ce929d] BookingController - POST /api/v1/bookings
14:23:45.118 [http-nio-8080-exec-1] INFO  [4bf92f35,b1d4e820] BookingService - Reserva creada booking.id=7821
14:23:45.389 [http-nio-8080-exec-1] WARN  [4bf92f35,b1d4e820] FlightService - Query lenta duracion_ms=268
14:23:45.401 [http-nio-8080-exec-1] ERROR [4bf92f35,c9f1a034] FlightService - Stock insuficiente
```

#### Appender JSON (perfil: docker, prod)
```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <includeMdcKeyName>traceId</includeMdcKeyName>
    <includeMdcKeyName>spanId</includeMdcKeyName>
    <customFields>{"service":"${spring.application.name}","env":"${APP_ENV:-local}"}</customFields>
  </encoder>
</appender>
```

**Resultado en Docker (JSON parseable por Loki):**
```json
{
  "@timestamp": "2026-08-31T14:23:45.102Z",
  "level": "INFO",
  "thread_name": "http-nio-8080-exec-1",
  "logger_name": "dakota.software.bookingservice.infrastructure.web.BookingController",
  "message": "POST /api/v1/bookings",
  "traceId": "4bf92f35a3ce929d",
  "spanId": "b1d4e820",
  "service": "booking-service",
  "env": "prod"
}
```

### 3.2 Dependencia nueva en `pom.xml` (7 servicios)

```xml
<!-- Logback JSON encoder for Loki/ELK (logs estructurados con traceId) -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

**¿Por qué?** El `LogstashEncoder` es el encoder estándar para logs JSON que Loki/Grafana pueden parsear. Sin esta dependencia, el appender JSON no funciona y el profile `docker/prod` falla.

### 3.3 Configuración existente (sin cambios necesarios)

#### Loki (`loki-config.yml`)
```yaml
limits_config:
  allow_structured_metadata: true  # ← Permite campos extra (traceId, spanId)
```

#### Promtail (`promtail-config.yml`)
```yaml
scrape_configs:
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
    relabel_configs:
      - source_labels: ['__meta_docker_container_name']
        target_label: 'container'
```

Promtail ya está configurado para leer logs de Docker via el socket. Los JSON logs que genera LogstashEncoder se envían automáticamente a Loki.

## 4. Cómo Funciona la Correlación

### Flujo de una petición

```
1. Request llega a api-gateway
   └── OpenTelemetry crea traceId: "4bf92f35"
   └── Logback lo mete en MDC: %X{traceId}
   └── Log: 14:23:45.102 [4bf92f35,...] INFO ApiGateway - Routing to booking-service

2. api-gateway → booking-service
   └── OpenTelemetry propaga traceId: "4bf92f35"
   └── Log: 14:23:45.118 [4bf92f35,...] INFO BookingService - Creando reserva

3. booking-service → flight-service (via Kafka o REST)
   └── OpenTelemetry propaga traceId: "4bf92f35"
   └── Log: 14:23:45.389 [4bf92f35,...] WARN FlightService - Query lenta
```

### El traceId se propaga automáticamente

**El primer número** (`4bf92f35`) es el `traceId` — es **el mismo en TODOS los servicios** por donde pase la petición. El segundo número (`a3ce929d`, `b1d4e820`) es el `spanId` — **diferente en cada paso**.

### Ejemplo real: petición que cruza 3 servicios

```
# api-gateway
14:23:45.102 [http-nio-8080-exec-1] INFO  [4bf92f35,a3ce929d] ApiGateway - Routing to booking-service

# booking-service
14:23:45.118 [http-nio-8080-exec-1] INFO  [4bf92f35,b1d4e820] BookingService - Creando reserva
14:23:45.125 [http-nio-8080-exec-1] INFO  [4bf92f35,c7e8f901] BookingService - Consultando vuelo

# flight-service
14:23:45.140 [http-nio-8080-exec-1] INFO  [4bf92f35,d9a1b2c3] FlightService - Buscando disponibilidad
14:23:45.200 [http-nio-8080-exec-1] WARN  [4bf92f35,e4f5g6h7] FlightService - Query lenta
```

- **`4bf92f35`** → Mismo en los 3 servicios (el trace completo)
- **`a3ce929d`**, **`b1d4e820`**, **`d9a1b2c3`** → Diferentes en cada paso (cada span)

Lo buscas en Jaeger con `4bf92f35` y ves toda la traza: cuánto tardó cada servicio, qué falló, qué fue lento.

### Búsqueda en Grafana/Loki

```
{container="booking-service"} | json | traceId="4bf92f35"
```

Resultado: todos los logs de esa petición en TODOS los servicios.

### Búsqueda en Jaeger

```
TraceID: 4bf92f35a3ce929d
```

Resultado: la traza completa con timing de cada span.

## 5. Servicios Modificados

| Servicio | logback-spring.xml | logstash-logback-encoder |
|----------|:------------------:|:------------------------:|
| api-gateway | ✅ | ✅ |
| auth-service | ✅ | ✅ |
| booking-service | ✅ | ✅ |
| flight-service | ✅ | ✅ |
| payment-service | ✅ | ✅ |
| checkin-service | ✅ | ✅ |
| notification-service | ✅ | ✅ |

## 6. Verificación

### Paso 1: Rebuild Docker
```bash
docker compose down && docker compose up -d --build
```

### Paso 2: Generar tráfico
```bash
curl http://localhost:8086/api/v1/flights?origin=BOG&destination=MIA
```

### Paso 3: Ver logs en consola del contenedor
```bash
docker logs booking-service --tail 20
```

Deberías ver:
```
14:23:45.102 [...] INFO  [4bf92f35,a3ce929d] BookingController - POST /api/v1/bookings
```

### Paso 4: Buscar en Grafana
1. Abrir http://localhost:3000
2. Ir a **Explore** → Seleccionar **Loki**
3. Query: `{container="booking-service"} | json | traceId != ""`
4. Los logs con traceId aparecerán listos para cruzar con Jaeger

### Paso 5: Buscar en Jaeger
1. Abrir http://localhost:16686
2. Seleccionar servicio → **Find Traces**
3. Copiar el traceId de un log y buscarlo en Jaeger

## 7. Formato de Log Esperado

### Desarrollo (perfil: local/default)
```
HH:mm:ss.SSS [thread] LEVEL [traceId,spanId] logger - mensaje
```

Ejemplo:
```
14:23:45.102 [http-nio-8080-exec-1] INFO  [4bf92f35,a3ce929d] BookingController - POST /api/v1/bookings
```

### Producción (perfil: docker/prod)
JSON estructurado que Loki parsea automáticamente:
```json
{
  "@timestamp": "...",
  "level": "INFO",
  "traceId": "4bf92f35",
  "spanId": "a3ce929d",
  "service": "booking-service",
  "message": "POST /api/v1/bookings"
}
```

## 8. Ejecución en Local (sin Docker compose completo)

### Servicios que SÍ necesitan Kafka

| Servicio | Kafka Producer | Kafka Consumer | ¿Arranca sin Kafka? |
|----------|:-:|:-:|:---:|
| booking-service | ✅ booking.created, booking.cancelled | — | ⚠️ Arranca pero no produce eventos |
| payment-service | — | ✅ booking.created | ⚠️ Arranca pero no consume |
| flight-service | — | ✅ booking.created, booking.cancelled | ⚠️ Arranca pero no consume |
| checkin-service | ✅ checkin.completed | ✅ booking.confirmed | ⚠️ Arranca pero no produce/consume |
| notification-service | — | ✅ booking.*, payment.*, checkin.* | ⚠️ Arranca pero no consume |

### Servicios que NO necesitan Kafka

| Servicio | DB | Dependencias externas |
|----------|-----|----------------------|
| auth-service | H2 (memoria) | Ninguna |
| api-gateway | — | auth-service (JWT validation) |

### Ejecución mínima en local

```bash
# Solo Kafka (necesario para booking, flight, payment, checkin, notification)
docker compose up -d kafka

# Opcional: PostgreSQL (si no usas H2)
docker compose up -d db
```

### En IntelliJ/VS Code

1. Arrancar `kafka` en Docker
2. Cada servicio → Run Configuration → Profile: `dev`
3. Correr en orden:
   - `auth-service` (puerto 8087)
   - `flight-service` (puerto 8086)
   - `booking-service` (puerto 8082)
   - `payment-service` (puerto 8083)
   - `checkin-service` (puerto 8084)
   - `notification-service` (puerto 8085)
   - `api-gateway` (puerto 8080) — último porque depende de los demás

### Verificar que funciona

```bash
# Health checks
curl http://localhost:8087/actuator/health  # auth-service
curl http://localhost:8086/actuator/health  # flight-service
curl http://localhost:8082/actuator/health  # booking-service

# Generar tráfico
curl "http://localhost:8086/api/v1/flights?origin=BOG&destination=MIA&departureDate=2025-12-25"
```

## 9. Grafana + Loki: Cómo Ver los Logs

### Data Sources configurados

| Data Source | Estado | URL interna | URL externa |
|-------------|:------:|-------------|-------------|
| Prometheus | ✅ | http://prometheus:9090 | http://localhost:9090 |
| Loki | ✅ | http://loki:3100 | (via Grafana) |

### Acceder a Grafana

1. Abrir **http://localhost:3000**
2. Login: `admin` / `admin` (cambiar en producción)
3. Ir a **Explore** (icono de brújula en el menú lateral)
4. Seleccionar **Loki** en el dropdown de data sources

### Queries útiles

#### Ver logs de un servicio específico
```
{container="booking-service"}
```

#### Ver logs de todos los servicios
```
{container=~".+"}
```

#### Buscar por traceId
```
{container="booking-service"} | json | traceId="5c74a879e6327ad427ec2e091c9c83e6"
```

#### Filtrar por nivel de log
```
{container="booking-service"} | json | level="WARN"
```

#### Buscar mensajes con errores
```
{container="booking-service"} | json | level="ERROR" |~ "Exception"
```

### Pila completa de observabilidad

| Herramienta | URL | Propósito |
|-------------|-----|-----------|
| Jaeger | http://localhost:16686 | Traces distribuidos |
| Prometheus | http://localhost:9090 | Métricas (targets) |
| Grafana | http://localhost:3000 | Dashboards unificados |
| Loki | (via Grafana) | Logs estructurados |
| Kafdrop | http://localhost:19000 | Inspección Kafka |

### Flujo de correlación completa

```
1. Request → api-gateway
   └── Log: [traceId=4bf92f35] INFO ApiGateway - Routing
   └── Jaeger: trace 4bf92f35 creado

2. api-gateway → booking-service
   └── Log: [traceId=4bf92f35] INFO BookingService - Creando reserva
   └── Jaeger: span añadido al trace

3. booking-service → flight-service
   └── Log: [traceId=4bf92f35] WARN FlightService - Query lenta
   └── Jaeger: span añadido al trace

4. En Grafana/Loki:
   └── Query: {container=~".+"} | json | traceId="4bf92f35"
   └── Resultado: todos los logs de los 3 servicios

5. En Jaeger:
   └── TraceID: 4bf92f35
   └── Resultado: traza completa con timing de cada paso
```

---

*Documento generado: 31 de agosto de 2026*
*Bootcamp: Bootcamp 1 2026 — Mestre, Enga & Saber*
