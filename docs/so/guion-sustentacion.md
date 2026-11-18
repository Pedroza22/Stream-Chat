# Guion de sustentación — Informe de Sistemas Operativos (Stream-Chat)

**Asignatura:** Sistemas Operativos • **Expositor principal:** Esteban Lasso (ID 898780) — **Persona 3**
**Apoyo:** P1 (archivos y memoria) y P2 (E/S) en sus bloques.

> Este guion está pensado para exponer en vivo. Los bloques de "qué decir" son frases **cortas y defendibles**;
> la sección de comandos se puede ejecutar en el momento. Ajustar los tiempos según el límite de la presentación.

---

## 1. Introducción (≈ 2 minutos)

**Qué decir:**

1. "Stream-Chat es un chat de grupos en tiempo real construido con **Spring Boot 3.2.5 sobre Java 21**, con
   PostgreSQL, RabbitMQ, Kafka y **Docker Compose**."
2. "Para Sistemas Operativos no lo tratamos como una página web más: **lo miramos como lo ve el kernel** —un
   conjunto de procesos con su PID, su memoria, sus archivos abiertos y su E/S."
3. "Cada servicio del `compose.yaml` es un **proceso** dentro de su contenedor: la JVM de la aplicación,
   PostgreSQL, RabbitMQ, Zookeeper y Kafka. Sobre ellos actúan los cuatro gestores del sistema operativo."
4. "Nuestro informe sigue la sección 9 del plan: para cada gestor documentamos una **decisión de diseño**, una
   **medición** con herramientas reales y una **evidencia**, más **seis experimentos** (E1 a E6)."
5. "El objetivo es demostrar que sabemos **explicar y medir** el comportamiento del sistema operativo sobre
   nuestro propio proyecto, no sobre un ejemplo de laboratorio."

**Transición:** "Empecemos por el gestor que más toca a la Persona 3: el **gestor de procesos**."

---

## 2. Bloque 1 — Gestor de procesos (P3, ≈ 3 minutos)

**Qué decir:**

1. "Un **proceso** es un programa en ejecución más su **Bloque de Control de Proceso**: PID, estado,
   planificación, punteros a memoria, contexto, E/S y auditoría. Cada una de esas columnas la podemos **ver** con
   `ps`, `top` o `pstree` sobre nuestros contenedores."
2. "Los estados clásicos —nuevo, preparado, ejecución, bloqueado— se ven en la columna `S` de `htop`: cuando un
   hilo consumidor **espera un mensaje**, está **bloqueado**; cuando lo procesa, está en **ejecución**."
3. "La aplicación ejecuta **tareas programadas** dentro de su proceso: el **barrido de expiración de typing** cada
   **2 segundos** y el **relay de outbox** cada **5 segundos**. Son hilos que compiten por la CPU con el resto."
4. "El estado de escritura se guarda en un `ConcurrentHashMap` con operaciones **atómicas** (`merge`, `remove`) —no
   en un `Map` normal ni en un `Map` estático—, así dos usuarios escribiendo a la vez **no se pisan**: eso es
   **sincronización** de grano fino y evita condiciones de carrera."
5. "Cuando un proceso consume demasiado, el gestor puede **terminarlo y reiniciarlo**. En Compose tenemos
   `restart: unless-stopped` y *health checks*, y límites de CPU y memoria por contenedor."
6. "El **experimento E1** mide la **latencia p95 de `TYPING_UPDATE`** sin carga, con la CPU saturada, y mejorando
   **prioridad** (`chrt`) y **afinidad** (`taskset`). Conecta directamente con la **planificación** del kernel."

**Frase de cierre:** "Comparando los números, se ve que el planificador CFS reparte la CPU y que dar prioridad o
fijar afinidad **baja la latencia**. Eso es planificación, no marketing."

---

## 3. Bloque 2 — Gestor de memoria (P1, ≈ 3 minutos)

**Qué decir:**

1. "La JVM separa **heap** —donde viven los objetos: mensajes, DTO, el mapa de typing— del **stack** —uno por
   hilo, con las variables locales y los marcos de llamada."
2. "**Acotamos el heap al 75 % del límite del contenedor**: el `Dockerfile` pone
   `-XX:MaxRAMPercentage=75.0` y Compose lo refuerza con `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`. Con 1152 MB
   de límite, el heap máximo es **≈ 864 MB**."
3. "¿Por qué porcentaje y no `-Xmx`? Porque `-XX:MaxRAMPercentage` se **adapta** al límite del cgroup: si subimos
   el límite, el heap crece solo. `-Xmx` habría que recalcularlo a mano."
4. "El sistema operativo usa **memoria virtual** y **paginación**: si el proceso pide más de lo que hay en RAM,
   aparecen *page faults* y, con mala configuración, **swap** —malo para PostgreSQL y los brokers, que necesitan
   latencias de microsegundos."
5. "El **aislamiento** lo garantiza el SO: cada proceso tiene su **espacio de direcciones**. La **protección** se
   refuerza con `kernel.randomize_va_space = 2` (ASLR) y `kernel.yama.ptrace_scope = 2`."
6. "**Criterio de fuga de memoria:** la métrica `typing_states_active` debe volver a **cero** tras una ráfaga de
   eventos. Si no vuelve, hay entradas que se insertan y nunca se liberan."
7. "El ejercicio de **traducción de direcciones**: con páginas de 4 KB —12 bits de desplazamiento—, la virtual
   `0x1234` con página 1 en marco 3 da la física `0x3234`. Lo importante es el papel de la **MMU**, la **tabla de
   páginas** y la **TLB**."

---

## 4. Bloque 3 — Gestor de archivos (P1, ≈ 3 minutos)

**Qué decir:**

1. "El **journaling** consiste en registrar el cambio **antes** de aplicarlo para recuperarse de un fallo. En
   Stream-Chat ese journaling se llama **WAL** y lo implementa **PostgreSQL**."
2. "**Experimento E3:** mientras se insertan mensajes, hacemos `kill -9` a PostgreSQL. Al reiniciar, PostgreSQL
   **repite el WAL** desde el último checkpoint: los mensajes **confirmados** siguen ahí y **ningún confirmado se
   pierde**. Eso enlaza con el patrón *Outbox*: mensaje y fila de outbox se confirman en la misma transacción."
3. "La **persistencia** real vive en **volúmenes nombrados** de Docker: `postgres_data`, `rabbitmq_data`,
   `kafka_data`… Con `docker compose down` los datos **sobreviven**; con `docker compose down -v` los volúmenes se
   **borran**."
4. "Los **metadatos y permisos** importan: el `.env` va con permisos **600** y el contenedor corre con un **usuario
   no root** creado en el `Dockerfile`. Es **mínimo privilegio** aplicado."
5. "La **asignación de espacio** en Kafka y PostgreSQL es **secuencial** (*append-only*), lo que encaja con la
   asignación **contigua** y con la **caché de disco** del SO."
6. "**Monitoreamos espacio e inodos** con `df -h`, `df -i`, `du` y `ncdu`; y verificamos con **`fsck -n`** solo
   sobre un volumen **desmontado**."
7. "Sobre **sistemas de archivos**: usamos **ext4** porque tiene journaling y permisos. **FAT32** no tiene ninguno
   de los dos y limita los archivos a **4 GB**, así que un volcado grande no cabría."

---

## 5. Bloque 4 — Gestor de E/S (P2, ≈ 3 minutos)

**Qué decir:**

1. "**Buffering:** cada sesión WebSocket tiene una **cola de salida**, que es un **buffer circular**. El servidor
   escribe; la red drena. Si el cliente es lento, la cola crece."
2. "**Decisión de diseño:** el buffer es **acotado** y al llenarse aplica una política *Strategy*: primero
   **descarta los `TYPING_UPDATE` antiguos** —la presencia es efímera—, **nunca** descarta mensajes, y si aún se
   llena, **desconecta al cliente lento**. Es el **experimento E4**."
3. "El **tamaño del buffer del consumidor** se configura: **`prefetch = 10`** en RabbitMQ y
   **`max.poll.records = 50`** en Kafka. Son *créditos* que evitan desbordar al consumidor."
4. "**Spooling:** RabbitMQ hace de **spooler**, como la cola de la impresora. El productor encola y sigue; el
   consumidor procesa a su ritmo. En el **experimento E5** detenemos el consumidor: la cola crece y, al volver, se
   **drena sin perder mensajes**."
5. "**Caching:** la **page cache** del SO guarda en RAM los bloques leídos. Una lectura repetida del historial
   probablemente no toca el disco."
6. "**E/S por interrupciones y DMA:** el paquete llega, la **tarjeta de red interrumpe** a la CPU, el **DMA** copia
   a memoria sin ocupar la CPU, y el kernel lo deja en el **buffer del socket** para que la JVM lo lea."
7. "**Disco y SSD:** en un volumen **SSD** (como EBS) no hay cabezal que mover, así que importan las **escrituras
   secuenciales**, el **TRIM** y el **wear leveling**, no FCFS/SSTF/SCAN."
8. "La **red es un dispositivo de E/S**: lo vemos con `ss -tunap` y `sar -n DEV 2`. Y el **experimento E6** mide la
   **E/S de escritura sostenida** con `iostat`, `iotop-c`, `df -h` y `df -i`."

---

## 6. Comandos de demostración en vivo (orden recomendado)

Ejecutar en este orden; entre paréntesis, lo que se debe ver.

```bash
# ── 0) Estado del entorno ─────────────────────────────────────────────
docker compose ps                                  # (servicios Up/healthy; proceso por servicio)

# ── 1) PROCESOS: tabla servicio → PID → usuario → estado → consumo ─────
ps aux | grep -E 'java|postgres|beam|kafka' | grep -v grep
pstree -p
ps -eo pid,user,%cpu,%mem,etime,cmd --sort=-%cpu | head -11
top -o %CPU -n 1 -b | head -20                    # (columna S = estado)

# ── 2) PLANIFICACIÓN: prioridad y afinidad ────────────────────────────
APP_PID=$(pgrep -f 'app.jar' | head -1)
chrt -p $APP_PID
ps -eo comm,pid,ni,pri --sort=-ni | head
taskset -p $APP_PID

# ── 3) MEMORIA ─────────────────────────────────────────────────────────
free -h
vmstat 2 &                                         # (columnas si/so/swpd/free/cs)
docker stats --no-stream                           # (memoria por contenedor)
docker exec streamchat-app jcmd 1 GC.heap_info      # (MaxHeapSize ≈ 75%)

# ── 4) ARCHIVOS ────────────────────────────────────────────────────────
docker volume ls | grep streamchat
ls -l .env                                          # (-rw-------, permisos 600)
df -h ; echo --- ; df -i
du -h --max-depth=1 . 2>/dev/null | sort -hr | head

# ── 5) E/S: red, colas y disco ────────────────────────────────────────
ss -tunap | grep -E '8080|5672|9092|5433'
docker exec streamchat-rabbitmq rabbitmqctl list_queues name messages consumers
iostat -xz 2 3                                      # (%util del disco)

# ── 6) RECUPERACIÓN: matar y ver el reinicio ──────────────────────────
docker kill --signal=SIGKILL streamchat-app
sleep 3 && docker compose ps                        # (vuelve a Up por 'restart: unless-stopped')
```

**Punto de impacto para el jurado:** después del `kill -9`, mostrar que el contenedor se levanta solo y que el
buffer/proceso vuelve a estado sano. Y en E3, mostrar que el conteo de mensajes **no baja** tras matar PostgreSQL.

---

## 7. Preguntas probables del jurado y respuestas cortas

| Pregunta | Respuesta corta |
|---|---|
| **¿Cuál es la diferencia entre un proceso y un hilo?** | El **proceso** tiene su propio espacio de direcciones y recursos (PID, archivos abiertos); los **hilos** de un mismo proceso **comparten** memoria y archivos, y cada uno tiene su **stack**. En Stream-Chat, la JVM es un proceso con varios hilos: los listeners, el scheduler de expiración y el relay. |
| **¿Por qué usar `ConcurrentHashMap` y no `synchronized`?** | `ConcurrentHashMap` da **atomicidad por entrada** (`merge`, `remove`), así varios usuarios escriben en paralelo sin un **mutex global** que serialice todo. Menos contención, sin condiciones de carrera. |
| **¿Qué pasa si mato la aplicación con `kill -9`?** | El contenedor se reinicia por `restart: unless-stopped`. Los mensajes ya confirmados **no se pierden** (el WAL y el patrón Outbox lo garantizan) y el relay de outbox publica lo pendiente al recuperarse. |
| **¿Por qué `-XX:MaxRAMPercentage` y no `-Xmx`?** | Porque se **adapta** al límite del contenedor (cgroup); si cambia el límite, el heap se recalcula solo. Evita el OOM global. |
| **¿Qué es un page fault?** | El acceso a una página virtual que **no está en RAM**: el SO la trae de disco y reintenta la instrucción. Muchos page faults degradan el rendimiento. |
| **Explique la traducción de direcciones.** | La dirección virtual se parte en **número de página** y **desplazamiento** (12 bits con páginas de 4 KB). La **MMU** usa la **tabla de páginas** para hallar el **marco** y combina marco + desplazamiento. `0x1234` (página 1, marco 3) → `0x3234`. La **TLB** cachea traducciones. |
| **¿Qué es el journaling y dónde lo ven?** | Registrar el cambio **antes** de aplicarlo para recuperarse de fallos. En Stream-Chat es el **WAL de PostgreSQL**: los mensajes confirmados se recuperan tras un `kill -9`. |
| **¿Qué pasa con `docker compose down -v`?** | El `-v` **borra los volúmenes**: se pierde el historial y los datos de los brokers. Con `down` (sin `-v`) los datos **sobreviven**. |
| **¿Qué es buffering y spooling?** | **Buffering** es un área temporal entre productor y consumidor (la cola de la sesión WebSocket, buffer circular). **Spooling** es que un intermediario (**RabbitMQ**) acepta el trabajo rápido y lo entrega a su ritmo, desacoplando productor y consumidor. |
| **¿Qué hace un cliente WebSocket lento y cómo lo controlan?** | No lee su socket, así que su cola crece. Aplicamos una política: **descartar `TYPING_UPDATE` antiguos, nunca mensajes, desconectar al cliente lento**. Así no bloquea a los demás ni agota la memoria (E4). |
| **¿Por qué prefetch = 10 y max.poll.records = 50?** | Son el **tamaño del buffer del consumidor** (control de flujo): evitan que el broker desborde a un consumidor lento y acotan la memoria usada por lote. |
| **¿Por qué en SSD no aplican SSTF/SCAN?** | Porque **no hay cabezal** que mover. Lo que importa es **escribir secuencialmente**, y usar **TRIM** y **wear leveling** para rendimiento y vida útil del dispositivo. |
| **¿Cómo demuestran que no hay fuga de memoria?** | La métrica `typing_states_active` sube con la ráfaga y **vuelve a 0** tras el TTL (5 s) y un barrido (2 s). Si no vuelve a 0, hay entradas sin liberar. |
| **¿Qué es el OOM Killer?** | El mecanismo que termina un proceso cuando se queda sin memoria. Con **cgroups**, Docker mata al proceso **del contenedor**, no al host; y `restart` lo levanta. Lo medimos en E2. |
| **¿Aplica el kernel FCFS o Round Robin?** | Ninguno puro: el kernel de Linux usa **CFS/EEVDF**, un reparto **equitativo ponderado por prioridad** (similar a Round Robin con pesos). `chrt` permite usar clases de **tiempo real**. |
| **¿Por qué no publican los puertos de PostgreSQL, RabbitMQ y Kafka?** | Porque son **superficie de ataque**. Están publicados solo en `127.0.0.1` y viven en la red interna de Docker; desde fuera no son alcanzables (hardening, sección 10). |

---

## 8. Reparto de la exposición (sugerido)

| Parte | Quién | Duración objetivo |
|---|---|---:|
| Introducción | P3 (Esteban Lasso) | 2 min |
| Gestor de procesos + E1 | P3 | 3 min |
| Gestor de memoria + E2 | P1 | 3 min |
| Gestor de archivos + E3 | P1 | 3 min |
| Gestor de E/S + E4/E5/E6 | P2 | 3–4 min |
| Preguntas | Los tres | 5 min |

**Cierre:** "Los cuatro gestores están documentados con **decisión de diseño, medición y evidencia** en
`docs/so/`, y los seis experimentos con su procedimiento y sus tablas de resultados. Este informe cumple el
**Hito 7**."
