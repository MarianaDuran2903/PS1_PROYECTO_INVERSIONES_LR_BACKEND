#!/usr/bin/env bash
# Restaura un backup hecho con backup-db.sh sobre la base del docker compose.
# Uso: scripts/backup/restaurar-db.sh /var/backups/inversiones-lr/inversiones_lr_20261118_0300.dump [--si]
#
# BORRA los datos actuales de la base. Detiene el backend mientras restaura.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="${BACKUP_CONFIG:-$SCRIPT_DIR/backup.env}"
# shellcheck source=/dev/null
[[ -f "$CONFIG" ]] && source "$CONFIG"

PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-$PROJECT_DIR/docker-compose.yml}"
DB_SERVICE="${DB_SERVICE:-db}"
BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"

leer_env() {
    local nombre="$1" defecto="$2" valor=""
    if [[ -f "$PROJECT_DIR/.env" ]]; then
        valor="$(grep -E "^${nombre}=" "$PROJECT_DIR/.env" | tail -n 1 | cut -d '=' -f 2- | tr -d '\r"'"'" || true)"
    fi
    printf '%s' "${valor:-$defecto}"
}

DB_NAME="${DB_NAME:-$(leer_env DB_NAME inversiones_lr)}"
DB_USER="${DB_USER:-$(leer_env DB_USER postgres)}"

ARCHIVO="${1:-}"
if [[ -z "$ARCHIVO" || ! -f "$ARCHIVO" ]]; then
    echo "Uso: $0 <archivo.dump> [--si]" >&2
    exit 1
fi

if [[ -f "$ARCHIVO.sha256" ]]; then
    (cd "$(dirname "$ARCHIVO")" && sha256sum -c --quiet "$(basename "$ARCHIVO").sha256")
fi

if [[ "${2:-}" != "--si" ]]; then
    read -r -p "Se reemplazarán los datos de '$DB_NAME' con $(basename "$ARCHIVO"). ¿Continuar? (escriba si): " respuesta
    [[ "$respuesta" == "si" ]] || { echo "Cancelado."; exit 1; }
fi

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

compose stop "$BACKEND_SERVICE"
trap 'compose start "$BACKEND_SERVICE"' EXIT

# --clean --if-exists borra y recrea cada objeto; --single-transaction deja la base intacta si algo falla.
compose exec -T "$DB_SERVICE" \
    pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner --single-transaction \
    < "$ARCHIVO"

echo "Restauración terminada; iniciando el backend."
