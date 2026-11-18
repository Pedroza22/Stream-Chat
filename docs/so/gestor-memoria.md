# Gestor de memoria aplicado a Stream-Chat

**Asignatura:** Sistemas Operativos • **Base del plan:** sección **9.2** • **Experimento:** **E2**
**Responsable:** Persona 1 (archivos y memoria) y Persona 3 (monitoreo y criterio de fugas).

---

## 1. Idea central

El gestor de memoria del sistema operativo **reparte la RAM física y la memoria virtual entre procesos**, los
**aísla** (cada uno cree que tiene toda la memoria) y **traduce** direcciones virtuales a físicas usando páginas.
En Stream-Chat el proceso que más memoria administra es la **JVM de Spring Boot**; los brokers (RabbitMQ, Kafka) y
PostgreSQL también reservan memoria relevante, pero cada uno está **acotado por su límite de contenedor**.

---

## 2. Tabla base de la sección 9.2 (presentaciones → Stream-Chat → evidencia)

| Qué indican las presentaciones | Aplicación en Stream-Chat | Evidencia |
|---|---|---|
| Asignación dinámica: **heap** para objetos y **stack** para llamadas y variables locales | La JVM usa el heap para mensajes y para el mapa de typing, y un stack por hilo. Se alinea `-Xmx` o `-XX:MaxRAMPercentage` ➕ con el límite de memoria del contenedor. | `free -h`, `vmstat 2`, `sar -r` |
| Memoria virtual, paginación, swap y *page fault* | **Experimento E2:** qué ocurre cuando el servicio supera la RAM disponible (swap, lentitud, OOM). Se explica por qué en bases de datos y brokers el swap degrada el rendimiento ➕. | `sar -S`, columnas `si/so` de `vmstat`; alarma de memoria > 90 % durante 10 min (hardening) |
| Traducción de direcciones: número de página y desplazamiento; con páginas de 4 KB, 12 bits de desplazamiento (ejemplo `0x1234` con página 1 en marco 3 da `0x3234`) | Ejercicio para la sustentación (sección 6 de este documento): resolver direcciones y explicar el papel de la MMU, la tabla de páginas y la TLB. Se enlaza con el aislamiento: cada proceso tiene su espacio de direcciones virtual. | Ejercicio escrito en `docs/so/` |
| Protección de memoria y aislamiento entre procesos | Parámetros del kernel de la guía de hardening: `kernel.randomize_va_space = 2` (ASLR) y `kernel.yama.ptrace_scope = 2`. | `sysctl kernel.randomize_va_space`, `sysctl kernel.yama.ptrace_scope` |
| Fragmentación y algoritmos de ubicación (primer, mejor, siguiente y peor ajuste) | Explicación conceptual: asignación dinámica y recolección de basura en el heap de la JVM ➕. | Opcional |
| Herramientas de análisis (Administrador de tareas, VMMap, `htop`) | Observar el uso de memoria por proceso durante la prueba de carga. | `htop`, `ps --sort=-%mem` |

**Criterio de fugas:** el estado de typing expira y se elimina; tras una ráfaga de eventos el número de estados
activos vuelve a **cero** (métrica `typing_states_active`).

---

## 3. Heap vs stack en la JVM

| Zona | Qué guarda | Tamaño | En Stream-Chat |
|---|---|---|---|
| **Heap** | Objetos creados con `new` (mensajes, DTO, entidades JPA, el `ConcurrentHashMap` de typing) | Acotado por `-Xmx` o `-XX:MaxRAMPercentage` | La JVM reserva hasta el **75 %** del límite del contenedor |
| **Stack** | Variables locales, marcos de llamada; **uno por hilo** | Pequeño por hilo (`-Xss`), pero crece con el número de hilos | Cada hilo de listener/scheduler tiene su stack |
| **Metaspace** | Metadatos de clases (fuera del heap) | Acotado por `-XX:MaxMetaspaceSize` | Clases de Spring, Hibernate, etc. |

### 3.1 Cómo se acota el heap en Stream-Chat

El `Dockerfile` fija el porcentaje de RAM que puede usar el heap:

```dockerfile
# Dockerfile — etapa de ejecución
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
```

Y `compose.yaml` lo **refuerza** para el contenedor de la aplicación:

```yaml
# compose.yaml — servicio app
environment:
  # Heap de la JVM acotado al 75% del límite de memoria del contenedor
  JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75"
deploy:
  resources:
    limits:
      memory: 1152m
```

**Cálculo:** `1152 MB × 0.75 = 864 MB` de heap máximo. El resto (≈ 288 MB) queda para stack, Metaspace, buffers
de E/S, la propia JVM y el sistema operativo del contenedor. Así el proceso **no puede** crecer hasta agotar la RAM
de la máquina y provocar un OOM global: si se pasa, el kernel mata **ese** contenedor (cgroup), no el host.

> **Ventaja de `-XX:MaxRAMPercentage` sobre `-Xmx`:** `-Xmx` es un valor absoluto y hay que ajustarlo si cambia el
> límite del contenedor; `-XX:MaxRAMPercentage` se **adapta** al límite del cgroup (GraalVM/Java 10+ detecta
> `--memory`/`deploy.resources.limits.memory`). Al subir el límite de 768m a 1152m, el heap crece solo.

---

## 4. Memoria virtual, paginación, swap y *page faults*

- **Espacio de direcciones virtual:** la JVM y cada proceso ven un rango de direcciones contiguo que el SO mapea a
  páginas físicas de 4 KB.
- **Paginación:** si una dirección virtual no está en RAM, se produce un **page fault** y el SO trae la página desde
  disco (o desde swap).
- **Swap:** Linux puede mover páginas a disco (swap) cuando falta RAM. Para PostgreSQL, RabbitMQ y Kafka esto es
  **malo**: sus latencias de E/S deben ser de microsegundos; un swap provoca *stalls* de decenas de milisegundos.
  Por eso la recomendación es que el contenedor **no haga swap** (en Docker: `--memory-swappiness=0` / evitar que
  el límite se supere).
- **OOM Killer:** si un contenedor supera su límite de memoria, los cgroups **no** matan a un proceso cualquiera del
  host: matan al proceso del contenedor (la JVM), y `restart: unless-stopped` la levanta de nuevo. Eso es
  exactamente lo que mide **E2**.

### Columnas clave de `vmstat`

| Columna | Significado | Qué mirar en E2 |
|---|---|---|
| `si` | Memoria **swap in** por segundo (KB) | Si `si > 0` con carga, hay *thrashing* |
| `so` | Memoria **swap out** por segundo (KB) | Si `so > 0`, el sistema está paginando a disco |
| `swpd` | Memoria virtual usada (KB) | Debe quedarse cerca de 0 en los brokers |
| `free` | RAM libre (KB) | Debe ser holgada |
| `cs` | Cambios de contexto por segundo | Sube mucho cuando hay *thrashing* |

---

## 5. Protección de memoria y aislamiento

| Control | Valor | Para qué sirve |
|---|---|---|
| `kernel.randomize_va_space` | `2` (ASLR completo) | Aleatoriza las direcciones (heap, stack, librerías) para dificultar exploits de corrupción de memoria |
| `kernel.yama.ptrace_scope` | `2` | Restringe `ptrace` a procesos hijos/relacionados; evita inspección de memoria ajena |

```bash
# Verificar ASLR y el alcance de ptrace
sysctl kernel.randomize_va_space      # esperado: kernel.randomize_va_space = 2
sysctl kernel.yama.ptrace_scope       # esperado: kernel.yama.ptrace_scope = 2
```

Cada proceso tiene su **propio espacio de direcciones**: un fallo de memoria en la JVM no corrompe a PostgreSQL.
Esta es la base de la **estabilidad** del despliegue con contenedores (sección 10 del plan).

---

## 6. Ejercicio para la sustentación: traducción de direcciones

**Datos:** páginas de **4 KB** = 2¹² bytes → **12 bits de desplazamiento** (`offset`). El resto de bits de la
dirección virtual forman el **número de página**; la **tabla de páginas** lo traduce a un **marco** físico.

**Ejemplo del plan:** dirección virtual `0x1234`, página 1 mapeada al marco 3.

```text
0x1234  = 0001 0010 0011 0100        (16 bits)
                  └────┬────┘
   número de página = 0x1 = 1        (bits altos, aquí 4 bits)
   desplazamiento   = 0x234          (12 bits bajos)

   Dirección física = (marco << 12) | offset = (3 << 12) | 0x234 = 0x3000 | 0x234 = 0x3234
```

**Papel de cada componente:**

- **MMU (Memory Management Unit):** hardware que hace la traducción en cada acceso.
- **Tabla de páginas:** estructura por proceso que mapea `página → marco` (más los bits de protección).
- **TLB:** caché de traducciones recientes; si hay *hit*, la MMU no consulta la tabla.

**Ejercicio adicional para resolver en la sustentación (rellenar):**

| Dirección virtual | Página | Marco | Desplazamiento | Dirección física | Cálculo |
|---|---|---|---|---:|---|
| `0x1234` | 1 | 3 | `0x234` | `0x3234` | `(3<<12)|0x234` |
| *(ejercicio)* `0x2ABC` | | | | | |
| *(ejercicio)* `0x10FF` | | | | | |

---

## 7. Herramientas de análisis de memoria

```bash
# Memoria física y swap del host, en formato legible
free -h

# Estadísticas de memoria virtual cada 2 segundos (mira swpd, si, so, free, cs)
vmstat 2

# Histórico de memoria (requiere sysstat); -r = memoria, -S = swap
sar -r 2
sar -S 2

# Memoria por proceso: htop ordena con F6 -> PERCENT_MEM
htop
ps --sort=-%mem -eo pid,user,%mem,rss,vsz,cmd | head -11

# Memoria de la JVM dentro del contenedor (heap en uso vs máximo)
docker exec streamchat-app jcmd 1 GC.heap_info
docker exec streamchat-app jstat -gc 1 2s 5

# Uso de memoria del contenedor según el cgroup (real, lo que cuenta para el OOM)
cat /sys/fs/cgroup/memory.max 2>/dev/null || cat /sys/fs/cgroup/memory/memory.limit_in_bytes
docker stats --no-stream
```

---

## 8. Experimento E2 — Límite de memoria, OOM y swap

**Objetivo:** observar qué ocurre cuando el servicio supera la RAM disponible: swap, degradación y OOM; y explicar
la relación entre el heap de la JVM y el límite del contenedor.

### 8.1 Procedimiento (resumen; detalle en `experimentos.md`)

```bash
# 1) Línea base: memoria libre y arranque sano
free -h
docker stats --no-stream

# 2) Vigilar swap y page faults mientras se genera carga
vmstat 2

# 3) Reducir el límite de memoria del contenedor de la app para forzar la presión
#    (o lanzar una carga de memoria que acerque la JVM a su 75%)
#    Se observa si aparece la columna si/so (swap) y si el contenedor reinicia.

# 4) Confirmar el mecanismo: el cgroup mata al proceso y 'restart' lo levanta
docker inspect streamchat-app --format '{{.State.OOMKilled}}'

# 5) Comprobar que el estado de typing se libera (criterio de fugas)
curl -s localhost:8080/actuator/metrics/typing_states_active | jq
```

### 8.2 Resultado esperado

- Con carga por debajo del límite: `free` holgado, `si`/`so` ≈ 0, sin reinicios.
- Al superar el límite del contenedor: el **OOM Killer del cgroup** termina la JVM (`OOMKilled=true`) y
  `restart: unless-stopped` la vuelve a levantar; el tiempo de recuperación se documenta.
- Si el host empieza a hacer swap (`so > 0`), el rendimiento de PostgreSQL y Kafka cae (latencias altas).

### 8.3 Criterio de fugas de memoria

La métrica gauge `typing_states_active` se registra directamente sobre el registry:

```java
// src/main/java/com/streamchat/presence/infrastructure/config/PresenceConfig.java
Gauge.builder("typing_states_active", registry, TypingStateRegistry::activeCount)
     .description("Estados de escritura activos")
     .register(meterRegistry);
```

El criterio de E2 es: **tras una ráfaga de eventos de typing, al expirar el TTL (5 s) el valor vuelve a 0**. Si
no vuelve a 0, hay una fuga: entradas que se insertan y nunca se eliminan.

```bash
# Antes de la ráfaga
curl -s localhost:8080/actuator/metrics/typing_states_active | jq '.measurements'
# (generar N usuarios escribiendo y no detenerlos)
# Tras el TTL (5 s) + un barrido (2 s)
sleep 8
curl -s localhost:8080/actuator/metrics/typing_states_active | jq '.measurements'   # debe ser 0.0
```

### 8.4 Tabla de resultados (rellenar al ejecutar)

> **Nota:** se entrega en blanco. Se rellena con la salida real.

| Momento | RAM libre (`free -h`) | `swpd` (KB) | `si` (KB/s) | `so` (KB/s) | `typing_states_active` | ¿OOMKilled? | Tiempo de recuperación |
|---|---:|---:|---:|---:|---:|---|---|
| Arranque en reposo | | | | | | | |
| Carga normal | | | | | | | |
| Antes de la ráfaga de typing | | | | | | | |
| Tras la ráfaga (inmediato) | | | | | | | |
| Tras TTL + barrido | | | | | | | |
| Presión de memoria / OOM | | | | | | | |

---

## 9. Evidencias a capturar

1. `free -h` en reposo y bajo carga.
2. `vmstat 2` mostrando (o no) columnas `si`/`so` durante la presión de memoria.
3. `docker stats --no-stream` y `docker inspect ... OOMKilled`.
4. Salida de `jcmd 1 GC.heap_info` mostrando `MaxHeapSize` ≈ 75 % del límite del contenedor.
5. `curl .../actuator/metrics/typing_states_active` antes y después de la ráfaga (debe volver a 0).
6. `sysctl kernel.randomize_va_space` y `sysctl kernel.yama.ptrace_scope`.
