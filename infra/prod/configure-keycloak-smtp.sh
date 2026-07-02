#!/usr/bin/env bash
# Configura (idempotente) o SMTP do realm Keycloak (smtpServer) a partir do .env (Gmail),
# habilitando os emails enviados PELO Keycloak: redefinição de senha ("Esqueci minha senha"),
# verificação de email, etc. (separado do SMTP do backend).
#
# Necessário porque o --import-realm NÃO re-importa um realm já existente; este passo é a
# única forma de ajustar o smtpServer num realm de produção sem zerá-lo.
#
# Chamado pelo deploy.sh após o Keycloak/realm estarem prontos. Lê GMAIL_* do .env.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"

if [ -z "${GMAIL_USER:-}" ] || [ -z "${GMAIL_APP_PASSWORD:-}" ]; then
  echo ">> GMAIL_USER/GMAIL_APP_PASSWORD vazios — pulei a config de SMTP do Keycloak."
  exit 0
fi

echo ">> Keycloak SMTP do realm: host=smtp.gmail.com from=$GMAIL_USER"

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

# GET realm → adiciona smtpServer → PUT (atualização do realm; não toca clients/users/roles)
curl -s "$KC/admin/realms/$REALM" -H "Authorization: Bearer $TOKEN" \
  | GUSER="$GMAIL_USER" GPASS="$GMAIL_APP_PASSWORD" python3 -c '
import sys, json, os
r = json.load(sys.stdin)
user = os.environ["GUSER"]; pwd = os.environ["GPASS"]
r["smtpServer"] = {
    "host": "smtp.gmail.com",
    "port": "587",
    "from": user,
    "fromDisplayName": "Meu Jet",
    "auth": "true",
    "user": user,
    "password": pwd,
    "starttls": "true",
    "ssl": "false",
}
json.dump(r, open("/tmp/kc_realm.json", "w"))
print(">> smtpServer montado (Gmail STARTTLS 587)")
'

curl -s -o /dev/null -w ">> PUT realm http=%{http_code}\n" -X PUT \
  "$KC/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_realm.json
rm -f /tmp/kc_realm.json
echo ">> SMTP do Keycloak configurado (reset de senha por email habilitado)."
