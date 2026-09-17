#!/usr/bin/env bash
###############################################################################
# Deploy de PRODUÇÃO — NÃO destrutivo (MeuJet)
#
# Fluxo (idempotente):
#   1. git pull (a menos que NO_PULL=1)
#   2. build das imagens (keycloak com cache; apps --no-cache) ANTES de tocar
#      em qualquer container — o build pesado não disputa CPU com serviço subindo
#   3. sobe infra base (postgres, redis, keycloak, opa, minio); o Keycloak só é
#      recriado se a imagem ou a config dele mudou (e aí o deploy espera o realm)
#   4. cria/atualiza role jetski_app (01-init-roles.sql)
#   5. aplica migrations Flyway como superuser (one-shot container)
#   6. concede grants + verifica RLS (02-verify-rls.sql) — aborta se faltar RLS
#   7. up --force-recreate das apps (backend/frontend/portal/console) + limpeza
#   8. recarrega OPA (policies sem --watch), recria nginx e sobe cloudflared
#   9. converge a config do Keycloak (clients, SMTP, sessões, 2FA…)
#  10. smoke check de saúde + timer de backup
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

# As imagens construídas aqui só rodam neste servidor (nada vai para registry), então
# atestados de proveniência/SBOM não têm uso — e, no containerd image store, o
# atestado muda o digest da imagem a CADA build, mesmo com todas as camadas em cache.
# O compose via "imagem nova" e recriava o Keycloak em todo deploy: SSO ~3,5 min fora
# duas vezes em 13/set/2026 (PLANO_SSO_DEPLOY.md, ajuste A). Sem atestado, build sem
# mudança devolve o mesmo id e o `up -d` mantém o container.
export BUILDX_NO_DEFAULT_ATTESTATIONS=1

KC_CONTAINER=jetski-keycloak
KC_IMAGE=jetski-keycloak:latest

# Ids para saber o que o build/up realmente fizeram com o Keycloak ("" = não existe).
kc_imagem_id()    { docker image inspect --format '{{.Id}}' "$KC_IMAGE" 2>/dev/null || true; }
kc_container_id() { docker inspect --format '{{.Id}}' "$KC_CONTAINER" 2>/dev/null || true; }

# Espera o realm responder. $1 = tentativas (3 s cada). Retorna 1 se não respondeu.
aguardar_keycloak() {
  local tentativas="${1:-40}" i
  for i in $(seq 1 "$tentativas"); do
    if curl -sf http://127.0.0.1:8080/realms/jetski-saas/.well-known/openid-configuration >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
  done
  return 1
}

[ -f .env ] || die ".env não encontrado. Copie de .env.prod.example e preencha."
set -a; . ./.env; set +a
: "${JETSKI_APP_DB_PASSWORD:?defina JETSKI_APP_DB_PASSWORD no .env}"
: "${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no .env}"
: "${PUBLIC_URL:?defina PUBLIC_URL no .env}"
: "${PORTAL_PUBLIC_URL:?defina PORTAL_PUBLIC_URL no .env (ex.: https://cliente.meujet.com.br)}"
: "${CONSOLE_PUBLIC_URL:?defina CONSOLE_PUBLIC_URL no .env (ex.: https://admin.meujet.com.br)}"

# Ambiente. Sem a variável = produção, exatamente como sempre foi.
# `espelho` é a VM de testes de carga (infra/espelho/README.md): mesma stack,
# mas o preflight reprova o .env antes de qualquer container subir se algo
# apontar para o mundo real (túnel, Gmail, backup off-site, hostnames), e a
# camada docker-compose.espelho.yml desvia todo e-mail para o Mailpit.
AMBIENTE="${MEUJET_AMBIENTE:-producao}"
case "$AMBIENTE" in
  producao) ;;
  espelho)
    log "ambiente ESPELHO — rodando o preflight antes de tocar em qualquer container..."
    bash infra/espelho/preflight.sh || die "preflight do espelho reprovou o .env — nada foi alterado."
    COMPOSE="$COMPOSE -f docker-compose.espelho.yml"
    PSQL="$COMPOSE exec -T postgres psql -v ON_ERROR_STOP=1 -U jetski -d jetski_prod"
    ;;
  *) die "MEUJET_AMBIENTE inválido: '$AMBIENTE' (use 'producao' ou 'espelho')" ;;
esac

# Criptografia de segredos (senha SMTP por tenant): se não houver chave, gera uma
# e grava no .env (uma única vez). NUNCA sobrescreve uma chave já existente —
# trocar a chave torna os segredos já cifrados indecifráveis.
if [ -z "${JETSKI_SECRET_KEY:-}" ]; then
  log "JETSKI_SECRET_KEY ausente — gerando chave-mestra de criptografia e gravando no .env..."
  JETSKI_SECRET_KEY="$(openssl rand -base64 32)"
  export JETSKI_SECRET_KEY
  printf '\n# Chave-mestra de criptografia de segredos (gerada pelo deploy; NÃO altere).\nJETSKI_SECRET_KEY=%s\n' \
    "$JETSKI_SECRET_KEY" >> .env
  warn "Chave gerada e salva no .env. Faça um BACKUP seguro dela (perdê-la = perder os segredos cifrados)."
fi

# HMAC dos tokens de ativação (magic-link): o default do application.yml está no
# repo (público) — em prod é obrigatório um segredo próprio. Gera uma vez e grava
# no .env; trocar a chave só invalida magic-links ainda não usados (inofensivo).
if [ -z "${JWT_MAGIC_LINK_SECRET:-}" ]; then
  log "JWT_MAGIC_LINK_SECRET ausente — gerando segredo do magic-link e gravando no .env..."
  JWT_MAGIC_LINK_SECRET="$(openssl rand -base64 48)"
  export JWT_MAGIC_LINK_SECRET
  printf '\n# Segredo HMAC dos tokens de ativação magic-link (gerado pelo deploy).\nJWT_MAGIC_LINK_SECRET=%s\n' \
    "$JWT_MAGIC_LINK_SECRET" >> .env
fi

# 1. Atualiza o código
if [ "${NO_PULL:-0}" != "1" ]; then
  log "git pull..."
  git pull --ff-only
fi

# 2. Build das imagens — antes de tocar em qualquer container.
# Antes os builds --no-cache (Maven + três Next.js) rodavam logo DEPOIS de subir a
# infra e disputavam CPU com o Keycloak recém-recriado, que levou ~3,5 min para
# responder (sobe em ~11 s com a VM ociosa). Construir primeiro deixa a VM livre
# quando os containers sobem, e a janela entre derrubar e subir cada um fica mínima.
if [ "${NO_BUILD:-0}" != "1" ]; then
  # Keycloak: build COM cache — as camadas só invalidam quando tema/SPI mudam (o COPY
  # invalida por checksum). Sem mudança, o id da imagem não muda (ver
  # BUILDX_NO_DEFAULT_ATTESTATIONS acima) e o container não é recriado.
  kc_img_antes="$(kc_imagem_id)"
  log "build da imagem keycloak (tema de login meujet + SPI)..."
  $COMPOSE build keycloak
  if [ -n "$kc_img_antes" ] && [ "$kc_img_antes" = "$(kc_imagem_id)" ]; then
    log "imagem do Keycloak inalterada (tema/SPI sem mudança)"
  else
    warn "imagem do Keycloak mudou (tema/SPI/base) — o container será recriado"
  fi

  log "build backend + frontends (--no-cache p/ evitar reaproveitar imagem velha)..."
  $COMPOSE build --no-cache backend
  $COMPOSE build --no-cache frontend
  $COMPOSE build --no-cache portal
  $COMPOSE build --no-cache console
fi

# 3. Infra base
# O compose só recria um serviço cuja imagem OU config mudou (ex.: env no
# docker-compose.prod.yml, ou o Postgres com pg_hba novo). Por isso NÃO usamos
# --no-recreate no Keycloak: uma mudança real de config precisa ser aplicada.
kc_ctr_antes="$(kc_container_id)"
log "subindo infra base (postgres/redis/keycloak/opa/minio)..."
$COMPOSE up -d postgres redis keycloak opa minio mailpit
if [ "$AMBIENTE" = "espelho" ]; then
  # Marinha/PagTesouro sintéticos. O backend sobe adiante com --no-deps, então o
  # depends_on não os traria. --force-recreate: o código vem por volume (sintetico/),
  # e só um container novo carrega a versão que o git pull acabou de trazer.
  log "espelho: subindo fakes-externos (Marinha/PagTesouro sintéticos)..."
  $COMPOSE up -d --force-recreate fakes-externos
fi
if [ "$(kc_container_id)" = "$kc_ctr_antes" ] && [ -n "$kc_ctr_antes" ]; then
  log "Keycloak mantido (mesma imagem e config) — SSO não foi interrompido"
else
  warn "Keycloak (re)criado neste deploy — SSO fora até o realm responder; aguardando..."
  if aguardar_keycloak 60; then
    log "Keycloak respondendo"
  else
    warn "Keycloak não respondeu em ~3 min — o deploy segue (verifique: $COMPOSE logs keycloak)"
  fi
fi

log "aguardando postgres..."
for i in $(seq 1 30); do
  if $COMPOSE exec -T postgres pg_isready -U jetski -d jetski_prod >/dev/null 2>&1; then break; fi
  sleep 2
  [ "$i" = "30" ] && die "postgres não respondeu a tempo"
done

# 4. Role de aplicação (idempotente)
log "criando/atualizando role jetski_app..."
$PSQL -v app_pwd="$JETSKI_APP_DB_PASSWORD" -f /dev/stdin < infra/prod/01-init-roles.sql

# 5. Migrations (superuser, one-shot)
log "aplicando migrations Flyway..."
$COMPOSE run --rm flyway || die "migrations falharam"

# 6. Grants pós-migration + verificação de RLS (aborta se faltar)
log "re-concedendo grants ao jetski_app..."
$PSQL -c "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jetski_app;" \
      -c "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jetski_app;"
log "verificando RLS em tabelas multi-tenant..."
$PSQL -f /dev/stdin < infra/prod/02-verify-rls.sql || die "verificação de RLS falhou — deploy abortado"

# 7. Recreate das apps (imagens já construídas no passo 2)
log "recriando backend, frontend, portal e console..."
$COMPOSE up -d --force-recreate --no-deps backend frontend portal console

# 7.5 Limpeza pós-build: como os builds do passo 2 são --no-cache, o build cache do
# BuildKit nunca é reaproveitado e só acumula (já chegou a 155GB e quase encheu
# o disco). Mantém 10GB dos mais recentes por segurança; remove também imagens
# dangling (as versões antigas de backend/frontend/portal/console que ficaram sem tag).
# Fica DEPOIS do recreate: as imagens novas precisam estar em uso antes do prune.
if [ "${NO_BUILD:-0}" != "1" ]; then
  log "limpando build cache antigo e imagens dangling..."
  docker builder prune -af --keep-storage 10GB >/dev/null 2>&1 || warn "builder prune falhou (ignorado)"
  docker image prune -f >/dev/null 2>&1 || warn "image prune falhou (ignorado)"
fi

# 8. OPA (recarrega policies — não tem --watch) e ingress
log "recarregando OPA e subindo nginx/cloudflared..."
$COMPOSE restart opa
# nginx SEMPRE recriado: o bind mount de arquivo único prende o inode — após
# um git pull que troque o nginx.conf, restart/reload continuam servindo o
# arquivo ANTIGO silenciosamente (mordeu no cutover do subdomínio do portal).
$COMPOSE up -d --force-recreate --no-deps nginx
$COMPOSE up -d cloudflared

# 9. Keycloak: converge o client jetski-backoffice (público + PKCE S256) +
# redirects de produção, idempotente. Num realm NOVO o realm.json já nasce certo
# (substituição de env var no import); este passo é a única forma de ajustar o
# client num realm EXISTENTE sem zerar o realm (--import-realm não re-importa).
# Não-fatal: a config persiste no banco do Keycloak.
log "aguardando realm Keycloak e configurando client jetski-backoffice..."
if aguardar_keycloak 40; then
  bash infra/prod/configure-keycloak-client.sh || warn "config do client Keycloak falhou (verifique manualmente)"
  bash infra/prod/configure-keycloak-smtp.sh || warn "config de SMTP do Keycloak falhou (verifique manualmente)"
  bash infra/prod/configure-keycloak-sessions.sh || warn "config de sessões SSO do Keycloak falhou (verifique manualmente)"
  bash infra/prod/configure-keycloak-grafana.sh || warn "config OIDC do Grafana falhou (verifique manualmente)"
  bash infra/prod/configure-keycloak-google-idp.sh || warn "config do IdP Google falhou (verifique manualmente)"
  bash infra/prod/configure-keycloak-password-check.sh || warn "config do client password-check falhou (verifique manualmente)"
  bash infra/prod/configure-keycloak-email-code.sh || warn "config do login por código de e-mail falhou (verifique manualmente)"
  bash infra/prod/configure-keycloak-2fa.sh || warn "config do 2FA (TOTP/WebAuthn) falhou (verifique manualmente)"
  # Depois do client (configure-keycloak-client.sh cria o jetski-platform-console)
  bash infra/prod/configure-keycloak-console-2fa.sh || warn "config do 2FA obrigatório do console falhou (verifique manualmente)"
  # Espelho: o SMTP do Keycloak vai para o Mailpit (o script de produção acima pulou,
  # por não haver credencial). É o que faz chegar o código de login do portal.
  if [ "$AMBIENTE" = "espelho" ]; then
    bash infra/espelho/configure-keycloak-smtp.sh || warn "config de SMTP do Keycloak (espelho) falhou"
  fi
else
  warn "Keycloak realm não respondeu — pulei a config do client/SMTP (rode os scripts em infra/prod/ depois)"
fi

# 10. Smoke (aguarda o boot do Spring — pode levar ~30-60s)
log "smoke check (aguardando backend subir)..."
code=000
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8090/api/actuator/health || echo 000)
  [ "$code" = "200" ] && break
  sleep 3
done
[ "$code" = "200" ] && log "backend healthy (200)" || warn "backend health=$code após ~90s (verifique: $COMPOSE logs backend)"

# 11. Backup diário via systemd timer (padrão desta VM — não há cron instalado).
#    Off-site: BACKUP_RCLONE_REMOTE no .env + rclone configurado — ver DEPLOY.md.
mkdir -p "$HOME/backups/meujet"
if [ "$AMBIENTE" = "espelho" ]; then
  # Dado sintético e descartável. E o timer é justamente o marcador que o
  # preflight usa para reconhecer a VM de produção — instalá-lo aqui apagaria
  # essa proteção.
  log "espelho: timer de backup NÃO instalado (dados sintéticos, descartáveis)."
elif [ ! -f /etc/systemd/system/meujet-backup.timer ]; then
  sudo tee /etc/systemd/system/meujet-backup.service > /dev/null <<UNIT
[Unit]
Description=Backup diario Meu Jet (Postgres + MinIO, ver DEPLOY.md)

[Service]
Type=oneshot
User=$(id -un)
ExecStart=$(pwd)/infra/prod/backup.sh
StandardOutput=append:$HOME/backups/meujet/backup.log
StandardError=append:$HOME/backups/meujet/backup.log
UNIT
  sudo tee /etc/systemd/system/meujet-backup.timer > /dev/null <<'UNIT'
[Unit]
Description=Timer do backup diario Meu Jet

[Timer]
OnCalendar=*-*-* 04:00:00
Persistent=true

[Install]
WantedBy=timers.target
UNIT
  sudo systemctl daemon-reload && sudo systemctl enable --now meujet-backup.timer
  log "timer systemd de backup diário instalado (04:00 — infra/prod/backup.sh)."
fi

log "deploy concluído. Público: ${PUBLIC_URL}"
$COMPOSE ps
