# Checklist de hardening — Stream-Chat (Ubuntu Server en AWS EC2)

> **Hito 8 — despliegue seguro.** Esta checklist es la versión *marcable* de la tabla de fases 0–9 de la
> **sección 10.2** del plan de trabajo (`Plan_Trabajo_Stream_Chat_3_Programadores_v2.md`). Cada ítem indica
> si pertenece al **Núcleo (obligatorio)** o a la **Ampliación (opcional, si hay tiempo)**, según la
> **sección 6** ("Qué es obligatorio y qué es opcional"), y el comando con el que se verifica cuando aplica.

**Proyecto:** Stream-Chat — Spring Boot 3.2.5 / Java 21 + PostgreSQL + RabbitMQ + Kafka, desplegado con
Docker Compose sobre Ubuntu Server LTS en AWS EC2.
**Responsable principal:** Programador 3 (Presence & DevOps). **Expone:** Esteban Lasso (ID 898780).

---

## Cómo usar esta checklist

1. Trabaja fase por fase, de la **0 a la 9**. Las fases siguen el orden de la presentación
   *Hardening de Ubuntu Server en AWS EC2* y de la sección 10.2 del plan.
2. Marca `- [x]` solo cuando el comando de verificación de la fila devuelva el **resultado esperado**.
3. Antes de cerrar cada fase anota la evidencia (captura o salida de consola) en la tabla final
   **"Registro de evidencias"**; estas alimentan la sección 10.5 del plan.
4. **Leyenda de alcance:**
   - **Núcleo** → obligatorio para el hito 8.
   - **Ampliación** → se ejecuta solo si el cronograma lo permite.

> ⚠️ **Advertencia general.** Aplica siempre los cambios de SSH y de firewall con **una segunda sesión ya
> abierta** como red de seguridad. Un error en `sshd_config` o en las reglas de nftables puede dejarte
> fuera del servidor. Valida con `sshd -t` y `nft -c` **antes** de aplicar.

---

## Fase 0 — Capa AWS (consola / IAM / Security Group)

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | MFA habilitado en la cuenta **root** y sin uso diario de root | Núcleo | Consola IAM → *Security credentials* → MFA activo |
| - [ ] | Usuario IAM de trabajo con **mínimo privilegio** (no usar root ni claves de larga vida) | Núcleo | `aws sts get-caller-identity` muestra el usuario IAM, no root |
| - [ ] | **Security Group**: `80` y `443` abiertos a `0.0.0.0/0`; `22` **solo** desde la IP de administración `/32` (o sin `22` si se usa SSM) | Núcleo | `aws ec2 describe-security-groups --group-ids sg-XXXXXXXX` |
| - [ ] | **IMDSv2** obligatorio (`HttpTokens=required`, `HttpPutResponseHopLimit=1`) | Núcleo | `aws ec2 describe-instances --instance-ids i-XXXXXXXX --query 'Reservations[].Instances[].MetadataOptions'` |
| - [ ] | **Volumen EBS cifrado** en reposo (`Encrypted=true`) | Núcleo | Consola EC2 → *Volumes* → columna *Encrypted* = Yes |
| - [ ] | Sin claves de acceso AWS guardadas en el servidor (`~/.aws/credentials` ausente) | Núcleo | `ls -la ~/.aws 2>/dev/null` (no debe existir) |
| - [ ] | **CloudTrail** activo (registro de la API de AWS) | Ampliación | Consola CloudTrail → *Event history* con eventos |
| - [ ] | **GuardDuty** habilitado (detección de amenazas) | Ampliación | Consola GuardDuty → estado *Enabled* |

> 📌 Nota: los identificadores `sg-XXXXXXXX` e `i-XXXXXXXX` son **placeholders**. Sustitúyelos por los
> reales de tu cuenta al ejecutar los comandos.

---

## Fase 1 — Actualizaciones y cuentas

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | Paquetes al día antes de empezar | Núcleo | `sudo apt update && sudo apt upgrade -y` |
| - [ ] | `unattended-upgrades` instalado y con **actualizaciones de seguridad automáticas** | Núcleo | `systemctl status unattended-upgrades` → active (running) |
| - [ ] | `needrestart` instalado (reinicia servicios tras actualizar librerías) | Núcleo | `dpkg -l needrestart` |
| - [ ] | Usuario **administrador propio** (no `ubuntu`, no `root`) con `sudo` | Núcleo | `id miadmin; groups miadmin` |
| - [ ] | Cuentas `root` y `ubuntu` **bloqueadas para login** | Núcleo | `sudo passwd -S root` / `sudo passwd -S ubuntu` → `L` (locked) |
| - [ ] | Política de contraseñas con `libpam-pwquality` (longitud, complejidad) | Núcleo | `dpkg -l libpam-pwquality` y revisar `/etc/security/pwquality.conf` |
| - [ ] | Bloqueo tras intentos fallidos con `faillock` (en `/etc/pam.d/common-auth`) | Núcleo | `faillock --user miadmin` |
| - [ ] | `sudo` endurecido: `use_pty` y `timestamp_timeout=5` en `/etc/sudoers.d/` | Núcleo | `sudo grep -rE 'use_pty|timestamp_timeout' /etc/sudoers /etc/sudoers.d/` |
| - [ ] | Contraseñas de servicios/BD rotadas (no quedan los `changeme` de `.env.example`) | Núcleo | Revisar `.env` real (permisos 600) |

---

## Fase 2 — SSH

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | Archivo `01-hardening.conf` creado en `/etc/ssh/sshd_config.d/` | Núcleo | `ls /etc/ssh/sshd_config.d/01-hardening.conf` |
| - [ ] | `PermitRootLogin no` | Núcleo | `sudo sshd -T \| grep permitrootlogin` → `no` |
| - [ ] | `PasswordAuthentication no` (solo claves públicas) | Núcleo | `sudo sshd -T \| grep passwordauthentication` → `no` |
| - [ ] | `AllowUsers miadmin` (solo el usuario de administración puede entrar) | Núcleo | `sudo sshd -T \| grep allowusers` |
| - [ ] | `MaxAuthTries 3` | Núcleo | `sudo sshd -T \| grep maxauthtries` → `3` |
| - [ ] | `LoginGraceTime 30` | Núcleo | `sudo sshd -T \| grep logingracetime` → `30` |
| - [ ] | **Validación de sintaxis** antes de reiniciar el servicio | Núcleo | `sudo sshd -t` (sin salida = correcto) |
| - [ ] | Login como `root` por SSH **falla** | Núcleo | `ssh root@203.0.113.10` → *Permission denied* |
| - [ ] | Login con contraseña **falla** | Núcleo | `ssh -o PubkeyAuthentication=no miadmin@203.0.113.10` → *Permission denied* |
| - [ ] | **MFA por TOTP** (`libpam-google-authenticator`, `AuthenticationMethods publickey,keyboard-interactive`) | Ampliación | `sudo sshd -T \| grep authenticationmethods` |
| - [ ] | Alternativa sin puerto 22: **AWS Systems Manager Session Manager** | Ampliación | `aws ssm start-session --target i-XXXXXXXX` |

> ⚠️ Prueba `sshd -t` y abre una **segunda sesión** con la nueva configuración antes de cerrar la actual.
> Si algo falla, recupera el acceso por la consola serie de EC2.

---

## Fase 3 — Firewall (nftables) y Fail2ban

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | `nftables` instalado; se usa **nftables o UFW, nunca ambos** | Núcleo | `sudo nft list ruleset` |
| - [ ] | `infra/nftables/nftables.conf`: política `drop` de entrada, estado de conexión (`ct state`) | Núcleo | `sudo nft list ruleset \| grep policy` |
| - [ ] | Regla de `admin_ips` (SSH solo desde IP de administración) **antes** de la anti-escaneo | Núcleo | `sudo nft list chain inet firewall input` |
| - [ ] | Puertos `80` y `443` aceptados (`tcp dport { 80, 443 } ct state new accept`) | Núcleo | `sudo nft list ruleset \| grep -E '80, 443'` |
| - [ ] | Sin `flush ruleset` global (no borra las reglas que instala Docker) | Núcleo | Revisar `/etc/nftables.conf` |
| - [ ] | Rule **forward** compatible con Docker (`net.ipv4.ip_forward = 1` conservado) | Núcleo | `cat /proc/sys/net/ipv4/ip_forward` → `1` |
| - [ ] | Configuración validada antes de aplicar | Núcleo | `sudo nft -c -f /etc/nftables.conf` |
| - [ ] | nftables habilitado al arranque | Núcleo | `systemctl is-enabled nftables` |
| - [ ] | Log de descartes en la entrada (`log prefix "nft-drop-in: " limit rate 10/minute`) | Núcleo | `sudo nft list chain inet firewall input` |
| - [ ] | **Fail2ban** con jails `sshd`, `nginx-*` y `recidive` | Ampliación | `sudo fail2ban-client status` |
| - [ ] | Estado de Fail2ban de la jail sshd | Ampliación | `sudo fail2ban-client status sshd` |

> 📌 Recuerda revisar la sección 10.4 del plan: las correcciones al `nftables.txt` original (IP válida,
> puerto 443, sin `flush ruleset`, orden de reglas, ICMP con límite, registro de descartes).

---

## Fase 4 — Kernel, `/tmp`, `/dev/shm` y servicios

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | `sysctl`: `rp_filter`, `tcp_syncookies`, `randomize_va_space = 2`, `ptrace_scope = 2` | Núcleo | `sysctl kernel.randomize_va_space` → `2` |
| - [ ] | `/tmp` montado con `noexec,nosuid,nodev` | Núcleo | `findmnt /tmp` |
| - [ ] | `/dev/shm` montado con `noexec,nosuid,nodev` | Núcleo | `findmnt /dev/shm` |
| - [ ] | Puertos en escucha revisados (solo 22, 80, 443 y `127.0.0.1`) | Núcleo | `sudo ss -tulpn` |
| - [ ] | Módulos del kernel innecesarios bloqueados (`/etc/modprobe.d/`) | Ampliación | `lsmod` comparado con la lista de bloqueo |
| - [ ] | Servicios innecesarios deshabilitados (p. ej. `cups`, `avahi`) | Ampliación | `systemctl list-unit-files --state=enabled` |
| - [ ] | IPv6 evaluado (si aplica, reglas `icmpv6` necesarias para el descubrimiento de vecinos) | Ampliación | `sysctl net.ipv6.conf.all.disable_ipv6` |

---

## Fase 5 — Aplicación confinada (AppArmor y systemd)

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | AppArmor activo | Ampliación | `sudo aa-status` |
| - [ ] | Perfil de AppArmor para el servicio (si corre sin Docker) | Ampliación | `sudo aa-status \| grep streamchat` |
| - [ ] | `infra/systemd/streamchat.service` con `NoNewPrivileges=yes`, `ProtectSystem=strict` | Ampliación | `systemctl cat streamchat.service` |
| - [ ] | Límites de recursos `MemoryMax` / `CPUQuota` definidos (alternativa a Docker) | Ampliación | `systemctl show streamchat.service -p MemoryMax -p CPUQuota` |
| - [ ] | Objetivo de `systemd-analyze security` **menor a 3** | Ampliación | `systemd-analyze security streamchat.service` |
| - [ ] | Auditoría de binarios SUID razonable | Ampliación | `find / -perm -4000 -type f 2>/dev/null` |

> 📌 En este proyecto la aplicación corre **dentro de Docker**, así que el endurecimiento de systemd aplica
> sobre todo si se decide desplegar el `.jar` directamente con `infra/systemd/streamchat.service`.

---

## Fase 6 — Reverse proxy Nginx (TLS)

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | Nginx instalado y activo | Núcleo | `systemctl status nginx` |
| - [ ] | `infra/nginx/streamchat.conf` desplegado en `/etc/nginx/sites-available/` y enlazado | Núcleo | `nginx -t` |
| - [ ] | **TLS con Let's Encrypt** (certbot) y renovación automática | Núcleo | `sudo certbot renew --dry-run` |
| - [ ] | DNS: registro **A** `midominio.com` → IP de la instancia | Núcleo | `dig +short midominio.com` |
| - [ ] | `server_tokens off` (no revela la versión de Nginx) | Núcleo | `curl -I https://midominio.com \| grep -i server` |
| - [ ] | Cabeceras de seguridad (`HSTS`, `X-Content-Type-Options`, `X-Frame-Options`) | Núcleo | `curl -I https://midominio.com` |
| - [ ] | Rate limiting (`limit_req`) y `limit_conn` configurados | Núcleo | `nginx -T \| grep limit_req` |
| - [ ] | **WebSocket** proxeado correctamente: `proxy_http_version 1.1`, cabeceras `Upgrade`/`Connection`, `proxy_read_timeout` amplio | Núcleo | Conectar a `wss://midominio.com/ws` y ver tráfico sostenido |
| - [ ] | `limit_conn perip` revisado (NAT puede agrupar varios usuarios bajo una IP) | Núcleo | `nginx -T \| grep limit_conn` |
| - [ ] | **ModSecurity** primero en `DetectionOnly` | Ampliación | `nginx -T \| grep -i modsecurity` |
| - [ ] | SSL Labs con nota **A** | Núcleo | https://www.ssllabs.com/ssltest/ |

---

## Fase 7 — Forward proxy Squid (ampliación)

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | Squid instalado con **lista blanca** de dominios | Ampliación | `systemctl status squid` |
| - [ ] | Regla de nftables que **fuerza** la salida por el proxy | Ampliación | `sudo nft list ruleset \| grep -i squid` |
| - [ ] | Salida directa bloqueada y verificada (si el objetivo es controlar exfiltración) | Ampliación | `curl --max-time 5 https://example.org` desde el host |

> 📌 Objetivo de la fase: limitar la **salida**. La sección 10.4 advierte que solo bloquear algunos DNS
> públicos no es un control de exfiltración real (se evade con DoH por el 443).

---

## Fase 8 — Monitoreo y auditoría

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | `sysstat` instalado y recolección activa | Núcleo | `systemctl status sysstat` |
| - [ ] | `htop` y `iotop-c` instalados | Núcleo | `htop --version`, `iotop-c --version` |
| - [ ] | `ncdu` instalado (análisis de disco) | Núcleo | `ncdu --version` |
| - [ ] | `infra/monitoring/check-resources.sh` ejecutado cada 5 min por cron | Núcleo | `ls /etc/cron.d/`, `journalctl -t resource-alert` |
| - [ ] | **CloudWatch Agent** enviando memoria y disco | Ampliación | `sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -m ec2 -a status` |
| - [ ] | Alarmas: CPU > 85 % (15 min), memoria > 90 % (10 min), disco > 80 % | Ampliación | Consola CloudWatch → *Alarms* |
| - [ ] | `auditd` activo con reglas sobre archivos sensibles | Ampliación | `sudo auditctl -l` |
| - [ ] | **AIDE** inicializado (integridad de archivos) | Ampliación | `sudo aide --check` (primera vez: `--init`) |
| - [ ] | `rkhunter` instalado y con base actualizada | Ampliación | `sudo rkhunter --check --sk` |
| - [ ] | **Lynis** ejecutado con **índice mayor a 80** | Ampliación | `sudo lynis audit system` |
| - [ ] | Retención de logs configurada (rotación) | Núcleo | `cat /etc/logrotate.d/* \| grep -A2 rotate` |

---

## Fase 9 — Respuesta a incidentes y backups

| ✔ | Ítem | Alcance | Verificación |
|---|------|---------|--------------|
| - [ ] | **Runbook de incidentes** de una página redactado | Ampliación | Ver [`runbook-incidentes.md`](./runbook-incidentes.md) |
| - [ ] | Backups de PostgreSQL (volumen `postgres_data`) programados | Ampliación | `ls /var/backups/streamchat/` |
| - [ ] | Backup **probado con restauración** (no solo generado) | Ampliación | Restaurar en un entorno de prueba y contar mensajes |
| - [ ] | Backups inmutables / fuera de la instancia (S3 con versionado) | Ampliación | Consola S3 → *Object versions* |
| - [ ] | Roles y contactos del equipo documentados en el runbook | Ampliación | Ver [`runbook-incidentes.md`](./runbook-incidentes.md) |

---

## Verificación final (sección 10.5 del plan)

Ejecuta estas comprobaciones **desde fuera** de la instancia, como cierre del hito 8:

| ✔ | Prueba | Resultado esperado | Comando |
|---|--------|--------------------|---------|
| - [ ] | Escaneo de puertos | Solo `80` y `443` (y `22` desde la IP de administración) | `nmap -Pn -p- 203.0.113.10` |
| - [ ] | SSH como root | Falla | `ssh root@203.0.113.10` |
| - [ ] | SSH solo con contraseña | Falla | `ssh -o PubkeyAuthentication=no miadmin@203.0.113.10` |
| - [ ] | Puertos en escucha local | Solo 22/80/443 y `127.0.0.1` | `sudo ss -tulpn` |
| - [ ] | HTTPS sin versión de Nginx | Sin cabecera de versión | `curl -I https://midominio.com` |
| - [ ] | Calidad TLS | Nota **A** | SSL Labs |
| - [ ] | Lynis (si se hizo la ampliación) | Índice **> 80** | `sudo lynis audit system` |
| - [ ] | Puertos internos (PostgreSQL 5433, RabbitMQ 5672/15672, Kafka 9092) desde fuera | **No alcanzables** | `nmap -Pn -p 5433,5672,9092,15672 203.0.113.10` |

> 📌 En `compose.yaml` estos servicios ya se publican solo en `127.0.0.1` (por ejemplo `127.0.0.1:5433:5432`),
> por lo que desde Internet **no** deben ser alcanzables aunque el firewall no los bloquee explícitamente.

---

## Registro de evidencias

Rellena una fila por fase con la evidencia recolectada (alimenta la sección 10.5 del plan y el informe de SO).

| Fase | Alcance cerrado | Evidencia (comando / captura) | Fecha | Responsable |
|------|------------------|-------------------------------|-------|-------------|
| 0 — Capa AWS | | | | |
| 1 — Cuentas | | | | |
| 2 — SSH | | | | |
| 3 — Firewall | | | | |
| 4 — Kernel | | | | |
| 5 — App confinada | | | | |
| 6 — Nginx TLS | | | | |
| 7 — Squid | | | | |
| 8 — Monitoreo | | | | |
| 9 — Incidentes | | | | |

---

*Documento académico del proyecto Stream-Chat. Referencia: secciones 5, 6, 10 y 11 del
`Plan_Trabajo_Stream_Chat_3_Programadores_v2.md`.*
