#!/usr/bin/env bash
# Configura (idempotente) o 2FA OBRIGATÓRIO do Console da Plataforma:
#   - flow top-level "console-browser" = senha REQUIRED + OTP REQUIRED
#   - binding browser do client jetski-platform-console nesse flow
#
# DIFERENÇAS DELIBERADAS em relação ao 2FA opt-in do backoffice/portal
# (configure-keycloak-2fa.sh) — cada uma é uma decisão, não um esquecimento:
#
#  1. SEM auth-cookie. No flow padrão o cookie de SSO é ALTERNATIVE e satisfaz o
#     login sozinho: quem entrasse no backoffice com 1 fator abriria o console
#     SEM 2FA nenhum — bypass silencioso. Aqui todo acesso ao console re-autentica.
#  2. SEM identity-provider-redirector. Operador de plataforma não entra por
#     login social; o Google IdP não vale para este client.
#  3. auth-otp-form REQUIRED (não ALTERNATIVE dentro de subflow). É o que torna o
#     2FA realmente obrigatório: sem credencial OTP o Keycloak injeta a required
#     action CONFIGURE_TOTP e força o cadastro no primeiro login. O arranjo
#     "subflow REQUIRED com webauthn/otp ALTERNATIVE" NÃO serve — a selection-list
#     filtra ALTERNATIVE sem credencial e o subflow falha (mesma mordida do
#     trusted device). WebAuthn como segunda opção fica para depois, e só com
#     validação de login ao vivo.
#
# Necessário porque --import-realm NÃO re-importa realm existente; num realm NOVO
# o infra/keycloak-realm.json já traz o flow e o binding (mudou lá, mude aqui).
#
# ROLLBACK=1 bash infra/prod/configure-keycloak-console-2fa.sh
#   → remove o binding do client (volta ao browser flow padrão do realm, 1 fator).
#     Kill switch para não perder o acesso à plataforma por problema no flow.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env 2>/dev/null || true; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"
CLIENT_ID="jetski-platform-console"
FLOW_ALIAS="console-browser"

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=${KEYCLOAK_ADMIN_PASSWORD:-Mazuca@123}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

auth=(-H "Authorization: Bearer $TOKEN")
json=(-H "Content-Type: application/json")

flow_id() {
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows" \
    | ALIAS="$1" python3 -c 'import sys,json,os
fs=json.load(sys.stdin)
print(next((f["id"] for f in fs if f.get("alias")==os.environ["ALIAS"]), ""))'
}

client_uuid() {
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/clients?clientId=$CLIENT_ID" \
    | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")'
}

set_requirement() { # $1 = flow alias; $2 = providerId; $3 = requirement
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows/$1/executions" \
    | M="$2" R="$3" python3 -c '
import sys, json, os
m, r = os.environ["M"], os.environ["R"]
for e in json.load(sys.stdin):
    if e.get("providerId") == m or e.get("displayName") == m:
        e["requirement"] = r
        print(json.dumps(e))
        break' \
    | { read -r exec_json || true
        if [ -n "${exec_json:-}" ]; then
          printf '%s' "$exec_json" | curl -s -o /dev/null \
            -w ">> PUT $1/$2 requirement=$3 http=%{http_code}\n" \
            -X PUT "$KC/admin/realms/$REALM/authentication/flows/$1/executions" \
            "${auth[@]}" "${json[@]}" -d @-
        fi; }
}

# Binding browser do client. $1 = flowId ("" remove o override).
bind_client() {
  local UUID FLOWID="$1"
  UUID=$(client_uuid)
  if [ -z "$UUID" ]; then
    echo ">> AVISO: client $CLIENT_ID não existe — rode configure-keycloak-client.sh antes"
    return 1
  fi
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/clients/$UUID" \
    | FLOWID="$FLOWID" python3 -c '
import sys, json, os
d = json.load(sys.stdin)
fid = os.environ["FLOWID"]
ov = d.get("authenticationFlowBindingOverrides") or {}
if fid:
    ov["browser"] = fid
else:
    ov.pop("browser", None)
d["authenticationFlowBindingOverrides"] = ov
print(json.dumps(d))' \
    | curl -s -o /dev/null -w ">> PUT client $CLIENT_ID browser-binding=${1:-<removido>} http=%{http_code}\n" \
        -X PUT "$KC/admin/realms/$REALM/clients/$UUID" "${auth[@]}" "${json[@]}" -d @-
}

# --- rollback: desbinda e sai -------------------------------------------------
if [ "${ROLLBACK:-0}" = "1" ]; then
  bind_client "" || true
  echo ">> ROLLBACK: console volta ao browser flow padrão do realm (1 fator)."
  exit 0
fi

# --- flow console-browser ----------------------------------------------------
# Shape:
#   console-browser (top-level, basic-flow)
#   ├─ auth-username-password-form REQUIRED
#   └─ auth-otp-form               REQUIRED   ← força CONFIGURE_TOTP se ausente
FID=$(flow_id "$FLOW_ALIAS")
if [ -z "$FID" ]; then
  echo ">> criando flow $FLOW_ALIAS (senha + OTP, ambos REQUIRED)..."
  curl -s -o /dev/null -w ">> POST flow $FLOW_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows" "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$FLOW_ALIAS\",\"description\":\"Console da plataforma — 2FA obrigatório (sem SSO cookie, sem IdP)\",\"providerId\":\"basic-flow\",\"topLevel\":true,\"builtIn\":false}"
  for prov in auth-username-password-form auth-otp-form; do
    curl -s -o /dev/null -w ">> POST exec $prov http=%{http_code}\n" -X POST \
      "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions/execution" \
      "${auth[@]}" "${json[@]}" -d "{\"provider\":\"$prov\"}"
  done
  FID=$(flow_id "$FLOW_ALIAS")
fi

# requirements (sempre converge — barato e corrige flow mexido na mão)
set_requirement "$FLOW_ALIAS" "auth-username-password-form" "REQUIRED"
set_requirement "$FLOW_ALIAS" "auth-otp-form" "REQUIRED"

[ -n "$FID" ] || { echo "ERRO: flow $FLOW_ALIAS não pôde ser criado" >&2; exit 1; }
bind_client "$FID"

echo ">> Console: 2FA obrigatório configurado (flow $FLOW_ALIAS = $FID)."
echo ">> Primeiro login de cada operador exige cadastrar o TOTP (CONFIGURE_TOTP)."
