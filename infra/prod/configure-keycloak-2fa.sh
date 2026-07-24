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

flow_id() { # $1 = alias → id ("" se ausente). NOTA: /authentication/flows só
            # lista flows TOP-LEVEL — subflows use child_flow_id.
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows" \
    | ALIAS="$1" python3 -c 'import sys,json,os
fs=json.load(sys.stdin)
print(next((f["id"] for f in fs if f.get("alias")==os.environ["ALIAS"]), ""))'
}

# flowId de um SUBFLOW (não aparece em /flows): varre as executions do
# ancestral top-level e casa pelo displayName (= alias do subflow).
child_flow_id() { # $1 = top-level alias; $2 = subflow alias → flowId ("" se ausente)
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows/$1/executions" \
    | DISP="$2" python3 -c 'import sys,json,os
try:
    es=json.load(sys.stdin)
except Exception:
    print(""); sys.exit()
print(next((e.get("flowId","") for e in es if e.get("displayName")==os.environ["DISP"] and e.get("flowId")), ""))'
}

# Remove um SUBFLOW pela sua EXECUTION no ancestral (DELETE /flows/{id} do
# subflow deixa a execution órfã no pai → NPE). Deletar a execution remove os
# dois.
delete_subflow() { # $1 = top-level alias; $2 = subflow alias
  local exec_id
  exec_id=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows/$1/executions" \
    | DISP="$2" python3 -c 'import sys,json,os
try: es=json.load(sys.stdin)
except Exception: es=[]
print(next((e.get("id","") for e in es if e.get("displayName")==os.environ["DISP"] and e.get("flowId")), ""))')
  if [ -n "$exec_id" ]; then
    curl -s -o /dev/null -w ">> DELETE execution do subflow $2 http=%{http_code}\n" \
      -X DELETE "$KC/admin/realms/$REALM/authentication/executions/$exec_id" "${auth[@]}"
  fi
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
  set_requirement "$FORMS_ALIAS" "mj-trusted-device-enroll" "DISABLED" || true
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

# --- subflow portal-2fa + enroll no forms — shape com trusted device ---------
# Estrutura (marker do shape novo = execução mj-trusted-device-enroll no forms):
#   portal-browser-forms
#   ├─ meujet-email-code          REQUIRED
#   ├─ portal-2fa (CONDITIONAL)   ← condição vê webauthn/otp como IRMÃOS diretos
#   │    ├─ conditional-user-configured REQUIRED
#   │    ├─ mj-trusted-device-check      ALTERNATIVE (cookie válido → pula)
#   │    ├─ webauthn-authenticator       ALTERNATIVE
#   │    └─ auth-otp-form                ALTERNATIVE
#   └─ mj-trusted-device-enroll   REQUIRED  (auto-decide se mostra: só com fator
#                                            e sem trusted-skip)
# NÃO aninhar os fatores: conditional-user-configured só enxerga irmãos diretos.
# enroll DIRETO no forms (level 0) = shape novo. O aninhado (level>0) é o antigo.
tem_enroll=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows/$FORMS_ALIAS/executions" \
  | python3 -c 'import sys,json
try: es=json.load(sys.stdin)
except Exception: es=[]
print(any(e.get("providerId")=="mj-trusted-device-enroll" and e.get("level")==0 for e in es))')
if [ "$tem_enroll" != "True" ]; then
  if [ -n "$(child_flow_id portal-browser "$TFA_ALIAS")" ]; then
    echo ">> shape antigo do $TFA_ALIAS — derrubando via execution..."
    delete_subflow "$FORMS_ALIAS" "$TFA_ALIAS"
  fi
  echo ">> criando $TFA_ALIAS (check-gate + webauthn/otp) em $FORMS_ALIAS..."
  curl -s -o /dev/null -w ">> POST subflow $TFA_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$FORMS_ALIAS/executions/flow" \
    "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$TFA_ALIAS\",\"type\":\"basic-flow\",\"description\":\"2FA opt-in + device confiável\",\"provider\":\"registration-page-form\"}"
  for prov in mj-trusted-device-check webauthn-authenticator auth-otp-form; do
    curl -s -o /dev/null -w ">> POST exec $prov http=%{http_code}\n" -X POST \
      "$KC/admin/realms/$REALM/authentication/flows/$TFA_ALIAS/executions/execution" \
      "${auth[@]}" "${json[@]}" -d "{\"provider\":\"$prov\"}"
  done
  echo ">> criando enroll no nível do forms..."
  curl -s -o /dev/null -w ">> POST exec mj-trusted-device-enroll http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$FORMS_ALIAS/executions/execution" \
    "${auth[@]}" "${json[@]}" -d '{"provider":"mj-trusted-device-enroll"}'
fi
# requirements (sempre converge). portal-2fa REQUIRED (o check é o gate opt-in).
set_requirement "$FORMS_ALIAS" "meujet-email-code" "REQUIRED"
set_requirement "$FORMS_ALIAS" "$TFA_ALIAS" "CONDITIONAL"
set_requirement "$FORMS_ALIAS" "mj-trusted-device-enroll" "REQUIRED"
set_requirement "$TFA_ALIAS" "mj-trusted-device-check" "REQUIRED"
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
# Marker do shape novo (com trusted device) = existência de post-broker-2fa-challenge.
PB_ID=$(flow_id "$PB_ALIAS")   # top-level: flow_id serve
# shape novo = mj-trusted-device-enroll no topo de post-broker-2fa
tem_pb_enroll=""
[ -n "$PB_ID" ] && tem_pb_enroll=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows/$PB_ALIAS/executions" \
  | python3 -c 'import sys,json
try: es=json.load(sys.stdin)
except Exception: es=[]
print(next((True for e in es if e.get("providerId")=="mj-trusted-device-enroll" and e.get("level")==0), ""))')
if [ -n "$PB_ID" ] && [ -z "$tem_pb_enroll" ]; then
  echo ">> shape antigo do $PB_ALIAS — recriando (delete cascateia os subflows)..."
  atualizar_post_broker_idp ""   # desbinda antes de deletar (flow em uso)
  curl -s -o /dev/null -w ">> DELETE flow $PB_ALIAS http=%{http_code}\n" \
    -X DELETE "$KC/admin/realms/$REALM/authentication/flows/$PB_ID" "${auth[@]}"
  PB_ID=""
fi
if [ -z "$PB_ID" ]; then
  echo ">> criando $PB_ALIAS (allow REQUIRED + cond CONDITIONAL flat + enroll REQUIRED)..."
  curl -s -o /dev/null -w ">> POST flow $PB_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows" "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$PB_ALIAS\",\"description\":\"2FA pós-broker (Google)\",\"providerId\":\"basic-flow\",\"topLevel\":true,\"builtIn\":false}"
  curl -s -o /dev/null -w ">> POST exec allow-access http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$PB_ALIAS/executions/execution" \
    "${auth[@]}" "${json[@]}" -d '{"provider":"allow-access-authenticator"}'
  curl -s -o /dev/null -w ">> POST subflow $PB_COND_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$PB_ALIAS/executions/flow" \
    "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$PB_COND_ALIAS\",\"type\":\"basic-flow\",\"description\":\"condicional 2FA + device\",\"provider\":\"registration-page-form\"}"
  for prov in mj-trusted-device-check webauthn-authenticator auth-otp-form; do
    curl -s -o /dev/null -w ">> POST exec $prov http=%{http_code}\n" -X POST \
      "$KC/admin/realms/$REALM/authentication/flows/$PB_COND_ALIAS/executions/execution" \
      "${auth[@]}" "${json[@]}" -d "{\"provider\":\"$prov\"}"
  done
  curl -s -o /dev/null -w ">> POST exec mj-trusted-device-enroll http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$PB_ALIAS/executions/execution" \
    "${auth[@]}" "${json[@]}" -d '{"provider":"mj-trusted-device-enroll"}'
fi
set_requirement "$PB_ALIAS" "allow-access-authenticator" "REQUIRED"
set_requirement "$PB_ALIAS" "$PB_COND_ALIAS" "CONDITIONAL"
set_requirement "$PB_ALIAS" "mj-trusted-device-enroll" "REQUIRED"
set_requirement "$PB_COND_ALIAS" "mj-trusted-device-check" "REQUIRED"
set_requirement "$PB_COND_ALIAS" "webauthn-authenticator" "ALTERNATIVE"
set_requirement "$PB_COND_ALIAS" "auth-otp-form" "ALTERNATIVE"
atualizar_post_broker_idp "$PB_ALIAS"

echo ">> 2FA opt-in configurado (portal-2fa condicional + post-broker google + webAuthnPolicy)."
