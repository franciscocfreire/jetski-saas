#!/usr/bin/env bash
# Configura (idempotente) o client jetski-backoffice no Keycloak de PRODUÇÃO,
# igual ao que o reset-ambiente-dev.sh faz no dev:
#   - confidencial (publicClient=false, clientAuthenticatorType=client-secret)
#   - secret = KEYCLOAK_BACKOFFICE_SECRET (.env)
#   - PKCE S256
#   - redirect URIs / web origins / post-logout do PUBLIC_URL
# Não remove URLs existentes. Sem isso, um deploy do zero deixa o client público
# e o login (NextAuth confidencial) quebra com invalid_client.
#
# Chamado pelo deploy.sh após o Keycloak/realm estarem prontos. Lê tudo do .env.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"
CID="jetski-backoffice"
BASE="${PUBLIC_URL%/}"
SECRET="${KEYCLOAK_BACKOFFICE_SECRET:-backoffice-secret}"

echo ">> Keycloak client config: base=$BASE secret=${SECRET:0:4}****"

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

CLIENT=$(curl -s "$KC/admin/realms/$REALM/clients?clientId=$CID" -H "Authorization: Bearer $TOKEN")
UUID=$(echo "$CLIENT" | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')
[ -n "$UUID" ] || { echo "ERRO: client $CID não encontrado no realm $REALM" >&2; exit 1; }

echo "$CLIENT" | BASE="$BASE" SECRET="$SECRET" python3 -c '
import sys, json, os
base=os.environ["BASE"]; secret=os.environ["SECRET"]
d=json.load(sys.stdin)[0]
ru=set(d.get("redirectUris") or []); ru.update([base+"/*", base+"/api/auth/callback/keycloak"]); d["redirectUris"]=sorted(ru)
wo=set(d.get("webOrigins") or []); wo.update([base]); d["webOrigins"]=sorted(wo)
d["publicClient"]=False
d["clientAuthenticatorType"]="client-secret"
d["secret"]=secret
a=d.get("attributes") or {}
a["pkce.code.challenge.method"]="S256"
parts=set(filter(None,(a.get("post.logout.redirect.uris","") or "").split("##"))); parts.update([base+"/*"]); a["post.logout.redirect.uris"]="##".join(sorted(parts))
d["attributes"]=a
json.dump(d, open("/tmp/kc_client.json","w"))
print(">> publicClient=False, client-secret, PKCE=S256; redirectUris="+str(d["redirectUris"]))
'

curl -s -o /dev/null -w ">> PUT http=%{http_code}\n" -X PUT \
  "$KC/admin/realms/$REALM/clients/$UUID" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_client.json
rm -f /tmp/kc_client.json
echo ">> client Keycloak configurado."
