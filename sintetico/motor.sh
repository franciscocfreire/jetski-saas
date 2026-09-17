#!/usr/bin/env bash
# =============================================================================
# Roda o motor de comportamento (fase E4) DE FORA da VM do espelho — o gerador não disputa
# CPU com a aplicação, e o tráfego entra pela Cloudflare como o de gente de verdade.
#
#   sintetico/motor.sh <ip-do-espelho> [dominio]
#
# O que faz: traz uma cópia do estado das personas (credenciais; 0600, apagada na saída),
# abre túneis SSH para o Mailpit (códigos de login, verificação de e-mail) e para o
# /_controle dos fakes (a cliente "paga" a GRU) — ambos só existem no loopback da VM —,
# e roda `node src/motor.ts dia` num container node:24-alpine.
#
# Variáveis que passam adiante: SEMENTE, CHEGADAS, FATOR, CENARIO. Relatórios em
# sintetico/relatorios/ (fora do git). Métricas Prometheus em http://127.0.0.1:9464/metrics.
# =============================================================================
set -euo pipefail

IP="${1:?uso: sintetico/motor.sh <ip-do-espelho> [dominio]}"
DOMINIO="${2:-jetsave.com.br}"
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Portas LOCAIS dos túneis. Não são 8025/8089 de propósito: o ambiente dev desta máquina
# costuma ter o próprio Mailpit em 8025, e o motor não pode ler a caixa errada.
PORTA_MAILPIT="${PORTA_MAILPIT:-18025}"
PORTA_FAKES="${PORTA_FAKES:-18089}"
SSH=(ssh -o BatchMode=yes -o ExitOnForwardFailure=yes "ubuntu@$IP")

case "$DOMINIO" in *meujet.com.br) echo "ERRO: $DOMINIO é PRODUÇÃO. O motor só roda no espelho." >&2; exit 1 ;; esac

TMP="$(mktemp -d)"; chmod 700 "$TMP"
SOQUETE="$TMP/ssh.sock"
limpar() {
  "${SSH[@]}" -S "$SOQUETE" -O exit 2>/dev/null || true
  [ -f "$TMP/personas.json" ] && { shred -u "$TMP/personas.json" 2>/dev/null || rm -f "$TMP/personas.json"; }
  rm -rf "$TMP"
}
trap limpar EXIT

echo ">> trazendo o estado das personas de $IP..."
( umask 077; "${SSH[@]}" 'sudo cat /var/lib/meujet-espelho/personas.json' > "$TMP/personas.json" )
[ -s "$TMP/personas.json" ] || { echo "ERRO: estado vazio — o espelho já foi semeado?" >&2; exit 1; }

echo ">> abrindo túneis: Mailpit (:$PORTA_MAILPIT) e /_controle dos fakes (:$PORTA_FAKES)..."
"${SSH[@]}" -M -S "$SOQUETE" -fN -L "127.0.0.1:$PORTA_MAILPIT:127.0.0.1:8025" -L "127.0.0.1:$PORTA_FAKES:127.0.0.1:8089"
curl -fsS -o /dev/null "http://127.0.0.1:$PORTA_FAKES/healthz" || { echo "ERRO: fakes-externos não respondeu pelo túnel." >&2; exit 1; }

mkdir -p "$AQUI/relatorios"
export DOMINIO ESTADO="$TMP/personas.json"
export MAILPIT_URL="http://127.0.0.1:$PORTA_MAILPIT" FAKES_URL="http://127.0.0.1:$PORTA_FAKES"
export RELATORIO="$AQUI/relatorios/motor-$(date +%Y%m%d-%H%M%S).json"

# Node local (≥ 22.18 roda TypeScript direto) é o caminho preferido: os túneis estão no
# loopback DESTA máquina, e no Docker Desktop/WSL o `--network host` de um container é o
# host da VM do Docker — ele não enxerga 127.0.0.1 daqui ("fetch failed" em tudo que usa túnel).
if command -v node >/dev/null && node -e 'const [a,b]=process.versions.node.split(".").map(Number);process.exit(a>22||(a===22&&b>=18)?0:1)'; then
  cd "$AQUI" && node src/motor.ts dia
else
  echo ">> sem Node ≥ 22.18 local — usando container (só funciona onde --network host é o host de verdade: Linux nativo)."
  docker run --rm --network host --user "$(id -u):$(id -g)" \
    -v "$AQUI":/app:ro -v "$AQUI/relatorios":/relatorios -v "$TMP/personas.json":/estado/personas.json \
    -e DOMINIO -e ESTADO=/estado/personas.json -e MAILPIT_URL -e FAKES_URL \
    -e RELATORIO="/relatorios/$(basename "$RELATORIO")" \
    ${SEMENTE:+-e SEMENTE} ${CHEGADAS:+-e CHEGADAS} ${FATOR:+-e FATOR} ${CENARIO:+-e CENARIO} \
    node:24-alpine node /app/src/motor.ts dia
fi
