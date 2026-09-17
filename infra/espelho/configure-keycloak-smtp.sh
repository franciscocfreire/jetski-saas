#!/usr/bin/env bash
# =============================================================================
# SMTP do realm Keycloak no ESPELHO → Mailpit (fase E1 do ECOSSISTEMA_SINTETICO_SPEC.md).
#
# Habilita os e-mails enviados PELO Keycloak — verificação de e-mail do cadastro do
# cliente, código de login do portal (SPI meujet-email-code), reset de senha, OTP de
# step-up — que no espelho não chegavam: o script de produção
# (infra/prod/configure-keycloak-smtp.sh) tem AUTH e STARTTLS fixos e pula quando não há
# credencial. Aqui o destino é o Mailpit da própria VM: sem AUTH, sem TLS, e nada sai.
#
# Script PRÓPRIO do espelho, de propósito: o de produção não muda por causa do espelho.
# Chamado pelo deploy.sh só com MEUJET_AMBIENTE=espelho. Idempotente.
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

[ "${MEUJET_AMBIENTE:-}" = "espelho" ] || { echo "ERRO: este script é só do espelho (MEUJET_AMBIENTE=espelho)." >&2; exit 1; }

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"
FROM="${PLATFORM_SMTP_FROM:-nao-responda@exemplo.invalid}"

TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

TMP=$(mktemp); trap 'rm -f "$TMP"' EXIT
# GET realm → troca só o smtpServer → PUT (não toca clients/users/roles/flows)
curl -s "$KC/admin/realms/$REALM" -H "Authorization: Bearer $TOKEN" \
  | GFROM="$FROM" python3 -c '
import sys, json, os
r = json.load(sys.stdin)
r["smtpServer"] = {
    "host": "mailpit",          # nome do serviço na rede do compose
    "port": "1025",
    "from": os.environ["GFROM"],
    "fromDisplayName": "Meu Jet (espelho)",
    "auth": "false",
    "starttls": "false",
    "ssl": "false",
}
json.dump(r, sys.stdout)
' > "$TMP"

http=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$KC/admin/realms/$REALM" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d @"$TMP")
[ "$http" = "204" ] || { echo "ERRO: PUT do realm devolveu HTTP $http" >&2; exit 1; }
echo ">> espelho: SMTP do Keycloak → mailpit:1025 (from=$FROM)"
