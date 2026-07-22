#!/usr/bin/env bash
# Configura (idempotente) o login sem senha por código de e-mail do portal do
# cliente: flow "portal-browser" (SPI meujet-email-code + senha como
# alternativas) + override de browser flow no client jetski-customer-portal +
# emailTheme "meujet-email" no realm.
#
# Necessário porque o --import-realm NÃO re-importa um realm já existente; num
# realm NOVO o infra/keycloak-realm.json já traz tudo (mudou lá, mude aqui).
# Também serve para dev com volume existente: rodar após ./rebuild.sh keycloak.
#
# ROLLBACK=1 bash infra/prod/configure-keycloak-email-code.sh
#   → remove só o override do client (kill switch: portal volta instantâneo ao
#     flow de senha; flow e SPI ficam dormentes, zero restart).
#
# Chamado pelo deploy.sh após o Keycloak/realm estarem prontos.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."   # raiz do repo
set -a; . ./.env; set +a

KC="${KC_URL:-http://127.0.0.1:8080}"
REALM="${KC_REALM:-jetski-saas}"
FLOW_ALIAS="portal-browser"
FORMS_ALIAS="portal-browser-forms"
# Portal do cliente E backoffice usam o mesmo flow (tela neutra; a autorização
# de população — CLIENTE × Membro — é da aplicação, não do login)
CLIENTS="jetski-customer-portal jetski-backoffice"
PROVIDER="meujet-email-code"

# Em prod a senha vem do .env; em dev ela é fixa no docker-compose.yml (o .env
# dev não a define) — fallback para o valor do compose dev.
TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password \
  -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=${KEYCLOAK_ADMIN_PASSWORD:-Mazuca@123}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "ERRO: sem token admin do Keycloak" >&2; exit 1; }

auth=(-H "Authorization: Bearer $TOKEN")
json=(-H "Content-Type: application/json")

# helper: PUT parcial do client preservando a representação atual
atualizar_override_client() { # $1 = clientId; $2 = json do campo authenticationFlowBindingOverrides
  local client_id="$1" overrides="$2" client_uuid
  client_uuid=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/clients?clientId=$client_id" \
    | python3 -c 'import sys,json;cs=json.load(sys.stdin);print(cs[0]["id"] if cs else "")')
  [ -n "$client_uuid" ] || { echo "ERRO: client $client_id não encontrado" >&2; return 1; }
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/clients/$client_uuid" \
    | OVERRIDES="$overrides" python3 -c '
import sys, json, os
rep = json.load(sys.stdin)
rep["authenticationFlowBindingOverrides"] = json.loads(os.environ["OVERRIDES"])
print(json.dumps(rep))' \
    | curl -s -o /dev/null -w ">> PUT client $client_id overrides http=%{http_code}\n" \
        -X PUT "$KC/admin/realms/$REALM/clients/$client_uuid" "${auth[@]}" "${json[@]}" -d @-
}

# --- rollback: só desbinda os overrides e sai --------------------------------
# GOTCHA Keycloak: map vazio {} é NO-OP no update (só processa chaves
# presentes); remover binding exige valor vazio {"browser": ""}.
if [ "${ROLLBACK:-0}" = "1" ]; then
  for c in $CLIENTS; do
    atualizar_override_client "$c" '{"browser": ""}'
  done
  echo ">> ROLLBACK: portal e backoffice voltaram ao browser flow padrão (senha + Google)."
  exit 0
fi

# --- guard: SPI presente na imagem? (imagem velha → pula sem quebrar deploy) -
tem_spi=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/authenticator-providers" \
  | PROVIDER="$PROVIDER" python3 -c 'import sys,json,os;ps=json.load(sys.stdin);print(any(p.get("id")==os.environ["PROVIDER"] for p in ps))')
if [ "$tem_spi" != "True" ]; then
  echo ">> AVISO: provider $PROVIDER ausente no Keycloak (imagem sem o SPI?) — pulei a config do login por código."
  exit 0
fi

# --- flow portal-browser: cria se ausente ------------------------------------
FLOW_ID=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows" \
  | ALIAS="$FLOW_ALIAS" python3 -c 'import sys,json,os
fs=json.load(sys.stdin)
print(next((f["id"] for f in fs if f.get("alias")==os.environ["ALIAS"]), ""))')

if [ -z "$FLOW_ID" ]; then
  echo ">> criando flow $FLOW_ALIAS..."
  curl -s -o /dev/null -w ">> POST flow $FLOW_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows" "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$FLOW_ALIAS\",\"description\":\"Browser flow do portal do cliente — código por e-mail como padrão (fonte: infra/keycloak-realm.json + configure-keycloak-email-code.sh)\",\"providerId\":\"basic-flow\",\"topLevel\":true,\"builtIn\":false}"

  # executions do topo (ordem de criação define prioridade)
  for prov in auth-cookie identity-provider-redirector; do
    curl -s -o /dev/null -w ">> POST execution $prov http=%{http_code}\n" -X POST \
      "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions/execution" \
      "${auth[@]}" "${json[@]}" -d "{\"provider\":\"$prov\"}"
  done

  # subflow forms
  curl -s -o /dev/null -w ">> POST subflow $FORMS_ALIAS http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions/flow" \
    "${auth[@]}" "${json[@]}" \
    -d "{\"alias\":\"$FORMS_ALIAS\",\"type\":\"basic-flow\",\"description\":\"código por e-mail primeiro, senha via Try another way\",\"provider\":\"registration-page-form\"}"

  curl -s -o /dev/null -w ">> POST execution $PROVIDER http=%{http_code}\n" -X POST \
    "$KC/admin/realms/$REALM/authentication/flows/$FORMS_ALIAS/executions/execution" \
    "${auth[@]}" "${json[@]}" -d "{\"provider\":\"$PROVIDER\"}"

  # executions novas nascem DISABLED → seta todas para ALTERNATIVE
  curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions" \
    | python3 -c '
import sys, json
for e in json.load(sys.stdin):
    e["requirement"] = "ALTERNATIVE"
    print(json.dumps(e))' \
    | while IFS= read -r exec_json; do
        printf '%s' "$exec_json" | curl -s -o /dev/null \
          -w ">> PUT execution requirement=ALTERNATIVE http=%{http_code}\n" \
          -X PUT "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions" \
          "${auth[@]}" "${json[@]}" -d @-
      done

  FLOW_ID=$(curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows" \
    | ALIAS="$FLOW_ALIAS" python3 -c 'import sys,json,os
fs=json.load(sys.stdin)
print(next((f["id"] for f in fs if f.get("alias")==os.environ["ALIAS"]), ""))')
  [ -n "$FLOW_ID" ] || { echo "ERRO: flow $FLOW_ALIAS não apareceu após criação" >&2; exit 1; }
else
  echo ">> flow $FLOW_ALIAS já existe (id $FLOW_ID)"
fi

# --- converge shape: senha foi absorvida pelo SPI (tela 2) — remove o
# --- auth-username-password-form de flows criados por versões anteriores
curl -s "${auth[@]}" "$KC/admin/realms/$REALM/authentication/flows/$FLOW_ALIAS/executions" \
  | python3 -c '
import sys, json
for e in json.load(sys.stdin):
    if e.get("providerId") == "auth-username-password-form":
        print(e["id"])' \
  | while IFS= read -r exec_id; do
      curl -s -o /dev/null -w ">> DELETE execution password-form http=%{http_code}\n" \
        -X DELETE "$KC/admin/realms/$REALM/authentication/executions/$exec_id" "${auth[@]}"
    done

# --- override do browser flow nos clients (sempre converge) ------------------
for c in $CLIENTS; do
  atualizar_override_client "$c" "{\"browser\":\"$FLOW_ID\"}"
done

# --- emailTheme do realm (template do e-mail do código vive no JAR do SPI) ---
curl -s "${auth[@]}" "$KC/admin/realms/$REALM" \
  | python3 -c '
import sys, json
rep = json.load(sys.stdin)
rep["emailTheme"] = "meujet-email"
print(json.dumps(rep))' \
  | curl -s -o /dev/null -w ">> PUT realm emailTheme=meujet-email http=%{http_code}\n" \
      -X PUT "$KC/admin/realms/$REALM" "${auth[@]}" "${json[@]}" -d @-

echo ">> Login por código de e-mail configurado ($CLIENTS → $FLOW_ALIAS)."
