#!/usr/bin/env bash
# Configura (idempotente) o 2FA OBRIGATÓRIO do Console da Plataforma:
#   - flow top-level "console-browser" = Google (IdP) OU senha+OTP, ambos REQUIRED
#   - binding browser do client jetski-platform-console nesse flow
#
# DIFERENÇAS DELIBERADAS em relação ao 2FA opt-in do backoffice/portal
# (configure-keycloak-2fa.sh) — cada uma é uma decisão, não um esquecimento:
#
#  1. SEM auth-cookie. No flow padrão o cookie de SSO é ALTERNATIVE e satisfaz o
#     login sozinho: quem entrasse no backoffice com 1 fator abriria o console
#     SEM 2FA nenhum — bypass silencioso. Aqui todo acesso ao console re-autentica.
#  2. COM identity-provider-redirector (Google liberado desde 25/jul). A regra
#     "operador não usa login social" caiu junto com a separação staff×cliente:
#     sob IDENTIDADE ÚNICA (CLAUDE.md #3) a mesma pessoa entra por onde já
#     autentica. O segundo fator do caminho Google vem do post-broker-2fa,
#     vinculado ao IdP.
#  3. auth-otp-form REQUIRED (não ALTERNATIVE dentro de subflow). É o que torna o
#     2FA realmente obrigatório no caminho SENHA: sem credencial OTP o Keycloak
#     injeta a required action CONFIGURE_TOTP e força o cadastro no primeiro
#     login. O arranjo "subflow REQUIRED com webauthn/otp ALTERNATIVE" NÃO serve
#     — a selection-list filtra ALTERNATIVE sem credencial e o subflow falha
#     (mesma mordida do trusted device).
#  4. Senha e Google como ALTERNATIVE dentro de um SUBFLOW, nunca irmãos de um
#     REQUIRED no mesmo nível: "REQUIRED and ALTERNATIVE at same level" faz o
#     Keycloak DESCARTAR os ALTERNATIVE (a mordida já paga no post-broker).
#
# LIMITE CONHECIDO do caminho Google: o post-broker-2fa é CONDICIONAL
# (conditional-user-configured) e vinculado ao IdP — compartilhado com backoffice
# e portal, não dá para exigi-lo só aqui. Quem não tem NENHUM fator cadastrado
# entraria com 1 fator. Por isso o backend exige o fator na IDENTIDADE ao
# conceder papel de plataforma (PlatformOperadorService → CONFIGURE_TOTP), o que
# faz a condição do post-broker passar a valer para todo operador.
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
FORMS_ALIAS="console-browser-forms"

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
# Shape alvo:
#   console-browser
#   ├─ identity-provider-redirector  ALTERNATIVE   (Google)
#   └─ console-browser-forms         ALTERNATIVE   (senha)
#        ├─ auth-username-password-form REQUIRED
#        └─ auth-otp-form               REQUIRED
# Marker do shape novo = existência do subflow console-browser-forms.
FID=$(flow_id "$FLOW_ALIAS")
tem_subflow=""
[ -n "$FID" ] && tem_subflow=$(curl -s "${auth[@]}" \
  "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions" \
  | DISP="$FORMS_ALIAS" python3 -c 'import sys,json,os
try: es=json.load(sys.stdin)
except Exception: es=[]
print(next((True for e in es if e.get("displayName")==os.environ["DISP"] and e.get("flowId")), ""))')

# Migração do shape antigo (senha+OTP direto no nível 0) para o novo: remove só as
# execuções órfãs do topo, sem deletar o flow. DELETE do flow inteiro falha com 500
# enquanto ele está vinculado ao client, e o "recria" que vem depois bate 409 e ACRESCENTA
# as execuções novas às antigas — sobrando REQUIRED e ALTERNATIVE no MESMO nível, que é
# justamente o arranjo que o Keycloak descarta. (Aconteceu em dev, 25/jul.)
if [ -n "$FID" ]; then
  for prov in auth-username-password-form auth-otp-form; do
    for exec_id in $(curl -s "${auth[@]}" \
        "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions" \
        | P="$prov" python3 -c 'import sys,json,os
try: es=json.load(sys.stdin)
except Exception: es=[]
for e in es:
    if e.get("level")==0 and e.get("providerId")==os.environ["P"]:
        print(e["id"])'); do
      echo ">> shape antigo: removendo $prov do nível 0..."
      curl -s -o /dev/null -w ">> DELETE execution $prov http=%{http_code}\n" \
        -X DELETE "$KC/admin/realms/$REALM/authentication/executions/$exec_id" "${auth[@]}"
    done
  done
fi

if [ -z "$FID" ]; then
  echo ">> criando flow $FLOW_ALIAS..."
  curl -s -o /dev/null -w ">> POST flow $FLOW_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows" "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$FLOW_ALIAS\",\"description\":\"Console da plataforma — 2FA obrigatorio, sem SSO cookie. Google via post-broker-2fa.\",\"providerId\":\"basic-flow\",\"topLevel\":true,\"builtIn\":false}"
  FID=$(flow_id "$FLOW_ALIAS")
fi

if [ -z "$tem_subflow" ]; then
  echo ">> montando Google | senha+OTP em $FLOW_ALIAS..."
  curl -s -o /dev/null -w ">> POST exec identity-provider-redirector http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions/execution" \
    "${auth[@]}" "${json[@]}" -d '{"provider":"identity-provider-redirector"}'
  curl -s -o /dev/null -w ">> POST subflow $FORMS_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions/flow" \
    "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$FORMS_ALIAS\",\"type\":\"basic-flow\",\"description\":\"senha + OTP\",\"provider\":\"registration-page-form\"}"
  for prov in auth-username-password-form auth-otp-form; do
    curl -s -o /dev/null -w ">> POST exec $prov http=%{http_code}\n" -X POST \
      "$KC/admin/realms/$REALM/authentication/flows/$FORMS_ALIAS/executions/execution" \
      "${auth[@]}" "${json[@]}" -d "{\"provider\":\"$prov\"}"
  done
fi

# requirements (sempre converge — barato e corrige flow mexido na mão)
set_requirement "$FLOW_ALIAS" "identity-provider-redirector" "ALTERNATIVE"
set_requirement "$FLOW_ALIAS" "$FORMS_ALIAS" "ALTERNATIVE"
set_requirement "$FORMS_ALIAS" "auth-username-password-form" "REQUIRED"
set_requirement "$FORMS_ALIAS" "auth-otp-form" "REQUIRED"

[ -n "$FID" ] || { echo "ERRO: flow $FLOW_ALIAS não pôde ser criado" >&2; exit 1; }
bind_client "$FID"

echo ">> Console: 2FA obrigatório configurado (flow $FLOW_ALIAS = $FID)."
echo ">> Primeiro login de cada operador exige cadastrar o TOTP (CONFIGURE_TOTP)."
