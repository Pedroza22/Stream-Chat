# Infraestructura Docker

La carpeta `infra/docker/` existe para respetar la estructura de la sección 5
del plan de trabajo. **La configuración de Docker del proyecto no se duplica
aquí**: vive en la raíz del repositorio, junto al código, para que
`docker compose` la encuentre directamente.

## Dónde está la configuración real

| Archivo | Contenido |
|---|---|
| [`../../compose.yaml`](../../compose.yaml) | Definición de los servicios (PostgreSQL, RabbitMQ, Zookeeper, Kafka y la app Spring Boot), volúmenes, healthchecks y `deploy.resources.limits`. |
| [`../../Dockerfile`](../../Dockerfile) | Build multi-etapa: compila `contracts` y la app con Maven y produce una imagen JRE 21 mínima que ejecuta el JAR como usuario `app` (no root). |
| [`../../.dockerignore`](../../.dockerignore) | Excluye del contexto de build lo innecesario (target, .git, etc.). |
| [`../../.env.example`](../../.env.example) | Plantilla de variables de entorno. El archivo `.env` real nunca se sube al repositorio. |

## Decisiones relevantes para el hardening (sección 10)

- **Puertos solo en loopback.** Todos los servicios se publican como
  `127.0.0.1:PUERTO:PUERTO` (PostgreSQL `5433`, RabbitMQ `5672/15672`, Kafka
  `9092`, app `8080`). Ninguno queda expuesto a Internet; el único punto público
  es Nginx (80/443).
- **Red interna de Compose.** La app alcanza PostgreSQL, RabbitMQ y Kafka por
  nombre de servicio (`postgres`, `rabbitmq`, `kafka`), no por puertos
  publicados, así que estos no atraviesan el hook `input` del firewall.
- **Límites de recursos.** Cada servicio declara `deploy.resources.limits`
  (CPU y memoria de Docker), coherentes con los límites de systemd de
  `infra/systemd/`.
- **Healthchecks.** Cada servicio define un `healthcheck`; la app depende de que
  los demás estén `service_healthy` antes de arrancar.
- **`ip_forward`.** No poner `net.ipv4.ip_forward = 0`: Docker necesita el
  reenvío de paquetes (ver `infra/nftables/README.md`).

## Comandos útiles

```bash
# Levantar todo el entorno.
docker compose up -d

# Estado y salud de los servicios.
docker compose ps

# Recursos en uso por contenedor.
docker stats --no-stream

# Detener sin borrar volúmenes (los datos persisten).
docker compose down
```
