#!/usr/bin/env bash
###############################################################################
# Limpa as RESERVAS (e dados derivados) de UM tenant — para zerar dados de teste.
#
# Apaga, dentro de uma transação e escopado ao tenant:
#   - comissao e abastecimento das locações do tenant
#   - locacao (cascateia foto, locacao_item_opcional)
#   - reserva (cascateia documento_emitido, reserva_aceite, reserva_comprovante,
#              reserva_habilitacao)
#
# NÃO toca em: clientes, jetskis, modelos, vendedores, instrutores, config do tenant.
# Objetos no MinIO (PDFs/fotos) ficam órfãos (inofensivo) — só as linhas do banco saem.
#
# Uso:
#   ./limpar-reservas-tenant.sh <slug-do-tenant>            # produção (jetski_prod)
#   ./limpar-reservas-tenant.sh <slug> --dry-run           # só mostra as contagens
#   DB=jetski_dev ./limpar-reservas-tenant.sh <slug>       # apontar para dev
#
# Pré-requisitos: rodar na raiz do repo, com os containers de pé (postgres).
###############################################################################
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; cd "$SCRIPT_DIR"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[limpar]${NC} $*"; }
warn() { echo -e "${YELLOW}[limpar]${NC} $*"; }
die()  { echo -e "${RED}[limpar] ERRO:${NC} $*" >&2; exit 1; }

SLUG="${1:-}"; DRY_RUN=0
[ "${2:-}" = "--dry-run" ] && DRY_RUN=1
[ -n "$SLUG" ] || die "informe o slug do tenant. Ex.: ./limpar-reservas-tenant.sh acme"

DB="${DB:-jetski_prod}"
if [ "$DB" = "jetski_prod" ]; then
  COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
else
  COMPOSE="docker compose"
fi
PSQL="$COMPOSE exec -T postgres psql -v ON_ERROR_STOP=1 -U jetski -d $DB"
Q() { $PSQL -t -A -c "$1"; }

# 1. Resolve o tenant
TID="$(Q "SELECT id FROM tenant WHERE slug='${SLUG//\'/}';" | tr -d '[:space:]')"
[ -n "$TID" ] || die "tenant com slug '$SLUG' não encontrado no banco '$DB'."
RAZAO="$(Q "SELECT razao_social FROM tenant WHERE id='$TID';")"
log "Tenant: ${RAZAO} (slug=$SLUG, id=$TID) — banco: $DB"

# 2. Contagens do que será apagado
N_RES="$(Q "SELECT count(*) FROM reserva WHERE tenant_id='$TID';")"
N_LOC="$(Q "SELECT count(*) FROM locacao WHERE tenant_id='$TID';")"
N_HAB="$(Q "SELECT count(*) FROM reserva_habilitacao WHERE reserva_id IN (SELECT id FROM reserva WHERE tenant_id='$TID');")"
N_DOC="$(Q "SELECT count(*) FROM documento_emitido WHERE reserva_id IN (SELECT id FROM reserva WHERE tenant_id='$TID');")"
echo
warn "Será APAGADO (escopo: tenant $SLUG, banco $DB):"
echo "    reservas ............. $N_RES (+ habilitação=$N_HAB, documentos=$N_DOC, aceites, comprovantes)"
echo "    locações ............. $N_LOC (+ fotos, itens opcionais, comissões, abastecimentos)"
echo "    PRESERVADOS .......... clientes, jetskis, modelos, vendedores, instrutores, config"
echo

if [ "$N_RES" = "0" ] && [ "$N_LOC" = "0" ]; then
  log "Nada para apagar (0 reservas e 0 locações). Saindo."; exit 0
fi
if [ "$DRY_RUN" = "1" ]; then
  log "--dry-run: nada foi apagado."; exit 0
fi

# 3. Confirmação explícita (digitar o slug)
read -r -p "$(echo -e "${RED}Confirme digitando o slug do tenant ($SLUG):${NC} ")" CONF
[ "$CONF" = "$SLUG" ] || die "confirmação não confere — abortado (nada apagado)."

# 4. Deleção transacional, na ordem das dependências
log "Apagando..."
$PSQL -v tid="$TID" <<'SQL'
BEGIN;
DELETE FROM comissao      WHERE locacao_id IN (SELECT id FROM locacao WHERE tenant_id = :'tid');
DELETE FROM abastecimento WHERE locacao_id IN (SELECT id FROM locacao WHERE tenant_id = :'tid');
DELETE FROM locacao       WHERE tenant_id = :'tid';
DELETE FROM reserva       WHERE tenant_id = :'tid';
COMMIT;
SQL

log "Concluído. Reservas e locações do tenant '$SLUG' zeradas."
warn "Obs.: PDFs/fotos no MinIO ficam órfãos (inofensivo) — só as linhas do banco foram removidas."
