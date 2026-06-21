#!/usr/bin/env bash
# Converge (idempotente) o client jetski-backoffice no Keycloak de PRODUÇÃO para:
#   - público + PKCE S256 (NextAuth não usa secret; mesmo modelo do dev)
#   - redirect URIs / web origins / post-logout do PUBLIC_URL
# Não remove URLs existentes. O realm.json já nasce assim no import, mas o
# --import-realm NÃO re-importa realms já existentes; este script garante que
# todo deploy (inclusive sobre realm antigo) converja o client ao estado correto.
#
# Chamado pelo deploy.sh após o Keycloak/realm estarem prontos. Lê tudo do .env.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"
CID="jetski-backoffice"
BASE="${PUBLIC_URL%/}"

echo ">> Keycloak client config: base=$BASE (público + PKCE S256)"

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

CLIENT=$(curl -s "$KC/admin/realms/$REALM/clients?clientId=$CID" -H "Authorization: Bearer $TOKEN")
UUID=$(echo "$CLIENT" | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')
[ -n "$UUID" ] || { echo "ERRO: client $CID não encontrado no realm $REALM" >&2; exit 1; }

echo "$CLIENT" | BASE="$BASE" python3 -c '
import sys, json, os
base=os.environ["BASE"]
d=json.load(sys.stdin)[0]
ru=set(d.get("redirectUris") or []); ru.update([base+"/*", base+"/api/auth/callback/keycloak"]); d["redirectUris"]=sorted(ru)
wo=set(d.get("webOrigins") or []); wo.update([base]); d["webOrigins"]=sorted(wo)
d["publicClient"]=True
d["clientAuthenticatorType"]="client-secret"
d.pop("secret", None)
a=d.get("attributes") or {}
a["pkce.code.challenge.method"]="S256"
parts=set(filter(None,(a.get("post.logout.redirect.uris","") or "").split("##"))); parts.update([base+"/*"]); a["post.logout.redirect.uris"]="##".join(sorted(parts))
d["attributes"]=a
json.dump(d, open("/tmp/kc_client.json","w"))
print(">> publicClient=True, PKCE=S256; redirectUris="+str(d["redirectUris"]))
'

curl -s -o /dev/null -w ">> PUT http=%{http_code}\n" -X PUT \
  "$KC/admin/realms/$REALM/clients/$UUID" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_client.json
rm -f /tmp/kc_client.json
echo ">> client Keycloak configurado."
