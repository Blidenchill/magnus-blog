#!/usr/bin/env bash
# Magnus Blog consistent backup:
#   1. stop the app container (single instance -> writes quiesce)
#   2. pg_dump (custom format) from the running db container
#   3. archive the app-data media directory
#   4. record image/db/migration versions
#   5. restart the app
# Usage: DATA_ROOT=/srv/magnus ./deploy/backup.sh
set -euo pipefail

DATA_ROOT="${DATA_ROOT:?set DATA_ROOT, e.g. /srv/magnus}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="$DATA_ROOT/backups/$STAMP"
COMPOSE_DIR="${COMPOSE_DIR:-/opt/magnus}"

mkdir -p "$DEST"

echo "==> stopping app container"
docker compose -f "$COMPOSE_DIR/compose.yaml" stop app

trap 'docker compose -f "$COMPOSE_DIR/compose.yaml" start app || true' EXIT

echo "==> pg_dump"
docker compose -f "$COMPOSE_DIR/compose.yaml" exec -T db \
    pg_dump --format=custom --file=/tmp/magnus.dump -U postgres magnus
docker compose -f "$COMPOSE_DIR/compose.yaml" cp db:/tmp/magnus.dump "$DEST/magnus.dump"
docker compose -f "$COMPOSE_DIR/compose.yaml" exec -T db rm -f /tmp/magnus.dump

echo "==> archiving media"
tar -C "$DATA_ROOT" -cf "$DEST/app-data.tar" app-data

echo "==> manifest"
{
    echo "timestamp=$STAMP"
    docker compose -f "$COMPOSE_DIR/compose.yaml" images --digests || true
    docker compose -f "$COMPOSE_DIR/compose.yaml" exec -T db \
        psql -U postgres -d magnus -Atc 'SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1' \
        | sed 's/^/flyway_version=/' || true
} > "$DEST/manifest.txt"

echo "==> checksums"
(cd "$DEST" && sha256sum magnus.dump app-data.tar > SHA256SUMS)

docker compose -f "$COMPOSE_DIR/compose.yaml" start app
trap - EXIT

echo "==> backup complete: $DEST"
echo "    copy $DEST to a separate host or object storage; this directory dies with the disk."
