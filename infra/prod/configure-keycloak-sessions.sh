#!/usr/bin/env bash
# Configura (idempotente) os tempos de sessão SSO do realm jetski-saas:
#   ssoSessionIdleTimeout  = 43200 (12h) — pausa (almoço, notebook dormindo) não derruba
#   ssoSessionMaxLifespan  = 50400 (14h) — teto duro: sessão nunca atravessa a noite
# accessTokenLifespan (5min) NÃO é alterado — é ele que limita token vazado.
#
# Decisão de jul/2026 (fricção no balcão × exposição de dispositivo desatendido):
# um login por dia cobre o expediente inteiro; o teto de 14h derruba overnight.
#
# Necessário porque o --import-realm NÃO re-importa um realm já existente; este
# passo é a única forma de ajustar os tempos num realm de produção sem zerá-lo.
# Chamado pelo deploy.sh após o Keycloak/realm estarem prontos.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"

SSO_IDLE="${KC_SSO_IDLE:-43200}"
SSO_MAX="${KC_SSO_MAX:-50400}"

echo ">> Keycloak sessões do realm: idle=${SSO_IDLE}s max=${SSO_MAX}s"

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

# GET realm → ajusta tempos → PUT (atualização do realm; não toca clients/users/roles)
curl -s "$KC/admin/realms/$REALM" -H "Authorization: Bearer $TOKEN" \
  | SSO_IDLE="$SSO_IDLE" SSO_MAX="$SSO_MAX" python3 -c '
import sys, json, os
r = json.load(sys.stdin)
r["ssoSessionIdleTimeout"] = int(os.environ["SSO_IDLE"])
r["ssoSessionMaxLifespan"] = int(os.environ["SSO_MAX"])
json.dump(r, open("/tmp/kc_realm_sessions.json", "w"))
print(">> tempos de sessão montados no payload do realm")
'

curl -s -o /dev/null -w ">> PUT realm http=%{http_code}\n" -X PUT \
  "$KC/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @/tmp/kc_realm_sessions.json
rm -f /tmp/kc_realm_sessions.json
echo ">> Sessões SSO do realm configuradas (afeta sessões NOVAS; as existentes mantêm os tempos antigos)."
