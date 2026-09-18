#!/usr/bin/env bash
# =============================================================================
# "Google" SINTÉTICO no ESPELHO (fase E7 do ECOSSISTEMA_SINTETICO_SPEC.md).
#
# O provider `google` do Keycloak tem endpoints fixos (accounts.google.com); um fake exige
# um IdP OIDC genérico com o MESMO alias `google` — é o alias que o portal, o backoffice, o
# tema de login e o serviço de unificação de CPF consomem. O provedor externo é o PRÓPRIO
# Keycloak do espelho: um segundo realm (`google-sintetico`) faz o papel do Google, com o
# molde já validado em dev (infra/keycloak-setup/add-idp-teste-dev.sh). Para o realm
# jetski-saas é um provedor externo qualquer: exercita os mesmos flows do Google real —
# first broker login, vínculo de conta existente por e-mail, post-broker 2FA, dispositivo
# confiável — de forma determinística.
#
# Script PRÓPRIO do espelho: produção não muda (lá o `google` real fica desabilitado sem
# GOOGLE_*, e o preflight do espelho continua exigindo essas variáveis vazias). Chamado pelo
# deploy.sh só com MEUJET_AMBIENTE=espelho. Idempotente. ROLLBACK=1 desfaz e recria o alias
# `google` como o import o deixa (nativo, desabilitado), senão o configure-keycloak-2fa.sh
# de produção passa a falhar a cada deploy ao re-PUTar um alias inexistente.
#
# Contas "Google" NÃO nascem aqui: o semeador as cria pela Admin API do realm sintético,
# com o client de serviço `semeador-contas` (só manage-users/view-users DESSE realm).
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

# --- travas (todas; a env sozinha não basta) ----------------------------------------------
[ "${MEUJET_AMBIENTE:-}" = "espelho" ] || { echo "ERRO: este script é só do espelho (MEUJET_AMBIENTE=espelho)." >&2; exit 1; }
case "${SSO_PUBLIC_URL:-}" in
  *meujet.com.br*|"") echo "ERRO: SSO_PUBLIC_URL='${SSO_PUBLIC_URL:-}' — parece produção (ou vazio)." >&2; exit 1 ;;
esac
[ -z "${GOOGLE_CLIENT_ID:-}" ] || { echo "ERRO: GOOGLE_CLIENT_ID preenchido — o Google REAL não convive com o sintético." >&2; exit 1; }
[ ! -f /etc/systemd/system/meujet-backup.timer ] || { echo "ERRO: esta máquina tem o timer de backup de PRODUÇÃO." >&2; exit 1; }
: "${GOOGLE_SINTETICO_BROKER_SECRET:?defina GOOGLE_SINTETICO_BROKER_SECRET no .env (openssl rand -base64 32)}"
: "${GOOGLE_SINTETICO_SEMEADOR_SECRET:?defina GOOGLE_SINTETICO_SEMEADOR_SECRET no .env (openssl rand -base64 32)}"

KC="${KC_URL:-http://127.0.0.1:8080}"          # host → Keycloak
KC_INTERNO="${KC_INTERNO_URL:-http://keycloak:8080}"   # Keycloak → Keycloak (mesmo nome que o backend usa)
SSO="${SSO_PUBLIC_URL%/}"                         # navegador → Keycloak
REALM_APP="${KC_REALM:-jetski-saas}"
REALM_IDP="google-sintetico"
ALIAS="google"
BROKER="jetski-broker"
SEMEADOR="semeador-contas"

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }
auth=(-H "Authorization: Bearer $TOKEN"); json=(-H "Content-Type: application/json")
api="$KC/admin/realms"
http() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
exigir() { # exigir <codigo-obtido> <esperado...> <mensagem>
  local obtido="$1"; shift; local msg="${*: -1}"
  for e in "${@:1:$(($# - 1))}"; do [ "$obtido" = "$e" ] && return 0; done
  echo "ERRO: $msg (http=$obtido)" >&2; exit 1
}

# --- rollback ------------------------------------------------------------------------------
if [ "${ROLLBACK:-0}" = "1" ]; then
  http -X DELETE "$api/$REALM_APP/identity-provider/instances/$ALIAS" "${auth[@]}" >/dev/null || true
  http -X DELETE "$api/$REALM_IDP" "${auth[@]}" >/dev/null || true
  # recria o alias como o import o deixa: Google nativo, desabilitado
  printf '%s' '{"alias":"google","displayName":"Google","providerId":"google","enabled":false,"trustEmail":true,"storeToken":false,"addReadTokenRoleOnCreate":false,"authenticateByDefault":false,"linkOnly":false,"hideOnLogin":false,"firstBrokerLoginFlowAlias":"first broker login","postBrokerLoginFlowAlias":"post-broker-2fa","config":{"clientId":"nao-configurado.apps.googleusercontent.com","clientSecret":"nao-configurado","syncMode":"IMPORT","useJwksUrl":"true"}}' \
    | curl -s -o /dev/null -w ">> POST idp google (nativo, desabilitado) http=%{http_code}\n" -X POST "$api/$REALM_APP/identity-provider/instances" "${auth[@]}" "${json[@]}" -d @-
  curl -s -o /dev/null -X POST "$api/$REALM_APP/identity-provider/instances/$ALIAS/mappers" "${auth[@]}" "${json[@]}" \
    -d '{"name":"google-role-cliente","identityProviderAlias":"google","identityProviderMapper":"oidc-hardcoded-role-idp-mapper","config":{"syncMode":"INHERIT","role":"CLIENTE"}}'
  echo ">> espelho: Google sintético removido; alias google voltou ao estado do import."
  exit 0
fi

# --- 1. realm do "Google" -------------------------------------------------------------------
# frontendUrl = BASE do servidor (sem /realms/...): sem ele o `iss` varia com o host da
# requisição e o broker recusa o id_token; com o caminho junto o login morre em 404.
# Tema de login PADRÃO do Keycloak, de propósito: a página do "Google" não deve parecer o
# Meu Jet, e o form padrão tem <form id="kc-form-login"> com #username/#password.
if [ "$(http "$api/$REALM_IDP" "${auth[@]}")" != "200" ]; then
  SSO="$SSO" R="$REALM_IDP" python3 -c '
import json, os
print(json.dumps({"realm": os.environ["R"], "enabled": True, "displayName": "Google sintético (espelho)", "sslRequired": "none",
  "registrationAllowed": False, "resetPasswordAllowed": False, "bruteForceProtected": True, "loginTheme": "keycloak",
  "attributes": {"frontendUrl": os.environ["SSO"]}}))' \
    | curl -s -o /dev/null -w ">> POST realm $REALM_IDP http=%{http_code}\n" -X POST "$api" "${auth[@]}" "${json[@]}" -d @-
fi
# PUT mínimo: o RealmRepresentation inteiro de volta responde 500 no KC 26.
SSO="$SSO" R="$REALM_IDP" python3 -c '
import json, os
print(json.dumps({"realm": os.environ["R"], "sslRequired": "none", "loginTheme": "keycloak", "attributes": {"frontendUrl": os.environ["SSO"]}}))' \
  | curl -s -o /dev/null -w ">> PUT realm $REALM_IDP (frontendUrl) http=%{http_code}\n" -X PUT "$api/$REALM_IDP" "${auth[@]}" "${json[@]}" -d @-

upsert_client() { # upsert_client <json>
  local cid uuid
  cid=$(printf '%s' "$1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["clientId"])')
  uuid=$(curl -s "$api/$REALM_IDP/clients?clientId=$cid" "${auth[@]}" | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')
  if [ -z "$uuid" ]; then
    exigir "$(printf '%s' "$1" | http -X POST "$api/$REALM_IDP/clients" "${auth[@]}" "${json[@]}" -d @-)" 201 "POST client $cid"
  else
    exigir "$(printf '%s' "$1" | http -X PUT "$api/$REALM_IDP/clients/$uuid" "${auth[@]}" "${json[@]}" -d @-)" 204 "PUT client $cid"
  fi
  echo ">> client $cid ok"
}

# --- 2. client que o jetski-saas usa como broker -------------------------------------------
upsert_client "$(CID="$BROKER" SEC="$GOOGLE_SINTETICO_BROKER_SECRET" SSO="$SSO" KCI="$KC_INTERNO" RA="$REALM_APP" A="$ALIAS" python3 -c '
import json, os
print(json.dumps({"clientId": os.environ["CID"], "name": "Broker do realm jetski-saas (espelho)", "enabled": True, "protocol": "openid-connect",
  "publicClient": False, "secret": os.environ["SEC"], "standardFlowEnabled": True, "directAccessGrantsEnabled": False, "serviceAccountsEnabled": False,
  "redirectUris": [os.environ["SSO"] + "/realms/" + os.environ["RA"] + "/broker/" + os.environ["A"] + "/endpoint",
                   os.environ["KCI"] + "/realms/" + os.environ["RA"] + "/broker/" + os.environ["A"] + "/endpoint"],
  "webOrigins": ["+"]}))')"

# --- 3. client de serviço do semeador (menor privilégio: só usuários DESTE realm) ------------
upsert_client "$(CID="$SEMEADOR" SEC="$GOOGLE_SINTETICO_SEMEADOR_SECRET" python3 -c '
import json, os
print(json.dumps({"clientId": os.environ["CID"], "name": "Semeador de contas Google sinteticas", "enabled": True, "protocol": "openid-connect",
  "publicClient": False, "secret": os.environ["SEC"], "standardFlowEnabled": False, "directAccessGrantsEnabled": False, "serviceAccountsEnabled": True}))')"
SEM_UUID=$(curl -s "$api/$REALM_IDP/clients?clientId=$SEMEADOR" "${auth[@]}" | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')
SA_ID=$(curl -s "$api/$REALM_IDP/clients/$SEM_UUID/service-account-user" "${auth[@]}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
RM_UUID=$(curl -s "$api/$REALM_IDP/clients?clientId=realm-management" "${auth[@]}" | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')
PAPEIS="[$(curl -s "$api/$REALM_IDP/clients/$RM_UUID/roles/manage-users" "${auth[@]}"),$(curl -s "$api/$REALM_IDP/clients/$RM_UUID/roles/view-users" "${auth[@]}")]"
exigir "$(printf '%s' "$PAPEIS" | http -X POST "$api/$REALM_IDP/users/$SA_ID/role-mappings/clients/$RM_UUID" "${auth[@]}" "${json[@]}" -d @-)" 204 "papéis manage-users/view-users para $SEMEADOR"
echo ">> $SEMEADOR: service account com manage-users/view-users de $REALM_IDP"

# --- 4. o IdP `google` no jetski-saas ------------------------------------------------------
# authorizationUrl é PÚBLICO (quem abre é o navegador); token/userinfo/jwks vão pelo nome
# interno — o Keycloak fala consigo mesmo sem passar pelo túnel.
IDP_JSON=$(A="$ALIAS" SSO="$SSO" KCI="$KC_INTERNO" R="$REALM_IDP" CID="$BROKER" SEC="$GOOGLE_SINTETICO_BROKER_SECRET" python3 -c '
import json, os
pub = os.environ["SSO"] + "/realms/" + os.environ["R"]; interno = os.environ["KCI"] + "/realms/" + os.environ["R"]
print(json.dumps({"alias": os.environ["A"], "displayName": "Google", "providerId": "oidc", "enabled": True, "trustEmail": True,
  "storeToken": False, "addReadTokenRoleOnCreate": False, "authenticateByDefault": False, "linkOnly": False,
  "hideOnLogin": False,  # obrigatório desde o KC 26.7: nulo derruba TODO login do realm com NPE
  "firstBrokerLoginFlowAlias": "first broker login", "postBrokerLoginFlowAlias": "post-broker-2fa",
  "config": {"clientId": os.environ["CID"], "clientSecret": os.environ["SEC"], "clientAuthMethod": "client_secret_post",
    "issuer": pub, "authorizationUrl": pub + "/protocol/openid-connect/auth",
    "tokenUrl": interno + "/protocol/openid-connect/token", "userInfoUrl": interno + "/protocol/openid-connect/userinfo",
    "jwksUrl": interno + "/protocol/openid-connect/certs", "useJwksUrl": "true", "validateSignature": "true",
    "defaultScope": "openid profile email", "syncMode": "IMPORT"}}))')
atual=$(curl -s "$api/$REALM_APP/identity-provider/instances/$ALIAS" "${auth[@]}")
provider=$(printf '%s' "$atual" | python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("providerId",""))
except Exception: print("")')
if [ "$provider" = "oidc" ]; then
  exigir "$(printf '%s' "$IDP_JSON" | http -X PUT "$api/$REALM_APP/identity-provider/instances/$ALIAS" "${auth[@]}" "${json[@]}" -d @-)" 204 "PUT idp $ALIAS"
  echo ">> idp $ALIAS já era oidc — convergido (PUT)"
else
  # Trocar o providerId exige recriar. Só acontece uma vez (estado do import: google nativo).
  [ -z "$provider" ] || http -X DELETE "$api/$REALM_APP/identity-provider/instances/$ALIAS" "${auth[@]}" >/dev/null
  exigir "$(printf '%s' "$IDP_JSON" | http -X POST "$api/$REALM_APP/identity-provider/instances" "${auth[@]}" "${json[@]}" -d @-)" 201 "POST idp $ALIAS"
  echo ">> idp $ALIAS recriado como oidc → $REALM_IDP (antes: '${provider:-ausente}')"
fi
tem_mapper=$(curl -s "$api/$REALM_APP/identity-provider/instances/$ALIAS/mappers" "${auth[@]}" \
  | python3 -c 'import sys,json;print(any(m.get("name")=="google-role-cliente" for m in json.load(sys.stdin)))')
if [ "$tem_mapper" != "True" ]; then
  exigir "$(http -X POST "$api/$REALM_APP/identity-provider/instances/$ALIAS/mappers" "${auth[@]}" "${json[@]}" \
    -d '{"name":"google-role-cliente","identityProviderAlias":"google","identityProviderMapper":"oidc-hardcoded-role-idp-mapper","config":{"syncMode":"INHERIT","role":"CLIENTE"}}')" 201 "mapper google-role-cliente"
fi

# --- 5. pós-condições: o que o login social vai exigir --------------------------------------
curl -s "$api/$REALM_APP/identity-provider/instances/$ALIAS" "${auth[@]}" | python3 -c '
import sys, json
i = json.load(sys.stdin)
falhas = [k for k, v in {"providerId=oidc": i.get("providerId") == "oidc", "enabled": i.get("enabled") is True,
  "hideOnLogin=false": i.get("hideOnLogin") is False, "postBrokerLoginFlowAlias=post-broker-2fa": i.get("postBrokerLoginFlowAlias") == "post-broker-2fa"}.items() if not v]
if falhas: print("ERRO: idp google fora do esperado: " + ", ".join(falhas), file=sys.stderr); sys.exit(1)'
issuer=$(curl -s "$KC/realms/$REALM_IDP/.well-known/openid-configuration" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("issuer",""))')
[ "$issuer" = "$SSO/realms/$REALM_IDP" ] || { echo "ERRO: issuer do $REALM_IDP é '$issuer', esperado '$SSO/realms/$REALM_IDP' (frontendUrl?)" >&2; exit 1; }
echo ">> espelho: Google sintético pronto — issuer $issuer; botão 'Entrar com Google' leva ao realm $REALM_IDP."
