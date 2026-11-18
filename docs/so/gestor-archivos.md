# Gestor de archivos aplicado a Stream-Chat

**Asignatura:** Sistemas Operativos • **Base del plan:** sección **9.3** • **Experimento:** **E3**
**Responsable:** Persona 1 (gestores de archivos y de memoria).

---

## 1. Idea central

El gestor de archivos del sistema operativo **organiza y controla el almacenamiento persistente**: la estructura
jerárquica de directorios, los **metadatos** e **inodos**, los **permisos**, la **asignación de bloques** en disco,
la **caché de disco** y las herramientas de **verificación y reparación**. En Stream-Chat la persistencia es
crítica: el historial de mensajes vive en PostgreSQL, y los brokers guardan sus datos en volúmenes de Docker.

---

## 2. Tabla base de la sección 9.3 (presentaciones → Stream-Chat → evidencia)

| Qué indican las presentaciones | Aplicación en Stream-Chat | Evidencia |
|---|---|---|
| **Journaling** (NTFS, ext4): registrar el cambio antes de aplicarlo para recuperarse de fallos | El **WAL** de PostgreSQL cumple la misma función. **Experimento E3:** `kill -9` al contenedor de PostgreSQL durante inserciones, reiniciar y verificar que no se perdió ningún mensaje confirmado. | Logs de recuperación de PostgreSQL y conteo antes/después |
| Persistencia y estructura jerárquica | Volúmenes nombrados de Docker para los datos de PostgreSQL y de los brokers. Mostrar qué se pierde con `docker compose down -v` y qué no. | `docker volume ls`, `df -hT` |
| Metadatos, permisos y control de acceso (rwx, `chmod`, ACL, inodos) | `.env` con permisos **600**, usuario **no root** dentro del contenedor, propietario correcto de los volúmenes. Archivos de arranque protegidos como en la guía de hardening. | `ls -l`, `stat`, `chmod` |
| Asignación de espacio (contigua, enlazada, indexada) y caché de disco | Kafka y PostgreSQL escriben registros de forma **secuencial** (solo agregan al final): asignación contigua. El SO amortigua con la **page cache**. | Explicación en el informe |
| Monitoreo de espacio | Alarma de disco > 80 %, rotación de logs y retención corta del topic de actividad para que no llenen el volumen. También **agotamiento de inodos**. | `df -h`, `df -i`, `du -h --max-depth=1 \| sort -hr`, `ncdu` |
| Verificación y reparación (`fsck`, `chkdsk`) | Demostrar `fsck -n` sobre un volumen de prueba **desmontado**; **nunca** sobre uno montado. | Captura del comando |
| Sistemas de archivos y sus límites (FAT32 no tiene permisos ni journaling, y limita los archivos a 4 GB) | Explicar por qué los datos del servidor van en **ext4** y por qué un volcado > 4 GB no cabría en FAT32. | Opcional |

**Criterio:** tras reiniciar el entorno, el historial sigue disponible y no hay mensajes confirmados que falten.

---

## 3. Journaling y el WAL de PostgreSQL

El *journaling* registra **los cambios en un diario antes de aplicarlos**, para poder recuperarse de un fallo a
mitad de una escritura. **PostgreSQL implementa su propio diario: el WAL (*Write-Ahead Log*)**.

### 3.1 Cómo funciona el WAL

1. Antes de modificar una página de dato, PostgreSQL escribe el cambio en el **WAL** y hace `fsync` (durabilidad).
2. Solo después se aplica al fichero de datos (que puede hacerse de forma perezosa).
3. Tras un `kill -9`, al arrancar PostgreSQL **repite (roll-forward) el WAL** desde el último *checkpoint*:
   las transacciones **confirmadas** (`COMMIT` que ya devolvió OK) se recuperan; las **no confirmadas** se
   descartan.

**Relación con Stream-Chat:** cada mensaje se guarda en PostgreSQL y en la tabla `outbox` **en la misma
transacción** (patrón Outbox, sección 2.4 del plan). Gracias al WAL, si el proceso muere entre el `COMMIT` y la
publicación, al reiniciar el dato sigue ahí y el **relay de outbox** lo publica.

```java
// src/main/java/com/streamchat/messaging/outbox/OutboxRelay.java
// Las transacciones confirmadas (mensaje + fila outbox) sobreviven a la caída gracias al WAL;
// este relay reintenta publicar las filas PENDING/FAILED al recover.
@Scheduled(fixedDelayString = "${app.messaging.outbox-relay-delay-ms:5000}")
public void processOutbox() { ... }
```

---

## 4. Persistencia y volúmenes en Docker

Los datos deben **sobrevivir** a la recreación de contenedores. Por eso `compose.yaml` declara volúmenes
nombrados (gestionados por Docker, no *bind mounts* del código):

```yaml
# compose.yaml — volúmenes por servicio
services:
  postgres:
    volumes:
      - postgres_data:/var/lib/postgresql/data
  rabbitmq:
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
  zookeeper:
    volumes:
      - zookeeper_data:/var/lib/zookeeper/data
      - zookeeper_log:/var/lib/zookeeper/log
  kafka:
    volumes:
      - kafka_data:/var/lib/kafka/data

volumes:
  postgres_data:
  rabbitmq_data:
  zookeeper_data:
  zookeeper_log:
  kafka_data:
```

### 4.1 Qué se conserva y qué se pierde

| Acción | ¿Se conservan los datos? | Por qué |
|---|---|---|
| `docker compose restart` | **Sí** | El contenedor se reinicia; el volumen sigue montado |
| `docker compose down` | **Sí** | Se eliminan contenedores, **no** los volúmenes nombrados |
| `docker compose down -v` | **No** | `-v` **elimina los volúmenes**: se borra el historial y los datos de los brokers |
| `docker volume rm postgres_data` | **No** | Borrado explícito del volumen |

```bash
# Ver los volúmenes creados por el proyecto
docker volume ls | grep streamchat

# Inspeccionar dónde vive físicamente un volumen y sus metadatos
docker volume inspect streamchat_postgres_data

# CUIDADO: 'down -v' borra los datos de forma irreversible
docker compose down -v

# Back up lógico antes de operaciones destructivas (respaldo del historial)
docker exec streamchat-postgres pg_dump -U streamchat streamchat > backup_$(date +%F).sql
```

---

## 5. Metadatos, permisos e inodos

### 5.1 El `.env` con permisos 600

El archivo `.env` contiene secretos (`POSTGRES_PASSWORD`, `JWT_SECRET`, credenciales de RabbitMQ, etc.). Debe ser
legible **solo por su dueño**:

```bash
# Restringir el .env a lectura/escritura del propietario
chmod 600 .env
ls -l .env
# -rw------- 1 usuario usuario ... .env
```

El archivo de ejemplo `.env.example` **no** contiene secretos reales (solo valores de ejemplo) y sí puede ir en el
repositorio:

```bash
# .env.example (extracto real)
POSTGRES_PASSWORD=changeme
JWT_SECRET=change-this-to-a-random-256-bit-base64-encoded-secret
RABBITMQ_PASSWORD=changeme
```

### 5.2 Usuario no root dentro del contenedor

El `Dockerfile` crea un usuario de sistema **sin privilegios** y ejecuta la aplicación con él (mínimo privilegio):

```dockerfile
# Dockerfile — etapa de ejecución
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --create-home app
USER app
```

Esto evita que un compromiso de la aplicación le dé **root** dentro del contenedor.

### 5.3 Inodos y metadatos

Cada archivo/carpeta consume un **inodo** (metadatos: permisos, propietario, tamaño, punteros a bloques). El
sistema de archivos puede agotar **inodos antes que espacio** si hay muchísimos archivos pequeños (por ejemplo,
si Kafka creara millones de *segments* diminutos). Por eso se monitorea `df -i`.

```bash
# Metadatos completos de un archivo (inodo, permisos, propietario, tiempos)
stat .env
stat compose.yaml

# Permisos de los archivos de arranque/configuración
ls -l .env Dockerfile compose.yaml
```

---

## 6. Asignación de espacio y caché de disco

| Forma de asignación | Descripción | En Stream-Chat |
|---|---|---|
| **Contigua** | Bloques seguidos | Kafka escribe *segments* **append-only**; PostgreSQL escribe el WAL de forma secuencial |
| **Enlazada** | Cada bloque apunta al siguiente | Menos frecuente en discos modernos |
| **Indexada** | Un índice (inodo) apunta a los bloques | Ext4 usa **extents** (punteros indexados a rangos contiguos) |

La **caché de disco (page cache)** del SO guarda en RAM los bloques leídos recientemente. PostgreSQL y Kafka se
benefician enormemente: una lectura repetida del historial de mensajes puede no tocar el disco. Esto es la base
del *caching* del gestor de E/S (ver `gestor-es.md`).

---

## 7. Monitoreo de espacio

```bash
# Espacio por sistema de archivos (humano y con tipo)
df -h
df -hT

# Inodos libres/usados (agotamiento de inodos)
df -i

# Tamaño por subdirectorio, ordenado de mayor a menor
du -h --max-depth=1 . | sort -hr

# Explorador interactivo de uso de disco (si está instalado)
ncdu

# Uso de los volúmenes de Docker
docker system df -v
```

**Reglas de operación (guía de hardening):** alarma si el disco supera el **80 %**; rotación de logs de la
aplicación; retención corta del topic de actividad (ya configurada en Kafka: **1 hora / 100 MB**):

```yaml
# compose.yaml — servicio kafka
KAFKA_LOG_RETENTION_HOURS: 1
KAFKA_LOG_RETENTION_BYTES: 104857600   # 100 MB
```

```yaml
# application.yml — topic de actividad (efímero)
app:
  messaging:
    activity-topic: user-activity
```

---

## 8. Verificación y reparación: `fsck`

`fsck` comprueba y repara la consistencia del sistema de archivos. **Nunca** debe ejecutarse sobre un volumen
**montado** (puede corromper datos). El modo `-n` es **solo lectura** (responde "no" a todo, no repara).

```bash
# Crear un sistema de archivos de PRUEBA en un archivo, montarlo y demostrar fsck -n
dd if=/dev/zero of=/tmp/prueba.img bs=1M count=64
mkfs.ext4 /tmp/prueba.img
mkdir -p /mnt/prueba
sudo mount -o loop /tmp/prueba.img /mnt/prueba
# ... crear/copiar archivos de prueba ...
sudo umount /mnt/prueba              # DESMONTAR antes de fsck

# Comprobación en solo lectura sobre el volumen desmontado
sudo fsck -n /tmp/prueba.img
```

> **Nunca** ejecutar `fsck` sobre `postgres_data` montado: se desmontaría el contenedor primero
> (`docker compose stop postgres`).

---

## 9. Sistemas de archivos y el límite de FAT32

| Sistema | Journaling | Permisos (rwx) | Límite de archivo | Uso en Stream-Chat |
|---|---|---|---|---|
| **ext4** | Sí | Sí (POSIX) | ~16 TB | Sistema de archivos del host / volúmenes Docker |
| **XFS** | Sí | Sí (POSIX) | ~8 EB | Alternativa para volúmenes de gran tamaño |
| **FAT32** | **No** | **No** | **4 GB por archivo** | **Inadecuado** para los datos del servidor |

**Por qué importa el límite de 4 GB:** un volcado (`pg_dump`) o un *segment* de Kafka que supere los 4 GB **no
cabría** en FAT32, además de que FAT32 **no guarda permisos** (no se podría aplicar el `chmod 600` del `.env`) ni
journaling (se corrompería ante un fallo). Por eso los datos van en **ext4**.

---

## 10. Experimento E3 — Durabilidad del WAL ante `kill -9`

**Objetivo:** demostrar que, con el WAL, matar PostgreSQL a la fuerza durante inserciones **no pierde** los
mensajes ya confirmados; y que el historial sigue disponible tras reiniciar.

### 10.1 Procedimiento (resumen; detalle en `experimentos.md`)

```bash
# 1) Conteo antes
docker exec streamchat-postgres psql -U streamchat -d streamchat \
  -c "SELECT count(*) FROM messages;"

# 2) Insertar mensajes en bucle (p. ej. con un script que envíe mensajes por REST)
#    ...

# 3) Conteo inmediatamente ANTES de matar (solo los confirmados)
docker exec streamchat-postgres psql -U streamchat -d streamchat \
  -c "SELECT count(*) FROM messages;"

# 4) Matar a la fuerza el proceso de PostgreSQL
docker kill --signal=SIGKILL streamchat-postgres

# 5) Arrancar de nuevo (o dejar que 'restart: unless-stopped' lo haga) y ver los logs de recuperación
docker compose up -d postgres
docker logs streamchat-postgres | grep -i -E 'recovery|redo|checkpoint|ready to accept'

# 6) Conteo después
docker exec streamchat-postgres psql -U streamchat -d streamchat \
  -c "SELECT count(*) FROM messages;"
```

### 10.2 Resultado esperado

- Los **mensajes confirmados antes de la caída** siguen presentes: el WAL se repite (*redo*) hasta el punto de
  consistencia.
- Las transacciones **no confirmadas** en el momento del `kill -9` se descartan (no aparecen).
- En los logs se observa el mensaje de **recuperación** de PostgreSQL.
- Tras reiniciar el entorno, el historial sigue disponible (criterio de la sección 9.3).

### 10.3 Tabla "Antes / Después" (rellenar al ejecutar)

> **Nota:** se entrega en blanco. Se rellena con los conteos reales.

| Momento | `SELECT count(*) FROM messages` | Transacciones confirmadas | Observaciones |
|---|---:|---:|---|
| Antes de insertar | | | |
| Justo antes del `kill -9` | | | |
| Después del reinicio | | | |
| Diferencia (confirmados perdidos) | | | **Esperado: 0** |

| Verificación adicional | Resultado |
|---|---|
| Línea de recuperación en `docker logs streamchat-postgres` | |
| `docker volume ls \| grep postgres_data` (el volumen sobrevive) | |
| `df -hT` del volumen de datos (sistema de archivos) | |

---

## 11. Evidencias a capturar

1. `docker volume ls` y `docker volume inspect streamchat_postgres_data`.
2. Demostración de `docker compose down -v` (y de `down` sin `-v`) con la diferencia de datos.
3. `ls -l .env` mostrando `-rw-------` (600) y el `USER app` del `Dockerfile`.
4. `df -h`, `df -i` y `du -h --max-depth=1 | sort -hr`.
5. Captura de `fsck -n` sobre el volumen de prueba **desmontado**.
6. Conteo antes/después de E3 y los logs de recuperación del WAL.
