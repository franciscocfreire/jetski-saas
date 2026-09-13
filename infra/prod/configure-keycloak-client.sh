#!/usr/bin/env bash
# Converge (idempotente) os clients públicos do Keycloak de PRODUÇÃO:
#   - jetski-backoffice      (base = PUBLIC_URL)
#   - jetski-customer-portal  (base = PORTAL_PUBLIC_URL, default PUBLIC_URL) — criado se não existir
#   - jetski-platform-console (base = CONSOLE_PUBLIC_URL) — console da plataforma, criado se não existir
# Ambos: público + PKCE S256, redirect URIs / web origins / post-logout convergidos.
# Não remove URLs existentes. O realm.json já nasce assim no import, mas o
# --import-realm NÃO re-importa realms já existentes; este script garante que
# todo deploy (inclusive sobre realm antigo) converja os clients.
#
# Chamado pelo deploy.sh após o Keycloak/realm estarem prontos. Lê tudo do .env.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

converge_client() {
  local CID="$1" BASE="$2" NAME="$3"
  BASE="${BASE%/}"
  echo ">> Keycloak client $CID: base=$BASE (público + PKCE S256)"

  local CLIENT UUID
  CLIENT=$(curl -s "$KC/admin/realms/$REALM/clients?clientId=$CID" -H "Authorization: Bearer $TOKEN")
  UUID=$(echo "$CLIENT" | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')

  if [ -z "$UUID" ]; then
    echo ">> client $CID não existe — criando"
    CID="$CID" NAME="$NAME" BASE="$BASE" python3 -c '
import json, os
cid=os.environ["CID"]; base=os.environ["BASE"]
d={
  "clientId": cid, "name": os.environ["NAME"], "enabled": True,
  "publicClient": True, "protocol": "openid-connect",
  "standardFlowEnabled": True, "implicitFlowEnabled": False,
  "directAccessGrantsEnabled": False, "serviceAccountsEnabled": False,
  "rootUrl": base, "baseUrl": "/",
  "redirectUris": [base+"/*", base+"/api/auth/callback/keycloak"],
  "webOrigins": [base],
  "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": "+",
                 "post.logout.redirect.uris": base+"/*"},
  "protocolMappers": [{
    "name": "roles-mapper", "protocol": "openid-connect",
    "protocolMapper": "oidc-usermodel-realm-role-mapper", "consentRequired": False,
    "config": {"userinfo.token.claim": "true", "id.token.claim": "true",
               "access.token.claim": "true", "claim.name": "roles",
               "jsonType.label": "String", "multivalued": "true"}
  }]
}
json.dump(d, open("/tmp/kc_client.json","w"))
'
    curl -s -o /dev/null -w ">> POST http=%{http_code}\n" -X POST \
      "$KC/admin/realms/$REALM/clients" \
      -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_client.json
    rm -f /tmp/kc_client.json
    return
  fi

  echo "$CLIENT" | BASE="$BASE" python3 -c '
import sys, json, os
base=os.environ["BASE"]
d=json.load(sys.stdin)[0]
ru=set(d.get("redirectUris") or []); ru.update([base+"/*", base+"/api/auth/callback/keycloak"]); d["redirectUris"]=sorted(ru)
wo=set(d.get("webOrigins") or []); wo.update([base]); d["webOrigins"]=sorted(wo)
# rootUrl/baseUrl: destino do "voltar à aplicação" pós required-action (troca de
# senha). Sem convergir, ficava no default do realm (http://localhost:3002).
d["rootUrl"]=base
d["baseUrl"]="/"
d["publicClient"]=True
d["clientAuthenticatorType"]="client-secret"
d.pop("secret", None)
a=d.get("attributes") or {}
a["pkce.code.challenge.method"]="S256"
a["post.logout.redirect.uris"]="+"
parts=set(filter(None,(a.get("post.logout.redirect.uris","") or "").split("##"))); parts.update([base+"/*"]); a["post.logout.redirect.uris"]="##".join(sorted(parts))
d["attributes"]=a
json.dump(d, open("/tmp/kc_client.json","w"))
print(">> publicClient=True, PKCE=S256; redirectUris="+str(d["redirectUris"]))
'
  curl -s -o /dev/null -w ">> PUT http=%{http_code}\n" -X PUT \
    "$KC/admin/realms/$REALM/clients/$UUID" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_client.json
  rm -f /tmp/kc_client.json
}

# Backoffice mora no subdomínio app.* (APP_PUBLIC_URL); fallback = PUBLIC_URL.
# O converge só ADICIONA redirect URIs — os do host www continuam valendo na transição.
converge_client "jetski-backoffice" "${APP_PUBLIC_URL:-$PUBLIC_URL}" "Jetski Backoffice"
converge_client "jetski-customer-portal" "${PORTAL_PUBLIC_URL:-$PUBLIC_URL}" "Meu Jet — Portal do Cliente"
# Console da plataforma (admin.*). Só converge se CONSOLE_PUBLIC_URL estiver no
# .env — em deploy sobre .env antigo, pular é melhor que criar client com URL errada.
if [ -n "${CONSOLE_PUBLIC_URL:-}" ]; then
  converge_client "jetski-platform-console" "$CONSOLE_PUBLIC_URL" "Meu Jet — Console da Plataforma"
else
  echo ">> CONSOLE_PUBLIC_URL ausente no .env — client jetski-platform-console NÃO convergido"
fi

# Client de TESTE (jetski-test: público, ROPC, redirect/webOrigins "*") — vem do
# infra/keycloak-realm.json porque dev e CI (Newman) precisam dele, mas o import
# também o cria num realm NOVO de produção. Em prod ele NUNCA fica habilitado:
# redirect "*" num client público com standard flow é primitiva de roubo de
# authorization code. Idempotente: força enabled=false e zera redirect/webOrigins
# e os fluxos a cada deploy (não apaga — o client some do realm.json só se dev/CI
# deixarem de usá-lo). Backstop na API: JwtAuthorizedPartyValidator rejeita
# azp=jetski-test fora de dev/test.
disable_test_client() {
  local CID="jetski-test" CLIENT UUID
  CLIENT=$(curl -s "$KC/admin/realms/$REALM/clients?clientId=$CID" -H "Authorization: Bearer $TOKEN")
  UUID=$(echo "$CLIENT" | python3 -c 'import sys,json;a=json.load(sys.stdin);print(a[0]["id"] if a else "")')
  if [ -z "$UUID" ]; then
    echo ">> client $CID não existe no realm — nada a desabilitar"
    return
  fi
  echo "$CLIENT" | python3 -c '
import sys, json
d = json.load(sys.stdin)[0]
d["enabled"] = False
d["standardFlowEnabled"] = False
d["directAccessGrantsEnabled"] = False
d["implicitFlowEnabled"] = False
d["redirectUris"] = []
d["webOrigins"] = []
json.dump(d, open("/tmp/kc_test_client.json", "w"))
'
  local HTTP
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
    "$KC/admin/realms/$REALM/clients/$UUID" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_test_client.json)
  rm -f /tmp/kc_test_client.json
  echo ">> client $CID desabilitado (PUT http=$HTTP)"
  # Confere o estado final (não confia só no status do PUT)
  local ENABLED
  ENABLED=$(curl -s "$KC/admin/realms/$REALM/clients/$UUID" -H "Authorization: Bearer $TOKEN" \
    | python3 -c 'import sys,json;print(json.load(sys.stdin).get("enabled"))')
  if [ "$ENABLED" != "False" ]; then
    echo "ERRO: client $CID continua habilitado em produção (enabled=$ENABLED)" >&2
    return 1
  fi
}

# Frontend URL do realm: base pública dos links gerados (e-mails de verificação,
# action-tokens) E do issuer efetivo (sobrepõe o KC_HOSTNAME). Sem isso,
# e-mails disparados por chamadas internas do backend saem com
# http://keycloak:8080. SSO_PUBLIC_URL (host dedicado sso.*) tem precedência.
SSO_BASE="${SSO_PUBLIC_URL:-$PUBLIC_URL}"
echo ">> Realm frontendUrl: ${SSO_BASE%/}"
curl -s "$KC/admin/realms/$REALM" -H "Authorization: Bearer $TOKEN" \
  | BASE="${SSO_BASE%/}" python3 -c '
import sys, json, os
d = json.load(sys.stdin)
a = d.get("attributes") or {}
a["frontendUrl"] = os.environ["BASE"]
d["attributes"] = a
# login por e-mail OU CPF: username editável (vira o CPF quando definido)
d["editUsernameAllowed"] = True
d["loginWithEmailAllowed"] = True
# marca na tela de login hospedada (em vez do nome técnico do realm)
d["displayName"] = "Meu Jet"
d["displayNameHtml"] = "Meu Jet"
# tema de login meujet (Keycloakify — infra/keycloak-theme; JAR na imagem custom)
d["loginTheme"] = "meujet"
json.dump(d, open("/tmp/kc_realm.json","w"))
'
curl -s -o /dev/null -w ">> PUT realm http=%{http_code}\n" -X PUT \
  "$KC/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_realm.json
rm -f /tmp/kc_realm.json

# Por último: se falhar (set -e), os passos acima já convergiram.
disable_test_client
echo ">> clients Keycloak configurados."
