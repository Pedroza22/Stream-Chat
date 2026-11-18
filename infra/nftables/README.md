# Firewall nftables para Stream-Chat

Este directorio contiene el firewall de host con **nftables** aplicado a
Stream-Chat, resultado de corregir el `nftables.txt` del campus según la
sección 10.4 del plan de trabajo.

| Archivo | Descripción |
|---|---|
| `nftables.conf` | Reglas del firewall. Copiar a `/etc/nftables.conf`. |
| `README.md` | Este documento: validar, aplicar, persistir y tabla de correcciones. |

## 1. Requisitos previos

- `nftables` instalado: `sudo apt-get install -y nftables`.
- **No** usar UFW en paralelo: elegir nftables **o** UFW, nunca ambos.
- Reemplazar el marcador `203.0.113.10/32` del set `admin_ips` por la IP de
  administración real (o la VPN/el túnel SSM desde el que se conecta el equipo).

## 2. Validar, aplicar y persistir

```bash
# 1) Validar la sintaxis SIN aplicar cambios. Es obligatorio y no altera reglas.
sudo nft -c -f /etc/nftables.conf

# 2) Copiar el archivo al servidor y aplicar.
sudo cp infra/nftables/nftables.conf /etc/nftables.conf
sudo nft -f /etc/nftables.conf

# 3) Persistir en el arranque (Ubuntu 22.04+).
sudo systemctl enable --now nftables
sudo systemctl status nftables

# 4) Revisar las reglas activas y los contadores de descartes.
sudo nft list ruleset
sudo nft list table inet firewall
```

> **Recrear sin borrar las reglas de Docker.** El archivo no usa
> `flush ruleset`. En su lugar declara la tabla propia y la borra para recrearla
> limpia:
>
> ```
> table inet firewall
> delete table inet firewall
> table inet firewall { ... }
> ```
>
> Así se reaplica el firewall sin tocar las tablas `ip nat` / `ip filter` que
> Docker instala. Tras aplicar, reiniciar Docker para que reconfigure sus reglas:
> `sudo systemctl restart docker`.

## 3. Verificar los descartes en el log

```bash
# Ver los paquetes descartados por la cadena de entrada.
sudo journalctl -k | grep 'nft-drop-in'
sudo journalctl -k | grep 'nft-ssh-scan'
```

## 4. Las 8 correcciones de la sección 10.4

| # | Observación original | Corrección aplicada | Dónde en `nftables.conf` |
|---|---|---|---|
| 1 | `¡TU_IP!` no es una dirección válida y el archivo no carga | IP de administración con máscara (`203.0.113.10/32`) y `flags interval` para admitir rangos | Set `admin_ips` + nota de reemplazo |
| 2 | Solo se permitía el puerto 80; sin 443 no hay HTTPS ni `wss://` | Se aceptan 80 y 443: `tcp dport { 80, 443 } ct state new accept` | Cadena `input`, regla 6 |
| 3 | `flush ruleset` borra también las reglas de Docker; `forward` en drop deja a los contenedores sin red | No se usa `flush ruleset`: se declara/borra/recrea SOLO `table inet firewall`; `forward` queda en `policy accept` | Cabecera del archivo + cadena `forward` + nota final sobre Docker |
| 4 | La regla anti-escaneo se evaluaba antes que la de IPs de administración, limitando también a la administración | La regla de `admin_ips` se coloca **antes**; la anti-escaneo solo registra (la política drop bloquea al resto) | Cadena `input`, reglas 4 y 5 |
| 5 | `icmp type echo-request accept` solo cubre IPv4 y no tiene límite; una tabla `inet` con drop rompe el descubrimiento de vecinos IPv6 | ICMP con `limit rate 5/second` y tipos necesarios de `icmpv6` (NDP, SLAAC, etc.) | Cadena `input`, reglas 3 y 3b |
| 6 | No se registra lo que se descarta al final de la entrada | `log prefix "nft-drop-in: " limit rate 10/minute` antes del `drop` final | Cadena `input`, regla 7 |
| 7 | Salida con política `accept` que solo bloqueaba 4 DNS públicos: evadible y no cubre a los contenedores | Se mantiene `accept` y se documenta; para limitar la salida, usar `drop` + lista blanca o Squid (ampliación) | Cadena `output` + comentario |
| 8 | Sin conjunto de bloqueo temporal ni límites por regla | Set `blocklist` con `flags timeout` para banear IPs con caducidad | Set `blocklist` + regla 0 |

## 5. Interacción con Docker (secciones 10.3 y 10.5)

- **`net.ipv4.ip_forward = 0` NO debe aplicarse** si se usa Docker: rompería la
  NAT interna y la publicación de puertos.
- **`forward` en `policy drop` rompería los contenedores.** Por eso queda en
  `accept`; si se quiere filtrar el reenvío, hacerlo en `DOCKER-USER`.
- Tras aplicar el firewall: `sudo systemctl restart docker` y comprobar la
  conectividad entre contenedores.

## 6. Evidencias (sección 10.5)

```bash
# Desde fuera: solo 80 y 443 (y 22 desde la IP de administración).
nmap -Pn -p- <IP_PUBLICA>

# En el host: solo 22, 80, 443 y servicios en 127.0.0.1.
sudo ss -tulpn

# Los puertos de PostgreSQL/RabbitMQ/Kafka no son alcanzables desde fuera.
```
