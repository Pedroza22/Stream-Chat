# Monitoreo de recursos

Carpeta de monitoreo del host según la fase 8 de la sección 10.2 del plan
(`sysstat`, script de recursos y, como ampliación, CloudWatch Agent).

| Archivo | Descripción |
|---|---|
| `check-resources.sh` | Comprueba CPU, memoria, disco e inodos y alerta vía `logger`. |
| `README.md` | Este documento: instalación, cron y herramientas de ampliación. |

## 1. Instalar el script

```bash
# Copiar a /usr/local/bin y dar permisos de ejecución (propietario root).
sudo cp infra/monitoring/check-resources.sh /usr/local/bin/check-resources.sh
sudo chmod 755 /usr/local/bin/check-resources.sh
sudo chown root:root /usr/local/bin/check-resources.sh

# Prueba manual (debe terminar sin salida si todo está bajo los umbrales).
/usr/local/bin/check-resources.sh
```

Los umbrales (CPU 85 %, memoria 90 %, disco 80 %, inodos 80 %) se pueden
sobrescribir por variables de entorno, por ejemplo
`CPU_THRESHOLD=90 DISK_MOUNT=/var/lib/docker /usr/local/bin/check-resources.sh`.

## 2. Programarlo con cron cada 5 minutos

Crear el archivo `/etc/cron.d/streamchat-resources`:

```bash
sudo tee /etc/cron.d/streamchat-resources >/dev/null <<'EOF'
# Stream-Chat — chequeo de recursos cada 5 minutos
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
*/5 * * * * root /usr/local/bin/check-resources.sh >/dev/null 2>&1
EOF
sudo chmod 644 /etc/cron.d/streamchat-resources
```

> Los archivos en `/etc/cron.d/` incluyen el usuario (`root`) como una columna
> más. No requieren que el script sea llamado desde un crontab de usuario.

## 3. Ver las alertas

```bash
# Todas las alertas registradas por el script.
journalctl -t resource-alert

# Solo las últimas y en vivo.
journalctl -t resource-alert -n 20
journalctl -t resource-alert -f
```

Cada alerta tiene la forma:

```
resource-alert: Disco / al 83% (umbral 80%)
```

## 4. Herramientas recomendadas (fase 8)

```bash
# Núcleo: herramientas de análisis del sistema.
sudo apt-get install -y sysstat htop iotop-c ncdu

# sysstat: histórico de CPU, memoria, E/S y red (usado en la sección 9).
#   sar -u 2     CPU
#   sar -r 2     memoria
#   sar -d 2     disco
#   sar -n DEV 2 red
sar -u 2 1
```

- **sysstat**: series históricas (`sar`) para los experimentos E1–E6.
- **htop**: vista interactiva de procesos, hilos y memoria.
- **iotop-c**: consumo de E/S por proceso (gestor de E/S).
- **ncdu**: uso de disco navegable por directorios.

## 5. Ampliación — CloudWatch Agent

Para alarmas gestionadas en AWS (CPU > 85 % durante 15 min, memoria > 90 %
durante 10 min, disco > 80 %), instalar el CloudWatch Agent en la instancia,
habilitar las métricas `mem_used_percent` y `disk_used_percent` en su
configuración y crear las alarmas en CloudWatch. Requiere un rol IAM con
`CloudWatchAgentServerPolicy`. Esta es una ampliación del plan (sección 10.2,
fase 8), no parte del núcleo obligatorio.
