#!/usr/bin/env bash
#
# ci-bootstrap-db.sh — bootstrap do banco para o job e2e do CI.
#
# Reproduz os passos de banco do reset-ambiente-dev.sh (4.1, 7, 7.1) SEM o resto
# do fluxo de dev (Keycloak admin, ngrok, frontend, etc.). Idempotente.
#
#   1. cria a role jetski_app (NOSUPERUSER NOBYPASSRLS) — para a RLS valer;
#   2. roda as migrations Flyway como superusuário (jetski) via container;
#   3. re-concede grants ao jetski_app nas tabelas criadas pelas migrations.
#
# Pré-requisito: `docker compose up -d postgres` já rodando.
# Variáveis sobrescrevíveis: COMPOSE_NETWORK, BACKEND_DIR.
set -euo pipefail

PG_USER=jetski
PG_PASSWORD=dev123
PG_DB=jetski_dev
# Nome da rede do compose = <projeto>_jetski-network, e o projeto = nome do
# diretório (local: jetski; CI checkout: jetski-saas). Em vez de hardcode,
# descobre a rede real criada pelo compose (que contém "jetski-network").
NET="${COMPOSE_NETWORK:-$(docker network ls --format '{{.Name}}' | grep -m1 'jetski-network')}"
BACKEND_DIR="${BACKEND_DIR:-backend}"

if [ -z "$NET" ]; then
  echo "ERRO: rede do compose (jetski-network) não encontrada. Rode 'docker compose up -d postgres' antes." >&2
  exit 1
fi
echo "==> usando rede docker: $NET"

echo "==> [1/3] criando role jetski_app (RLS-safe)..."
docker compose exec -T postgres psql -U "$PG_USER" -d "$PG_DB" <<'EOSQL'
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jetski_app') THEN
        CREATE ROLE jetski_app WITH LOGIN PASSWORD 'dev123' NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;
GRANT USAGE ON SCHEMA public TO jetski_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jetski_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jetski_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jetski_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO jetski_app;
EOSQL

echo "==> [2/3] rodando migrations Flyway (superusuário ${PG_USER})..."
docker run --rm \
    --network "$NET" \
    -v "$PWD/$BACKEND_DIR/src/main/resources/db/migration:/flyway/sql:ro" \
    flyway/flyway:10-alpine \
    -url="jdbc:postgresql://postgres:5432/${PG_DB}" \
    -user="${PG_USER}" \
    -password="${PG_PASSWORD}" \
    -baselineOnMigrate=true \
    -baselineVersion=0 \
    -outOfOrder=true \
    migrate

echo "==> [3/3] re-concedendo grants ao jetski_app..."
docker compose exec -T postgres psql -U "$PG_USER" -d "$PG_DB" <<'EOSQL'
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jetski_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jetski_app;
EOSQL

echo "==> bootstrap do banco concluído."
