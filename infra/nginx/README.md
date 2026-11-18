# Nginx para Stream-Chat

Este directorio contiene el bloque `server` de Nginx para exponer Stream-Chat en
producción académica: **TLS con Let's Encrypt, cabeceras de seguridad, rate
limiting y proxy de WebSocket/STOMP**.

Nginx es el **único punto público** de la arquitectura (sección 10.1 del plan):
Internet → Security Group → nftables → **Nginx (80/443)** → Spring Boot en
`127.0.0.1:8080` → red interna de Docker.

## Archivos

| Archivo | Descripción |
|---|---|
| `streamchat.conf` | Bloque `server` para `/etc/nginx/sites-available/`. Reemplazar `DOMINIO`. |
| `README.md` | Este documento (despliegue, certbot y conflictos de la sección 10.3). |

## 1. Requisitos previos

- Nginx instalado: `sudo apt-get install -y nginx`.
- El dominio ya resuelve (registro A/AAAA) al IP público de la instancia EC2.
- Los puertos 80 y 443 están abiertos en el Security Group y en nftables.
- La app escucha en `127.0.0.1:8080` (`docker compose up -d` o el servicio systemd).

## 2. Desplegar el bloque `server`

```bash
# 1) Copiar el archivo y reemplazar el marcador DOMINIO por el real.
sudo cp infra/nginx/streamchat.conf /etc/nginx/sites-available/streamchat.conf
sudo sed -i 's/DOMINIO/chat.example.com/g' /etc/nginx/sites-available/streamchat.conf

# 2) Habilitar el sitio con un enlace simbólico.
sudo ln -sf /etc/nginx/sites-available/streamchat.conf \
            /etc/nginx/sites-enabled/streamchat.conf

# 3) Quitar el sitio por defecto para que no capture server_name _.
sudo rm -f /etc/nginx/sites-enabled/default

# 4) Validar la sintaxis ANTES de recargar (nunca recargar a ciegas).
sudo nginx -t

# 5) Recargar sin cortar conexiones (reload, no restart).
sudo systemctl reload nginx
```

> **Nota sobre el contexto `http`.** Las zonas `limit_req_zone` y
> `limit_conn_zone` deben declararse en el contexto `http`. Como
> `sites-enabled/*` se incluye dentro de `http {}`, están en el lugar correcto
> siempre que se despliegue como se indica. Si se integra el bloque de otra
> forma, moverlas a `/etc/nginx/conf.d/` o a `nginx.conf`.

## 3. Certificado TLS con certbot

```bash
# Instalar el cliente y el plugin de Nginx.
sudo apt-get install -y certbot python3-certbot-nginx

# Emitir el certificado (certbot edita el server block y añade TLS).
sudo certbot --nginx -d chat.example.com

# Verificar la renovación automática (systemd timer o cron).
sudo certbot renew --dry-run
systemctl list-timers | grep certbot
```

El archivo `streamchat.conf` ya contempla la ruta
`/etc/letsencrypt/live/DOMINIO/` y la ubicación del reto
`/.well-known/acme-challenge/`. Si se usa `--webroot`, apuntar el webroot a
`/var/www/html` (coincide con la configuración incluida).

## 4. Puntos conflictivos con la guía genérica (sección 10.3)

### 4.1 Timeout de WebSocket

La guía de hardening fija `proxy_read_timeout 30s` en `proxy.conf`. Ese valor
**cortaría las sesiones WebSocket/STOMP inactivas** cada 30 segundos. Por eso la
ubicación `/ws`:

- usa `proxy_http_version 1.1` y envía `Upgrade` / `Connection "upgrade"`
  (necesarias para el handshake WebSocket);
- sube `proxy_read_timeout` a `3600s`;
- desactiva el buffering (`proxy_buffering off`) porque es un flujo en tiempo
  real bidireccional;
- sirve `wss://` (el `connect-src` de la CSP lo permite explícitamente).

Además, la aplicación envía *heartbeats* de STOMP, de modo que la conexión se
considera activa y no se cierra por inactividad.

### 4.2 NAT y `limit_conn`

`limit_conn streamchat_conn 50` limita conexiones **por IP**. Varios usuarios
detrás de una misma IP pública (NAT) comparten `$binary_remote_addr`; un límite
bajo como el `perip 20` de la guía castigaría a usuarios legítimos. Se elige un
valor holgado y se comenta en el archivo.

### 4.3 Por qué Nginx es el único punto público

Los puertos de PostgreSQL, RabbitMQ y Kafka **no** se publican al exterior: en
`compose.yaml` aparecen como `127.0.0.1:PUERTO:PUERTO` o quedan solo en la red
interna de Compose. Docker instala sus propias reglas y los puertos publicados
no pasan por la cadena `input` del firewall del host, así que la única forma de
controlar el acceso externo es que **solo Nginx** escuche en 80/443 y que el
resto de la app escuche en `127.0.0.1`.

### 4.4 Endpoint SockJS

El endpoint STOMP usa SockJS (`/ws`), que además de WebSocket expone sub-rutas
HTTP de reserva (`/ws/info`, `/ws/{server}/{session}/websocket`). El prefijo
`location /ws` las cubre todas, por lo que no hace falta una ubicación extra.

## 5. Verificación

```bash
# Sintaxis y recarga.
sudo nginx -t && sudo systemctl reload nginx

# No se revela la versión de Nginx (evidencia 10.5).
curl -sI https://chat.example.com | grep -i server

# Solo 80 y 443 (y 22 desde la IP de administración) son visibles desde fuera.
nmap -Pn -p- <IP_PUBLICA>

# El handshake WebSocket responde 101 Switching Protocols.
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
     -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: x3JJHMbDL1EzLkh9GBhXDw==" \
     https://chat.example.com/ws/info
```
