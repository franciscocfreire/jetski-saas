#!/usr/bin/env bash
###############################################################################
# Deploy de PRODUÇÃO — NÃO destrutivo (Pega o Jet)
#
# Fluxo (idempotente):
#   1. git pull (a menos que NO_PULL=1)
#   2. sobe infra base (postgres, redis, keycloak, opa, minio)
#   3. cria/atualiza role jetski_app (01-init-roles.sql)
#   4. aplica migrations Flyway como superuser (one-shot container)
#   5. concede grants + verifica RLS (02-verify-rls.sql) — aborta se faltar RLS
#   6. (re)build das imagens app (backend/frontend) e up --force-recreate
#   7. recarrega OPA (policies sem --watch) e sobe cloudflared
#   8. smoke check de saúde
#
# Uso:
#   ./deploy.sh                 # deploy completo
#   NO_BUILD=1 ./deploy.sh      # pula rebuild de imagens (só migrations/restart)
#   NO_PULL=1 ./deploy.sh       # não faz git pull (usado pelo CD, que já fez)
#
# Pré-requisitos no servidor: Docker + compose plugin, arquivo .env preenchido
# (ver .env.prod.example). NUNCA roda DROP/TRUNCATE — não apaga dados.
###############################################################################
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[deploy]${NC} $*"; }
warn() { echo -e "${YELLOW}[deploy]${NC} $*"; }
die()  { echo -e "${RED}[deploy] ERRO:${NC} $*" >&2; exit 1; }

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
PSQL="$COMPOSE exec -T postgres psql -v ON_ERROR_STOP=1 -U jetski -d jetski_prod"

[ -f .env ] || die ".env não encontrado. Copie de .env.prod.example e preencha."
set -a; . ./.env; set +a
: "${JETSKI_APP_DB_PASSWORD:?defina JETSKI_APP_DB_PASSWORD no .env}"
: "${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no .env}"
: "${PUBLIC_URL:?defina PUBLIC_URL no .env}"

# 1. Atualiza o código
if [ "${NO_PULL:-0}" != "1" ]; then
  log "git pull..."
  git pull --ff-only
fi

# 2. Infra base
log "subindo infra base (postgres/redis/keycloak/opa/minio)..."
$COMPOSE up -d postgres redis keycloak opa minio

log "aguardando postgres..."
for i in $(seq 1 30); do
  if $COMPOSE exec -T postgres pg_isready -U jetski -d jetski_prod >/dev/null 2>&1; then break; fi
  sleep 2
  [ "$i" = "30" ] && die "postgres não respondeu a tempo"
done

# 3. Role de aplicação (idempotente)
log "criando/atualizando role jetski_app..."
$PSQL -v app_pwd="$JETSKI_APP_DB_PASSWORD" -f /dev/stdin < infra/prod/01-init-roles.sql

# 4. Migrations (superuser, one-shot)
log "aplicando migrations Flyway..."
$COMPOSE run --rm flyway || die "migrations falharam"

# 5. Grants pós-migration + verificação de RLS (aborta se faltar)
log "re-concedendo grants ao jetski_app..."
$PSQL -c "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jetski_app;" \
      -c "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jetski_app;"
log "verificando RLS em tabelas multi-tenant..."
$PSQL -f /dev/stdin < infra/prod/02-verify-rls.sql || die "verificação de RLS falhou — deploy abortado"

# 6. Build + recreate das apps
if [ "${NO_BUILD:-0}" != "1" ]; then
  log "build backend + frontend (--no-cache p/ evitar reaproveitar imagem velha)..."
  $COMPOSE build --no-cache backend
  $COMPOSE build --no-cache frontend
fi
log "recriando backend e frontend..."
$COMPOSE up -d --force-recreate --no-deps backend frontend

# 7. OPA (recarrega policies — não tem --watch) e ingress
log "recarregando OPA e subindo nginx/cloudflared..."
$COMPOSE restart opa
$COMPOSE up -d nginx cloudflared

# 7.5 Keycloak: converge o client jetski-backoffice (público + PKCE S256) +
# redirects de produção, idempotente. Num realm NOVO o realm.json já nasce certo
# (substituição de env var no import); este passo é a única forma de ajustar o
# client num realm EXISTENTE sem zerar o realm (--import-realm não re-importa).
# Não-fatal: a config persiste no banco do Keycloak.
log "aguardando realm Keycloak e configurando client jetski-backoffice..."
kc_ready=0
for i in $(seq 1 40); do
  if curl -sf http://127.0.0.1:8080/realms/jetski-saas/.well-known/openid-configuration >/dev/null 2>&1; then
    kc_ready=1; break
  fi
  sleep 3
done
if [ "$kc_ready" = "1" ]; then
  bash infra/prod/configure-keycloak-client.sh || warn "config do client Keycloak falhou (verifique manualmente)"
  bash infra/prod/configure-keycloak-smtp.sh || warn "config de SMTP do Keycloak falhou (verifique manualmente)"
else
  warn "Keycloak realm não respondeu — pulei a config do client/SMTP (rode os scripts em infra/prod/ depois)"
fi

# 8. Smoke (aguarda o boot do Spring — pode levar ~30-60s)
log "smoke check (aguardando backend subir)..."
code=000
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8090/api/actuator/health || echo 000)
  [ "$code" = "200" ] && break
  sleep 3
done
[ "$code" = "200" ] && log "backend healthy (200)" || warn "backend health=$code após ~90s (verifique: $COMPOSE logs backend)"

log "deploy concluído. Público: ${PUBLIC_URL}"
$COMPOSE ps
