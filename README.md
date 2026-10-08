# Feature 3 — Envío de pagos a sistemas externos

Microservicio que toma pagos pendientes de la base de datos de AQUORA, los publica en una
cola (RabbitMQ) y un consumidor los "envía" a 3 sistemas externos simulados (bancos),
con reintentos e idempotencia.

Mapeo con el diagrama de clase:

| Diagrama | En este proyecto |
|---|---|
| MySQL replica | Tu MariaDB local de AQUORA (tabla `pagos` + 3 columnas nuevas) |
| producer ("consulta pagos pendientes por medio de flag") | `PagoProducer` (clase programada cada 30s) |
| cola | RabbitMQ (`docker-compose.yml`) |
| consumer | `PagoConsumer` |
| Bancos | `BancoSimulado` (simula 3 sistemas externos) |
| Lambda Function ("flag pagos enviados a banco") | Lo hace el propio `PagoConsumer`, actualizando `estado_envio` en la tabla `pagos` |
| DynamoDB Logs | Por ahora: logs normales en consola (`log.info`/`log.error`). Se puede agregar DynamoDB real más adelante si se confirma que es requisito — ver nota abajo. |

## Pasos para arrancar hoy

**Requisitos:** JDK 25 LTS y Maven 3.9 o posterior.

### 1. Preparar la base de datos
Abre tu AQUORA local en DBeaver (o donde administres tu MariaDB) y ejecuta el script:
```
sql/agregar_columna_envio.sql
```
Esto solo agrega 3 columnas nuevas a la tabla `pagos` que ya tienes. No borra ni cambia nada del proyecto AQUORA.

Después, para tener algo que probar de inmediato, marca 1 o 2 pagos existentes como pendientes de envío (el script trae un ejemplo comentado al final).

### 2. Levantar RabbitMQ
Con Docker Desktop corriendo:
```bash
docker compose up -d
```
Verifica en el navegador: http://localhost:15672 (usuario `guest`, contraseña `guest`). Ahí vas a ver la cola `pagos.envio.queue` una vez que arranque la aplicación.

### 3. Ajustar `application.properties`
Revisa `src/main/resources/application.properties`:
- `spring.datasource.url` — cambia el nombre de la base (`aquora`) si en tu Laragon se llama distinto.
- `spring.datasource.username` / `password` — igual que como te conectas en DBeaver.

### 4. Correr la aplicación
```bash
mvn spring-boot:run
```
(si no tienes Maven instalado, en IntelliJ basta con abrir el proyecto y correr `PagosExternosApplication`, el IDE trae su propio Maven).

### 5. Probar el flujo
- Swagger UI: http://localhost:8083/swagger-ui.html
- Botón `POST /api/pagos-externos/procesar` → dispara la revisión de pendientes de inmediato (sin esperar los 30s).
- `GET /api/pagos-externos/estado` → ver cómo cambia el estado de cada pago: `pendiente` → `en_cola` → `enviado` (o `fallido` tras 3 intentos).
- En la consola vas a ver los logs del producer, el consumer, y los 3 "bancos" simulados.

### 6. Probar reintentos/fallo
`BancoSimulado` simula ~80% de éxito al azar, así que si corres varias veces vas a ver también el caso donde falla y se reintenta, y eventualmente queda en `fallido` si falla 3 veces seguidas.

## Pendiente / próximos pasos
- Documentar bien los endpoints en Swagger (ya está la base con `springdoc-openapi`, falta pulir descripciones).
- Confirmar con el profe si realmente se requiere usar AWS DynamoDB para los logs y AWS Lambda literal, o si el diagrama es solo ilustrativo y vale con el equivalente actual (log en consola / base de datos). Si se requiere DynamoDB real, se puede agregar después sin tener que rehacer el producer/consumer — solo se cambiaría dónde se registra el log.
- Agregar manejo de errores más robusto (hoy el fallo es simulado al azar, en la versión real podría ser una excepción de conexión).
- Pruebas end-to-end (tarea ya listada en el backlog de Jira, Epic 19).
