#!/usr/bin/env bash
# Configura (idempotente) o 2FA opt-in (TOTP + WebAuthn) na identidade única:
#  - subflow condicional "portal-2fa" no forms do flow portal-browser
#    (exige o fator SÓ de quem cadastrou — condition-user-configured)
#  - flow "post-broker-2fa" no IdP google (2FA também pra login social)
#  - webAuthnPolicy do realm + required actions de cadastro/remoção via AIA
#
# Necessário porque --import-realm NÃO re-importa realm existente; num realm
# NOVO o infra/keycloak-realm.json já traz flows/policy (mudou lá, mude aqui).
# Dev com volume existente: rodar após ./rebuild.sh keycloak.
#
# ROLLBACK=1 bash infra/prod/configure-keycloak-2fa.sh
#   → subflow portal-2fa vira DISABLED + remove postBrokerLoginFlowAlias do
#     google (kill switch: login volta a 1 fator, sem restart).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"
FORMS_ALIAS="portal-browser-forms"
TFA_ALIAS="portal-2fa"
PB_ALIAS="post-broker-2fa"
PB_GATE_ALIAS="post-broker-2fa-gate"
PB_COND_ALIAS="post-broker-2fa-cond"

# Em prod a senha vem do .env; em dev ela é fixa no docker-compose.yml.
TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=${KEYCLOAK_ADMIN_PASSWORD:-Mazuca@123}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

auth=(-H "Authorization: Bearer $TOKEN")
json=(-H "Content-Type: application/json")

flow_id() { # $1 = alias → id ("" se ausente)
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows" \
    | ALIAS="$1" python3 -c 'import sys,json,os
fs=json.load(sys.stdin)
print(next((f["id"] for f in fs if f.get("alias")==os.environ["ALIAS"]), ""))'
}

# seta requirement de uma execution do flow $1 cujo displayName/alias/provider casa $2
set_requirement() { # $1 = flow alias; $2 = matcher (providerId ou displayName); $3 = requirement
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

atualizar_post_broker_idp() { # $1 = alias do flow ("" remove). GET→merge→PUT sem tocar no config (segredo mascarado!)
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/identity-provider/instances/google" \
    | FLOWALIAS="$1" python3 -c '
import sys, json, os
rep = json.load(sys.stdin)
alias = os.environ["FLOWALIAS"]
if alias:
    rep["postBrokerLoginFlowAlias"] = alias
else:
    rep.pop("postBrokerLoginFlowAlias", None)
print(json.dumps(rep))' \
    | curl -s -o /dev/null -w ">> PUT idp google postBrokerLoginFlowAlias=${1:-<removido>} http=%{http_code}\n" \
        -X PUT "$KC/admin/realms/$REALM/identity-provider/instances/google" \
        "${auth[@]}" "${json[@]}" -d @-
}

# --- rollback: desliga o degrau e sai ----------------------------------------
if [ "${ROLLBACK:-0}" = "1" ]; then
  set_requirement "$FORMS_ALIAS" "$TFA_ALIAS" "DISABLED" || true
  # o matcher acima usa displayName do subflow = alias
  atualizar_post_broker_idp ""
  echo ">> ROLLBACK: 2FA desativado (login volta a 1 fator; fatores cadastrados ficam dormentes)."
  exit 0
fi

# --- required actions built-in: cadastro (AIA) e remoção com step-up ---------
for RA in webauthn-register delete_credential; do
  RA_JSON=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/required-actions/$RA")
  if [ -n "$RA_JSON" ] && [ "$RA_JSON" != "null" ]; then
    printf '%s' "$RA_JSON" | python3 -c 'import sys,json;d=json.load(sys.stdin);d["enabled"]=True;print(json.dumps(d))' \
      | curl -s -o /dev/null -w ">> PUT required-action $RA enabled http=%{http_code}\n" \
          -X PUT "$KC/admin/realms/$REALM/authentication/required-actions/$RA" \
          "${auth[@]}" "${json[@]}" -d @-
  else
    echo ">> AVISO: required action $RA não registrada neste Keycloak"
  fi
done

# --- required action CUSTOM mj-2fa-disable: registrar (nasce unregistered) ----
DISABLE_RA="mj-2fa-disable"
ja_registrada=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/required-actions/$DISABLE_RA" \
  | python3 -c 'import sys,json
try:
    d=json.load(sys.stdin); print(bool(d) and d.get("alias")==sys.argv[1])
except Exception: print(False)' "$DISABLE_RA")
if [ "$ja_registrada" != "True" ]; then
  NOME=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/unregistered-required-actions" \
    | python3 -c 'import sys,json
pid=sys.argv[1]
print(next((a.get("name") for a in json.load(sys.stdin) if a.get("providerId")==pid), ""))' "$DISABLE_RA")
  if [ -n "$NOME" ]; then
    curl -s -o /dev/null -w ">> POST register-required-action $DISABLE_RA http=%{http_code}\n" -X POST \
      "$KC/admin/realms/$REALM/authentication/register-required-action" "${auth[@]}" "${json[@]}" \
      -d "{\"providerId\":\"$DISABLE_RA\",\"name\":\"$NOME\"}"
  else
    echo ">> AVISO: RA $DISABLE_RA ausente em unregistered (imagem sem o SPI novo?) — pulei"
  fi
fi
RA_JSON=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/required-actions/$DISABLE_RA")
if [ -n "$RA_JSON" ] && [ "$RA_JSON" != "null" ]; then
  printf '%s' "$RA_JSON" | python3 -c 'import sys,json;d=json.load(sys.stdin);d["enabled"]=True;print(json.dumps(d))' \
    | curl -s -o /dev/null -w ">> PUT required-action $DISABLE_RA enabled http=%{http_code}\n" \
        -X PUT "$KC/admin/realms/$REALM/authentication/required-actions/$DISABLE_RA" \
        "${auth[@]}" "${json[@]}" -d @-
fi

# --- webAuthnPolicy do realm -------------------------------------------------
curl -s "${auth[@]}" "$KC/admin/realms/$REALM" \
  | python3 -c '
import sys, json
rep = json.load(sys.stdin)
rep.update({
    "webAuthnPolicyRpEntityName": "Meu Jet",
    "webAuthnPolicyRpId": "",
    "webAuthnPolicySignatureAlgorithms": ["ES256", "RS256"],
    "webAuthnPolicyAttestationConveyancePreference": "not specified",
    "webAuthnPolicyAuthenticatorAttachment": "not specified",
    "webAuthnPolicyRequireResidentKey": "not specified",
    "webAuthnPolicyUserVerificationRequirement": "preferred",
    "webAuthnPolicyCreateTimeout": 0,
    "webAuthnPolicyAvoidSameAuthenticatorRegister": False,
    "webAuthnPolicyAcceptableAaguids": [],
})
print(json.dumps(rep))' \
  | curl -s -o /dev/null -w ">> PUT realm webAuthnPolicy http=%{http_code}\n" \
      -X PUT "$KC/admin/realms/$REALM" "${auth[@]}" "${json[@]}" -d @-

# --- subflow portal-2fa no forms (cria se ausente) ---------------------------
if [ -z "$(flow_id "$TFA_ALIAS")" ]; then
  echo ">> criando subflow $TFA_ALIAS em $FORMS_ALIAS..."
  curl -s -o /dev/null -w ">> POST subflow $TFA_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$FORMS_ALIAS/executions/flow" \
    "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$TFA_ALIAS\",\"type\":\"basic-flow\",\"description\":\"2FA opt-in (TOTP/WebAuthn)\",\"provider\":\"registration-page-form\"}"
  for prov in conditional-user-configured webauthn-authenticator auth-otp-form; do
    curl -s -o /dev/null -w ">> POST execution $prov http=%{http_code}\n" -X POST \
      "$KC/admin/realms/$REALM/authentication/flows/$TFA_ALIAS/executions/execution" \
      "${auth[@]}" "${json[@]}" -d "{\"provider\":\"$prov\"}"
  done
fi
# requirements (sempre converge — cobre rollback anterior e criação nova)
set_requirement "$FORMS_ALIAS" "meujet-email-code" "REQUIRED"
set_requirement "$FORMS_ALIAS" "$TFA_ALIAS" "CONDITIONAL"
set_requirement "$TFA_ALIAS" "conditional-user-configured" "REQUIRED"
set_requirement "$TFA_ALIAS" "webauthn-authenticator" "ALTERNATIVE"
set_requirement "$TFA_ALIAS" "auth-otp-form" "ALTERNATIVE"

# --- flow post-broker-2fa + bind no IdP google -------------------------------
# ESTRUTURA (a única que fecha as três restrições do DefaultAuthenticationFlow):
#   post-broker-2fa
#   ├─ allow-access REQUIRED   ← piso de sucesso (flow 100% pulado = exception)
#   └─ post-broker-2fa-cond CONDITIONAL [condition + webauthn ALT + otp ALT]
# NUNCA allow-access como ALTERNATIVE: (a) irmão de CONDITIONAL é descartado
# ("REQUIRED and ALTERNATIVE at same level"); (b) em outro arranjo ele vira
# opção SELECIONÁVEL no "try another way" = BYPASS do 2FA (bug real, 23/jul).
# Shapes antigos (gate, allow ALTERNATIVE) são derrubados e recriados.
tem_gate=$(flow_id "$PB_GATE_ALIAS")
PB_ID=$(flow_id "$PB_ALIAS")
allow_req=""
if [ -n "$PB_ID" ]; then
  allow_req=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows/$PB_ALIAS/executions" \
    | python3 -c 'import sys,json
print(next((e["requirement"] for e in json.load(sys.stdin) if e.get("providerId")=="allow-access-authenticator" and e.get("level")==0), ""))')
fi
if [ -n "$PB_ID" ] && { [ -n "$tem_gate" ] || [ "$allow_req" != "REQUIRED" ]; }; then
  echo ">> shape antigo detectado — recriando $PB_ALIAS..."
  atualizar_post_broker_idp ""   # desbinda antes de deletar (flow em uso)
  curl -s -o /dev/null -w ">> DELETE flow $PB_ALIAS http=%{http_code}\n" \
    -X DELETE "$KC/admin/realms/$REALM/authentication/flows/$PB_ID" "${auth[@]}"
  GATE_ID=$(flow_id "$PB_GATE_ALIAS")
  [ -n "$GATE_ID" ] && curl -s -o /dev/null -w ">> DELETE flow órfão $PB_GATE_ALIAS http=%{http_code}\n" \
    -X DELETE "$KC/admin/realms/$REALM/authentication/flows/$GATE_ID" "${auth[@]}"
  PB_ID=""
fi
if [ -z "$PB_ID" ]; then
  echo ">> criando flow $PB_ALIAS (allow-access REQUIRED + cond CONDITIONAL)..."
  curl -s -o /dev/null -w ">> POST flow $PB_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows" "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$PB_ALIAS\",\"description\":\"2FA pós-broker (Google)\",\"providerId\":\"basic-flow\",\"topLevel\":true,\"builtIn\":false}"
  curl -s -o /dev/null -w ">> POST execution allow-access http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$PB_ALIAS/executions/execution" \
    "${auth[@]}" "${json[@]}" -d '{"provider":"allow-access-authenticator"}'
  curl -s -o /dev/null -w ">> POST subflow $PB_COND_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$PB_ALIAS/executions/flow" \
    "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$PB_COND_ALIAS\",\"type\":\"basic-flow\",\"description\":\"condicional 2FA\",\"provider\":\"registration-page-form\"}"
  for prov in conditional-user-configured webauthn-authenticator auth-otp-form; do
    curl -s -o /dev/null -w ">> POST execution $prov http=%{http_code}\n" -X POST \
      "$KC/admin/realms/$REALM/authentication/flows/$PB_COND_ALIAS/executions/execution" \
      "${auth[@]}" "${json[@]}" -d "{\"provider\":\"$prov\"}"
  done
fi
set_requirement "$PB_ALIAS" "allow-access-authenticator" "REQUIRED"
set_requirement "$PB_ALIAS" "$PB_COND_ALIAS" "CONDITIONAL"
set_requirement "$PB_COND_ALIAS" "conditional-user-configured" "REQUIRED"
set_requirement "$PB_COND_ALIAS" "webauthn-authenticator" "ALTERNATIVE"
set_requirement "$PB_COND_ALIAS" "auth-otp-form" "ALTERNATIVE"
atualizar_post_broker_idp "$PB_ALIAS"

echo ">> 2FA opt-in configurado (portal-2fa condicional + post-broker google + webAuthnPolicy)."
