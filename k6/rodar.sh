#!/usr/bin/env bash
# =============================================================================
# Roda um cenário k6 contra o ESPELHO, cuidando do que cada um precisa nos fakes.
#
#   ./k6/rodar.sh <ip> <cenario> [perfil] [--modo <falha>] [-- <args extras do k6>]
#
#   cenario: leitura | balcao | portal | emissao | plataforma | resiliencia
#   perfil:  smoke (padrão) | load | stress | soak | spike        (ver k6/perfis.js)
#   --modo:  só para resiliencia — nenhum | marinha-fora | pagtesouro-lento | pagtesouro-pendurado
#
# O que faz além de chamar o k6:
#   - deriva BASE_URL/ISSUER do domínio (padrão jetsave.com.br; nunca meujet.com.br);
#   - emissao/resiliencia: liga `autoPagarAposSeg: 0` no PagTesouro sintético (a "cliente" paga
#     na hora) e, na resiliência, injeta o modo de falha pelo /_controle — tudo por SSH, porque
#     o /_controle só existe no loopback da VM; ao sair, restaura a configuração padrão;
#   - grava o resumo em k6/resultados/<cenario>-<perfil>-<data>.json (fora do git).
#
# Requer: ssh para ubuntu@<ip>, Docker (grafana/k6) e k6/.auth/tokens.json (./k6/gerar-tokens.sh).
# =============================================================================
set -euo pipefail

IP="${1:?uso: $0 <ip> <cenario> [perfil] [--modo <falha>] [-- args do k6]}"
CENARIO="${2:?informe o cenário}"
shift 2
PERFIL="smoke"; MODO="nenhum"; EXTRAS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --modo) MODO="$2"; shift 2 ;;
    --) shift; EXTRAS=("$@"); break ;;
    *) PERFIL="$1"; shift ;;
  esac
done
DOMINIO="${DOMINIO:-jetsave.com.br}"
case "$DOMINIO" in meujet.com.br|*.meujet.com.br) echo "ERRO: $DOMINIO é PRODUÇÃO. Carga roda no espelho." >&2; exit 1 ;; esac

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -f "$RAIZ/k6/cenarios/$CENARIO.js" ] || { echo "ERRO: cenário desconhecido: $CENARIO" >&2; exit 1; }
[ -f "$RAIZ/k6/.auth/tokens.json" ] || { echo "ERRO: sem k6/.auth/tokens.json — rode ./k6/gerar-tokens.sh $IP" >&2; exit 1; }
mkdir -p "$RAIZ/k6/resultados"
SAIDA="k6/resultados/$CENARIO-$PERFIL${MODO:+-$MODO}-$(date +%Y%m%d-%H%M%S).json"

controle() { # controle <json de config>  — POST /_controle/config no espelho
  ssh -o BatchMode=yes "ubuntu@$IP" "curl -sf -X POST http://127.0.0.1:8089/_controle/config -H 'Content-Type: application/json' -d '$1' >/dev/null"
}
PADRAO='{"autoPagarAposSeg":null,"falhas":{"marinha":{"taxa":0},"bridge":{"taxa":0},"pagtesouro":{"taxa":0}},"latenciaMs":{"marinha":0,"bridge":0,"pagtesouro":0}}'

case "$CENARIO" in
  emissao)
    [ "$MODO" = "nenhum" ] || { echo "ERRO: --modo só vale para resiliencia" >&2; exit 1; }
    CFG='{"autoPagarAposSeg":0}'
    echo ">> fakes: $CFG"
    controle "$CFG"
    trap 'echo ">> fakes: restaurando a configuração padrão"; controle "$PADRAO" || true' EXIT
    ;;
  resiliencia)
    case "$MODO" in
      nenhum)               CFG='{"autoPagarAposSeg":0}' ;;
      marinha-fora)         CFG='{"autoPagarAposSeg":0,"falhas":{"marinha":{"taxa":1,"modo":"erro"}}}' ;;
      pagtesouro-lento)     CFG='{"autoPagarAposSeg":0,"latenciaMs":{"pagtesouro":8000}}' ;;
      pagtesouro-pendurado) CFG='{"autoPagarAposSeg":0,"falhas":{"pagtesouro":{"taxa":1,"modo":"pendurar"}}}' ;;
      *) echo "ERRO: modo desconhecido: $MODO" >&2; exit 1 ;;
    esac
    echo ">> fakes: $CFG"
    controle "$CFG"
    trap 'echo ">> fakes: restaurando a configuração padrão"; controle "$PADRAO" || true' EXIT
    ;;
  *) [ "$MODO" = "nenhum" ] || { echo "ERRO: --modo só vale para resiliencia" >&2; exit 1; } ;;
esac

echo ">> k6 $CENARIO/$PERFIL contra https://www.$DOMINIO (resumo em $SAIDA)"
# `-e` DEPOIS do subcomando `run`: antes do nome da imagem o Docker o engole e o k6 cai no smoke.
docker run --rm -i -u "$(id -u):$(id -g)" -v "$RAIZ:/src" -w /src grafana/k6:latest run \
  -e BASE_URL="https://www.$DOMINIO/api" -e ISSUER="https://sso.$DOMINIO/realms/jetski-saas" \
  -e PERFIL="$PERFIL" -e MODO="$MODO" \
  ${VUS:+-e VUS="$VUS"} ${MAX_VUS:+-e MAX_VUS="$MAX_VUS"} ${DURACAO:+-e DURACAO="$DURACAO"} ${PICO:+-e PICO="$PICO"} ${CREDITOS_MINIMO:+-e CREDITOS_MINIMO="$CREDITOS_MINIMO"} \
  --summary-export "/src/$SAIDA" "${EXTRAS[@]}" "k6/cenarios/$CENARIO.js"
