#!/usr/bin/env bash
# =============================================================================
# Gera o .env do ESPELHO a partir do modelo, com segredos NOVOS.
#
#   ./infra/espelho/gerar-env.sh meujet-carga.com.br
#
# Existe para que ninguém monte o espelho copiando o .env de produção: cada
# __GERADO__ vira um valor aleatório próprio, e o que depende de ação humana
# (token e UUID do túnel) continua como placeholder — o preflight reprova até
# ser preenchido.
# =============================================================================
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODELO="$RAIZ/infra/espelho/env.espelho.example"
DESTINO="${DESTINO:-$RAIZ/.env}"
DOMINIO="${1:-}"

if [ -z "$DOMINIO" ]; then
  echo "uso: $0 <dominio-do-espelho>   (ex.: meujet-carga.com.br)" >&2
  exit 1
fi
case "$DOMINIO" in
  meujet.com.br|*.meujet.com.br|pegaojet.com.br|*.pegaojet.com.br)
    echo "ERRO: $DOMINIO é de produção ou do dev. O espelho precisa de domínio próprio." >&2
    exit 1 ;;
esac
if [ -e "$DESTINO" ]; then
  echo "ERRO: $DESTINO já existe — não sobrescrevo um .env (pode ser o de produção)." >&2
  exit 1
fi

# Cada ocorrência de __GERADO__ recebe um valor diferente: segredo repetido
# entre banco, MinIO e NextAuth transforma um vazamento em todos.
awk -v dominio="$DOMINIO" '
  {
    gsub(/__DOMINIO__/, dominio)
    while (index($0, "__GERADO__") > 0) {
      cmd = "openssl rand -base64 32 | tr -d \"=+/\\n\""
      cmd | getline segredo
      close(cmd)
      sub(/__GERADO__/, segredo)
    }
    print
  }
' "$MODELO" > "$DESTINO"
chmod 600 "$DESTINO"

echo "Gerado: $DESTINO (permissão 600)"
echo
echo "Falta preencher à mão (o preflight reprova enquanto não fizer):"
grep -nE '__[A-Z_]+__' "$DESTINO" | grep -vE '^[0-9]+:\s*#' | sed 's/=.*/=…/; s/^/  linha /'
echo
echo "Depois: ./infra/espelho/preflight.sh"
