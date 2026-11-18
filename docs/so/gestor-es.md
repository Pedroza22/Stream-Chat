# Gestor de E/S aplicado a Stream-Chat

**Asignatura:** Sistemas Operativos • **Base del plan:** sección **9.4** • **Experimentos:** **E4, E5, E6**
**Responsable:** Persona 2 (E/S: buffers y spooling) y Persona 3 (E/S y archivos, carga sostenida).

---

## 1. Idea central

El gestor de E/S del sistema operativo **media entre los procesos y los dispositivos** (disco, red, teclado,
tarjeta de red, impresora…). Sus mecanismos clave son: **buffering**, **spooling**, **caching**, **E/S asíncrona**,
**interrupciones**, **DMA** y **planificación de disco**. En Stream-Chat la E/S es casi toda **de red** (WebSocket,
RabbitMQ, Kafka, PostgreSQL) y **de disco** (WAL de PostgreSQL, *segments* de Kafka, logs).

---

## 2. Tabla base de la sección 9.4 (presentaciones → Stream-Chat → evidencia)

| Qué indican las presentaciones | Aplicación en Stream-Chat | Evidencia |
|---|---|---|
| **Buffering** simple, doble y circular (punteros `in` y `out`; el circular es el más indicado para streaming y comunicaciones de red) | La cola de salida de cada sesión WebSocket es un **buffer acotado**. **Experimento E4:** un cliente que no lee. Política (*Strategy*) al llenarse: descartar `TYPING_UPDATE` antiguos, **nunca** mensajes, y desconectar al cliente lento. El *prefetch* de RabbitMQ y `max.poll.records` de Kafka son el tamaño del buffer del consumidor. | Prueba de cliente lento; memoria acotada |
| **Spooling**: el proceso rápido deja el trabajo en una cola y el *spooler* lo atiende a su ritmo (ejemplo de la impresora) | **RabbitMQ** cumple el papel del *spooler*: el productor encola y sigue; el consumidor procesa a su ritmo. **Experimento E5:** detener el consumidor y observar la cola crecer y vaciarse al volver. | `rabbitmqctl list_queues` |
| **Caching** (acierto y fallo, *page cache*) | El SO guarda en RAM bloques leídos de disco; PostgreSQL y Kafka se benefician. Medir una lectura repetida del historial y mostrar la columna de caché. Caché de membresía de grupo opcional. | `free -h`, tiempos de consulta |
| E/S asíncrona, interrupciones, DMA y *polling* | WebSocket y consumidores no deben bloquear hilos de E/S. Se explica el recorrido: paquete llega a la tarjeta de red, interrupción, kernel, buffer del socket, JVM. `poll()` de Kafka es un ejemplo de sondeo a nivel de aplicación ➕. | Diagrama en `docs/so/` (sección 8) |
| Planificación de disco (FCFS, SSTF, SCAN, C-SCAN) y particularidades de SSD (wear leveling, recolección de basura, TRIM) | Sobre un volumen SSD (como **EBS**) no hay cabezal que mover, pero sí importan las escrituras secuenciales y el TRIM. | `iostat -xz 2`, `iotop-c`, `sar -d` |
| La red como dispositivo de E/S | Ver conexiones y tráfico durante la carga. | `ss -tunap`, `sar -n DEV 2` |
| Capas del subsistema de E/S: llamadas al sistema, software independiente del dispositivo, drivers, manejadores de interrupciones | Diagrama del camino de un `socket.write()` hasta la tarjeta de red, con cada capa nombrada. | Diagrama en `docs/so/` (sección 8) |

**Criterio:** un cliente lento no afecta a los demás ni hace crecer la memoria sin límite, y la cola de RabbitMQ se
recupera tras la caída del consumidor.

---

## 3. Buffering

### 3.1 Tipos de buffer

| Tipo | Punteros | Ventaja | Uso típico |
|---|---|---|---|
| **Simple** | — | Sencillo | Copiar datos en un único bloque |
| **Doble** | — | Permite escribir mientras se lee del otro bloque | *Double buffering* de E/S |
| **Circular** | `in` / `out` | **Ideal para streaming y red**: productor y consumidor avanzan sin reasignar | Cola de salida de una sesión WebSocket; cola de un broker |

### 3.2 El buffer de las sesiones WebSocket

Cada sesión WebSocket tiene una **cola de salida**. El servidor **escribe** en ella; la pila de red la **drena**
hacia el cliente. Si el cliente es más lento de lo que el servidor produce (**cliente lento**), la cola crece y,
si no se acota, **agota la memoria**.

**Decisión de diseño (sección 9.4 del plan):** el buffer por sesión debe ser **acotado**, con una **política**
(*Strategy*) al llenarse, aplicada en este orden de prioridad:

1. **Descartar `TYPING_UPDATE` antiguos** — la presencia es efímera y volverá a notificarse.
2. **NUNCA descartar mensajes** (`MESSAGE_NEW`) — perder un mensaje rompería la garantía del chat.
3. **Desconectar al cliente lento** — si aún así se llena, se cierra la sesión para proteger al resto.

Esta es la **política `SlowConsumerPolicy`** prevista en la sección 8.3 del plan. La implementación actual delega la
entrega en `SimpMessagingTemplate` de Spring (que internamente usa el *buffer* de WebSocket); el plan contempla el
decorador `ConcurrentWebSocketSessionDecorator` de Spring, que aporta un buffer de salida acotado y cierra la
sesión cuando se excede. El experimento **E4** verifica este comportamiento.

```java
// src/main/java/com/streamchat/chat/infrastructure/websocket/WebSocketClientNotifier.java
@Override
public void notifyGroup(GroupId groupId, ServerEvent event) {
    // Buffer de salida por sesión; el diseño exige acotarlo y aplicar SlowConsumerPolicy
    messagingTemplate.convertAndSend("/topic/groups/" + groupId.value(), event);
}
```

### 3.3 El tamaño del buffer del consumidor

| Broker | Parámetro | Valor en Stream-Chat | Significado |
|---|---|---|---|
| **RabbitMQ** | `prefetch` | **10** | Nº de mensajes sin ACK que el consumidor retiene a la vez |
| **Kafka** | `max.poll.records` | **50** | Nº de registros que `poll()` entrega por llamada |

```yaml
# application.yml — RabbitMQ
spring.rabbitmq.listener.simple:
  acknowledge-mode: manual     # ACK manual
  prefetch: 10                 # buffer del consumidor

# application.yml — Kafka
spring.kafka.consumer:
  enable-auto-commit: false    # commit manual después de procesar
  properties:
    max.poll.records: 50
```

El *prefetch* es un **control de flujo** (créditos): si el consumidor es lento, el broker le deja de enviar al
llegar a 10. Esto es *buffering* **del lado del broker** para no desbordar al consumidor.

---

## 4. Spooling con RabbitMQ

En el ejemplo clásico de la impresora, el **spooler** acepta trabajos rápido y los imprime a su ritmo **en disco**,
desacoplando al emisor (rápido) del dispositivo (lento). En Stream-Chat, **RabbitMQ actúa de spooler**:

1. El productor (`OutboxRelay`) publica el evento en el exchange `streamchat.events` y **sigue** (no espera a que
   los clientes lo reciban).
2. La cola `streamchat.messages.created` **almacena** los mensajes (durable).
3. El consumidor los procesa **a su ritmo**; si está caído, la cola crece y el trabajo **no se pierde**.

```text
# Topología real (RabbitMqTopologyConfig / RabbitMqProperties)
Publisher ──► [streamchat.events]  (topic)
                    │ routing key: message.created
                    ▼
              [streamchat.messages.created]  ── NACK ──► [streamchat.events.retry] (TTL 10 s)
                    ▲                                          │ TTL expirado
                    └──────────── reenruta ───────────────────┘
                                                  │ reintentos agotados
                                                  ▼
                                     [streamchat.messages.created.dlq]
```

Las colas de reintento con **TTL de 10 s** (`DEFAULT_RETRY_TTL_MS = 10_000`) evitan bucles inmediatos: es un
*spool* con **espera**, no un reintento a ciegas.

---

## 5. Caching (page cache)

El SO mantiene en RAM (*page cache*) los bloques recientemente leídos de disco. Una **lectura repetida** del
historial de mensajes probablemente no toque el disco: es un **acierto de caché**.

- **Acierto (hit):** el bloque está en RAM → latencia mínima.
- **Fallo (miss):** hay que ir al disco → *page fault* + E/S física.

```bash
# Ver cuánta RAM usa la page cache (columna 'buff/cache' de free)
free -h

# Ver actividad de caché/swap y page faults
vmstat 2
```

En Stream-Chat, además, se puede cachear la **membresía de un grupo** (comprobar si un usuario pertenece a él es
una consulta caliente). Es opcional (sección 9.4 del plan).

---

## 6. E/S asíncrona, interrupciones, DMA y polling

| Mecanismo | Qué es | En Stream-Chat |
|---|---|---|
| **Interrupción** | El dispositivo avisa a la CPU cuando tiene datos | La tarjeta de red interrumpe al llegar un paquete WebSocket |
| **Polling** | La CPU pregunta en bucle si hay datos | `poll()` de Kafka: la aplicación sondea el broker en busca de registros |
| **DMA** | El dispositivo transfiere a memoria **sin** la CPU | La NIC escribe el paquete directamente en el buffer del socket |
| **E/S asíncrona** | El proceso sigue trabajando mientras la E/S avanza | WebSocket y consumidores no bloquean sus hilos esperando la E/S |

**Regla de diseño:** la aplicación no debe bloquearse en E/S; por eso los listeners de RabbitMQ tienen un pool de
hilos y las sesiones WebSocket un buffer. El productor publica y sigue; el consumidor procesa y confirma.

---

## 7. Planificación de disco y SSD

| Algoritmo | Idea | Relevancia |
|---|---|---|
| **FCFS** | Atiende por orden de llegada | Referencia conceptual |
| **SSTF** | Primero el bloque más cercano al cabezal | Minimiza movimiento de cabeza (HDD) |
| **SCAN / C-SCAN** | Barrido tipo ascensor | Buen rendimiento secuencial (HDD) |
| **SSD** | **No hay cabezal** | Importan: **escrituras secuenciales**, **TRIM**, **wear leveling** y **recolección de basura** |

En un volumen **SSD** (como **EBS** de AWS) el "planificador de disco" clásico pierde sentido porque no hay
movimiento de cabeza. Pero sí importa:

- **Escribir de forma secuencial** (Kafka es *append-only*; PostgreSQL escribe el WAL al final).
- **TRIM**: el SO informa al SSD de bloques libres para que mantenga el rendimiento y reduzca el desgaste.
- **Wear leveling**: el firmware reparte las escrituras entre celdas para alargar la vida del dispositivo.

```bash
# Estadísticas extendidas de los discos (utilización, IOPS, colas) cada 2 s
iostat -xz 2

# Procesos ordenados por E/S (si iotop-c está instalado)
sudo iotop-c -o

# Actividad de disco por dispositivo (sysstat)
sar -d 2
```

---

## 8. Diagrama de capas: el camino de un `socket.write()` hasta la tarjeta de red

```text
   Aplicación (Java / Spring WebSocket)
   │  messagingTemplate.convertAndSend(...)  → serializa el ServerEvent a JSON
   ▼
   [1] Llamada al sistema:  write(fd, buf, len)   ← interfaz de sistema de archivos/socket
   ▼
   [2] Software independiente del dispositivo:  capa de sockets del kernel
   │      - coloca los datos en el buffer del socket (SND.BUF)
   │      - aplica el protocolo TCP/IP (segmenta, añade cabeceras)
   ▼
   [3] Driver de red (controlador del dispositivo):
   │      - encola el paquete en el buffer de la NIC
   ▼
   [4] Hardware (NIC) + DMA:
   │      - la NIC copia el paquete a la red SIN la CPU (DMA)
   ▼
   [5] Manejador de interrupciones:
          - la NIC interrumpe a la CPU al completar la transmisión
          - el kernel libera el buffer del socket
   ▼
   ▶ Red  →  cliente WebSocket
```

**Recepción (camino inverso):** paquete en la NIC → **interrupción** → driver lo sube a la pila TCP/IP → buffer de
recepción del socket (RCV.BUF) → la aplicación lo lee con `read()`/`poll()`.

---

## 9. La red como dispositivo de E/S

```bash
# Conexiones y sockets abiertos (procesos y puertos), incluyendo los de Docker
ss -tunap

# Solo los sockets del proceso de la JVM
ss -tunap | grep java

# Tráfico por interfaz de red cada 2 segundos (RX/TX en KB/s)
sar -n DEV 2

# Ver la topología de red de Compose y qué puertos quedan en localhost
docker network ls
docker compose ps --format 'table {{.Service}}\t{{.Ports}}'
```

**Nota de hardening (sección 10 del plan):** PostgreSQL (5433), RabbitMQ (5672/15672) y Kafka (9092) están
publicados **solo en `127.0.0.1`** (`"127.0.0.1:5433:5432"`), no en `0.0.0.0`. Así la red interna de Docker es,
para un atacante externo, un **dispositivo de E/S inalcanzable**.

---

## 10. Experimento E4 — Cliente WebSocket lento (buffers)

**Objetivo:** demostrar que un cliente que **no lee** su socket no bloquea a los demás y **no** hace crecer la
memoria sin límite; y documentar la política aplicada.

### 10.1 Procedimiento (resumen; detalle en `experimentos.md`)

```bash
# 1) Conectar un cliente "lento" que abre el WebSocket pero deja de leer su socket (p. ej. pausando el proceso).
# 2) Conectar un cliente "normal" y verificar que sigue recibiendo MESSAGE_NEW sin retraso.
# 3) Generar una ráfaga de TYPING_UPDATE y mensajes hacia el grupo del cliente lento.
# 4) Observar: (a) el buffer de la sesión lenta, (b) la memoria del proceso, (c) si la sesión se cierra.
docker stats --no-stream streamchat-app
curl -s localhost:8080/actuator/metrics/typing_states_active | jq
```

### 10.2 Resultado esperado

- El cliente lento **no** retrasa al cliente normal.
- El buffer de la sesión lenta está **acotado**; al llenarse se aplica la política (descartar `TYPING_UPDATE`,
  no descartar mensajes, desconectar al cliente lento).
- La memoria de la JVM **no crece** de forma indefinida.

### 10.3 Tabla de resultados (rellenar al ejecutar)

> **Nota:** se entrega en blanco. Se rellena con las mediciones reales.

| Métrica | Cliente lento | Cliente normal | Observaciones |
|---|---:|---:|---|
| Latencia de recepción de `MESSAGE_NEW` | | | |
| Tamaño máximo del buffer de salida | | | |
| Memoria de la JVM (`docker stats`) | | | |
| ¿Se aplicó la política (descartes/cierre)? | | | |

---

## 11. Experimento E5 — Spooling: detener y levantar el consumidor

**Objetivo:** comprobar que RabbitMQ actúa de **spooler**: la cola crece mientras el consumidor está caído y se
vacía cuando vuelve, sin perder mensajes.

### 11.1 Procedimiento (resumen; detalle en `experimentos.md`)

```bash
# 1) Detener el consumidor (parar la aplicación o el listener)
docker compose stop app

# 2) Publicar mensajes (insertar en la BD y dejar que el relay publique), o publicarlos directamente

# 3) Observar la profundidad de la cola crecer
docker exec streamchat-rabbitmq rabbitmqctl list_queues name messages consumers
docker exec streamchat-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged

# 4) Levantar el consumidor
docker compose start app

# 5) Ver la cola vaciarse y los mensajes procesados
docker exec streamchat-rabbitmq rabbitmqctl list_queues name messages
```

### 11.2 Resultado esperado

- Con el consumidor caído: `messages_ready` **sube** (trabajo en spool) y `consumers` = 0.
- Al levantar el consumidor: `messages_ready` **baja** hasta 0 y los eventos se procesan (notificación WebSocket).
- Ningún mensaje se pierde (las colas son `durable`).

### 11.3 Tabla de resultados (rellenar al ejecutar)

> **Nota:** se entrega en blanco. Se rellena con la salida real de `rabbitmqctl`.

| Momento | `messages_ready` | `messages_unacknowledged` | `consumers` |
|---|---:|---:|---:|
| Antes de detener el consumidor | | | |
| Consumidor detenido + N mensajes publicados | | | |
| Justo al levantar el consumidor | | | |
| Cola drenada | | | |

---

## 12. Experimento E6 — Carga de escritura sostenida

**Objetivo:** medir el comportamiento de **E/S de disco** bajo carga de escritura sostenida y observar el
crecimiento de logs y del WAL.

### 12.1 Procedimiento (resumen; detalle en `experimentos.md`)

```bash
# 1) En una terminal, medir E/S de disco continuamente
iostat -xz 2
#    (otra terminal) sudo iotop-c -o
#    (otra terminal) sar -d 2

# 2) Generar carga sostenida: enviar mensajes por REST, provocar typing,
#    y dejar que PostgreSQL escriba WAL y Kafka escriba segments.

# 3) Observar el crecimiento de datos y el uso de disco/inodos
watch -n 5 'df -h; echo ---; df -i'
du -h --max-depth=1 /var/lib/docker/volumes 2>/dev/null | sort -hr | head

# 4) Ver el tamaño de los logs de los contenedores
docker system df -v
```

### 12.2 Resultado esperado

- El disco tiene **utilización alta** (`%util` en `iostat`) durante la carga.
- Crecen el WAL de PostgreSQL y los *segments* de Kafka; los logs de la aplicación también crecen.
- El espacio libre (`df -h`) y los **inodos** (`df -i`) disminuyen: hay que vigilar ambos.
- La retención corta de Kafka (1 h / 100 MB) limita el crecimiento del topic de actividad.

### 12.3 Tabla de resultados (rellenar al ejecutar)

> **Nota:** se entrega en blanco. Se rellena con las mediciones reales.

| Momento | `%util` (`iostat`) | Disco libre (`df -h`) | Inodos libres (`df -i`) | Tamaño WAL | Crecimiento de logs |
|---|---:|---:|---:|---:|---:|
| Antes de la carga | | | | | |
| A mitad de la carga | | | | | |
| Al terminar | | | | | |

---

## 13. Evidencias a capturar

1. Captura de `rabbitmqctl list_queues` durante E5 (crecimiento y drenaje).
2. `iostat -xz 2`, `iotop-c -o` y `sar -d 2` durante E6.
3. `df -h`, `df -i` y `du -h` antes/después de la carga de E6.
4. `ss -tunap` y `sar -n DEV 2` durante E4/E5.
5. El diagrama del camino de `socket.write()` (sección 8) incluido en el informe.
6. La prueba de cliente lento de E4 con su tabla de resultados.
