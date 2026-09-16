#!/usr/bin/env bash
# =============================================================================
# Obtém os tokens que os cenários k6 usam — UMA vez por turno de teste.
#
# Por que existe: nenhum client do realm que a API aceita tem ROPC
# (`directAccessGrants`). Os que têm — `jetski-password-check` e `jetski-test` —
# não estão em `jetski.security.jwt.allowed-clients`, então o token deles volta
# 401; em produção isso é proposital (correção de segurança) e não vamos
# reabrir só para o teste.
#
# Então fazemos aqui o login DE VERDADE (authorization_code + PKCE, o mesmo
# fluxo do navegador) e guardamos o refresh token. O k6 só troca refresh por
# access token, que é um POST simples, sem HTML para parsear.
#
# Validade: o refresh token acompanha a sessão SSO (12 h ociosa / 14 h máxima
# neste realm). Um tokens.json serve para um turno inteiro de testes.
#
# Uso:
#   ISSUER=https://sso.<dominio-do-espelho>/realms/jetski-saas \
#   APP_URL=https://app.<dominio-do-espelho> \
#   USUARIOS="carga1@exemplo.invalid:senha1 carga2@exemplo.invalid:senha2" \
#   ./k6/auth-setup.sh
#
# Saída: k6/.auth/tokens.json  (ignorado pelo git — contém credencial viva)
# =============================================================================
set -euo pipefail

ISSUER="${ISSUER:-http://localhost:8080/realms/jetski-saas}"
APP_URL="${APP_URL:-http://localhost:3001}"
CLIENT_ID="${CLIENT_ID:-jetski-backoffice}"
# Qualquer caminho sob o host do backoffice serve: o realm registra ${APP_URL}/*
REDIRECT_URI="${REDIRECT_URI:-${APP_URL}/api/auth/callback/keycloak}"
SAIDA="${SAIDA:-$(dirname "$0")/.auth/tokens.json}"

# Decisão de 16/set/2026: carga não roda em produção (mesma trava do k6).
host="${ISSUER#*://}"; host="${host%%[/:]*}"
if [[ "$host" == meujet.com.br || "$host" == *.meujet.com.br ]] \
   && [[ "${PERMITIR_PRODUCAO:-}" != "sim, é produção" ]]; then
  echo "ERRO: ${ISSUER} é o SSO de PRODUÇÃO. Autentique no espelho (infra/espelho/README.md)." >&2
  exit 1
fi

if [[ -z "${USUARIOS:-}" ]]; then
  echo "ERRO: defina USUARIOS=\"email:senha email2:senha2\"" >&2
  exit 1
fi

mkdir -p "$(dirname "$SAIDA")"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# Extrai o action do formulário de login do HTML do Keycloak e desescapa &amp;
extrair_action() {
  grep -oE 'action="[^"]*"' "$1" | head -1 | sed 's/action="//; s/"$//; s/&amp;/\&/g'
}

autenticar() { # autenticar <email> <senha> -> imprime o JSON do token
  local email="$1" senha="$2"
  local jar="$TMP/cookies-$RANDOM" html="$TMP/login-$RANDOM.html"

  # PKCE: verifier aleatório, challenge = base64url(sha256(verifier))
  local verifier challenge
  verifier=$(openssl rand -hex 48)
  challenge=$(printf '%s' "$verifier" | openssl dgst -sha256 -binary | b64url)

  # 1. Página de login (guarda os cookies de sessão do Keycloak)
  curl -sS -c "$jar" -b "$jar" -o "$html" \
    --get "${ISSUER}/protocol/openid-connect/auth" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "redirect_uri=${REDIRECT_URI}" \
    --data-urlencode "response_type=code" \
    --data-urlencode "scope=openid" \
    --data-urlencode "code_challenge=${challenge}" \
    --data-urlencode "code_challenge_method=S256"

  local action
  action=$(extrair_action "$html")
  if [[ -z "$action" ]]; then
    echo "ERRO: não achei o formulário de login para ${email}." >&2
    echo "      Primeiras linhas da resposta do Keycloak:" >&2
    head -c 400 "$html" >&2; echo >&2
    return 1
  fi

  # 2. Credenciais.
  #    O tema meujet MOSTRA o login em dois passos (e-mail → Continuar → senha),
  #    mas isso é do lado do cliente: o autenticador por trás é o
  #    UsernamePasswordForm padrão, que aceita usuário e senha num POST só.
  #    Se um dia o fluxo virar identifier-first DE VERDADE (duas execuções no
  #    Keycloak), este POST passa a devolver o formulário da senha em vez do
  #    302 — e o erro abaixo diz exatamente isso.
  local resposta
  resposta=$(curl -sS -c "$jar" -b "$jar" -o "$html" -w '%{http_code} %{redirect_url}' \
    -X POST "$action" \
    --data-urlencode "username=${email}" \
    --data-urlencode "password=${senha}" \
    --data-urlencode "credentialId=")

  local status="${resposta%% *}" destino="${resposta#* }"
  if [[ "$status" != "302" || -z "$destino" ]]; then
    echo "ERRO: login de ${email} não redirecionou (HTTP ${status})." >&2
    if grep -qi 'invalid\|inválid\|error' "$html"; then
      echo "      O Keycloak devolveu uma página de erro — senha errada, usuário" >&2
      echo "      com 2FA/ação pendente (atualizar senha), ou conta não ativada." >&2
    else
      echo "      Pode ser fluxo identifier-first em dois POSTs; ver o comentário acima." >&2
    fi
    return 1
  fi

  local code="${destino##*code=}"; code="${code%%&*}"
  if [[ -z "$code" || "$code" == "$destino" ]]; then
    echo "ERRO: não achei o authorization code no redirect de ${email}: ${destino}" >&2
    return 1
  fi

  # 3. Troca código por tokens
  curl -sS -X POST "${ISSUER}/protocol/openid-connect/token" \
    --data-urlencode "grant_type=authorization_code" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "code=${code}" \
    --data-urlencode "redirect_uri=${REDIRECT_URI}" \
    --data-urlencode "code_verifier=${verifier}"
}

echo "Autenticando em ${ISSUER} como ${CLIENT_ID}..."
entradas=()
for par in $USUARIOS; do
  email="${par%%:*}"; senha="${par#*:}"
  printf '  %-40s ' "$email"
  if json=$(autenticar "$email" "$senha"); then
    refresh=$(printf '%s' "$json" | sed -n 's/.*"refresh_token":"\([^"]*\)".*/\1/p')
    if [[ -z "$refresh" ]]; then
      echo "FALHOU (sem refresh_token na resposta)"; continue
    fi
    entradas+=("{\"usuario\":\"${email}\",\"refreshToken\":\"${refresh}\"}")
    echo "ok"
  else
    echo "FALHOU"
  fi
done

if [[ ${#entradas[@]} -eq 0 ]]; then
  echo "Nenhum usuário autenticou — nada a escrever." >&2
  exit 1
fi

printf '{\n  "geradoEm": "%s",\n  "issuer": "%s",\n  "clientId": "%s",\n  "usuarios": [\n    %s\n  ]\n}\n' \
  "$(date -Iseconds)" "$ISSUER" "$CLIENT_ID" \
  "$(IFS=,; echo "${entradas[*]}" | sed 's/},{/},\n    {/g')" > "$SAIDA"

chmod 600 "$SAIDA"
echo "Gravado: ${SAIDA} (${#entradas[@]} usuário(s)) — vale ~12 h."
