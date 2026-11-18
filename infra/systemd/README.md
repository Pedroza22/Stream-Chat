# Despliegue sin Docker con systemd

Este directorio contiene una **Unit de systemd endurecida** como alternativa a
`docker compose` para ejecutar Stream-Chat en el servidor. Aplica la fase 5 de
la guía de hardening (sección 10.2): confinamiento de la aplicación con límites
de recursos y protecciones de systemd.

| Archivo | Descripción |
|---|---|
| `streamchat.service` | Unit para `/etc/systemd/system/streamchat.service`. |
| `README.md` | Este documento: instalación, operación y verificación. |

> Esta opción **no reemplaza** el despliegue con Docker del `compose.yaml`;
> es una alternativa por si se quiere prescindir de contenedores (por ejemplo,
> para demostrar `systemd-analyze security`). Elegir una de las dos.

## 1. Preparación

```bash
# 1) Usuario de sistema sin shell ni home de login.
sudo useradd --system --no-create-home --home-dir /opt/streamchat \
             --shell /usr/sbin/nologin streamchat

# 2) Directorio de la app y del JAR.
sudo mkdir -p /opt/streamchat /opt/streamchat/logs /var/log/streamchat
sudo cp target/*.jar /opt/streamchat/app.jar
sudo chown -R streamchat:streamchat /opt/streamchat /var/log/streamchat

# 3) Archivo de entorno con los secretos, con permisos 600 (sección 10.3, punto 5).
sudo mkdir -p /etc/streamchat
sudo cp .env.example /etc/streamchat/streamchat.env   # editar valores reales
sudo chmod 600 /etc/streamchat/streamchat.env
sudo chown root:root /etc/streamchat/streamchat.env
```

> Ajustar `/etc/streamchat/streamchat.env` con los hosts reales. En modo sin
> Docker, PostgreSQL/RabbitMQ/Kafka pueden estar en el propio host o en
> `127.0.0.1` con los puertos de `compose.yaml`.

## 2. Instalar y activar el servicio

```bash
# 1) Copiar la Unit y recargar el gestor.
sudo cp infra/systemd/streamchat.service /etc/systemd/system/streamchat.service
sudo systemctl daemon-reload

# 2) Habilitar (arranque automático) y arrancar.
sudo systemctl enable streamchat
sudo systemctl start streamchat

# 3) Ver el estado y el resultado del arranque.
sudo systemctl status streamchat
```

## 3. Operar y depurar

```bash
# Logs en vivo de la aplicación.
sudo journalctl -u streamchat -f

# Últimas 200 líneas.
sudo journalctl -u streamchat -n 200

# Reiniciar / detener / arrancar.
sudo systemctl restart streamchat
sudo systemctl stop    streamchat
sudo systemctl start   streamchat
```

## 4. Verificar los límites de recursos

```bash
# Comprobar los límites efectivos de la Unit.
systemctl show streamchat -p MemoryMax -p CPUQuota -p TasksMax -p LimitNOFILE

# Uso en tiempo real de la CPU, la memoria y el servicio.
systemd-cgtop
systemctl status streamchat   # muestra uso de CPU y memoria
```

Para probar el reinicio automático (matar el proceso y medir la recuperación,
sección 9.1):

```bash
sudo kill -9 $(systemctl show -p MainPID --value streamchat)
watch -n1 systemctl is-active streamchat   # debe volver a "active"
```

## 5. `systemd-analyze security`

```bash
# Puntuación de exposición; el objetivo de la guía es < 3.
sudo systemd-analyze security streamchat
```

Si la puntuación es alta, valorar añadir en la Unit directivas como
`ProtectKernelTunables=true`, `ProtectControlGroups=true`, `RestrictAddressFamilies=`
o `SystemCallFilter=`. Comprobar tras cada cambio que la app sigue arrancando
(los logs de `journalctl -u streamchat` avisan de cualquier incompatibilidad).
