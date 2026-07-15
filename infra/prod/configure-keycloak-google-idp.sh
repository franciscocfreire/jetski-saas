#!/usr/bin/env bash
# Configura (idempotente) o Identity Provider "google" no realm jetski-saas +
# mapper hardcoded role CLIENTE — habilita o "Entrar com Google" (portal do
# cliente e backoffice; a tela de login do Keycloak é por realm).
#
# Necessário porque o --import-realm NÃO re-importa um realm já existente; este
# passo é a única forma de adicionar o IdP num realm de produção sem zerá-lo.
#
# Chamado pelo deploy.sh após o Keycloak/realm estarem prontos.
# Lê GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET do .env; sem elas, pula com aviso.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"

if [ -z "${GOOGLE_CLIENT_ID:-}" ] || [ -z "${GOOGLE_CLIENT_SECRET:-}" ]; then
  echo ">> GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET ausentes no .env — pulei o IdP Google (login Google desabilitado)."
  exit 0
fi

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

IDP_JSON=$(GID="$GOOGLE_CLIENT_ID" GSEC="$GOOGLE_CLIENT_SECRET" python3 -c '
import json, os
print(json.dumps({
    "alias": "google",
    "displayName": "Google",
    "providerId": "google",
    "enabled": True,
    "trustEmail": True,
    "storeToken": False,
    "addReadTokenRoleOnCreate": False,
    "authenticateByDefault": False,
    "linkOnly": False,
    "firstBrokerLoginFlowAlias": "first broker login",
    "config": {
        "clientId": os.environ["GID"],
        "clientSecret": os.environ["GSEC"],
        "syncMode": "IMPORT",
        "useJwksUrl": "true",
    },
}))')

# Cria OU atualiza o IdP (GET → 200=PUT, 404=POST)
code=$(curl -s -o /dev/null -w '%{http_code}' \
  "$KC/admin/realms/$REALM/identity-provider/instances/google" \
  -H "Authorization: Bearer $TOKEN")
if [ "$code" = "200" ]; then
  curl -s -o /dev/null -w ">> PUT idp google http=%{http_code}\n" -X PUT \
    "$KC/admin/realms/$REALM/identity-provider/instances/google" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$IDP_JSON"
else
  curl -s -o /dev/null -w ">> POST idp google http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/identity-provider/instances" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$IDP_JSON"
fi

# Mapper hardcoded role CLIENTE — só na criação de usuário novo via Google
# (syncMode IMPORT do IdP); staff que linka Google NÃO ganha CLIENTE.
# Mappers não têm upsert por nome: cria apenas se ausente.
tem_mapper=$(curl -s "$KC/admin/realms/$REALM/identity-provider/instances/google/mappers" \
  -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;ms=json.load(sys.stdin);print(any(m.get("name")=="google-role-cliente" for m in ms))')
if [ "$tem_mapper" != "True" ]; then
  curl -s -o /dev/null -w ">> POST mapper CLIENTE http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/identity-provider/instances/google/mappers" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d '{"name":"google-role-cliente","identityProviderAlias":"google","identityProviderMapper":"oidc-hardcoded-role-idp-mapper","config":{"syncMode":"INHERIT","role":"CLIENTE"}}'
fi
echo ">> IdP Google configurado (Entrar com Google habilitado)."
