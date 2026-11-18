# Guía de despliegue de Stream-Chat en AWS EC2 (Ubuntu Server LTS)

> **Guía detallada del hito 8 (despliegue seguro).** Explica paso a paso cómo desplegar Stream-Chat
> (Spring Boot 3.2.5 / Java 21 + PostgreSQL + RabbitMQ + Kafka, vía Docker Compose) sobre **Ubuntu Server
> LTS en AWS EC2**, incluyendo Nginx con TLS, firewall, monitoreo y verificación final.
> **Responsable:** Programador 3. **Expone:** Esteban Lasso (ID 898780).

Esta guía acompaña a:
- [`checklist-hardening.md`](./checklist-hardening.md) — checklist marcable de las fases 0–9.
- [`runbook-incidentes.md`](./runbook-incidentes.md) — qué hacer si algo sale mal en producción.

> ⚠️ **Placeholders.** Todos los identificadores y direcciones de esta guía son **ejemplos** y deben
> sustituirse por los reales de tu cuenta: instancia `i-XXXXXXXX`, Security Group `sg-XXXXXXXX`,
> IP pública `203.0.113.10`, dominio `midominio.com` y AMI `ami-XXXXXXXX`. Nunca publiques secretos reales
> (`.env`, claves `.pem`, `JWT_SECRET`) en el repositorio ni en capturas.

---

## Índice

1. [Requisitos y decisiones de tamaño](#1-requisitos-y-decisiones-de-tamaño)
2. [Creación de la instancia](#2-creación-de-la-instancia)
3. [Key pair y conexión SSH inicial](#3-key-pair-y-conexión-ssh-inicial)
4. [Security Group](#4-security-group)
5. [Instalación de Docker y Docker Compose](#5-instalación-de-docker-y-docker-compose)
6. [Obtención del código](#6-obtención-del-código)
7. [Configuración del `.env`](#7-configuración-del-env)
8. [Build y despliegue](#8-build-y-despliegue)
9. [Nginx y TLS con Let's Encrypt](#9-nginx-y-tls-con-lets-encrypt)
10. [Firewall e Fail2ban](#10-firewall-y-fail2ban)
11. [Monitoreo](#11-monitoreo)
12. [Endurecimiento de systemd (opcional)](#12-endurecimiento-de-systemd-opcional)
13. [Verificación final](#13-verificación-final)
14. [Operación](#14-operación)
15. [Errores comunes y solución de problemas](#15-errores-comunes-y-solución-de-problemas)
16. [Chuleta: comando → para qué sirve](#16-chuleta-comando--para-qué-sirve)

---

## 1. Requisitos y decisiones de tamaño

### 1.1 ¿Cuánta memoria necesita Stream-Chat?

El `compose.yaml` **limita** cada servicio con `deploy.resources.limits`. La suma de esos límites es la
memoria mínima razonable, porque el sistema operativo, Docker, Nginx y los *sidecars* consumen aparte:

| Servicio | Imagen | Límite CPU | Límite memoria |
|---|---|---:|---:|
| `postgres` | `postgres:16-alpine` | 1.0 | 768 MiB |
| `rabbitmq` | `rabbitmq:3.13-management-alpine` | 1.0 | 768 MiB |
| `zookeeper` | `confluentinc/cp-zookeeper:7.6.1` | 0.5 | 512 MiB |
| `kafka` | `confluentinc/cp-kafka:7.6.1` | 1.0 | 1 GiB |
| `app` (JVM) | build local (`eclipse-temurin:21-jre`) | 1.5 | 1152 MiB |
| **Total** | | **5.0 vCPU** | **≈ 4.1 GiB** |

La JVM usa `-XX:MaxRAMPercentage=75` (ver `Dockerfile` y `compose.yaml`), es decir, hasta ~864 MiB de heap
sobre el límite de 1152 MiB del contenedor. Por eso la elección de instancia debe dejar **margen**.

### 1.2 Tipo de instancia recomendado

| Opción | vCPU / RAM | Veredicto |
|--------|-----------|-----------|
| `t3.small` | 2 / 2 GiB | ❌ Insuficiente: no entra Kafka + el resto |
| `t3.medium` | 2 / 4 GiB | ⚠️ Mínimo justo: los 4.1 GiB de límites + SO dejan la máquina al borde; sirve solo para demo |
| **`t3.large`** | **2 / 8 GiB** | ✅ **Recomendado**: margen para JVM, página de caché y picos |
| `t3.xlarge` | 4 / 16 GiB | Cómodo si se hace carga concurrente (hito 5) |

> 📌 El plan pide "t3.medium o superior". Para la **sustentación** y para no arriesgar OOM (experimento E2),
> usa **`t3.large`** o superior. En CPU, Kafka y la JVM compiten; añade vCPU (`cpus` del compose) si mides
> latencia bajo carga (experimento E1).

### 1.3 Otros requisitos

- **AMI:** Ubuntu Server **22.04 LTS** o **24.04 LTS** (64-bit x86). Ambas traen soporte de apt, `unattended-upgrades` y nftables.
- **Disco:** volumen **EBS gp3 de ≥ 30 GiB**, **cifrado** (`Encrypted=true`). PostgreSQL, Kafka y RabbitMQ
  escriben mucho; 20 GiB quedan cortos con imágenes Docker y datos.
- **IP elástica (EIP):** asígnala para que la IP no cambie al reiniciar la instancia (importante para el DNS y el Security Group).
- **DNS:** un dominio con registro **A** apuntando a la IP elástica (necesario para Let's Encrypt).

---

## 2. Creación de la instancia

### 2.1 Claves SSH y Security Group previos

Crea el *key pair* (lo usarás en el paso 3) y el Security Group (paso 4) **antes** de lanzar la instancia.

### 2.2 Opción A — Consola de AWS

1. EC2 → **Launch instance**.
2. **Name:** `streamchat-prod`.
3. **AMI:** Ubuntu Server 24.04 LTS.
4. **Instance type:** `t3.large`.
5. **Key pair:** crea uno nuevo (`.pem`) y guárdalo.
6. **Network settings:** selecciona el Security Group creado en el paso 4 (o edítalo después).
7. **Configure storage:** 30 GiB `gp3`, marca **Encrypt this volume**.
8. **Advanced details → Metadata:** exige **IMDSv2** (*Metadata version: V2 only*).
9. Launch. Asocia luego una **Elastic IP** a la instancia.

### 2.3 Opción B — AWS CLI

```bash
# Requisitos: aws cli configurado con las credenciales del usuario IAM
# (NO con la cuenta root). Sustituye los valores de ejemplo.

aws ec2 run-instances \
  --image-id ami-XXXXXXXX \
  --instance-type t3.large \
  --key-name streamchat-key \
  --security-group-ids sg-XXXXXXXX \
  --subnet-id subnet-XXXXXXXX \
  --iam-instance-profile Name=streamchat-ssm-profile \
  --metadata-options "HttpTokens=required,HttpPutResponseHopLimit=1,HttpEndpoint=enabled" \
  --block-device-mappings '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":30,"VolumeType":"gp3","Encrypted":true}}]' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=streamchat-prod}]'

# Guarda el InstanceId que devuelve el comando y asocia una IP elástica:
aws ec2 allocate-address --domain vpc
aws ec2 associate-address --instance-id i-XXXXXXXX --allocation-id eipalloc-XXXXXXXX
```

> 📌 `HttpTokens=required` fuerza **IMDSv2** (fase 0 de la checklist), mitigando el robo de credenciales
> temporales desde SSRF.

---

## 3. Key pair y conexión SSH inicial

### 3.1 Proteger la clave privada

```bash
# En tu equipo local, no en el servidor:
chmod 400 streamchat-key.pem
```

### 3.2 Primer acceso (antes del hardening)

Ubuntu crea el usuario `ubuntu`. Este primer acceso es **temporal**; en el paso 5.3 crearemos el usuario
administrador `miadmin`, y en la fase 2 de la checklist bloquearemos `ubuntu` y `root`.

```bash
ssh -i streamchat-key.pem ubuntu@203.0.113.10
```

### 3.3 Crear el usuario de administración

```bash
# Añade tu clave pública al nuevo usuario desde el propio servidor:
sudo adduser miadmin
sudo usermod -aG sudo miadmin

# Copia la clave pública de ubuntu al usuario nuevo (o pega la tuya en authorized_keys):
sudo install -d -m 700 -o miadmin -g miadmin /home/miadmin/.ssh
sudo cp /home/ubuntu/.ssh/authorized_keys /home/miadmin/.ssh/authorized_keys
sudo chown miadmin:miadmin /home/miadmin/.ssh/authorized_keys
sudo chmod 600 /home/miadmin/.ssh/authorized_keys
```

Prueba el acceso con `miadmin` **en una segunda terminal** antes de continuar; después deshabilita el acceso
por contraseña y de root (fase 2, ver [`checklist-hardening.md`](./checklist-hardening.md)).

> ⚠️ **Nunca cierres la única sesión** mientras edites SSH. Mantén una sesión abierta de respaldo y valida
> con `sudo sshd -t` antes de reiniciar el servicio.

---

## 4. Security Group

El Security Group es el primer control de red (defensa en profundidad) y se aplica **antes** de tocar el
servidor. Reglas de **entrada** recomendadas:

| Tipo | Protocolo | Puerto | Origen | Descripción |
|------|-----------|--------|--------|-------------|
| HTTP | TCP | 80 | `0.0.0.0/0` | Redirección a HTTPS y validación de certbot |
| HTTPS | TCP | 443 | `0.0.0.0/0` | Aplicación y WebSocket (`wss://`) |
| SSH | TCP | 22 | `203.0.113.10/32` | **Solo tu IP de administración** (o cerrado si usas SSM) |
| SSH (IPv6) | TCP | 22 | `2001:db8::/32` | Solo si administras por IPv6 |

Reglas de **salida:** permitir todo (`0.0.0.0/0`), necesario para apt, Let's Encrypt y las dependencias.

```bash
# Añadir la regla de SSH desde tu IP (sustituye la IP y el SG):
aws ec2 authorize-security-group-ingress \
  --group-id sg-XXXXXXXX --protocol tcp --port 22 \
  --cidr 203.0.113.10/32

# Abrir 80 y 443 al mundo:
aws ec2 authorize-security-group-ingress \
  --group-id sg-XXXXXXXX --protocol tcp --port 80 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress \
  --group-id sg-XXXXXXXX --protocol tcp --port 443 --cidr 0.0.0.0/0

# Verificar:
aws ec2 describe-security-groups --group-ids sg-XXXXXXXX \
  --query 'SecurityGroups[].IpPermissions'
```

> 📌 Alternativa sin abrir el 22: **AWS Systems Manager Session Manager** (`aws ssm start-session`), que
> evita exponer SSH. Se cubre como ampliación en la checklist (fase 2b).

---

## 5. Instalación de Docker y Docker Compose

### 5.1 Actualizar el sistema

```bash
sudo apt update && sudo apt upgrade -y
```

### 5.2 Instalar Docker Engine desde el repositorio oficial

> ⚠️ No uses `docker.io` de los repos de Ubuntu: es viejo y no trae el plugin Compose v2. Usa el repo
> oficial de Docker.

```bash
# 1) Dependencias y clave GPG oficial
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 2) Repositorio (detecta la versión de Ubuntu automáticamente)
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 3) Instalar Docker Engine, CLI, containerd y plugins (Buildx y Compose v2)
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 4) Verificar
docker --version
docker compose version        # nota: es "docker compose", no "docker-compose"
```

### 5.3 Post-instalación y usuario en el grupo `docker`

```bash
# Arrancar Docker al inicio del sistema
sudo systemctl enable --now docker

# Permitir a miadmin usar docker sin sudo
sudo usermod -aG docker miadmin
# Cierra sesión y vuelve a entrar para que el grupo surta efecto, luego:
docker ps        # debe funcionar sin sudo
```

> ⚠️ Pertenecer al grupo `docker` equivale a root (puedes montar el sistema de archivos del host). Es una
> decisión consciente para un servidor de un solo administrador; documentarlo como riesgo aceptado.

---

## 6. Obtención del código

### 6.1 Con token de acceso (HTTPS)

```bash
sudo apt install -y git
cd /opt
sudo git clone https://<USUARIO>:<TOKEN>@gitlab.com/organizacion/stream-chat.git streamchat
sudo chown -R miadmin:miadmin /opt/streamchat
cd /opt/streamchat
```

### 6.2 Con deploy key (SSH, recomendado)

```bash
# Genera una clave solo para despliegue y registra la pública en el repositorio (Deploy Key de solo lectura)
ssh-keygen -t ed25519 -C "deploy@streamchat" -f ~/.ssh/deploy_streamchat -N ""
cat ~/.ssh/deploy_streamchat.pub        # pega este contenido como Deploy Key

# Configura el host con la clave de despliegue
cat >> ~/.ssh/config <<'EOF'
Host gitlab.com
  HostName gitlab.com
  User git
  IdentityFile ~/.ssh/deploy_streamchat
  IdentitiesOnly yes
EOF

cd /opt && sudo git clone git@gitlab.com:organizacion/stream-chat.git streamchat
sudo chown -R miadmin:miadmin /opt/streamchat
```

> 📌 El archivo `.gitignore` ya excluye `.env`, `target/`, `*.jar` y `*.log`, así que el clon no trae
> secretos. **El `.env` real se crea solo en el servidor** (paso 7).

---

## 7. Configuración del `.env`

El repositorio incluye `.env.example` (valores de ejemplo). El `.env` real **no** se sube nunca al repo.

### 7.1 Crear el `.env` y protegerlo

```bash
cd /opt/streamchat
cp .env.example .env
chmod 600 .env          # solo el propietario puede leerlo (fase 1: permisos de archivos)
```

### 7.2 Generar un `JWT_SECRET` seguro

El `compose.yaml` exige `JWT_SECRET` (línea `JWT_SECRET: ${JWT_SECRET:?...}`) y no arranca sin él. Genera
un secreto aleatorio de 256 bits codificado en base64:

```bash
openssl rand -base64 32
# Ejemplo de salida: 3nq... (NO uses el ejemplo; genera el tuyo)
```

### 7.3 Editar el `.env`

```bash
nano .env
```

Valores a cambiar respecto a `.env.example` (verifica cada uno contra las variables que consume el
`compose.yaml`):

| Variable | Valor en `.env.example` | Qué poner |
|----------|--------------------------|-----------|
| `JWT_SECRET` | `change-this-to-a-random-...` | El resultado de `openssl rand -base64 32` |
| `POSTGRES_PASSWORD` / `DB_PASSWORD` | `changeme` | Contraseña fuerte y única |
| `RABBITMQ_PASSWORD` | `changeme` | Contraseña fuerte y única |
| `DB_URL` | `jdbc:postgresql://localhost:5433/streamchat` | Déjalo: el `compose.yaml` lo sobreescribe a `postgres:5432` dentro de la red de Docker |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Déjalo: el compose lo fija a `kafka:29092` para la app |
| `PRESENCE_TYPING_TTL_MS` | `5000` | TTL del estado de *typing* (ajusta según diseño) |

> ⚠️ **No cambies las contraseñas después de inicializar los volúmenes** salvo que también las actualices
> dentro de las bases (ver [Operación](#14-operación)). Cambiar `POSTGRES_PASSWORD` en el `.env` con un
> volumen ya creado **no** cambia la contraseña real de PostgreSQL.

---

## 8. Build y despliegue

### 8.1 Construir la imagen

El `Dockerfile` compila primero el módulo `contracts` y luego la aplicación con Maven (`maven:3.9-eclipse-temurin-21`).

```bash
cd /opt/streamchat
docker compose build            # usa el Dockerfile del compose
```

### 8.2 Levantar el entorno

```bash
docker compose up -d
```

### 8.3 Verificar los healthchecks

El compose define `depends_on` con `condition: service_healthy`, así que **la app solo arranca cuando
PostgreSQL, RabbitMQ y Kafka están sanos**. Kafka usa `start_period: 30s` y la app `start_period: 120s`
(la JVM tarda en arrancar, ver sección 15).

```bash
docker compose ps          # todos deben estar "healthy" o "running (healthy)"
```

Salida esperada (resumida):

```
NAME                    STATUS
streamchat-postgres     Up ... (healthy)
streamchat-rabbitmq     Up ... (healthy)
streamchat-zookeeper    Up ... (healthy)
streamchat-kafka        Up ... (healthy)
streamchat-app          Up ... (healthy)   # puede tardar ~2 min por la JVM
```

Comprobaciones puntuales:

```bash
# Estado de la app por su endpoint de health (management.endpoints expone health,info,metrics):
curl -fsS http://127.0.0.1:8080/actuator/health

# Base de datos lista:
docker compose exec postgres pg_isready -U streamchat -d streamchat

# Logs en vivo de la app:
docker compose logs -f app
```

> 📌 La app publica `127.0.0.1:8080:8080`: en este punto solo es accesible **desde el propio servidor**.
> Nginx (paso 9) será el único punto público.

---

## 9. Nginx y TLS con Let's Encrypt

### 9.1 DNS

Crea un registro **A** `midominio.com` → IP elástica de la instancia, y verifica la propagación:

```bash
dig +short midominio.com     # debe devolver la IP elástica
```

### 9.2 Instalar Nginx y desplegar el proxy

```bash
sudo apt install -y nginx

# Copia la configuración del repositorio:
sudo cp /opt/streamchat/infra/nginx/streamchat.conf /etc/nginx/sites-available/streamchat.conf
sudo ln -s /etc/nginx/sites-available/streamchat.conf /etc/nginx/sites-enabled/streamchat.conf

# Quita el sitio por defecto para evitar conflictos en el puerto 80:
sudo rm -f /etc/nginx/sites-enabled/default

# Valida y recarga:
sudo nginx -t
sudo systemctl reload nginx
```

`infra/nginx/streamchat.conf` debe cumplir los puntos de la sección 10.3 del plan:
- **WebSocket:** `proxy_http_version 1.1` y cabeceras `Upgrade`/`Connection` hacia `127.0.0.1:8080/ws`,
  con `proxy_read_timeout` amplio para no cerrar conexiones ociosas.
- **Seguridad:** `server_tokens off`, cabeceras de seguridad, rate limiting (`limit_req`, `limit_conn`).

### 9.3 TLS con certbot

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d midominio.com -d www.midominio.com
# Introduce un correo y acepta la redirección a HTTPS.
```

Certbot edita el sitio para servir `https://` y programa la **renovación automática**. Verifícala:

```bash
sudo certbot renew --dry-run
systemctl list-timers | grep certbot
```

---

## 10. Firewall y Fail2ban

### 10.1 Aplicar nftables

Usa la configuración del repositorio y **valida la sintaxis antes de aplicar** (sección 10.4 del plan):

```bash
sudo cp /opt/streamchat/infra/nftables/nftables.conf /etc/nftables.conf

# 1) Validar SIN aplicar:
sudo nft -c -f /etc/nftables.conf

# 2) Habilitar y aplicar:
sudo systemctl enable --now nftables
sudo systemctl restart nftables
sudo nft list ruleset
```

> ⚠️ **Docker + nftables.** Docker instala sus propias reglas y no uses `flush ruleset` global (borra las de
> Docker). Además, con Docker debes **conservar** `net.ipv4.ip_forward = 1` y no poner `forward` en `drop`
> sin reglas de estado, o los contenedores pierden la red. Tras aplicar el firewall, **reinicia Docker** y
> verifica la conectividad de los contenedores:
> ```bash
> sudo systemctl restart docker
> docker compose exec app curl -fsS http://rabbitmq:15672 >/dev/null && echo "red de contenedores OK"
> ```

### 10.2 Fail2ban

```bash
sudo apt install -y fail2ban
sudo systemctl enable --now fail2ban
sudo fail2ban-client status            # debe listar la jail sshd (y nginx-* si se configura)
sudo fail2ban-client status sshd
```

Configura jails para `sshd`, `nginx-*` y `recidive` (las de Nginx vigilan los logs del proxy).

---

## 11. Monitoreo

### 11.1 Herramientas base

```bash
sudo apt install -y sysstat htop iotop-c ncdu auditd aide rkhunter
```

### 11.2 Script de recursos por cron

```bash
sudo install -m 755 /opt/streamchat/infra/monitoring/check-resources.sh /usr/local/bin/check-resources.sh

# Ejecutar cada 5 minutos (cron del sistema):
echo "*/5 * * * * root /usr/local/bin/check-resources.sh" | sudo tee /etc/cron.d/streamchat-resources

# Verificar: el script escribe con el tag 'resource-alert' en journald
sudo journalctl -t resource-alert --since "10 minutes ago"
```

### 11.3 CloudWatch Agent y alarmas (ampliación)

- Instala el **CloudWatch Agent** y envía **memoria** y **disco** (CloudWatch no los recoge por defecto).
- Crea alarmas: **CPU > 85 % durante 15 min**, **memoria > 90 % durante 10 min**, **disco > 80 %**.

### 11.4 Auditoría (ampliación)

```bash
sudo aideinit && sudo mv /var/lib/aide/aide.db.new /var/lib/aide/aide.db   # una vez
sudo rkhunter --update && sudo rkhunter --check --sk
sudo lynis audit system                                                    # objetivo: índice > 80
```

---

## 12. Endurecimiento de systemd (opcional)

Esta ruta aplica **solo si despliegas sin Docker** (el `.jar` directo). Si usas Docker Compose, sáltala.

```bash
sudo cp /opt/streamchat/infra/systemd/streamchat.service /etc/systemd/system/streamchat.service
sudo systemctl daemon-reload
sudo systemctl enable --now streamchat.service
systemctl status streamchat.service

# Ver el endurecimiento y el objetivo (< 3 en la fase 5):
systemctl cat streamchat.service
systemd-analyze security streamchat.service
```

El unit incluye `NoNewPrivileges=yes`, `ProtectSystem=strict`, `MemoryMax` y `CPUQuota` (equivalen a los
`deploy.resources.limits` del compose).

---

## 13. Verificación final

Cierra el hito 8 ejecutando las pruebas de la sección 10.5 del plan. Las de puertos y SSH se lanzan
**desde fuera** de la instancia.

```bash
# 1) Escaneo de puertos: solo 80 y 443 (y 22 desde tu IP)
nmap -Pn -p- 203.0.113.10

# 2) Puertos internos NO alcanzables desde fuera
nmap -Pn -p 5433,5672,9092,15672 203.0.113.10

# 3) SSH como root debe fallar
ssh root@203.0.113.10

# 4) SSH solo con contraseña debe fallar
ssh -o PubkeyAuthentication=no miadmin@203.0.113.10
```

Y **dentro** del servidor:

```bash
# Solo 22/80/443 y servicios en 127.0.0.1 (8080, 5433, 5672, 9092...)
sudo ss -tulpn

# HTTPS responde sin revelar la versión de Nginx
curl -I https://midominio.com

# (Ampliación) índice de Lynis
sudo lynis audit system
```

Resultados esperados:
- `nmap` externo → **solo** `80` y `443` (y `22` desde tu IP).
- `ssh root@...` y `ssh -o PubkeyAuthentication=no ...` → *Permission denied*.
- `ss -tulpn` → solo 22/80/443 en `0.0.0.0`/`::`, y los internos solo en `127.0.0.1`.
- **SSL Labs** → nota **A**.

---

## 14. Operación

### 14.1 Actualizar la aplicación

```bash
cd /opt/streamchat
git pull                          # trae los cambios
docker compose build              # reconstruye la imagen
docker compose up -d              # recrea solo lo que cambió
docker compose ps                 # verificar healthchecks
```

### 14.2 Ver logs

```bash
docker compose logs -f app                     # app en vivo
docker compose logs --since 1h postgres        # últimas horas de la BD
docker compose logs kafka rabbitmq             # brokers
sudo journalctl -u nginx --since "30 min ago"  # proxy
```

### 14.3 Reiniciar tras un cambio de `.env`

```bash
nano .env
chmod 600 .env
docker compose up -d --force-recreate app      # recrea la app con las nuevas variables
```

### 14.4 Backup de los volúmenes de PostgreSQL

```bash
# Volcado lógico (recomendado; consistente y portable)
mkdir -p /var/backups/streamchat
docker compose exec -T postgres pg_dump -U streamchat streamchat \
  | gzip > /var/backups/streamchat/streamchat-$(date +%F).sql.gz

# Copia de seguridad fuera de la instancia (S3 con versionado):
aws s3 cp /var/backups/streamchat/streamchat-$(date +%F).sql.gz s3://mi-bucket-backups/
```

> 🔁 **Prueba la restauración**, no solo el backup: restaura el volcado en un entorno de prueba y verifica
> que el historial de mensajes sigue completo. Un backup que nunca se restauró no es un backup.

Restauración:

```bash
docker compose stop app
gunzip -c /var/backups/streamchat/streamchat-2026-11-18.sql.gz \
  | docker compose exec -T postgres psql -U streamchat -d streamchat
docker compose start app
```

---

## 15. Errores comunes y solución de problemas

| Síntoma | Causa | Solución |
|---------|-------|----------|
| `streamchat-app` se reinicia en bucle al arrancar | La **JVM tarda ~100 s**; el healthcheck la marca insana pronto | Usa `start_period` en el healthcheck (el compose ya usa `120s`); espera y revisa `docker compose logs app` |
| `depends_on` de Kafka nunca pasa a *healthy* | Kafka y Zookeeper arrancan lento | El compose usa `start_period: 30s` y 10 reintentos; dale tiempo antes de diagnosticar |
| La app no conecta a Kafka | Se usa `localhost:9092` en lugar de `kafka:29092` | Dentro de Compose la app usa `KAFKA_BOOTSTRAP_SERVERS=kafka:29092` (lo fija el compose) |
| `JWT_SECRET:?define JWT_SECRET...` al levantar | Falta `JWT_SECRET` en `.env` | Crea el `.env` real (paso 7) y genera el secreto |
| Contenedores sin red tras aplicar el firewall | `flush ruleset` o `forward` en `drop` | No uses `flush ruleset` global; conserva `net.ipv4.ip_forward=1`; reinicia Docker |
| `curl -I https://...` da error de certificado | DNS aún no propaga o certbot no corrió | Verifica `dig`, corre `sudo certbot --nginx` de nuevo |
| WebSocket se cae a los ~30 s | `proxy_read_timeout` corto en Nginx | Ajusta `proxy_read_timeout`, envía *heartbeats* y usa `wss://` |
| Puertos internos accesibles desde fuera | Se publicaron en `0.0.0.0` en lugar de `127.0.0.1` | En `compose.yaml` ya están como `127.0.0.1:puerto:puerto`; no los cambies |
| `nft -c -f` falla con "syntax error" | IP con máscara mal escrita o set mal definido | Usa `203.0.113.10/32` y `flags interval`; valida antes de aplicar |
| OOM: contenedor reiniciado | Instancia pequeña (`t3.medium`) | Migra a `t3.large`; el heap ya está en `MaxRAMPercentage=75` |
| Memoria no aparece en CloudWatch | CloudWatch no mide memoria por defecto | Instala el CloudWatch Agent (paso 11.3) |

---

## 16. Chuleta: comando → para qué sirve

| Comando | Para qué sirve |
|---------|----------------|
| `ssh -i streamchat-key.pem ubuntu@203.0.113.10` | Primer acceso SSH a la instancia |
| `sudo sshd -t` | Validar la sintaxis de SSH **antes** de reiniciar |
| `sudo nft -c -f /etc/nftables.conf` | Validar el firewall **antes** de aplicarlo |
| `sudo nft list ruleset` | Ver las reglas de firewall activas |
| `sudo fail2ban-client status sshd` | Ver IPs baneadas y estado de la jail SSH |
| `docker compose build` | Construir/actualizar la imagen de la app |
| `docker compose up -d` | Levantar/actualizar todos los servicios |
| `docker compose up -d --force-recreate app` | Recrear la app tras cambiar el `.env` |
| `docker compose ps` | Ver estado y healthchecks de los contenedores |
| `docker compose logs -f app` | Ver en vivo los logs de la aplicación |
| `curl -fsS http://127.0.0.1:8080/actuator/health` | Comprobar que la app está sana |
| `sudo ss -tulpn` | Ver puertos y procesos en escucha (solo 22/80/443 + 127.0.0.1) |
| `nmap -Pn -p- 203.0.113.10` | Escaneo de puertos desde fuera (verificación 10.5) |
| `openssl rand -base64 32` | Generar un `JWT_SECRET` seguro |
| `sudo certbot --nginx -d midominio.com` | Emitir/renovar el certificado TLS |
| `sudo certbot renew --dry-run` | Probar la renovación automática del certificado |
| `docker compose exec -T postgres pg_dump -U streamchat streamchat` | Backup lógico de PostgreSQL |
| `sudo journalctl -t resource-alert` | Ver alertas del script de recursos |
| `sudo lynis audit system` | Auditoría de seguridad del sistema (índice objetivo > 80) |

---

*Documento académico del proyecto Stream-Chat. Referencia: secciones 6, 10 (10.1–10.5) y 11 del
`Plan_Trabajo_Stream_Chat_3_Programadores_v2.md`. Complementos: [`checklist-hardening.md`](./checklist-hardening.md)
y [`runbook-incidentes.md`](./runbook-incidentes.md).*
