#!/usr/bin/env bash
# Full database backup (including invoice files) with mongodump.
# Needs the MongoDB Database Tools: https://www.mongodb.com/docs/database-tools/installation/
#   export MONGODB_URI="mongodb+srv://..."
#   ./scripts/backup.sh [output-file]
set -euo pipefail
: "${MONGODB_URI:?Set MONGODB_URI first}"
OUT="${1:-hospital-backup-$(date +%Y%m%d-%H%M).archive.gz}"
mongodump --uri="$MONGODB_URI" --archive="$OUT" --gzip
echo "Saved $OUT. It contains hospital data: keep it private and encrypted."
