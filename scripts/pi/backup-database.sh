#!/bin/bash
# Creates a timestamped plain-SQL + custom-format PostgreSQL backup of the
# greenhouse database, with a checksum file. Run as the lodiv user (uses
# sudo -u postgres internally via peer auth, no password needed).
set -euo pipefail

BACKUP_DIR="/opt/greenhouse/backups"
TS=$(date +%Y%m%d-%H%M)

mkdir -p "$BACKUP_DIR"

sudo -u postgres pg_dump --format=plain greenhouse > "$BACKUP_DIR/greenhouse-$TS.sql"
sudo -u postgres pg_dump --format=custom greenhouse > "$BACKUP_DIR/greenhouse-$TS.dump"

sha256sum "$BACKUP_DIR/greenhouse-$TS.sql" "$BACKUP_DIR/greenhouse-$TS.dump" > "$BACKUP_DIR/greenhouse-$TS.sha256"

echo "Backup created:"
ls -lh "$BACKUP_DIR"/greenhouse-$TS.*
