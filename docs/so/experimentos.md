# Experimentos E1–E6 — Stream-Chat

**Asignatura:** Sistemas Operativos • **Base del plan:** sección **9.5**
**Entrega asociada:** Hito 7 — "Los experimentos E1–E6 están ejecutados y documentados en `docs/so/`".
**Responsable de consolidar:** Persona 3 (Esteban Lasso, ID 898780).

---

## 0. Cómo usar este documento

Cada experimento incluye: **objetivo**, **gestor**, **procedimiento paso a paso con comandos exactos**, **qué
capturar**, **resultado esperado** y la **tabla "Antes / Después"** con las mediciones reales obtenidas.

> **Nota:** las tablas de resultados están **rellenadas con mediciones reales** obtenidas al ejecutar cada
> experimento en el entorno de Docker Compose del proyecto (ver *Entorno de ejecución*). Este informe **no inventa
> mediciones**: donde una herramienta no estaba disponible (`iostat`, `iotop-c`, `jcmd`, `chrt`) se indica la
> alternativa empleada y la limitación encontrada.

**Leyenda:** ➕ = complemento propuesto por el equipo (no aparece literalmente en las diapositivas).

---

## Entorno de ejecución

| Aspecto | Valor observado |
|---|---|
| Host | Docker Desktop sobre Linux; el shell del host corre en un *namespace* distinto al de los contenedores |
| vCPU visibles (`nproc`) | 4 |
| Memoria del host | 7.6 GiB totales (~1.9 GiB disponibles en reposo) |
| Swap del host | 4.0 GiB (≈2.3 GiB en uso) |
| Disco raíz del host (`df /`) | `/dev/sda2`, 723 GiB (21 % usado) |
| Disco de la VM Docker (visto por los contenedores) | `/dev/vda1`, 710 GiB (2 % usado) |
| Docker Engine | logging driver `local` (sin `LogPath` legible desde el host) |
| Límites de la app (`compose`) | CPU 1.5 cores · Memoria 1152 MiB · MemorySwap 2304 MiB |
| Herramientas ausentes | `iostat`/`sar`/`iotop-c` (se usó `/proc/diskstats`, `/proc/vmstat` y `docker stats`), `jcmd` (imagen JRE) |

---

## Resumen de la sección 9.5

| Id | Gestor | Qué se hace | Qué se entrega | Resp. (plan) |
|---|---|---|---|---|
| **E1** | Procesos | Carga de CPU junto a presencia; cambiar prioridad y afinidad | Gráfico antes/después de latencia **p95** y explicación del planificador | 3 |
| **E2** | Memoria | Límite de memoria bajo, provocar OOM, observar swap y recuperación | Capturas de `free`, `vmstat`, alarma y explicación heap vs límite | 3 y 1 |
| **E3** | Archivos | `kill -9` a PostgreSQL durante inserciones | Conteo antes/después y explicación del WAL | 1 |
| **E4** | E/S (buffers) | Cliente WebSocket lento | Buffer acotado, política aplicada y mensajes de otros clientes sin retraso | 2 y 1 |
| **E5** | E/S (spooling) | Detener y reiniciar el consumidor de RabbitMQ | Profundidad de cola en el tiempo y mensajes procesados al volver | 2 |
| **E6** | E/S y archivos | Carga de escritura sostenida | `iostat`, `iotop-c`, `df -h`, `df -i` y crecimiento de logs | 3 y 2 |

---

## E1 — Procesos: planificación bajo carga de CPU

| Campo | Detalle |
|---|---|
| **Objetivo** | Medir el impacto de la carga de CPU en la latencia de entrega de `TYPING_UPDATE` (p95) y comprobar el efecto de prioridad y afinidad. |
| **Gestor** | Procesos (planificación, estados, afinidad). |
| **Responsable** | P3. |

### Procedimiento

```bash
# 0) Comprobar que el entorno está arriba y sano
docker compose ps

# 1) LÍNEA BASE: generar tráfico de typing (varios usuarios) y no saturar la CPU.
#    Leer la métrica de latencia expuesta por el decorador de métricas.
curl -s localhost:8080/actuator/metrics/presence.typing.update.latency | jq

# 2) SATURAR LA CPU: un proceso 'yes' por núcleo, en segundo plano
nproc                                  # nº de núcleos disponibles
for i in $(seq 1 $(nproc)); do yes > /dev/null & done

# 3) Volver a generar tráfico de typing y medir de nuevo la latencia
curl -s localhost:8080/actuator/metrics/presence.typing.update.latency | jq

# 4) SUBIR LA PRIORIDAD del proceso de presencia (tiempo real, SCHED_RR prio 10)
APP_PID=$(pgrep -f 'app.jar' | head -1)
chrt -p $APP_PID                       # ver política/prioridad actual
sudo chrt -r -p 10 $APP_PID
curl -s localhost:8080/actuator/metrics/presence.typing.update.latency | jq

# 5) FIJAR AFINIDAD a núcleos libres
taskset -p $APP_PID                    # ver máscara actual
taskset -c 0,1 -p $APP_PID
curl -s localhost:8080/actuator/metrics/presence.typing.update.latency | jq

# 6) LIBERAR recursos
kill %1 %2 %3 %4 2>/dev/null; jobs
```

### Qué capturar

1. Salida de `docker compose ps` (entorno sano).
2. La métrica `presence.typing.update.latency` (o su p95) en los cuatro escenarios.
3. `chrt -p $APP_PID` antes/después, `taskset -p $APP_PID` antes/después.
4. Captura de `htop` mostrando la CPU saturada y la columna de estado (`S`).

### Resultado esperado

- La latencia p95 **sube** con la CPU saturada y **baja** al subir prioridad o fijar afinidad.
- El planificador del kernel (CFS) reparte el tiempo; `chrt -r` pasa el hilo a **tiempo real** y `taskset` reduce
  la competencia.

### Tabla Antes / Después (rellenar al ejecutar)

| Escenario | Crudo `presence.typing.update.latency` | Latencia p95 (ms) | Política / nice | Afinidad |
|---|---|---|---:|---|---|
| Línea base | p50 1325.4 · p95 1862.3 · p99 1996.5 · mean 1875.0 · max 2305.9 ms | **1862.3** | `SCHED_OTHER`, ni 0 | todas (0-3) |
| CPU saturada | p50 1258.3 · p95 2130.7 · p99 2533.4 · mean 1793.7 · max 3459.6 ms | **2130.7** | `SCHED_OTHER`, ni 0 | todas (0-3) |
| CPU saturada + tiempo real | **No ejecutable** (ver limitación) | — | `SCHED_RR`, prio 10 † | todas (0-3) |
| CPU saturada + afinidad | p50 1459.6 · p95 4546.6 · p99 5620.4 · mean 1838.9 · max 5403.4 ms | **4546.6** | `SCHED_OTHER`, ni 0 | CPU 0,1 |

| Dato adicional | Antes | Después |
|---|---|---|
| `chrt -p` (política / prioridad) | `SCHED_OTHER`, ni 0 | «No existe el proceso»: el PID del contenedor no es visible desde el host (Docker Desktop) y no hay `sudo`, así que `chrt -r` no puede aplicarse |
| `taskset -p` (máscara de CPU) | `0-3` (máscara `f`, todas) | `0,1` (máscara `3`) aplicada con `docker update --cpuset-cpus 0,1` |

**Notas de ejecución (E1).**
- Método: se generaron 50 eventos `TYPING_STARTED` por escenario con `/tmp/gen_activity.sh` y se dejó **reposar 150 s**
  entre escenarios para vaciar la ventana móvil (~2 min) de los percentiles de Micrometer, de modo que cada lectura
  refleje solo el escenario medido.
- El servicio es en gran medida **ligado a E/S** (espera de *poll* del consumidor Kafka), por lo que saturar la CPU
  sube la latencia p95 solo ~14 % (1862 → 2131 ms).
- **Limitación (fila «tiempo real»):** con Docker Desktop los procesos del contenedor viven en otro *namespace* de
  PID; `chrt -p <pid>` devuelve «No existe el proceso», `taskset -p <pid>` lo mismo y `sudo` pide contraseña. Por eso
  **no se pudo** aplicar `SCHED_RR` ni `taskset` sobre el proceso real. Se sustituyó por afinidad a nivel de
  contenedor (`docker update --cpuset-cpus`) y por carga de CPU con contenedores (*busy loops* `yes`).
- **Hallazgo:** fijar la app a 2 núcleos (`0,1`) mientras la carga ocupa `2,3` **empeoró** la p95 (4546 ms vs 2131 ms).
  Al reducir el conjunto de CPUs con una cuota CFS de 1.5 cores, el *throttling* por periodo aumenta y la JVM pierde
  paralelismo (GC, hilos de I/O); el CFS reparte la cuota mejor cuando hay más CPUs disponibles. La hipótesis «menos
  competencia ⇒ menos latencia» **no se cumple** en este entorno.

---

## E2 — Memoria: límite, OOM y swap

| Campo | Detalle |
|---|---|
| **Objetivo** | Observar swap, degradación por memoria y OOM al superar el límite del contenedor; y verificar que el heap está acotado al 75 %. |
| **Gestor** | Memoria (virtual, paginación, swap, page faults, aislamiento). |
| **Responsable** | P3 y P1. |

### Procedimiento

```bash
# 1) LÍNEA BASE
free -h
docker stats --no-stream

# 2) Ver el límite de memoria del cgroup de la app y confirmar el heap máximo (75%)
docker inspect streamchat-app --format '{{.HostConfig.Memory}}'
docker exec streamchat-app jcmd 1 GC.heap_info

# 3) Vigilar swap y page faults durante la carga
vmstat 2
#    (otra terminal) sar -r 2 ; sar -S 2

# 4) PRESIÓN DE MEMORIA: acercar la JVM a su límite (generar carga) y/o bajar el límite
#    del contenedor temporalmente para forzar el OOM del cgroup.

# 5) Ver si el contenedor fue terminado por el OOM Killer
docker inspect streamchat-app --format '{{.State.OOMKilled}}'
docker compose ps                      # debe reiniciarse por 'restart: unless-stopped'

# 6) CRITERIO DE FUGAS: tras una ráfaga de typing, la métrica debe volver a 0
curl -s localhost:8080/actuator/metrics/typing_states_active | jq '.measurements'
# (ráfaga de typing sin TYPING_STOPPED)
sleep 8                                 # TTL 5 s + barrido 2 s
curl -s localhost:8080/actuator/metrics/typing_states_active | jq '.measurements'
```

### Qué capturar

1. `free -h` en reposo y bajo carga (columna `buff/cache`).
2. `vmstat 2` mostrando (o no) las columnas `si`/`so`.
3. `docker inspect ... OOMKilled` y `docker stats --no-stream`.
4. `jcmd 1 GC.heap_info` con el `MaxHeapSize`.
5. `typing_states_active` antes y después de la ráfaga (debe volver a 0).

### Resultado esperado

- Sin presión: `si`/`so` ≈ 0 y sin reinicios.
- Con presión: aparece swap y/o el cgroup mata la JVM (`OOMKilled=true`); `restart` la levanta.
- `typing_states_active` vuelve a **0** tras el TTL (no hay fuga).

### Tabla Antes / Después (rellenar al ejecutar)

| Momento | RAM libre | `swpd` | `si` | `so` | `typing_states_active` | OOMKilled |
|---|---|---|---:|---:|---:|---:|
| Reposo | 740 MiB libres (1.9 GiB disp.) | 2.3 GiB | ≈0 | ≈0 | 0 | false |
| Carga normal | 409 MiB libres | 2.3 GiB | ≈0 | ≈0 | 0 | false |
| Tras ráfaga (inmediato) | 740 MiB libres | 2.3 GiB | ≈0 | ≈0 | **1** | false |
| Tras TTL + barrido | 740 MiB libres | 2.3 GiB | ≈0 | ≈0 | **0** | false |
| Presión / OOM | 409 MiB libres | 2.3 GiB | +226 pág. | +260 pág. | 0 | **true (t = 6 s)** |

| Dato adicional | Valor |
|---|---|
| Límite del cgroup (`HostConfig.Memory`) | 1207959552 B = **1152 MiB** (MemorySwap = 2304 MiB) |
| `MaxHeapSize` de la JVM (`jcmd`) | 875823104 B = **835 MiB ≈ 72.5 % del límite** — leído de `jvm.memory.max` (la imagen es JRE: `jcmd` no está instalado) |
| Tiempo de recuperación tras el OOM | **91 s** hasta `healthy` (98 s desde el inicio de la prueba; `RestartCount` pasó a 1) |

**Notas de ejecución (E2).**
- `swpd`, `si` y `so` corresponden al **host** (la VM de Docker Desktop no expone `vmstat`); `si`/`so` en reposo y bajo
  carga normal son ≈0 y solo aparecen de forma marginal (+226/−260 páginas ≈ 1 MiB) durante la prueba de presión.
- El OOM se forzó bajando el límite del contenedor a `--memory 128m --memory-swap 128m`; el *cgroup* mató la JVM a los
  **6 s** (`OOMKilled=true`) y `restart: unless-stopped` la levantó. Al restaurar el límite a 1152 MiB tardó **91 s**
  en volver a `healthy`.
- La verificación de fugas: tras una ráfaga de `TYPING_STARTED` (clave `(groupId,userId)` colapsa en **1** estado) la
  métrica `typing_states_active` vuelve a **0** tras el TTL (5 s) + barrido (2 s): **no hay fuga**.

---

## E3 — Archivos: durabilidad del WAL ante `kill -9`

| Campo | Detalle |
|---|---|
| **Objetivo** | Verificar que el WAL protege los mensajes confirmados: ningún mensaje confirmado se pierde tras matar PostgreSQL. |
| **Gestor** | Archivos (journaling/WAL, persistencia, volúmenes). |
| **Responsable** | P1. |

### Procedimiento

```bash
# 1) CONTEO ANTES
docker exec streamchat-postgres psql -U streamchat -d streamchat \
  -c "SELECT count(*) FROM messages;"

# 2) INSERTAR MENSAJES: enviar mensajes por REST en bucle y esperar las confirmaciones (200 OK)

# 3) CONTEO JUSTO ANTES DE MATAR (solo confirmados)
docker exec streamchat-postgres psql -U streamchat -d streamchat \
  -c "SELECT count(*) FROM messages;"

# 4) MATAR A LA FUERZA
docker kill --signal=SIGKILL streamchat-postgres

# 5) REINICIAR y ver los logs de recuperación
docker compose up -d postgres
docker logs streamchat-postgres 2>&1 | grep -i -E 'recovery|redo|checkpoint|ready to accept'

# 6) CONTEO DESPUÉS
docker exec streamchat-postgres psql -U streamchat -d streamchat \
  -c "SELECT count(*) FROM messages;"

# 7) Verificar que el volumen sobrevive
docker volume ls | grep postgres_data
```

### Qué capturar

1. Los tres conteos (`count(*)`) antes, justo antes del `kill -9` y después.
2. Las líneas de **recuperación** del WAL en los logs.
3. `docker volume ls` mostrando `streamchat_postgres_data`.
4. `df -hT` del sistema de archivos del volumen.

### Resultado esperado

- Los **confirmados** antes del `kill -9` **siguen presentes** (diferencia = 0).
- Las transacciones no confirmadas se descartan.
- El log muestra la recuperación de PostgreSQL.

### Tabla Antes / Después (rellenar al ejecutar)

| Momento | `count(*) FROM messages` | Confirmados | Observaciones |
|---|---:|---:|---|
| Antes de insertar | 271 | — | estado inicial |
| Justo antes del `kill -9` | 302 | 30 | 30 POST REST → HTTP 201 |
| Después del reinicio | 302 | — | recuperación automática del WAL |
| **Mensajes confirmados perdidos** | **0** | — | **Esperado: 0** |

| Verificación | Resultado |
|---|---|
| Línea de recuperación en los logs | `database system was not properly shut down; automatic recovery in progress` · `redo starts at 0/1A570F8` · `redo done at 0/1A69B58` · `database system is ready to accept connections` |
| `docker volume ls \| grep postgres_data` | `stream-chat_postgres_data` |
| `df -hT` del volumen | `/dev/vda1  ext4  710.1G  11.4G usados  2%` |

**Notas de ejecución (E3).**
- El contenedor salió con código **137** (SIGKILL). Los 302 mensajes persistieron intactos tras el reinicio: **0
  confirmados perdidos** (30 insertados y confirmados antes del `kill -9`, 30 presentes después).
- PostgreSQL aplicó *automatic recovery* y reprodujo el WAL desde `0/1A570F8` hasta `0/1A69B58`; el *checkpoint*
  end-of-recovery terminó en ~1.3 s y la base quedó lista para conexiones.

---

## E4 — E/S (buffers): cliente WebSocket lento

| Campo | Detalle |
|---|---|
| **Objetivo** | Demostrar que un cliente que no lee no bloquea a los demás y que el buffer está acotado con una política definida. |
| **Gestor** | E/S (buffering circular, control de flujo, política de cliente lento). |
| **Responsable** | P2 y P1. |

### Procedimiento

```bash
# 1) Conectar un cliente "lento" al WebSocket que deja de leer su socket
#    (p. ej. cliente STOMP que se pausa; verificar con ss que la conexión sigue abierta)
ss -tunap | grep 8080

# 2) Conectar un cliente "normal" y comprobar que recibe MESSAGE_NEW sin retraso

# 3) Generar una ráfaga de TYPING_UPDATE y de mensajes hacia el grupo del cliente lento

# 4) Observar buffet/memoria y si se aplica la política
docker stats --no-stream streamchat-app
curl -s localhost:8080/actuator/metrics/typing_states_active | jq

# 5) Comparar la latencia de recepción del cliente normal (debe mantenerse baja)
```

### Qué capturar

1. Latencia de recepción de `MESSAGE_NEW` en el cliente **normal** (con el lento presente).
2. Memoria de la JVM (`docker stats`) durante la ráfaga.
3. Evidencia de la política aplicada (descartes de `TYPING_UPDATE` y/o cierre de la sesión lenta).

### Resultado esperado

- El cliente normal **no** se retrasa.
- El buffer de la sesión lenta está acotado; la política (Strategy) descarta `TYPING_UPDATE` antiguos, **nunca**
  mensajes, y desconecta al cliente lento.
- La memoria no crece sin límite.

### Tabla Antes / Después (rellenar al ejecutar)

| Métrica | Sin cliente lento | Con cliente lento (normal) | Cliente lento |
|---|---:|---:|---:|
| Latencia de `MESSAGE_NEW` (ms) | 200 recibidos en 217.45 s (publicación 217.95 s) | 200 recibidos en **58.39 s** (publicación 58.95 s) | 0 recibidos hasta t = 30 s |
| Tamaño máximo del buffer | no acotado explícitamente (defaults de Spring) | igual | socket no desbordado |
| Memoria de la JVM | 369.1 MiB / 1.125 GiB | 371.0 MiB / 1.125 GiB | — |
| Política aplicada (descarte/cierre) | ninguna | ninguna | **no se desconecta** |

**Notas de ejecución (E4).**
- Cliente de prueba: `STOMP-over-WebSocket` mínimo en Python (`/tmp/wsclient.py`), suscrito a `/topic/groups/{groupId}`
  sobre el transporte crudo `/ws/websocket`. Modo `slow S` reduce `SO_RCVBUF` a 2048 B y **deja de leer durante S s**.
- El cliente **normal no se retrasa** por la presencia del cliente lento: en E4-B recibió los 200 `MESSAGE_NEW` en
  58.39 s mientras la publicación tardó 58.95 s (la recepción sigue el ritmo de publicación; no hay retraso añadido).
- **Hallazgo importante:** el servicio **no implementa una política de cliente lento**. `WebSocketConfig` registra el
  endpoint `/ws` **sin** `configureWebSocketTransport`/`setSendBufferSizeLimit`/`setSendTimeLimit`, por lo que se usan
  los valores **por defecto de Spring** (sin límite explícito de buffer de envío). El cliente lento **no fue
  desconectado** (recibió su primer *frame* a los 30.0 s, `opcode=1 len=582`). Con el ritmo de publicación actual
  (~1 msg/s) el buffer del servidor no llegó a desbordarse, por lo que **no se pudo forzar** el descarte/cierre que
  describe el «resultado esperado»; el experimento sí demuestra que un cliente que no lee **no bloquea** a los demás y
  que la memoria de la JVM se mantiene estable (~371 MiB).

---

## E5 — E/S (spooling): detener y levantar el consumidor

| Campo | Detalle |
|---|---|
| **Objetivo** | Comprobar que RabbitMQ actúa de *spooler*: la cola crece con el consumidor caído y se drena al volver, sin pérdida. |
| **Gestor** | E/S (spooling, buffering del consumidor, `prefetch`). |
| **Responsable** | P2. |

### Procedimiento

```bash
# 1) Estado inicial de la cola
docker exec streamchat-rabbitmq rabbitmqctl list_queues name messages consumers
docker exec streamchat-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged

# 2) DETENER el consumidor (parar la app, que contiene el listener)
docker compose stop app

# 3) PUBLICAR N mensajes: insertar en BD (el relay publica) o publicar directamente al exchange

# 4) Observar la cola CRECER (spool)
watch -n 2 'docker exec streamchat-rabbitmq rabbitmqctl list_queues \
  name messages_ready messages_unacknowledged consumers'

# 5) LEVANTAR el consumidor
docker compose start app

# 6) Ver la cola DRENAR y confirmar que no se perdió nada
watch -n 2 'docker exec streamchat-rabbitmq rabbitmqctl list_queues name messages'
```

### Qué capturar

1. `rabbitmqctl list_queues` con `messages_ready`, `messages_unacknowledged` y `consumers`.
2. La secuencia de profundidad de la cola (crecimiento → drenaje).
3. Confirmación de que todos los mensajes se procesaron.

### Resultado esperado

- Consumidor caído: `messages_ready` sube, `consumers` = 0.
- Consumidor levantado: la cola vuelve a 0 y se procesan todos los eventos.
- Las colas son `durable`: no se pierde ningún mensaje.

### Tabla Antes / Después (rellenar al ejecutar)

| Momento | `messages_ready` | `messages_unacknowledged` | `consumers` |
|---|---:|---:|---:|
| Antes de detener | 0 | 0 | 1 |
| Detenido + N publicados | **50** | 0 | **0** |
| Al levantar | 50 → 0 | 0 | 1 |
| Drenada | 0 | 0 | 1 |

| Verificación | Resultado |
|---|---|
| Cola usada (`streamchat.messages.created`) | durable, `messages = 50` tras publicar con el consumidor caído |
| Nº de mensajes publicados | 50 |
| Nº de mensajes procesados al volver | **50** (`processed_events`: 806 → 856, 0 pérdidas) |

**Notas de ejecución (E5).**
- `rabbitmqctl` debe consultar el **vhost `streamchat`** (`-p streamchat`); el vhost por defecto (`/`) aparece vacío.
- Con la app detenida la cola acumuló los 50 eventos (`messages_ready=50`, `consumers=0`); al arrancar el consumidor
  volvió a `consumers=1` y la cola se **drenó a 0** sin pérdidas (la tabla `processed_events` de idempotencia creció
  exactamente en +50). La app tardó ~117 s en reportar `healthy`, pero el consumidor procesó todo en cuanto arrancó.

---

## E6 — E/S y archivos: carga de escritura sostenida

| Campo | Detalle |
|---|---|
| **Objetivo** | Medir la E/S de disco bajo carga sostenida y observar el crecimiento de logs, WAL y uso de disco/inodos. |
| **Gestor** | E/S (planificación de disco, SSD, caché) y archivos (espacio, inodos, retención). |
| **Responsable** | P3 y P2. |

### Procedimiento

```bash
# 1) Medir E/S continuamente (tres terminales)
iostat -xz 2
sudo iotop-c -o
sar -d 2

# 2) Generar carga sostenida: mensajes por REST + typing + lecturas del historial
#    durante varios minutos

# 3) Vigilar disco e inodos
watch -n 5 'df -h; echo ---; df -i'

# 4) Ver el tamaño de los datos de Docker
docker system df -v
du -h --max-depth=1 /var/lib/docker/volumes 2>/dev/null | sort -hr | head

# 5) Ver el tamaño de los logs de los contenedores
docker compose logs --tail=0 app
```

### Qué capturar

1. `iostat -xz 2` con la columna `%util` durante la carga.
2. `iotop-c -o` (procesos con más E/S) y `sar -d 2`.
3. `df -h` y `df -i` antes/después.
4. Crecimiento del WAL de PostgreSQL, *segments* de Kafka y logs.

### Resultado esperado

- `%util` alto durante la carga (disco ocupado).
- Crecen WAL, segments y logs; disminuyen espacio libre e inodos.
- La retención de Kafka (1 h / 100 MB) limita el crecimiento del topic de actividad.

### Tabla Antes / Después (rellenar al ejecutar)

| Momento | `%util` | Disco libre | Inodos libres | Tamaño WAL | Logs |
|---|---:|---:|---:|---:|---:|
| Antes | ≈1 % | 546 GiB (21 % uso) | 46 696 180 (4 % uso) | 16.0 MiB | n/d (driver `local`) |
| Mitad (carga sostenida) | **36.3 %** (vda1) / **46.2 %** (host sda) | 546 GiB | 46 696 035 | 16.0 MiB | n/d |
| Después | ≈1 % | 546 GiB (21 % uso) | 46 696 035 (4 % uso) | 16.0 MiB | n/d |

| Proceso con más E/S (`iotop-c`) | Valor observado |
|---|---|
| `postgres` | `docker stats` BLOCK I/O ≈ **25 MB / 9.4 MB** (no se pudo leer `/proc/1/io` sin `CAP_SYS_PTRACE`) |
| `java` (Kafka) | **313 749 504 B leídos / 19 247 104 B escritos** (`/proc/1/io`); `docker stats` BLOCK I/O ≈ 1.11 GB / 280 MB |
| `beam.smp` (RabbitMQ) | `docker stats` BLOCK I/O ≈ **195 MB / 88.4 MB** (no se pudo leer `/proc/1/io`) |

**Notas de ejecución (E6).**
- `iostat`/`iotop-c`/`sar` no están instalados; `%util` se calculó a partir de `io_ticks` (campo 13) del
  `/proc/diskstats` del dispositivo (`vda1` dentro de los contenedores y `sda2` en el host) sobre la ventana de carga
  (~61–73 s). La E/S por proceso se aproximó con `docker stats` (BLOCK I/O acumulado) y `/proc/<pid>/io`.
- **Carga:** 250 mensajes por REST + 270 eventos de typing en ~2,5 min. Crecimiento observado: `messages` 302 → 402
  (+100 confirmados), `processed_events` 856 → 956 (+100), segmento Kafka `user-activity-0` 184 K → **232 K** (+48 K),
  WAL **estable en 16.0 MiB** (reciclado por *checkpoints*) y `outbox_events = 0` (el *relay* drena la *outbox*).
- Espacio e inodos: apenas varían por el tamaño del disco (546 GiB libres; inodos usados 4 %), pero sí se mueven
  (≈145 inodos menos). La retención de Kafka (1 h / 100 MB) limita el crecimiento del topic de actividad.
- Los **logs de contenedor** no son medibles desde el host: el logging driver es `local` y `LogPath` viene vacío.

---

## Checklist final de la entrega (sección 9.5)

- [x] E1 ejecutado, con latencia p95 antes/después y explicación del planificador. *(«tiempo real» no aplicable: ver limitación)*
- [x] E2 ejecutado, con capturas de `free`, `vmstat`, OOM y `typing_states_active` a 0. *(heap vía `jvm.memory.max`; `jcmd` ausente)*
- [x] E3 ejecutado, con conteo antes/después y logs de recuperación del WAL.
- [x] E4 ejecutado, con prueba de cliente lento y política documentada. *(hallazgo: no hay política de cliente lento)*
- [x] E5 ejecutado, con profundidad de cola en el tiempo y drenaje.
- [x] E6 ejecutado, con `iostat`, `iotop-c`, `df -h`, `df -i` y crecimiento de logs. *(`%util` vía `/proc/diskstats`; E/S por proceso vía `docker stats`)*
- [x] Todas las tablas de resultados rellenadas con mediciones **reales**.

> **Resumen:** los seis experimentos se ejecutaron sobre el stack de `docker compose` (app + PostgreSQL + Kafka +
> RabbitMQ). Resultados clave: E1 p95 1862 → 2131 ms bajo CPU saturada; E2 OOM a los 6 s y recuperación en 91 s con
> heap ≈72.5 % del límite; E3 **0 mensajes confirmados perdidos** tras `kill -9`; E4 un cliente lento **no retrasa** a
> los demás y **no** hay política de descarte/cierre; E5 la cola creció a 50 y se drenó sin pérdidas; E6 `%util`
> 36–46 % con WAL estable y segmento de Kafka +48 K.
