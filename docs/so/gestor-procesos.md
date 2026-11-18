# Gestor de procesos aplicado a Stream-Chat

**Asignatura:** Sistemas Operativos • **Base del plan:** sección **9.1** • **Experimento:** **E1**
**Responsable:** Persona 3 (Esteban Lasso, ID 898780) — defensa de procesos, monitoreo y hardening.

---

## 1. Idea central

Un **proceso** es un *programa en ejecución* junto con toda la información que el sistema operativo necesita para
gestionarlo. Esa información vive en el **Bloque de Control de Proceso (BCP / PCB)**:

| Campo del BCP | Qué es | Dónde se ve en Stream-Chat |
|---|---|---|
| **PID** | Identificador único del proceso | `ps aux` → columna `PID` (cada contenedor es un proceso) |
| **Estado** | Nuevo, preparado, ejecución, bloqueado, terminado | `top`/`htop` → columna `S` |
| **Planificación** | Prioridad, política, tiempo de CPU | `chrt -p`, `nice`, `ps -eo ... -ni -pri` |
| **Punteros a memoria** | Segmentos de código, datos, heap, stack | `pmap`, `htop` (RES/SHR), ver `gestor-memoria.md` |
| **Contexto (registros, PC)** | Estado del procesador al cambiar de proceso | *Context switch*: `vmstat` columna `cs` |
| **Información de E/S** | Ficheros y dispositivos abiertos | `lsof -p PID`, sockets de los brokers |
| **Comunicación** | Pipes, sockets, colas | Red interna de Docker (brokers) y puertos locales |
| **Auditoría / contabilidad** | CPU, tiempo, usuario | `ps -eo pid,user,%cpu,etime` |

En Stream-Chat **cada servicio de `compose.yaml` es un proceso** dentro de su contenedor: la JVM de la aplicación,
PostgreSQL, RabbitMQ, Zookeeper, Kafka y (en despliegue) Nginx. El kernel planifica todos esos procesos sobre los
mismos núcleos del host.

---

## 2. Tabla base de la sección 9.1 (presentaciones → Stream-Chat → evidencia)

| Qué indican las presentaciones | Aplicación en Stream-Chat | Evidencia |
|---|---|---|
| Un proceso es un programa en ejecución con su BCP (PID, estado, planificación, punteros a memoria, contexto, E/S, comunicación y auditoría) | Cada servicio (JVM, PostgreSQL, RabbitMQ, Zookeeper, Kafka, Nginx) es un proceso con su PID y usuario. Se documenta la tabla "servicio → PID → usuario → estado → consumo" y se relaciona cada columna con un campo del BCP. | `ps aux`, `pstree -p`, `ps -eo pid,user,%cpu,%mem,etime,cmd --sort=-%cpu \| head -11` |
| Estados: nuevo, preparado, ejecución, bloqueado | Un hilo consumidor esperando mensajes está **bloqueado** (espera de E/S); uno que procesa está en **ejecución**. Se dibuja el diagrama de estados del hilo de consumo. | `top` o `htop` (columna de estado `S`) |
| Planificación: FCFS, SJN, Round Robin, prioridades y tiempo real | **Experimento E1:** lanzar carga de CPU junto al servicio de presencia y medir cuánto tarda en llegar `TYPING_UPDATE`. Repetir cambiando la prioridad. Explicar qué algoritmo aplica el kernel. | `yes > /dev/null`, `ps -eo comm,pid,ni,pri --sort=-ni`, `chrt -p PID`, `sudo chrt -r -p 10 PID`, `nice` ➕ |
| Afinidad de CPU | Fijar el proceso de presencia a los núcleos 0 y 1 y comparar con el comportamiento por defecto. En contenedores, `--cpus` y `--cpuset-cpus` ➕ (Compose ya fija `cpus` por servicio). | `taskset -p PID`, `taskset -c 0,1 comando` |
| Sincronización y comunicación entre procesos: mutex, semáforos, tuberías | El mapa de typing se accede desde varios hilos. Se usan estructuras concurrentes y operaciones atómicas (`compute`, `merge`) y se prueba con N hilos. Se explica qué es una condición de carrera y por qué un `Map` común la produciría. | Prueba de concurrencia de la matriz de pruebas |
| Gestión de recursos: si un proceso consume demasiado, el gestor puede terminarlo o reiniciarlo | `restart: unless-stopped` y *health checks* en Compose. Con systemd: `MemoryMax`, `CPUQuota`, `TasksMax`. Se mata un proceso y se muestra la recuperación. | `kill -9 PID`, tiempo de recuperación documentado |
| Procesamiento por lotes y programación de tareas (cron, `schtasks`) | `check-resources.sh` cada 5 minutos con cron (guía de hardening) y volcado nocturno de PostgreSQL ➕. El barrido de expiración de typing es una tarea programada *interna* de la aplicación. | `/etc/cron.d/`, `journalctl -t resource-alert`, `@Scheduled` de `TypingExpirationScheduler` |
| Hilos dentro de un proceso (por ejemplo, uno para la interfaz y otro para cálculo) | Justificar el tamaño del pool de hilos de los *listeners* de RabbitMQ y de las sesiones WebSocket según los núcleos disponibles. | `top -H` o `ps -eLf` ➕ |

**Criterio:** informe antes/después de E1 con latencia p95 y explicación del algoritmo de planificación involucrado.

---

## 3. Del BCP al código real del proyecto

Stream-Chat materializa el gestor de procesos en tres puntos concretos del repositorio:

### 3.1 Tareas programadas dentro del proceso de la JVM

El proceso Java no solo atiende peticiones: también ejecuta **tareas programadas por temporizador**. Son hilos
gestionados por el `TaskScheduler` de Spring (un hilo por ejecución dentro del mismo proceso):

```java
// src/main/java/com/streamchat/presence/infrastructure/scheduling/TypingExpirationScheduler.java
@Scheduled(fixedDelayString = "${app.presence.expiration-scan-ms:2000}")
public void scanExpiredTypingStates() {
    expireTypingStatesUseCase.expireNow();
}
```

```java
// src/main/java/com/streamchat/messaging/outbox/OutboxRelay.java
@Scheduled(fixedDelayString = "${app.messaging.outbox-relay-delay-ms:5000}")
@Transactional
public void processOutbox() { /* publica hasta 50 eventos pendientes */ }
```

- **Barrido de expiración de typing:** cada **2 000 ms** (`PRESENCE_EXPIRATION_SCAN_MS=2000`).
- **Relay de outbox:** cada **5 000 ms** (`outbox-relay-delay-ms: 5000`).

Cada disparo es una **ráfaga de trabajo** que compite por la CPU con los hilos que atienden WebSocket y con los
listeners de RabbitMQ/Kafka. Esto justifica que E1 mida la latencia del sistema **bajo carga de CPU**.

### 3.2 Sincronización sin bloqueos globales (evitar condiciones de carrera)

El estado de escritura se guarda en un `ConcurrentHashMap` con operaciones **atómicas**, no en un `Map` común ni en
un `Map` estático:

```java
// src/main/java/com/streamchat/presence/domain/service/TypingStateRegistry.java
private final ConcurrentHashMap<TypingKey, UserTypingState> states = new ConcurrentHashMap<>();

public UserTypingState typingStarted(TypingKey key, Instant occurredAt) {
    return states.merge(
            key,
            UserTypingState.started(key, occurredAt),
            (existing, incoming) -> existing.renewedAt(incoming.lastActivityAt()));
}

public Optional<UserTypingState> typingStopped(TypingKey key) {
    return Optional.ofNullable(states.remove(key));
}
```

- `merge` realiza *leer-modificar-escribir* de forma **atómica** por clave: dos usuarios escribiendo a la vez no se
  pisan.
- `remove(key, value)` es un borrado **condicional** que evita eliminar un estado renovado entre la lectura y el
  borrado (en `expire`).
- Con un `Map` normal harían falta `synchronized` o un `mutex` global, que serializaría a todos los usuarios. Aquí
  el "mutex" es de grano fino: **una entrada del mapa**.

### 3.3 Gestión de recursos: reinicio y aislamiento

En `compose.yaml` cada servicio declara su política de recursos y de reinicio:

```yaml
restart: unless-stopped          # el gestor reinicia el proceso si cae
deploy:
  resources:
    limits:
      cpus: "1.0"                # límite de CPU (el kernel reparte con cgroups)
      memory: 768m               # tope de memoria (ver gestor-memoria.md)
healthcheck:                     # el orquestador vigila la salud del proceso
  test: ["CMD-SHELL", "pg_isready -U streamchat -d streamchat"]
  interval: 10s
```

Límites declarados (servicio → CPU → memoria):

| Servicio | CPUs | Memoria | `restart` |
|---|---:|---|---|
| `postgres` | 1.0 | 768m | `unless-stopped` |
| `rabbitmq` | 1.0 | 768m | `unless-stopped` |
| `zookeeper` | 0.5 | 512m | `unless-stopped` |
| `kafka` | 1.0 | 1g | `unless-stopped` |
| `app` (JVM) | 1.5 | 1152m | `unless-stopped` |

---

## 4. Tabla "servicio → PID → usuario → estado → consumo" (rellenar al ejecutar)

> **Nota:** esta tabla se entrega **en blanco**. Se rellena ejecutando los comandos de la sección 5 y copiando la
> salida real. No se incluyen valores inventados.

| Servicio | Nombre del proceso | PID | Usuario | Estado (`S`) | %CPU | %MEM | ETIME | Relación con un campo del BCP |
|---|---|---|---:|---|---:|---:|---|---|
| `app` (Spring Boot / JVM) | `java -jar app.jar` | | `app` (no root) | | | | | PID, estado, contabilidad |
| `postgres` | `postgres` | | `postgres` | | | | | PID, E/S (WAL) |
| `rabbitmq` | `beam.smp` (Erlang VM) | | `rabbitmq` | | | | | PID, E/S, comunicación |
| `zookeeper` | `java ... QuorumPeerMain` | | `zookeeper` | | | | | PID, planificación |
| `kafka` | `java ... kafka.Kafka` | | `kafka` | | | | | PID, E/S (log de particiones) |
| `nginx` (despliegue) | `nginx` (master + workers) | | `root`/`www-data` | | | | | PID, planificación por workers |

---

## 5. Comandos reales del gestor de procesos

### 5.1 Ver procesos, jerarquía y consumo

```bash
# Todos los procesos con usuario, estado y consumo
ps aux

# Árbol de procesos (padres → hijos): muestra master/worker de Nginx, la JVM, etc. (-p = incluir PID)
pstree -p

# Top por consumo de CPU (las 10 filas más costosas + cabecera)
ps -eo pid,user,%cpu,%mem,etime,cmd --sort=-%cpu | head -11

# Top por consumo de memoria
ps --sort=-%mem -eo pid,user,%mem,%cpu,cmd | head -11

# Solo los procesos dentro de un contenedor (por nombre)
docker exec streamchat-app ps -eo pid,user,%cpu,%mem,etime,cmd
```

### 5.2 Monitor interactivo (estado, carga, cambios de contexto)

```bash
# top: columna S (estado: R=ejecución, S=bloqueado por E/S, D=espera de disco, Z=zombi)
top -o %CPU
# Dentro de top: P -> ordenar por CPU; H -> ver hilos (threads); M -> ordenar por memoria

# htop: vista de árbol y de hilos (F2 configura columnas)
htop

# Ver hilos dentro del proceso (top -H, o ps con -L)
top -H -p $(pgrep -f 'app.jar' | head -1)
ps -eLf | grep -E 'java|beam' | head -20
```

### 5.3 Planificación: prioridad y política

```bash
# Ver la política de planificación y la prioridad en tiempo real (SCHED_FIFO/RR/BATCH/OTHER)
chrt -p $(pgrep -f 'app.jar' | head -1)

# Ver nice (ni) y prioridad estática (pri) de todos los procesos, ordenado por nice
ps -eo comm,pid,ni,pri --sort=-ni

# Cambiar el "nice" de un proceso que ya corre (menor niceness = más prioridad; -20..19)
sudo renice -n -5 -p PID

# Lanzar un proceso con otra prioridad (20 = menor prioridad)
nice -n 10 comando

# Poner el proceso de presencia en política de tiempo real (SCHED_RR, prioridad 10)
sudo chrt -r -p 10 PID
```

### 5.4 Afinidad de CPU (fijar núcleos)

```bash
# Ver en qué CPU(s) puede correr un proceso y su máscara de afinidad
taskset -p PID

# Fijar un proceso en ejecución a los núcleos 0 y 1
taskset -c 0,1 -p PID

# Lanzar un proceso fijado a los núcleos 0 y 1
taskset -c 0,1 comando
```

### 5.5 Gestión de recursos: matar y observar la recuperación

```bash
# SIGTERM: petición de terminación ordenada
kill -15 PID
# SIGKILL: terminación forzosa (el proceso no puede capturarla)
kill -9 PID

# Con Docker: matar y comprobar que el proceso se reinicia solo (restart: unless-stopped)
docker compose ps
docker kill --signal=SIGKILL streamchat-app
sleep 3 && docker compose ps        # debe volver a 'Up' / 'healthy'
```

---

## 6. Diagrama de estados del hilo consumidor

El hilo que consume eventos pasa por los estados clásicos del gestor de procesos:

```text
        llega un mensaje
   ┌───────────────────────────────────────────────┐
   │                                               ▼
[Nuevo] ──crear──► [Preparado] ──planifica──► [Ejecución] ──procesa el evento──► [Terminado]
                        ▲                          │
                        │                          │ llama a Postgres / RabbitMQ
                        │                          ▼ (espera de E/S)
                        └───── E/S completa ── [Bloqueado]
```

- **Nuevo:** el hilo se crea para atender el mensaje.
- **Preparado:** está listo pero el planificador no le ha dado la CPU.
- **Ejecución:** procesa el evento (idempotencia + notificación WebSocket).
- **Bloqueado:** espera a que termine una E/S (consulta a PostgreSQL, ACK al broker).
- **Terminado:** libera recursos y vuelve al pool.

---

## 7. Experimento E1 — Planificación bajo carga de CPU

**Objetivo:** medir cómo cambia la **latencia de entrega de `TYPING_UPDATE`** (percentil 95) cuando la CPU está
saturada, y comprobar el efecto de cambiar prioridad y afinidad.

### 7.1 Qué se mide

La métrica ya instrumentada en el código es un `Timer` de Micrometer expuesto en el endpoint de actuator:

```java
// src/main/java/com/streamchat/presence/infrastructure/metrics/MetricsProcessActivityUseCase.java
Timer.builder("presence.typing.update.latency")
     .description("Latencia de procesamiento de eventos de actividad (occurredAt -> procesado)")
     .register(registry);
```

Se consulta en `http://localhost:8080/actuator/metrics/presence.typing.update.latency` (incluye `count`,
`sum` y, si se habilita la publicación de percentiles, `max`/`p95`).

### 7.2 Procedimiento (resumen; detalle completo en `experimentos.md`)

```bash
# 1) Línea base: sin carga, generar tráfico de typing y leer la métrica
curl -s localhost:8080/actuator/metrics/presence.typing.update.latency | jq

# 2) Saturadores de CPU (uno por núcleo, en segundo plano)
for i in 1 2 3 4; do yes > /dev/null & done

# 3) Bajo carga, volver a generar tráfico y medir la latencia p95
curl -s localhost:8080/actuator/metrics/presence.typing.update.latency | jq

# 4) Cambiar la prioridad del proceso de presencia y repetir
sudo chrt -r -p 10 $(pgrep -f 'app.jar' | head -1)

# 5) Fijar afinidad a núcleos libres (0 y 1) y repetir
taskset -c 0,1 -p $(pgrep -f 'app.jar' | head -1)

# 6) Liberar la CPU
kill %1 %2 %3 %4
```

### 7.3 Resultado esperado

- **Línea base:** latencia p95 baja (dominada por el *fixedDelay* del barrido de 2 s y el camino Kafka → WebSocket).
- **Con CPU saturada:** la latencia sube, porque el planificador reparte el *time slice* entre más procesos
  ejecutables y el hilo de procesamiento espera más tiempo en estado **preparado**.
- **Con mayor prioridad / tiempo real (`chrt -r`):** la latencia vuelve a bajar, porque el hilo recibe CPU antes.
- **Con afinidad a núcleos libres:** la latencia mejora, porque el hilo no compite con los saturators.

### 7.4 Tabla de resultados (rellenar al ejecutar)

> **Nota:** se entrega en blanco. Se rellena con la salida real del endpoint para cada escenario.

| Escenario | Prioridad / política | Afinidad | Latencia p95 de `TYPING_UPDATE` (ms) | Observaciones |
|---|---|---|---|---|
| Línea base (sin carga) | `SCHED_OTHER`, nice 0 | todas | | |
| CPU saturada | `SCHED_OTHER`, nice 0 | todas | | |
| CPU saturada + prioridad alta | `SCHED_RR` prio 10 | todas | | |
| CPU saturada + afinidad | `SCHED_OTHER` | CPU 0,1 | | |

---

## 8. Algoritmos de planificación y su relación con E1

El kernel de Linux usa por defecto **CFS / EEVDF** (*Completely Fair Scheduler*), que **no es** FCFS ni Round Robin
puro, sino un planificador de **reparto equitativo ponderado por prioridad**:

| Algoritmo | Idea | Relación con E1 |
|---|---|---|
| **FCFS** | Atiende por orden de llegada | No es el que usa Linux para procesos interactivos |
| **SJN** | Primero el trabajo más corto | Requiere conocer la duración; no se usa en tiempo real |
| **Round Robin** | Reparte *time slices* por turnos | Base conceptual del reparto equitativo de CFS |
| **Prioridades** | El de mayor prioridad corre antes | Se activa con `nice`/`renice` (mayor prioridad = menor *niceness*) |
| **Tiempo real** | `SCHED_FIFO`/`SCHED_RR` adelantan al resto | Se activa con `chrt -r -p 10 PID` (paso 4 de E1) |

**Explicación para la sustentación:** al saturar la CPU, el hilo de presencia compite con los `yes`; CFS le da una
**cuota ponderada** del tiempo de CPU. Con `chrt -r` se cambia a la clase de **tiempo real**, que tiene prioridad
sobre CFS, y con `taskset` se reduce la **competencia** limitándolo a núcleos libres. Por eso la latencia mejora.

---

## 9. Evidencias a capturar

1. Salida de `ps aux`, `pstree -p` y de la tabla "servicio → PID → usuario → estado → consumo".
2. Captura de `top`/`htop` mostrando la columna de estado durante la carga.
3. Salida de `chrt -p PID` antes y después del cambio de política.
4. Salida de `taskset -p PID` antes y después de fijar afinidad.
5. La métrica `presence.typing.update.latency` en los cuatro escenarios de E1.
6. Captura de `docker compose ps` antes y después de `kill -9` a un servicio, con el tiempo de recuperación.
