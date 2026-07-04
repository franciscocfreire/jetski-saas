#!/usr/bin/env bash
# Converge (idempotente) o client OIDC do GRAFANA no Keycloak:
#   - roles de realm grafana_admin / grafana_viewer (acesso à observabilidade)
#   - client confidencial "grafana" com secret (gerado e gravado no .env na
#     primeira execução) e redirect da URL pública
#   - atribui grafana_admin aos e-mails de GRAFANA_ADMIN_EMAILS (vírgula)
#
# SEGURANÇA: o realm é compartilhado com os CLIENTES do portal — o acesso ao
# Grafana é negado a quem não tiver uma das roles (role_attribute_strict no
# Grafana). Este script nunca dá role a ninguém que não esteja na lista.
#
# Chamado pelo deploy.sh após o realm estar pronto. Lê tudo do .env.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"
BASE="${GRAFANA_PUBLIC_URL:-${PUBLIC_URL}}"; BASE="${BASE%/}"

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

# 1) Secret do client: gera e persiste no .env na primeira vez
if [ -z "${GRAFANA_OIDC_CLIENT_SECRET:-}" ]; then
  GRAFANA_OIDC_CLIENT_SECRET="$(openssl rand -hex 24)"
  printf '\n# Secret do client OIDC do Grafana (gerado pelo deploy)\nGRAFANA_OIDC_CLIENT_SECRET=%s\n' \
    "$GRAFANA_OIDC_CLIENT_SECRET" >> .env
  echo ">> GRAFANA_OIDC_CLIENT_SECRET gerado e gravado no .env (recrie o grafana p/ aplicar)"
fi

# 2) Roles de realm (POST é idempotente na prática: 409 se já existe)
for R in grafana_admin grafana_viewer; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$KC/admin/realms/$REALM/roles" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"name\":\"$R\",\"description\":\"Acesso ao Grafana (observabilidade)\"}")
  echo ">> role $R: http=$code (201=criada, 409=já existia)"
done

# 3) Client confidencial "grafana"
CLIENT=$(curl -s "$KC/admin/realms/$REALM/clients?clientId=grafana" -H "Authorization: Bearer $TOKEN")
UUID=$(echo "$CLIENT" | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')

BASE="$BASE" SECRET="$GRAFANA_OIDC_CLIENT_SECRET" python3 -c '
import json, os
base=os.environ["BASE"]
d={
  "clientId": "grafana", "name": "Grafana (observabilidade)", "enabled": True,
  "protocol": "openid-connect", "publicClient": False,
  "secret": os.environ["SECRET"],
  "standardFlowEnabled": True, "implicitFlowEnabled": False,
  "directAccessGrantsEnabled": False, "serviceAccountsEnabled": False,
  "redirectUris": [base+"/grafana/login/generic_oauth"],
  "webOrigins": [],
  "attributes": {"post.logout.redirect.uris": "+"},
  "protocolMappers": [{
    "name": "roles-mapper", "protocol": "openid-connect",
    "protocolMapper": "oidc-usermodel-realm-role-mapper", "consentRequired": False,
    "config": {"userinfo.token.claim": "true", "id.token.claim": "true",
               "access.token.claim": "true", "claim.name": "roles",
               "jsonType.label": "String", "multivalued": "true"}
  }]
}
json.dump(d, open("/tmp/kc_grafana.json","w"))
'
if [ -z "$UUID" ]; then
  curl -s -o /dev/null -w ">> POST client grafana http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_grafana.json
else
  # preserva o id e converge secret/redirects (mappers já existentes ficam)
  python3 -c "
import json
d=json.load(open('/tmp/kc_grafana.json')); d['id']='$UUID'; d.pop('protocolMappers')
json.dump(d, open('/tmp/kc_grafana.json','w'))"
  curl -s -o /dev/null -w ">> PUT client grafana http=%{http_code}\n" -X PUT \
    "$KC/admin/realms/$REALM/clients/$UUID" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_grafana.json
fi
rm -f /tmp/kc_grafana.json

# 4) grafana_admin para os e-mails autorizados
ROLE_JSON=$(curl -s "$KC/admin/realms/$REALM/roles/grafana_admin" -H "Authorization: Bearer $TOKEN")
IFS=',' read -ra EMAILS <<< "${GRAFANA_ADMIN_EMAILS:-}"
if [ ${#EMAILS[@]} -eq 0 ]; then
  echo ">> GRAFANA_ADMIN_EMAILS vazio — ninguém recebeu grafana_admin (defina no .env)"
fi
for EMAIL in "${EMAILS[@]}"; do
  EMAIL=$(echo "$EMAIL" | xargs); [ -z "$EMAIL" ] && continue
  UID_USER=$(curl -s "$KC/admin/realms/$REALM/users?email=$EMAIL&exact=true" \
    -H "Authorization: Bearer $TOKEN" \
    | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')
  if [ -z "$UID_USER" ]; then
    echo ">> AVISO: usuário $EMAIL não encontrado no realm — pulei"
    continue
  fi
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    "$KC/admin/realms/$REALM/users/$UID_USER/role-mappings/realm" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "[$ROLE_JSON]")
  echo ">> grafana_admin → $EMAIL: http=$code"
done
echo ">> Grafana OIDC configurado (client + roles)."
