#!/usr/bin/env bash
# =============================================================================
# Backup do Meu Jet — Postgres (jetski_prod, INCLUI o schema keycloak) +
# volume do MinIO (fotos/documentos/assinaturas).
#
# Instalado no crontab pelo deploy.sh (diário, 03:30). Também roda manual:
#   ./infra/prod/backup.sh
#
# Variáveis (todas opcionais):
#   BACKUP_DIR            destino local (default: $HOME/backups/meujet)
#   BACKUP_DB             banco (default: jetski_prod; use jetski_dev p/ testar)
#   BACKUP_RETENTION_DAYS dias de retenção local (default: 14)
#   BACKUP_RCLONE_REMOTE  remoto rclone p/ cópia OFF-SITE (ex.: oci:meujet-backup).
#                         Sem ele o backup fica SÓ na VM — configure assim que
#                         possível (perda da VM = perda dos backups locais).
#
# Restore: ver DEPLOY.md ("Backup e restore").
# =============================================================================
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_DIR"

# .env da VM fornece BACKUP_RCLONE_REMOTE etc. (variáveis já exportadas na
# chamada têm precedência — útil p/ testar com BACKUP_DB=jetski_dev)
if [ -f .env ]; then
    while IFS='=' read -r k v; do
        case "$k" in BACKUP_*) [ -z "${!k:-}" ] && export "$k=$v" ;; esac
    done < <(grep -E '^BACKUP_[A-Z_]+=' .env || true)
fi

BACKUP_DIR="${BACKUP_DIR:-$HOME/backups/meujet}"
BACKUP_DB="${BACKUP_DB:-jetski_prod}"
RETENTION="${BACKUP_RETENTION_DAYS:-14}"
STAMP="$(date +%F-%H%M)"

log() { echo "[backup $(date '+%F %T')] $*"; }

mkdir -p "$BACKUP_DIR/postgres" "$BACKUP_DIR/minio"

# ---------- 1. Postgres (formato custom: comprimido, pg_restore seletivo) ----
PG_OUT="$BACKUP_DIR/postgres/${BACKUP_DB}-${STAMP}.dump"
log "pg_dump $BACKUP_DB -> $PG_OUT"
docker compose exec -T postgres pg_dump -U jetski -d "$BACKUP_DB" --format=custom > "$PG_OUT"
# Dump vazio/truncado = falha silenciosa; um banco real tem sempre > 100 KB.
[ "$(stat -c%s "$PG_OUT")" -gt 102400 ] || { log "ERRO: dump suspeito (<100KB)"; exit 1; }

# ---------- 2. MinIO (tar do volume nomeado) --------------------------------
MINIO_VOL="$(docker volume ls -q | grep -E '(^|_)minio_data$' | head -1 || true)"
if [ -n "$MINIO_VOL" ]; then
    MINIO_OUT="$BACKUP_DIR/minio/minio-${STAMP}.tgz"
    log "tar do volume $MINIO_VOL -> $MINIO_OUT"
    docker run --rm -v "$MINIO_VOL":/data:ro -v "$BACKUP_DIR/minio":/backup alpine \
        tar czf "/backup/minio-${STAMP}.tgz" -C /data .
else
    log "AVISO: volume minio_data não encontrado — pulado"
fi

# ---------- 3. Rotação local ------------------------------------------------
find "$BACKUP_DIR/postgres" -name '*.dump' -mtime +"$RETENTION" -delete
find "$BACKUP_DIR/minio" -name '*.tgz' -mtime +"$RETENTION" -delete

# ---------- 4. Off-site (rclone, se configurado) ----------------------------
if [ -n "${BACKUP_RCLONE_REMOTE:-}" ]; then
    if command -v rclone >/dev/null 2>&1; then
        log "rclone sync -> $BACKUP_RCLONE_REMOTE"
        rclone sync "$BACKUP_DIR" "$BACKUP_RCLONE_REMOTE" --transfers 2
    else
        log "AVISO: BACKUP_RCLONE_REMOTE definido mas rclone não instalado"
    fi
else
    log "AVISO: sem off-site (BACKUP_RCLONE_REMOTE vazio) — backup só nesta VM"
fi

# ---------- 5. Marcador de sucesso (base p/ alerta futuro) -------------------
date +%s > "$BACKUP_DIR/last-success"
log "OK — $(du -sh "$BACKUP_DIR" | cut -f1) em $BACKUP_DIR"
