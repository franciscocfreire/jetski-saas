#!/usr/bin/env bash
# =============================================================================
# Limites por IP do nginx no ESPELHO (decisão nº 5 do ECOSSISTEMA_SINTETICO_SPEC.md).
#
# O nginx limita por IP de origem (rl_otp, rl_auth, rl_token, rl_publico, rl_midia). Um teste
# de carga sai de UMA máquina: todas as VUs caem no mesmo balde e o que se mede é o nginx, não
# a aplicação. Aqui se gera `nginx.espelho.conf` a partir do nginx.conf de produção — que NÃO
# muda —, com os limites:
#
#   ESPELHO_LIMITES=frouxos  (padrão)  rate ×50 e burst ×10: mede a aplicação
#   ESPELHO_LIMITES=reais              cópia fiel: `./k6/rodar.sh <ip> portal` mostra o 429 (contador respostas_429)
#
# A camada docker-compose.espelho.yml monta o arquivo gerado no lugar do de produção; o
# deploy.sh chama este script antes de subir o nginx. Só os números de rate/burst mudam —
# qualquer outra diferença entre os dois arquivos é bug deste script.
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
MODO="${ESPELHO_LIMITES:-frouxos}"
ORIGEM=infra/nginx/nginx.conf
DESTINO=infra/espelho/nginx.espelho.conf
if [ -d "$DESTINO" ]; then
  echo "ERRO: $DESTINO é um DIRETÓRIO — o nginx subiu antes deste script e o Docker criou a montagem vazia. Remova (rmdir $DESTINO) e rode de novo." >&2; exit 1
fi

case "$MODO" in
  reais)
    cp "$ORIGEM" "$DESTINO"
    ;;
  frouxos)
    # rate=NNr/m → ×50 ; burst=NN → ×10 (só nessas duas diretivas; nada mais é tocado)
    python3 - "$ORIGEM" "$DESTINO" <<'PY'
import re, sys
texto = open(sys.argv[1]).read()
texto = re.sub(r"rate=(\d+)r/m", lambda m: f"rate={int(m.group(1)) * 50}r/m", texto)
texto = re.sub(r"burst=(\d+)\b", lambda m: f"burst={int(m.group(1)) * 10}", texto)
open(sys.argv[2], "w").write(texto)
PY
    ;;
  *) echo "ERRO: ESPELHO_LIMITES=$MODO (use frouxos ou reais)" >&2; exit 1 ;;
esac

# Prova de que só rate/burst mudaram: fora das linhas com limit_req, os arquivos são iguais.
if ! diff <(grep -v 'limit_req' "$ORIGEM") <(grep -v 'limit_req' "$DESTINO") >/dev/null; then
  echo "ERRO: nginx.espelho.conf difere do nginx.conf fora das linhas de limit_req" >&2; exit 1
fi
echo ">> espelho: nginx.espelho.conf gerado (limites: $MODO) — $(grep -c 'limit_req_zone' "$DESTINO") zonas"
grep 'limit_req_zone' "$DESTINO" | sed 's/^ *//' | cut -c1-110
