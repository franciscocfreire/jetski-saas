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
# Uso:
#   BASE_URL=https://www.meujet.com.br/api ./k6/provisionar-tenant.sh
#   BASE_URL=... PLATFORM_TOKEN=<jwt> ./k6/provisionar-tenant.sh   # aprova sozinho
#
# Saída: k6/.auth/tenant.json com tenantId, slug e credenciais do admin.
# Limpeza: ver o README (exclusão de empresa pelo console, com arquivamento).
# =============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8090/api}"
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

echo "==> 1. Criando a empresa de carga (slug: ${SLUG})"
RESP=$(api POST /v1/signup/tenant "$(jq -nc \
  --arg rs "Locadora de Carga ${CARIMBO}" --arg s "$SLUG" --arg e "$ADMIN_EMAIL" \
  '{razaoSocial:$rs, slug:$s, adminEmail:$e, adminNome:"Admin Carga"}')")

TENANT_ID=$(echo "$RESP" | jq -r '.tenantId // empty')
if [[ -z "$TENANT_ID" ]]; then
  echo "ERRO no signup: $RESP" >&2; exit 1
fi
echo "    tenantId: ${TENANT_ID}"

echo "==> 2. Aprovação da empresa"
if [[ -n "${PLATFORM_TOKEN:-}" ]]; then
  api POST "/v1/platform/tenants/${TENANT_ID}/approve" '{}' \
      -H "Authorization: Bearer ${PLATFORM_TOKEN}" >/dev/null
  echo "    aprovada via API de plataforma."
else
  cat <<AVISO
    PARE AQUI e aprove a empresa no console da plataforma:
      admin.meujet.com.br → Empresas → "${SLUG}" → Aprovar

    Depois rode de novo com TENANT_ID=${TENANT_ID} para seguir do passo 3,
    ou forneça PLATFORM_TOKEN para o script aprovar sozinho.

    (O portão é de propósito: nenhuma empresa entra em operação sem alguém dizer sim.)
AVISO
  echo "$RESP" | jq --arg slug "$SLUG" --arg email "$ADMIN_EMAIL" \
      '{tenantId:.tenantId, slug:$slug, adminEmail:$email, aprovado:false}' > "$SAIDA"
  exit 0
fi

echo "==> 3. Ativando a conta do admin"
echo "    O link de ativação vai por e-mail. Em dev, veja no Mailpit (:8025)."
echo "    Depois de definir a senha, exporte ADMIN_SENHA e rode o passo 4."

if [[ -z "${ADMIN_SENHA:-}" ]]; then
  jq -n --arg t "$TENANT_ID" --arg s "$SLUG" --arg e "$ADMIN_EMAIL" \
     '{tenantId:$t, slug:$s, adminEmail:$e, aprovado:true, senhaDefinida:false}' > "$SAIDA"
  echo "    Gravado parcial em ${SAIDA}. Defina a senha e rode com ADMIN_SENHA=..."
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

jq -n --arg t "$TENANT_ID" --arg s "$SLUG" --arg e "$ADMIN_EMAIL" \
   '{tenantId:$t, slug:$s, adminEmail:$e, aprovado:true, senhaDefinida:true}' > "$SAIDA"
chmod 600 "$SAIDA"
echo "Pronto. ${SAIDA}"
echo
echo "Exporte para os cenários:"
echo "  export TENANT_ID=${TENANT_ID} TENANT_SLUG=${SLUG}"
