#!/usr/bin/env bash
# Converge (idempotente) o client confidencial jetski-password-check de PRODUÇÃO:
# direct grant on, standard flow off, sem service account — usado pelo backend
# para validar a senha ATUAL na troca de senha do perfil staff.
#
# Secret vem de KEYCLOAK_PASSWORD_CHECK_CLIENT_SECRET no .env; sem ele o script
# avisa e sai (o backend fica fail-closed: troca de senha indisponível).
#
# Chamado pelo deploy.sh após o Keycloak/realm estarem prontos. Lê tudo do .env.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"
SECRET="${KEYCLOAK_PASSWORD_CHECK_CLIENT_SECRET:-}"

if [ -z "$SECRET" ]; then
  echo ">> KEYCLOAK_PASSWORD_CHECK_CLIENT_SECRET ausente no .env — pulando client jetski-password-check (troca de senha ficará indisponível)"
  exit 0
fi

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

CID="jetski-password-check"
echo ">> Keycloak client $CID (confidencial, direct grant only)"

CLIENT=$(curl -s "$KC/admin/realms/$REALM/clients?clientId=$CID" -H "Authorization: Bearer $TOKEN")
UUID=$(echo "$CLIENT" | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')

SECRET="$SECRET" python3 -c '
import json, os
d = {
  "clientId": "jetski-password-check",
  "name": "Validação de senha (perfil self-service)",
  "description": "Direct grant dedicado à validação da senha atual na troca de senha do perfil staff",
  "enabled": True,
  "publicClient": False,
  "clientAuthenticatorType": "client-secret",
  "secret": os.environ["SECRET"],
  "protocol": "openid-connect",
  "standardFlowEnabled": False,
  "implicitFlowEnabled": False,
  "directAccessGrantsEnabled": True,
  "serviceAccountsEnabled": False,
  "redirectUris": [],
  "webOrigins": []
}
json.dump(d, open("/tmp/kc_pwcheck.json","w"))
'

if [ -z "$UUID" ]; then
  curl -s -o /dev/null -w ">> POST http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_pwcheck.json
else
  curl -s -o /dev/null -w ">> PUT http=%{http_code}\n" -X PUT \
    "$KC/admin/realms/$REALM/clients/$UUID" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_pwcheck.json
fi
rm -f /tmp/kc_pwcheck.json
echo ">> client jetski-password-check configurado."
