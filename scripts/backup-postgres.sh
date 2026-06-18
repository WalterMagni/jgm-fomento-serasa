#!/usr/bin/env bash
set -euo pipefail

# Backup logico do PostgreSQL do Portal Serasa.
# Uso:
#   ./scripts/backup-postgres.sh
#
# Variaveis opcionais:
#   BACKUP_DIR=/var/backups/portal-serasa
#   RETENTION_DAYS=30
#   COMPOSE_FILE=docker-compose.yml
#   DB_SERVICE=postgres
#   DB_USER=serasa
#   DB_NAME=portal_serasa

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

BACKUP_DIR="${BACKUP_DIR:-${PROJECT_DIR}/backups/postgres}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
DB_SERVICE="${DB_SERVICE:-postgres}"
DB_USER="${DB_USER:-serasa}"
DB_NAME="${DB_NAME:-portal_serasa}"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/portal_serasa_${TIMESTAMP}.sql.gz"
LATEST_FILE="${BACKUP_DIR}/portal_serasa_latest.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "[backup] Iniciando backup logico do banco ${DB_NAME}..."
cd "${PROJECT_DIR}"

docker compose -f "${COMPOSE_FILE}" exec -T "${DB_SERVICE}" \
  pg_dump -U "${DB_USER}" -d "${DB_NAME}" --no-owner --no-privileges \
  | gzip -9 > "${BACKUP_FILE}"

ln -sfn "$(basename "${BACKUP_FILE}")" "${LATEST_FILE}"

echo "[backup] Arquivo gerado: ${BACKUP_FILE}"
echo "[backup] Removendo backups com mais de ${RETENTION_DAYS} dias..."
find "${BACKUP_DIR}" -type f -name "portal_serasa_*.sql.gz" -mtime +"${RETENTION_DAYS}" -delete

echo "[backup] Validando arquivo..."
gzip -t "${BACKUP_FILE}"

echo "[backup] Concluido com sucesso."
