#!/usr/bin/env bash
# Restore a backup made by backup.sh. WARNING: --drop replaces existing collections.
#   export MONGODB_URI="mongodb+srv://..."
#   ./scripts/restore.sh hospital-backup-YYYYMMDD-HHMM.archive.gz
set -euo pipefail
: "${MONGODB_URI:?Set MONGODB_URI first}"
FILE="${1:?Usage: restore.sh <backup-file>}"
read -r -p "This REPLACES the data in the target database. Type YES to continue: " ok
[ "$ok" = "YES" ] || { echo "Cancelled."; exit 1; }
mongorestore --uri="$MONGODB_URI" --archive="$FILE" --gzip --drop
