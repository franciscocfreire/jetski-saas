#!/usr/bin/env bash
# =============================================================================
# Gera o k6/.auth/tokens.json a partir das personas do espelho.
#
# As empresas de carga e seus admins são criados pelo semeador (sintetico/), dentro
# da VM, no provisionamento. Aqui só buscamos o estado das personas por SSH e
# fazemos o login de cada admin — pelo MESMO código de login do semeador — para
# obter os refresh tokens que os cenários usam. Vale ~12 h (sessão SSO do realm).
#
# Uso:
#   ./k6/gerar-tokens.sh <ip-do-espelho> [dominio]        (domínio padrão: jetsave.com.br)
#
# Requer: ssh para ubuntu@<ip> e Docker (roda o Node 24 em container; nada a instalar).
# =============================================================================
set -euo pipefail
IP="${1:?uso: $0 <ip-do-espelho> [dominio]}"
DOMINIO="${2:-jetsave.com.br}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

case "$DOMINIO" in
  meujet.com.br|*.meujet.com.br) echo "ERRO: $DOMINIO é PRODUÇÃO. Carga roda no espelho." >&2; exit 1 ;;
esac

umask 077
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT   # o estado tem senhas e segredos TOTP: não fica em disco
mkdir -p "$RAIZ/k6/.auth"
ssh -o BatchMode=yes "ubuntu@$IP" 'sudo cat /var/lib/meujet-espelho/personas.json' > "$TMP/personas.json"

docker run --rm -u "$(id -u):$(id -g)" \
  -v "$RAIZ/sintetico:/app:ro" -v "$TMP:/estado" -v "$RAIZ/k6/.auth:/saida" \
  -e DOMINIO="$DOMINIO" -e ESTADO=/estado/personas.json -e SAIDA=/saida/tokens.json \
  node:24-alpine node /app/src/semeador.ts tokens

echo
echo "Pronto. Para rodar:"
echo "  export BASE_URL=https://www.$DOMINIO/api ISSUER=https://sso.$DOMINIO/realms/jetski-saas"
echo "  k6 run -e PERFIL=smoke k6/cenarios/leitura.js"
