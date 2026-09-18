#!/usr/bin/env bash
# =============================================================================
# Gera o k6/.auth/tokens.json a partir das personas do espelho.
#
# O semeador (sintetico/) roda DENTRO da VM — é lá que estão o estado das personas, o
# Mailpit (o cliente do portal entra pelo código do e-mail) e o /_controle dos fakes (prova
# de que o backend fala com a Marinha SINTÉTICA, sem a qual os cenários de emissão se
# recusam a rodar). Só o tokens.json volta para cá. Vale ~12 h (sessão SSO do realm).
#
# Credenciais por tipo: carga (admins das empresas de carga), emissao (atendentes da EAMA e
# das delegadas + instrutor), portal (clientes recorrentes) e plataforma (operadora, console).
#
# Uso:
#   ./k6/gerar-tokens.sh <ip-do-espelho> [dominio]        (domínio padrão: jetsave.com.br)
#
# Requer: ssh para ubuntu@<ip>. O código do semeador é o do checkout da VM (~/jetski).
# =============================================================================
set -euo pipefail
IP="${1:?uso: $0 <ip-do-espelho> [dominio]}"
DOMINIO="${2:-jetsave.com.br}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

case "$DOMINIO" in
  meujet.com.br|*.meujet.com.br) echo "ERRO: $DOMINIO é PRODUÇÃO. Carga roda no espelho." >&2; exit 1 ;;
esac

umask 077
mkdir -p "$RAIZ/k6/.auth"
ssh -o BatchMode=yes "ubuntu@$IP" "set -e
  sudo docker run --rm --network host -v /home/ubuntu/jetski/sintetico:/app:ro -v /var/lib/meujet-espelho:/estado \
    -e DOMINIO='$DOMINIO' -e ESTADO=/estado/personas.json -e SAIDA=/estado/tokens.json \
    node:24-alpine node /app/src/semeador.ts tokens >&2
  sudo cat /var/lib/meujet-espelho/tokens.json && sudo rm -f /var/lib/meujet-espelho/tokens.json" > "$RAIZ/k6/.auth/tokens.json"

python3 - "$RAIZ/k6/.auth/tokens.json" <<'PY'
import json, sys, collections
d = json.load(open(sys.argv[1]))
n = collections.Counter(u.get("tipo", "carga") for u in d["usuarios"])
print(f"tokens.json: {dict(n)}; fakes verificados: {d.get('fakesVerificados')}")
PY
echo "Pronto. Para rodar:  ./k6/rodar.sh $IP <leitura|balcao|portal|emissao|plataforma|resiliencia> [perfil]"
