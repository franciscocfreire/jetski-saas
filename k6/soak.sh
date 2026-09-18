#!/usr/bin/env bash
# =============================================================================
# SOAK — resistência (fase E5): horas de operação realista, para o que só aparece com tempo:
# heap que sobe e não volta, conexões Hikari não devolvidas, threads do Tomcat acumulando,
# jobs de hora fixa (04h15 métricas, 05h15 trial, 06h faturamento/manutenção) no meio da carga.
#
#   ./k6/soak.sh <ip-do-espelho> <minutos> [vus-do-k6]
#
# Dois geradores ao mesmo tempo, os dois de fora da VM:
#   - o MOTOR de personas em TEMPO REAL (fator 1): um dia de operação que dura o que o soak durar,
#     com a taxa de chegadas do sábado sintético (~5 por hora);
#   - o k6 `leitura` no perfil soak, poucas VUs: o ruído de fundo das telas abertas.
# No fim, compara a JVM antes e depois (Prometheus do espelho) e escreve o veredito.
#
# Para a madrugada: ./k6/soak.sh <ip> 480   (das 22h às 6h atravessa todos os jobs).
# =============================================================================
set -euo pipefail
IP="${1:?uso: $0 <ip> <minutos> [vus]}"
MIN="${2:?informe a duração em minutos}"
VUS="${3:-3}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
mkdir -p "$RAIZ/k6/resultados"
CARIMBO="$(date +%Y%m%d-%H%M%S)"

jvm() { # jvm <rótulo>  — fotografia da JVM do backend pelo Prometheus da VM
  ssh -o BatchMode=yes "ubuntu@$IP" '
    q() { curl -s --data-urlencode "query=$1" http://127.0.0.1:9090/api/v1/query | python3 -c "import sys,json;r=json.load(sys.stdin)[\"data\"][\"result\"];print(round(float(r[0][\"value\"][1]),1) if r else \"-\")"; }
    printf "heap_apos_gc_mb=%s " "$(q "sum(jvm_memory_used_bytes{job=\"jetski-backend\",area=\"heap\"})/1048576")"
    printf "threads=%s " "$(q "jvm_threads_live_threads{job=\"jetski-backend\"}")"
    printf "hikari_ativas=%s " "$(q "sum(hikaricp_connections_active{job=\"jetski-backend\"})")"
    printf "hikari_pendentes=%s " "$(q "sum(hikaricp_connections_pending{job=\"jetski-backend\"})")"
    printf "tomcat_ocupadas=%s " "$(q "sum(tomcat_threads_busy_threads{job=\"jetski-backend\"})")"
    printf "gc_pausa_s_5m=%s\n" "$(q "sum(rate(jvm_gc_pause_seconds_sum{job=\"jetski-backend\"}[5m]))")"'
}

echo ">> soak de $MIN min contra $IP — JVM antes:"; ANTES="$(jvm)"; echo "   $ANTES"

# Cenário do motor: um dia em tempo real com a duração do soak, chegadas na taxa do sábado.
python3 - "$RAIZ/sintetico/catalogo/e4-sabado.json" "$T/cenario-soak.json" "$MIN" <<'PY'
import json, sys, math
c = json.load(open(sys.argv[1])); minutos = int(sys.argv[3])
c["dia"] = {"abre": "09:00", "fecha": f"{9 + minutos // 60:02d}:{minutos % 60:02d}", "fator": 1}
horas = list(range(9, 9 + max(1, math.ceil(minutos / 60))))
c["curvaPorHora"] = {str(h): 1 / len(horas) for h in horas}
c["chegadas"] = max(2, round(minutos / 60 * 5))
c["manutencao"]["janela"] = ["09:05", c["dia"]["fecha"]]
# Em tempo real, um passeio de 2 h simuladas dura 2 h REAIS: as jornadas têm de caber no soak.
c["portal"]["antecedenciaMin"] = [5, 20]
c["balcao"]["esperaAteSairMin"] = [2, 8]
c["passeio"]["duracaoMin"] = {"15": 0.6, "30": 0.4}
c["passeio"]["atrasaDevolucao"] = 0
c["manutencao"]["duracaoMin"] = [10, 20]
json.dump(c, open(sys.argv[2], "w"), ensure_ascii=False)
print(f"   motor: {c['chegadas']} chegadas em tempo real, {c['dia']['abre']}–{c['dia']['fecha']}")
PY

( cd "$RAIZ" && CENARIO="$T/cenario-soak.json" bash sintetico/motor.sh "$IP" > "$RAIZ/k6/resultados/soak-motor-$CARIMBO.log" 2>&1 ) &
MOTOR=$!
( cd "$RAIZ" && VUS="$VUS" DURACAO="${MIN}m" bash k6/rodar.sh "$IP" leitura soak > "$RAIZ/k6/resultados/soak-k6-$CARIMBO.log" 2>&1 ) &
K6=$!
echo ">> motor (pid $MOTOR) e k6 leitura/soak com $VUS VUs (pid $K6) rodando; logs em k6/resultados/soak-*-$CARIMBO.log"
wait "$K6"   || echo "!! k6 saiu com código != 0 (threshold estourado ou erro — veja o log)"
# O motor pode ter gente na água depois que o k6 acaba; dá 15 min e então encerra o dia à força.
for _ in $(seq 1 90); do kill -0 "$MOTOR" 2>/dev/null || break; sleep 10; done
if kill -0 "$MOTOR" 2>/dev/null; then echo ">> motor ainda com jornadas em curso 15 min após o k6 — encerrando"; pkill -P "$MOTOR" 2>/dev/null; kill "$MOTOR" 2>/dev/null; fi
wait "$MOTOR" 2>/dev/null || echo "!! motor saiu com código != 0 (veja o log)"

echo ">> JVM depois:"; DEPOIS="$(jvm)"; echo "   $DEPOIS"
python3 - "$ANTES" "$DEPOIS" <<'PY'
import sys
a = dict(x.split("=") for x in sys.argv[1].split()); d = dict(x.split("=") for x in sys.argv[2].split())
def n(v): return float(v) if v not in ("-", "") else None
heap = (n(a["heap_apos_gc_mb"]), n(d["heap_apos_gc_mb"])); thr = (n(a["threads"]), n(d["threads"])); pend = n(d["hikari_pendentes"])
alerta = []
if heap[0] and heap[1] and heap[1] > heap[0] * 1.5 and heap[1] - heap[0] > 100: alerta.append(f"heap subiu {heap[0]}→{heap[1]} MB")
if thr[0] and thr[1] and thr[1] > thr[0] + 20: alerta.append(f"threads subiram {thr[0]}→{thr[1]}")
if pend and pend > 0: alerta.append(f"{pend} conexões esperando no Hikari")
print("veredito:", "ATENÇÃO — " + "; ".join(alerta) if alerta else "sem sinal de vazamento (heap/threads/pool estáveis)")
PY
grep -E "=== desfechos|^portal|^balcao|^falhas" "$RAIZ/k6/resultados/soak-motor-$CARIMBO.log" || true
grep -E "http_req_duration\.|http_req_failed\.|checks_succeeded" "$RAIZ/k6/resultados/soak-k6-$CARIMBO.log" || true
