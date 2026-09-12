#!/usr/bin/env bash
# =============================================================================
# IdP OIDC de TESTE (só dev/CI) — substitui o Google nos testes automatizados.
#
# POR QUE EXISTE: o caminho de identity brokering (first broker login,
# post-broker 2FA, dispositivo confiável, logout federado) não tinha cobertura
# automatizada, porque o único provedor era o Google — que bloqueia navegador
# automatizado, desafia IP de datacenter e muda a própria tela de login sem
# aviso. Um teste assim falha sem regressão nossa e não distingue "nosso
# brokering quebrou" de "o Google desconfiou hoje".
#
# O QUE FAZ: usa o PRÓPRIO Keycloak como provedor externo — cria um realm
# `idp-teste` com um usuário fixo e o registra no realm da aplicação como um
# IdP OIDC comum. Para o realm jetski-saas, é um provedor externo qualquer:
# exercita exatamente os mesmos flows do Google, de forma determinística.
#
# O QUE NÃO COBRE (segue sendo verificação manual): peculiaridades do Google —
# formato do id_token, claims como email_verified, tela de consentimento e o
# kc_idp_hint do botão "Entrar com Google".
#
# Uso:
#   bash infra/keycloak-setup/add-idp-teste-dev.sh
#   ROLLBACK=1 bash infra/keycloak-setup/add-idp-teste-dev.sh   # remove tudo
# =============================================================================
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-Mazuca@123}"
REALM_APP="${KC_REALM:-jetski-saas}"
REALM_IDP="idp-teste"
ALIAS="idp-teste"

# URL pública do SSO: o browser precisa alcançar o authorize do realm de teste.
# Em dev o Keycloak vive em sso.*; sem túnel, cai no próprio localhost.
SSO_PUBLIC="${SSO_PUBLIC_URL:-https://sso.pegaojet.com.br}"
SSO_PUBLIC="${SSO_PUBLIC%/}"

# Credenciais do usuário de teste (dev — nada aqui é segredo de verdade).
IDP_USER="${IDP_TESTE_USER:-e2e@idp-teste.local}"
IDP_PASS="${IDP_TESTE_PASSWORD:-e2e123}"
IDP_CLIENT="jetski-broker"
IDP_SECRET="${IDP_TESTE_SECRET:-dev-idp-teste-secret}"

# --- TRAVA: isto NUNCA pode rodar contra produção -----------------------------
# O alvo precisa ser um Keycloak local. Um IdP de teste num realm de produção
# seria uma porta de entrada alternativa com senha conhecida em repositório.
case "$KEYCLOAK_URL" in
  http://localhost:*|http://127.0.0.1:*|http://keycloak:*) ;;
  *)
    echo "RECUSADO: este script é só para dev (KEYCLOAK_URL=$KEYCLOAK_URL)." >&2
    echo "Um IdP de teste com senha conhecida não pode existir em produção." >&2
    exit 1
    ;;
esac

TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=$ADMIN_USER" --data-urlencode "password=$ADMIN_PASS" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

auth=(-H "Authorization: Bearer $TOKEN")
json=(-H "Content-Type: application/json")
api="$KEYCLOAK_URL/admin/realms"

# --- rollback ----------------------------------------------------------------
if [ "${ROLLBACK:-0}" = "1" ]; then
  curl -s -o /dev/null -w ">> DELETE idp $ALIAS http=%{http_code}\n" \
    -X DELETE "$api/$REALM_APP/identity-provider/instances/$ALIAS" "${auth[@]}"
  curl -s -o /dev/null -w ">> DELETE realm $REALM_IDP http=%{http_code}\n" \
    -X DELETE "$api/$REALM_IDP" "${auth[@]}"
  echo ">> IdP de teste removido."
  exit 0
fi

# --- 1. realm do provedor externo --------------------------------------------
# frontendUrl fixo: sem ele o `iss` do token varia conforme o host da requisição
# (browser via sso.*, servidor via localhost) e o broker recusa o id_token.
#
# ATENÇÃO: o valor é a BASE do servidor, SEM /realms/<nome>. Com o caminho do
# realm junto, o Keycloak monta .../realms/idp-teste/realms/idp-teste/... e a
# submissão do login morre num "We are sorry... Page not found".
existe=$(curl -s -o /dev/null -w '%{http_code}' "$api/$REALM_IDP" "${auth[@]}")
if [ "$existe" != "200" ]; then
  echo ">> criando realm $REALM_IDP..."
  SSO="$SSO_PUBLIC" R="$REALM_IDP" python3 -c '
import json, os
print(json.dumps({
    "realm": os.environ["R"],
    "enabled": True,
    "displayName": "IdP de teste (dev)",
    "sslRequired": "none",
    "attributes": {"frontendUrl": os.environ["SSO"]},
}))' | curl -s -o /dev/null -w ">> POST realm $REALM_IDP http=%{http_code}\n" \
    -X POST "$api" "${auth[@]}" "${json[@]}" -d @-
else
  echo ">> realm $REALM_IDP já existe — convergindo frontendUrl"
fi

# Converge o frontendUrl sempre: um realm criado por uma versão anterior deste
# script pode ter ficado com o valor errado.
#
# PUT MÍNIMO de propósito: devolver ao Keycloak 26 o RealmRepresentation inteiro
# que veio do GET responde 500. Só os campos a mudar.
SSO="$SSO_PUBLIC" R="$REALM_IDP" python3 -c '
import json, os
print(json.dumps({
    "realm": os.environ["R"],
    "sslRequired": "none",
    "attributes": {"frontendUrl": os.environ["SSO"]},
}))' | curl -s -o /dev/null -w ">> PUT realm $REALM_IDP (frontendUrl) http=%{http_code}\n" \
    -X PUT "$api/$REALM_IDP" "${auth[@]}" "${json[@]}" -d @-

# --- 2. client que o realm da aplicação usa como broker ----------------------
CLIENT_UUID=$(curl -s "$api/$REALM_IDP/clients?clientId=$IDP_CLIENT" "${auth[@]}" \
  | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')
CLIENT_JSON=$(CID="$IDP_CLIENT" SEC="$IDP_SECRET" SSO="$SSO_PUBLIC" KCL="$KEYCLOAK_URL" RA="$REALM_APP" python3 -c '
import json, os
alias = "idp-teste"
redirects = [
    os.environ["SSO"] + "/realms/" + os.environ["RA"] + "/broker/" + alias + "/endpoint",
    os.environ["KCL"] + "/realms/" + os.environ["RA"] + "/broker/" + alias + "/endpoint",
]
print(json.dumps({
    "clientId": os.environ["CID"],
    "name": "Broker do realm da aplicacao",
    "enabled": True,
    "protocol": "openid-connect",
    "publicClient": False,
    "secret": os.environ["SEC"],
    "standardFlowEnabled": True,
    "directAccessGrantsEnabled": True,
    "redirectUris": redirects,
    "webOrigins": ["+"],
}))')
if [ -z "$CLIENT_UUID" ]; then
  printf '%s' "$CLIENT_JSON" | curl -s -o /dev/null -w ">> POST client $IDP_CLIENT http=%{http_code}\n" \
    -X POST "$api/$REALM_IDP/clients" "${auth[@]}" "${json[@]}" -d @-
else
  printf '%s' "$CLIENT_JSON" | curl -s -o /dev/null -w ">> PUT client $IDP_CLIENT http=%{http_code}\n" \
    -X PUT "$api/$REALM_IDP/clients/$CLIENT_UUID" "${auth[@]}" "${json[@]}" -d @-
fi

# --- 3. usuário de teste ------------------------------------------------------
USER_ID=$(curl -s "$api/$REALM_IDP/users?email=$IDP_USER&exact=true" "${auth[@]}" \
  | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')
if [ -z "$USER_ID" ]; then
  U="$IDP_USER" P="$IDP_PASS" python3 -c '
import json, os
print(json.dumps({
    "username": os.environ["U"],
    "email": os.environ["U"],
    "emailVerified": True,
    "enabled": True,
    "firstName": "Teste",
    "lastName": "Broker",
    "credentials": [{"type": "password", "value": os.environ["P"], "temporary": False}],
}))' | curl -s -o /dev/null -w ">> POST usuario de teste http=%{http_code}\n" \
    -X POST "$api/$REALM_IDP/users" "${auth[@]}" "${json[@]}" -d @-
else
  echo ">> usuário de teste já existe"
fi

# --- 4. o IdP no realm da aplicação ------------------------------------------
# authorizationUrl é PÚBLICO (quem abre é o browser); token/userinfo/jwks vão
# pelo caminho interno — o Keycloak fala consigo mesmo e não depende do túnel.
IDP_JSON=$(A="$ALIAS" SSO="$SSO_PUBLIC" KCL="$KEYCLOAK_URL" R="$REALM_IDP" CID="$IDP_CLIENT" SEC="$IDP_SECRET" python3 -c '
import json, os
pub = os.environ["SSO"] + "/realms/" + os.environ["R"]
int_ = os.environ["KCL"] + "/realms/" + os.environ["R"]
print(json.dumps({
    "alias": os.environ["A"],
    "displayName": "Entrar com IdP de teste",
    "providerId": "oidc",
    "enabled": True,
    "trustEmail": True,
    "storeToken": False,
    "addReadTokenRoleOnCreate": False,
    "authenticateByDefault": False,
    "linkOnly": False,
    # obrigatório desde o KC 26.7: nulo aqui derruba TODO login do realm com NPE
    "hideOnLogin": False,
    "firstBrokerLoginFlowAlias": "first broker login",
    "postBrokerLoginFlowAlias": "post-broker-2fa",
    "config": {
        "clientId": os.environ["CID"],
        "clientSecret": os.environ["SEC"],
        "clientAuthMethod": "client_secret_post",
        "issuer": pub,
        "authorizationUrl": pub + "/protocol/openid-connect/auth",
        "tokenUrl": int_ + "/protocol/openid-connect/token",
        "userInfoUrl": int_ + "/protocol/openid-connect/userinfo",
        "jwksUrl": int_ + "/protocol/openid-connect/certs",
        "useJwksUrl": "true",
        "validateSignature": "true",
        "defaultScope": "openid profile email",
        "syncMode": "IMPORT",
    },
}))')
code=$(curl -s -o /dev/null -w '%{http_code}' "$api/$REALM_APP/identity-provider/instances/$ALIAS" "${auth[@]}")
if [ "$code" = "200" ]; then
  printf '%s' "$IDP_JSON" | curl -s -o /dev/null -w ">> PUT idp $ALIAS http=%{http_code}\n" \
    -X PUT "$api/$REALM_APP/identity-provider/instances/$ALIAS" "${auth[@]}" "${json[@]}" -d @-
else
  printf '%s' "$IDP_JSON" | curl -s -o /dev/null -w ">> POST idp $ALIAS http=%{http_code}\n" \
    -X POST "$api/$REALM_APP/identity-provider/instances" "${auth[@]}" "${json[@]}" -d @-
fi

echo ">> IdP de teste pronto: alias=$ALIAS, usuário=$IDP_USER (senha: $IDP_PASS)"
echo ">> e2e: kc_idp_hint=$ALIAS leva direto ao provedor, sem passar pela tela do Keycloak."
