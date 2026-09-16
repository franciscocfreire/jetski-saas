#!/usr/bin/env bash
# =============================================================================
# Provisiona o TENANT ISOLADO de teste de carga.
#
# Usa o fluxo de signup REAL da plataforma (o mesmo do e2e), não INSERT direto:
# assim o tenant nasce com tudo que um tenant de verdade tem (assinatura, plano,
# créditos de adesão, papéis) e o script não precisa saber o schema — que muda.
#
# O tenant nasce PENDENTE_APROVACAO de propósito: existe um portão humano antes
# de qualquer empresa entrar em operação. Este script para nesse ponto e diz o
# que fazer, a menos que você forneça um token de operador de plataforma.
#
# Uso (contra o ESPELHO — ver infra/espelho/README.md). Retomável: rode o mesmo
# comando a cada etapa, acrescentando o que ela pediu.
#   export BASE_URL=https://www.<dominio>/api \
#          ISSUER=https://sso.<dominio>/realms/jetski-saas \
#          APP_URL=https://app.<dominio>
#   ./k6/provisionar-tenant.sh                             # 1. cadastra e para
#   APROVADA=sim ./k6/provisionar-tenant.sh                # 2. depois de aprovar no console
#   APROVADA=sim ADMIN_SENHA=... ./k6/provisionar-tenant.sh  # 3+4. depois de ativar a conta
#   PLATFORM_TOKEN=<jwt> ./k6/provisionar-tenant.sh        # alternativa: aprova sozinho
# ISSUER e APP_URL são obrigatórios no passo 4 (login via auth-setup.sh): os
# padrões apontam para localhost.
#
# Saída: k6/.auth/tenant.json com tenantId, slug e credenciais do admin.
# Limpeza: no espelho, recriar o banco; ver o README do espelho.
# =============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8090/api}"

# Decisão de 16/set/2026: carga não roda em produção. Este script CRIA uma
# empresa — em meujet.com.br só com a frase exata (mesma trava do k6).
host="${BASE_URL#*://}"; host="${host%%[/:]*}"
if [[ "$host" == meujet.com.br || "$host" == *.meujet.com.br ]] \
   && [[ "${PERMITIR_PRODUCAO:-}" != "sim, é produção" ]]; then
  echo "ERRO: ${BASE_URL} é PRODUÇÃO. Provisione o tenant de carga no espelho (infra/espelho/README.md)." >&2
  exit 1
fi
SAIDA="${SAIDA:-$(dirname "$0")/.auth/tenant.json}"
CARIMBO=$(date +%Y%m%d-%H%M%S)
SLUG="${SLUG:-carga-${CARIMBO}}"          # o prefixo `carga-` é a trava do config.js
ADMIN_EMAIL="${ADMIN_EMAIL:-carga.admin.${CARIMBO}@exemplo.invalid}"
JETSKIS="${JETSKIS:-12}"

command -v jq >/dev/null || { echo "ERRO: jq é necessário." >&2; exit 1; }
mkdir -p "$(dirname "$SAIDA")"

api() { # api <metodo> <caminho> [corpo] [cabecalho-extra...]
  local metodo="$1" caminho="$2" corpo="${3:-}"; shift 3 || shift 2
  if [[ -n "$corpo" ]]; then
    curl -sS -X "$metodo" "${BASE_URL}${caminho}" -H 'Content-Type: application/json' "$@" -d "$corpo"
  else
    curl -sS -X "$metodo" "${BASE_URL}${caminho}" "$@"
  fi
}

# O script é RETOMÁVEL: grava o progresso em $SAIDA e, ao rodar de novo, continua
# de onde parou em vez de cadastrar outra empresa. Antes desta correção ele sempre
# recomeçava pelo signup — e, sem PLATFORM_TOKEN, sempre parava no passo 2 —, então
# o fluxo manual (aprovar no console e rodar de novo) nunca chegava ao fim e cada
# tentativa deixava uma empresa pendente a mais no espelho.
salvar() { # salvar <etapa>
  jq -n --arg t "$TENANT_ID" --arg s "$SLUG" --arg e "$ADMIN_EMAIL" --arg etapa "$1" \
     '{tenantId:$t, slug:$s, adminEmail:$e, etapa:$etapa}' > "$SAIDA"
  chmod 600 "$SAIDA"
}

TENANT_ID=""
if [[ -f "$SAIDA" ]] && [[ -n "$(jq -r '.tenantId // empty' "$SAIDA")" ]]; then
  TENANT_ID=$(jq -r '.tenantId' "$SAIDA")
  SLUG=$(jq -r '.slug' "$SAIDA")
  ADMIN_EMAIL=$(jq -r '.adminEmail' "$SAIDA")
  ETAPA=$(jq -r '.etapa // "aguardando-aprovacao"' "$SAIDA")
  echo "==> Retomando ${SLUG} (${TENANT_ID}) — etapa registrada: ${ETAPA}"
  echo "    (para começar outra empresa do zero, apague ${SAIDA})"
else
  echo "==> 1. Criando a empresa de carga (slug: ${SLUG})"
  RESP=$(api POST /v1/signup/tenant "$(jq -nc \
    --arg rs "Locadora de Carga ${CARIMBO}" --arg s "$SLUG" --arg e "$ADMIN_EMAIL" \
    '{razaoSocial:$rs, slug:$s, adminEmail:$e, adminNome:"Admin Carga"}')")
  TENANT_ID=$(echo "$RESP" | jq -r '.tenantId // empty')
  if [[ -z "$TENANT_ID" ]]; then
    echo "ERRO no signup: $RESP" >&2; exit 1
  fi
  echo "    tenantId: ${TENANT_ID}"
  ETAPA="aguardando-aprovacao"
  salvar "$ETAPA"
fi

if [[ "$ETAPA" == "pronto" ]]; then
  echo "==> ${SLUG} já está provisionada (frota semeada). Nada a fazer."
  echo "    export TENANT_ID=${TENANT_ID} TENANT_SLUG=${SLUG}"
  exit 0
fi

if [[ "$ETAPA" == "aguardando-aprovacao" ]]; then
  echo "==> 2. Aprovação da empresa"
  if [[ -n "${PLATFORM_TOKEN:-}" ]]; then
    api POST "/v1/platform/tenants/${TENANT_ID}/approve" '{}' \
        -H "Authorization: Bearer ${PLATFORM_TOKEN}" >/dev/null
    echo "    aprovada via API de plataforma."
  elif [[ "${APROVADA:-}" != "sim" ]]; then
    cat <<AVISO
    PARE AQUI e aprove a empresa no console da plataforma:
      https://admin.<dominio-do-espelho> → Empresas → "${SLUG}" → Aprovar

    Depois rode de novo com APROVADA=sim — o script retoma daqui, não cria outra.
    (O portão é de propósito: nenhuma empresa entra em operação sem alguém dizer sim.)
AVISO
    exit 0
  fi
  ETAPA="aguardando-senha"
  salvar "$ETAPA"
fi

if [[ -z "${ADMIN_SENHA:-}" ]]; then
  cat <<AVISO
==> 3. Ativação da conta do admin (${ADMIN_EMAIL})
    O link de ativação vai por e-mail — no espelho, para o Mailpit:
      ssh -L 8025:127.0.0.1:8025 ubuntu@<ip-do-espelho>  →  http://localhost:8025
    Defina a senha pelo link e rode de novo com ADMIN_SENHA=...
AVISO
  exit 0
fi

echo "==> 4. Semeando a frota (${JETSKIS} jetskis)"

# Reaproveita o login do auth-setup.sh e troca o refresh por um access token.
REFRESH=$(USUARIOS="${ADMIN_EMAIL}:${ADMIN_SENHA}" SAIDA=/dev/stdout \
          "$(dirname "$0")/auth-setup.sh" 2>/dev/null | jq -r '.usuarios[0].refreshToken // empty')
if [[ -z "$REFRESH" ]]; then
  echo "ERRO: não consegui autenticar como ${ADMIN_EMAIL}." >&2; exit 1
fi
ISSUER="${ISSUER:-http://localhost:8080/realms/jetski-saas}"
ACCESS=$(curl -sS -X POST "${ISSUER}/protocol/openid-connect/token" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "client_id=${CLIENT_ID:-jetski-backoffice}" \
  --data-urlencode "refresh_token=${REFRESH}" | jq -r '.access_token // empty')
[[ -n "$ACCESS" ]] || { echo "ERRO: refresh token não virou access token." >&2; exit 1; }

AUTH=(-H "Authorization: Bearer ${ACCESS}" -H "X-Tenant-Id: ${TENANT_ID}")

MODELO_ID=$(api POST "/v1/tenants/${TENANT_ID}/modelos" "$(jq -nc '{
    nome: "Carga GTI 130", fabricante: "Sintetico", potenciaHp: 130,
    capacidadePessoas: 3, precoBaseHora: 350.00, toleranciaMin: 10,
    incluiCombustivel: false, duracaoMinimaMin: 30,
    descricao: "CARGA — modelo sintético de teste"
  }')" "${AUTH[@]}" | jq -r '.id // empty')
[[ -n "$MODELO_ID" ]] || { echo "ERRO ao criar o modelo." >&2; exit 1; }
echo "    modelo: ${MODELO_ID}"

# A frota precisa ser maior que o número de VUs do cenário de balcão: cada
# jornada ocupa um jetski do check-in ao check-out, e sem folga as VUs passam o
# teste inteiro disputando a mesma máquina (o contador
# `jornadas_abortadas_sem_jetski` denuncia isso).
criados=0
for i in $(seq 1 "$JETSKIS"); do
  serie="CARGA-${CARIMBO}-$(printf '%03d' "$i")"
  id=$(api POST "/v1/tenants/${TENANT_ID}/jetskis" "$(jq -nc --arg m "$MODELO_ID" --arg s "$serie" '{
      modeloId: $m, serie: $s, ano: 2026, horimetroAtual: 100.0, status: "DISPONIVEL"
    }')" "${AUTH[@]}" | jq -r '.id // empty')
  [[ -n "$id" ]] && criados=$((criados + 1))
done
echo "    jetskis criados: ${criados}/${JETSKIS}"

salvar "pronto"
echo "Pronto. ${SAIDA}"
echo
echo "Exporte para os cenários:"
echo "  export TENANT_ID=${TENANT_ID} TENANT_SLUG=${SLUG}"
