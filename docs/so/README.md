# Informe de Sistemas Operativos — Stream-Chat

**Asignatura:** Sistemas Operativos
**Proyecto:** Stream-Chat (chat en tiempo real con Spring Boot 3.2.5 / Java 21, PostgreSQL, RabbitMQ, Kafka y Docker Compose)
**Hito asociado:** Hito 7 — "Los experimentos E1–E6 están ejecutados y documentados en `docs/so/`".
**Alumno que consolida y expone:** Esteban Lasso (ID 898780) — **Persona 3 (P3)**.

---

## 1. Qué contiene esta carpeta

Esta carpeta reúne el **informe de gestión de recursos del sistema operativo** aplicado al proyecto Stream-Chat.
Cada documento parte de lo que indican las *presentaciones del curso* (gestor de procesos, gestor de memoria,
gestor de archivos, gestor de E/S y hardening de Ubuntu Server en AWS EC2) y lo aterriza en **decisiones de diseño
reales del proyecto**, con **herramientas de medición** y **evidencias** reproducibles.

| Documento | Gestor | Base en el plan | Experimentos |
|---|---|---|---|
| [`gestor-procesos.md`](gestor-procesos.md) | Procesos | Sección **9.1** | **E1** |
| [`gestor-memoria.md`](gestor-memoria.md) | Memoria | Sección **9.2** | **E2** |
| [`gestor-archivos.md`](gestor-archivos.md) | Archivos | Sección **9.3** | **E3** |
| [`gestor-es.md`](gestor-es.md) | E/S | Sección **9.4** | **E4, E5, E6** |
| [`experimentos.md`](experimentos.md) | Los cuatro | Sección **9.5** | **E1–E6** (procedimiento completo) |
| [`guion-sustentacion.md`](guion-sustentacion.md) | Los cuatro | Secciones 9 y 11 | Guion de exposición |

Cada documento es **autocontenido**: se puede leer y defender sin depender de los demás.

---

## 2. Reparto de responsabilidades (sección 9 y sección 15 del plan)

El plan reparte los gestores entre las tres personas. Esta carpeta documenta los cuatro gestores, porque la
sustentación exige que **cualquier integrante sepa explicar los cuatro a nivel conceptual**.

| Integrante | Módulo | Gestores de SO que defiende | Experimentos |
|---|---|---|---|
| **P1** | Chat Core (dominio, API, WebSocket, PostgreSQL, outbox) | **Archivos** y **memoria** | E3, parte de E2 |
| **P2** | Messaging (RabbitMQ, Kafka, outbox relay, idempotencia) | **E/S** (buffers y spooling) | E4, E5, parte de E6 |
| **P3 — Esteban Lasso** | Presence & DevOps (typing, expiración, concurrencia, Docker, hardening, observabilidad) | **Procesos**, **monitoreo** y **hardening**; consolida el informe | E1, E2, E6 |

---

## 3. Tabla resumen de los cuatro gestores

Esta es la tabla de cierre del informe: cada fila es un gestor con su **decisión de diseño**, su **medición** y su
**evidencia**.

| Gestor | Decisión de diseño en Stream-Chat | Cómo se mide | Evidencia concreta | Detalle |
|---|---|---|---|---|
| **Procesos** | Cada servicio (JVM, PostgreSQL, RabbitMQ, Kafka, Nginx) es un proceso con su PID; Compose fija `restart: unless-stopped`, *health checks* y límites de CPU. El estado de typing se guarda con operaciones atómicas (`ConcurrentHashMap.merge`), sin bloqueos globales. | `ps`, `top`/`htop`, `pstree`, `chrt`, `nice`, `taskset`; latencia p95 de `TYPING_UPDATE` | Tabla servicio → PID → usuario → estado → consumo; latencia antes/después de E1 | [`gestor-procesos.md`](gestor-procesos.md) |
| **Memoria** | Heap de la JVM acotado al 75 % del límite del contenedor (`-XX:MaxRAMPercentage=75`, reforzado por `JAVA_TOOL_OPTIONS` en Compose). El mapa de typing expira y libera memoria. | `free -h`, `vmstat 2`, `sar -r`; métrica `typing_states_active` | Capturas de `free`/`vmstat` bajo carga y tendencia de `typing_states_active` a cero | [`gestor-memoria.md`](gestor-memoria.md) |
| **Archivos** | Datos persistentes en volúmenes nombrados de Docker; el WAL de PostgreSQL da durabilidad y recuperación; `.env` con permisos 600 y usuario no root en el contenedor. | `docker volume ls`, `df -h`, `df -i`, `du -h`, `fsck -n` | Conteo de mensajes antes/después de `kill -9` a PostgreSQL (E3) | [`gestor-archivos.md`](gestor-archivos.md) |
| **E/S** | Buffer acotado por sesión WebSocket con política *Strategy* (descartar `TYPING_UPDATE` antiguos, nunca mensajes, desconectar cliente lento); `prefetch=10` en RabbitMQ y `max.poll.records=50` en Kafka como tamaño de buffer del consumidor; RabbitMQ actúa de *spooler*. | `rabbitmqctl list_queues`, `iostat -xz 2`, `iotop-c`, `sar -n DEV 2`, `ss -tunap` | Profundidad de cola al detener el consumidor (E5); prueba de cliente lento (E4); carga de escritura (E6) | [`gestor-es.md`](gestor-es.md) |

---

## 4. Cómo esta carpeta cubre las secciones 9 y 11 del plan

### Sección 9 — Gestión de recursos del SO aplicada a Stream-Chat

| Subsección del plan | Tabla "qué indican las presentaciones → aplicación en Stream-Chat → evidencia" | Documento |
|---|---|---|
| 9.1 Gestor de procesos | Completa (proceso/BCP, estados, planificación, afinidad, sincronización, recursos, tareas programadas, hilos) | [`gestor-procesos.md`](gestor-procesos.md) |
| 9.2 Gestor de memoria | Completa (heap/stack, virtual/paginación/swap, traducción de direcciones, protección, fragmentación, herramientas) | [`gestor-memoria.md`](gestor-memoria.md) |
| 9.3 Gestor de archivos | Completa (journaling/WAL, persistencia, permisos, asignación, monitoreo, `fsck`, sistemas de archivos) | [`gestor-archivos.md`](gestor-archivos.md) |
| 9.4 Gestor de E/S | Completa (buffering, spooling, caching, E/S asíncrona, planificación de disco/SSD, red, capas) | [`gestor-es.md`](gestor-es.md) |
| 9.5 Experimentos E1–E6 | Objetivo, procedimiento, comando, captura y tablas antes/después | [`experimentos.md`](experimentos.md) |

### Sección 11 — Evidencias exigidas de Sistemas Operativos

| Evidencia pedida en la sección 11 | Dónde se entrega |
|---|---|
| Tabla servicio, PID, usuario, estado y consumo (E1) | [`gestor-procesos.md`](gestor-procesos.md) §4 y [`experimentos.md`](experimentos.md) E1 |
| Prueba con varios clientes concurrentes | [`gestor-procesos.md`](gestor-procesos.md) §6 (sincronización) y E1 |
| Medición de CPU, memoria y E/S bajo carga (`sar`, `vmstat`, `iostat`) | [`gestor-memoria.md`](gestor-memoria.md), [`gestor-es.md`](gestor-es.md), [`experimentos.md`](experimentos.md) E6 |
| Explicación de hilos, sincronización y condiciones de carrera | [`gestor-procesos.md`](gestor-procesos.md) §6 |
| Límites de recursos de Docker/systemd y qué ocurre al superarlos (E2) | [`gestor-memoria.md`](gestor-memoria.md) y [`experimentos.md`](experimentos.md) E2 |
| Qué ocurre cuando un servicio se detiene o se mata y vuelve a iniciar (E3, E5) | [`gestor-archivos.md`](gestor-archivos.md) y [`gestor-es.md`](gestor-es.md) |
| Buffers, spooling y caché aplicados al proyecto (E4, E5, E6) | [`gestor-es.md`](gestor-es.md) |
| Checklist de hardening y verificaciones de la sección 10 | [`guion-sustentacion.md`](guion-sustentacion.md) §5 (demostración) |

---

## 5. Convenciones del informe

- **Arquitectura real del repositorio:** los datos de servicios, límites, health checks, variables y nombres de
  colas provienen de `compose.yaml`, `Dockerfile`, `src/main/resources/application.yml` y `.env.example`.
- **Leyenda del plan:** sin marca = lo indican las presentaciones; ➕ = complemento propuesto por el equipo.
- **Tablas de medición:** todas las tablas de resultados de experimentos se entregan **en blanco**, con la nota
  de que se rellenan al ejecutar el experimento. Este informe no inventa mediciones.
- **Idioma:** español, tono académico y didáctico.
