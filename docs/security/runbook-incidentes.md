# Runbook de respuesta a incidentes — Stream-Chat

> **Una página.** Fase 9 de la sección 10.2 del plan de trabajo. Une los pasos que indica la presentación de
> hardening: **detectar → no apagar → aislar → revocar credenciales → preservar evidencia → analizar →
> recuperar → documentar.** Responsable: Programador 3. Expone: **Esteban Lasso (ID 898780)**.

**Regla de oro:** ante un incidente sospechoso, **no apagues ni destruyas la instancia**. Apagar borra la
memoria y el estado volátil, que es justamente la evidencia. Primero **aísla** la red, luego preserva la
evidencia y solo después recupera.

> ⚠️ Los valores `i-XXXXXXXX`, `203.0.113.10` y `midominio.com` son **placeholders**; sustitúyelos por los
> reales. Nunca pegues secretos reales (`JWT_SECRET`, contraseñas) en capturas, tickets ni en el chat.

---

## Contactos y roles

| Rol | Persona | Responsabilidad en el incidente | Contacto |
|-----|---------|----------------------------------|----------|
| Incident Commander | Esteban Lasso (ID 898780) | Coordina, decide el aislamiento y aprueba el post-mortem | *(correo / chat del equipo)* |
| Chat Core (P1) | Por definir | Revisa tokens, autenticación y datos en PostgreSQL | *(correo / chat)* |
| Messaging (P2) | Por definir | Revisa RabbitMQ/Kafka y credenciales de brokers | *(correo / chat)* |
| Infra & Red (P3) | Por definir | nftables, Security Group, nftables/Squid, Nginx | *(correo / chat)* |
| Escalamiento AWS | AWS Support / TAM | Incidentes que afectan la cuenta (compromiso de credenciales IAM) | Consola AWS → *Support* |

---

## Paso 1 — Detectar

Síntomas típicos: alarma de CloudWatch (CPU/memoria/disco), `check-resources.sh` en
`journalctl -t resource-alert`, tráfico anómalo en `ss -tunap`, logins SSH desconocidos en
`journalctl -u ssh`, o consumo de CPU inesperado.

```bash
sudo journalctl -t resource-alert --since "1 hour ago"     # alertas del script de recursos
sudo journalctl -u ssh --since "1 hour ago"                # intentos de login SSH
sudo ss -tunap                                             # conexiones activas y su proceso
sudo tail -n 200 /var/log/auth.log                         # autenticaciones
docker compose ps                                          # estado de los contenedores
```

**Registra la hora exacta de detección** (UTC): es el `T0` del incidente.

## Paso 2 — No apagar (preservar el estado volátil)

- **No** hagas `shutdown`, **no** reinicies la instancia, **no** hagas `docker compose down -v`.
- Recupera primero lo volátil (procesos, conexiones, memoria) porque desaparece al reiniciar.
- Anota qué usuario está conectado: `who`, `w`, `last -a | head`.

## Paso 3 — Aislar

Corta el acceso sin destruir la máquina. Dos niveles, de menor a mayor impacto:

```bash
# a) Fail2ban: banear la IP atacante en la jail sshd
sudo fail2ban-client set sshd banip 198.51.100.23

# b) nftables: registrar y descartar la IP (usar el set con flags timeout)
sudo nft add element inet firewall blocklist '{ 198.51.100.23 timeout 1h }'

# c) AWS Security Group: quitar temporalmente el 80/443 al mundo y dejar solo tu IP
aws ec2 revoke-security-group-ingress --group-id sg-XXXXXXXX \
  --protocol tcp --port 443 --cidr 0.0.0.0/0
```

Si el compromiso es grave y hay que cortar todo sin apagar: cambia las reglas de entrada del Security Group
para permitir **solo** tu IP de administración `/32` y deja el 80/443 cerrado. Valida la sintaxis del
firewall antes de aplicarlo: `sudo nft -c -f /etc/nftables.conf`.

## Paso 4 — Revocar credenciales

Asume que **todo lo que estaba en el servidor está comprometido** y rota en este orden:

```bash
# 1) Claves SSH: quita la autorizada sospechosa y genera/instala una nueva
sudo cat /home/miadmin/.ssh/authorized_keys          # inspecciona y elimina líneas desconocidas
ssh-keygen -t ed25519 -C "miadmin@streamchat-nueva"  # genera el par nuevo en tu equipo

# 2) Tokens de la app: rota JWT_SECRET (invalida TODOS los tokens emitidos)
#    Genera un secreto aleatorio de 256 bits codificado en base64:
openssl rand -base64 32
#    Copia el valor en el .env real (permisos 600) y recrea SOLO el contenedor de la app:
sudo chmod 600 .env
docker compose up -d --force-recreate app

# 3) Contraseñas de brokers y base de datos (.env):
#    POSTGRES_PASSWORD, RABBITMQ_PASSWORD — cámbialas por valores nuevos y fuertes.
docker compose up -d --force-recreate postgres rabbitmq
```

> ⚠️ **Cuidado con PostgreSQL:** cambiar `POSTGRES_PASSWORD` en `.env` **no** cambia la contraseña de un
> volumen ya inicializado. Hay que cambiarla también dentro de la base:
> ```bash
> docker compose exec postgres psql -U streamchat -d streamchat \
>   -c "ALTER USER streamchat WITH PASSWORD 'NuevaContraseñaFuerte';"
> ```
> Rotar `JWT_SECRET` cierra sesión a **todos** los usuarios (comportamiento deseado tras un incidente).

## Paso 5 — Preservar evidencia

Guarda copias **antes** de que la rotación de logs las borre, y deja constancia de la cadena de custodia:

```bash
sudo mkdir -p /root/incidente-$(date +%F)
sudo journalctl --since "24 hours ago" -o short-precise > /root/incidente-*/journald.log
sudo ss -tunap > /root/incidente-*/conexiones.txt
sudo chmod 700 /root/incidente-*           # solo root, sin passwords en texto si se puede
docker compose logs --since 24h > /root/incidente-*/compose.log 2>&1
```

Evidencia a recolectar:

- **journald**: `journalctl` (auth, ssh, resource-alert) exportado a archivo.
- **CloudWatch**: métricas y alarmas del periodo del incidente (captura con marca de tiempo).
- **Procesos y red**: salida de `ss -tunap`, `ps auxf`, `pstree -p`.
- **Historial**: `history` del usuario (no lo borres; exporta `cat ~/.bash_history`).
- **Contenedores**: estado e logs de `docker compose` y `docker inspect`.
- **Integridad**: `sudo aide --check` y `sudo rkhunter --check --sk` (si están instalados).

Pasa la copia a un almacenamiento fuera de la instancia (S3 con versionado) para conservarla aunque la
instancia se pierda.

## Paso 6 — Analizar

- Reconstruye la línea temporal (`T0` detección, aislamiento, rotación).
- Identifica la **causa raíz** y el **vector**: ¿contraseña débil, clave filtrada, servicio expuesto,
  contenedor con privilegios, dependencia vulnerable?
- Comprueba si hubo movimiento lateral: conexiones a brokers/BD, creación de usuarios, cron nuevo.
- Verifica qué puertos estaban expuestos (deberían ser solo 22/80/443): `sudo ss -tulpn`.

## Paso 7 — Recuperar

Depende de la gravedad. Si solo hubo un intento de intrusión fallido, basta restaurar la configuración.

```bash
# Opción A — Restaurar desde backup de PostgreSQL (volumen postgres_data)
docker compose stop app
docker compose exec -T postgres pg_restore -U streamchat -d streamchat < backup.sql
docker compose up -d
docker compose ps                     # verificar healthchecks UP

# Opción B — Reconstruir la instancia desde cero (compromiso grave)
#   1. Termina la instancia actual (tras exportar la evidencia).
#   2. Lanza una nueva desde la AMI endurecida siguiendo docs/security/despliegue-ec2.md.
#   3. Restaura los datos y re-aplica el .env con secretos nuevos.
```

Verifica la recuperación con la **checklist** (sección "Verificación final") y confirma que el servicio
vuelve a responder: `curl -I https://midominio.com` y `docker compose ps`.

## Paso 8 — Documentar (post-mortem)

Redacta un post-mortem **sin culpar a personas**, con: resumen, línea temporal, impacto, causa raíz,
qué funcionó, qué falló y **acciones de mejora con responsable y fecha**. Actualiza esta checklist y el
hardening con lo aprendido. Cierra el incidente solo cuando las acciones correctivas estén verificadas.

---

## Chuleta de comandos del proyecto

| Acción | Comando |
|--------|---------|
| Ver estado de servicios | `docker compose ps` |
| Reiniciar tras cambiar `.env` | `docker compose up -d --force-recreate app` |
| Ver logs de la app | `docker compose logs -f app` |
| Rotar secreto JWT | `openssl rand -base64 32` → editar `.env` → `docker compose up -d --force-recreate app` |
| Rotar contraseña de PostgreSQL | `docker compose exec postgres psql -U streamchat -d streamchat -c "ALTER USER streamchat WITH PASSWORD '...';"` |
| Banear IP con Fail2ban | `sudo fail2ban-client set sshd banip <IP>` |
| Banear IP con nftables | `sudo nft add element inet firewall blocklist '{ <IP> timeout 1h }'` |
| Validar firewall antes de aplicar | `sudo nft -c -f /etc/nftables.conf` |
| Exportar evidencia | `sudo journalctl --since "24 hours ago" > incidente.log` |
| Backup de la base | `docker compose exec -T postgres pg_dump -U streamchat streamchat > backup.sql` |

---

*Documento académico del proyecto Stream-Chat. Referencia: secciones 6, 10.2 (fase 9) y 11 del
`Plan_Trabajo_Stream_Chat_3_Programadores_v2.md`.*
