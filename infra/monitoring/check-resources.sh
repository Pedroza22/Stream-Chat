#!/usr/bin/env bash
# =============================================================================
# Stream-Chat — check-resources.sh
# -----------------------------------------------------------------------------
# Comprueba CPU, memoria, disco e inodos contra umbrales y registra alertas en
# el journal vía `logger -t resource-alert`. Corresponde a la fase 8 de la
# sección 10.2 ("Monitoreo y auditoría") del plan de trabajo.
#
# PROGRÁMALO CON CRON cada 5 minutos. Crear /etc/cron.d/streamchat-resources:
#
#     # /etc/cron.d/streamchat-resources
#     SHELL=/bin/bash
#     PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
#     */5 * * * * root /usr/local/bin/check-resources.sh >/dev/null 2>&1
#
# Ver las alertas:
#     journalctl -t resource-alert
#
# Diseño:
#   - `set -u` para detectar variables no definidas.
#   - NO se usa `set -e`: si una métrica falla, el script sigue con las demás.
#   - No depende de utilidades opcionales: CPU y memoria se leen de /proc; solo
#     el disco usa `df` (coreutils, presente en Ubuntu por defecto).
#   - Umbrales configurables por variables de entorno al inicio.
# =============================================================================

set -u

# -----------------------------------------------------------------------------
# Umbrales configurables (porcentaje). Se pueden sobrescribir desde el entorno,
# por ejemplo en el cron:  */5 * * * * root CPU_THRESHOLD=90 /usr/local/bin/...
# -----------------------------------------------------------------------------
CPU_THRESHOLD=${CPU_THRESHOLD:-85}      # uso de CPU > 85%
MEM_THRESHOLD=${MEM_THRESHOLD:-90}      # memoria usada > 90%
DISK_THRESHOLD=${DISK_THRESHOLD:-80}    # uso de disco > 80%
INODE_THRESHOLD=${INODE_THRESHOLD:-80}  # uso de inodos > 80%

# Sistema de archivos a vigilar (punto de montaje donde vive Docker/la app).
DISK_MOUNT=${DISK_MOUNT:-/}

# Etiqueta del logger (visible en `journalctl -t resource-alert`).
LOGGER_TAG="resource-alert"

# -----------------------------------------------------------------------------
# Utilidades
# -----------------------------------------------------------------------------
alert() {
    # Envía una alerta al journal de systemd con la etiqueta fija.
    logger -t "$LOGGER_TAG" "$1"
}

have() {
    command -v "$1" >/dev/null 2>&1
}

is_uint() {
    # Verdadero si el argumento es un entero no negativo.
    case "${1:-}" in
        ''|*[!0-9]*) return 1 ;;
        *) return 0 ;;
    esac
}

# -----------------------------------------------------------------------------
# 1) CPU — se calcula el porcentaje de uso a partir de /proc/stat (dos muestras
#    separadas 1 segundo), sin depender de `mpstat` ni `top`.
# -----------------------------------------------------------------------------
check_cpu() {
    [ -r /proc/stat ] || return 0

    local s1 s2 idle1 tot1 idle2 tot2 dtot didle used
    s1=$(awk '/^cpu /{idle=$5+$6; tot=0; for(i=2;i<=NF;i++) tot+=$i; print idle, tot}' /proc/stat)
    idle1=${s1%% *}; tot1=${s1##* }
    sleep 1
    s2=$(awk '/^cpu /{idle=$5+$6; tot=0; for(i=2;i<=NF;i++) tot+=$i; print idle, tot}' /proc/stat)
    idle2=${s2%% *}; tot2=${s2##* }

    is_uint "$tot1" && is_uint "$tot2" || return 0
    dtot=$(( tot2 - tot1 ))
    didle=$(( idle2 - idle1 ))
    [ "$dtot" -gt 0 ] || return 0

    used=$(( 100 * (dtot - didle) / dtot ))
    if [ "$used" -ge "$CPU_THRESHOLD" ]; then
        alert "CPU alta: ${used}% (umbral ${CPU_THRESHOLD}%)"
    fi
}

# -----------------------------------------------------------------------------
# 2) Memoria — porcentaje usado a partir de MemTotal y MemAvailable en /proc.
#    (Se usa MemAvailable, no MemFree, porque tiene en cuenta la caché.)
# -----------------------------------------------------------------------------
check_mem() {
    [ -r /proc/meminfo ] || return 0

    local total avail used
    total=$(awk '/^MemTotal:/{print $2}' /proc/meminfo)
    avail=$(awk '/^MemAvailable:/{print $2}' /proc/meminfo)
    is_uint "$total" && is_uint "$avail" || return 0
    [ "$total" -gt 0 ] || return 0

    used=$(( 100 * (total - avail) / total ))
    if [ "$used" -ge "$MEM_THRESHOLD" ]; then
        alert "Memoria alta: ${used}% (umbral ${MEM_THRESHOLD}%)"
    fi
}

# -----------------------------------------------------------------------------
# 3) Disco — porcentaje de uso del sistema de archivos vigilado (columna $5 de
#    `df -P`). `-P` fuerza el formato POSIX (una línea por FS).
# -----------------------------------------------------------------------------
check_disk() {
    have df || return 0

    local pct
    pct=$(df -P "$DISK_MOUNT" 2>/dev/null | awk 'NR==2{gsub("%","",$5); print $5}')
    is_uint "$pct" || return 0

    if [ "$pct" -ge "$DISK_THRESHOLD" ]; then
        alert "Disco ${DISK_MOUNT} al ${pct}% (umbral ${DISK_THRESHOLD}%)"
    fi
}

# -----------------------------------------------------------------------------
# 4) Inodos — el agotamiento de inodos impide crear archivos aunque quede
#    espacio libre (columna $5 de `df -Pi`). Algunas FS muestran "-"; se ignora.
# -----------------------------------------------------------------------------
check_inodes() {
    have df || return 0

    local pct
    pct=$(df -Pi "$DISK_MOUNT" 2>/dev/null | awk 'NR==2{gsub("%","",$5); print $5}')
    is_uint "$pct" || return 0

    if [ "$pct" -ge "$INODE_THRESHOLD" ]; then
        alert "Inodos de ${DISK_MOUNT} al ${pct}% (umbral ${INODE_THRESHOLD}%)"
    fi
}

# -----------------------------------------------------------------------------
# Ejecución
# -----------------------------------------------------------------------------
check_cpu
check_mem
check_disk
check_inodes

exit 0
