#!/usr/bin/env bash
# ==============================================================================
# Automated Backup Script for Vikunja (PostgreSQL DB + Uploaded Files)
# Checklist Item 6: Automated Data Backups
# ==============================================================================

set -euo pipefail

# Configuration
VIKUNJA_DIR="${VIKUNJA_DIR:-/var/www/vikunja}"
BACKUP_DEST="${BACKUP_DEST:-${VIKUNJA_DIR}/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
DATE="$(date +%Y-%m-%d_%H-%M-%S)"

mkdir -p "${BACKUP_DEST}"

echo "================================================"
echo " Starting Vikunja Backup: ${DATE}"
echo "================================================"

# Load environment variables if .env exists
if [ -f "${VIKUNJA_DIR}/.env" ]; then
    # shellcheck disable=SC1091
    export $(grep -v '^#' "${VIKUNJA_DIR}/.env" | xargs)
fi

DB_USER="${DB_USER:-vikunja}"
DB_NAME="${DB_NAME:-vikunja}"

# 1. Backup PostgreSQL Database
DB_BACKUP_FILE="${BACKUP_DEST}/vikunja_db_${DATE}.sql.gz"
echo "[1/2] Exporting and compressing PostgreSQL database..."
docker exec vikunja-db pg_dump -U "${DB_USER}" "${DB_NAME}" | gzip > "${DB_BACKUP_FILE}"
echo "      Database backup saved: ${DB_BACKUP_FILE} ($(du -h "${DB_BACKUP_FILE}" | cut -f1))"

# 2. Backup Uploaded Files / Attachments
FILES_BACKUP_FILE="${BACKUP_DEST}/vikunja_files_${DATE}.tar.gz"
echo "[2/2] Archiving uploaded task attachments..."
docker run --rm \
    --volumes-from vikunja-app \
    -v "${BACKUP_DEST}:/backup" \
    alpine tar -czf "/backup/vikunja_files_${DATE}.tar.gz" -C /app/vikunja files
echo "      Files backup saved: ${FILES_BACKUP_FILE} ($(du -h "${FILES_BACKUP_FILE}" | cut -f1))"

# 3. Retention cleanup (Delete backups older than RETENTION_DAYS)
echo "Cleaning up backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DEST}" -type f \( -name "vikunja_db_*.sql.gz" -o -name "vikunja_files_*.tar.gz" \) -mtime "+${RETENTION_DAYS}" -exec rm -f {} \;

echo "Backup complete! All files secured in ${BACKUP_DEST}."
echo "================================================"
