#!/usr/bin/env bash
# =============================================================================
# Linha de base de capacidade — extrai do Prometheus/Postgres da VM o retrato
# de uso e de consumo de recursos. SOMENTE LEITURA (nenhuma escrita, nenhum
# restart). Base da Fase 0 de CAPACIDADE_E_LIMITES.md.
#
# Rodar NA VM:        ./infra/observability/linha-de-base.sh > linha-de-base.txt
# Rodar de fora:      ssh -i CHAVE ubuntu@HOST 'bash -s' < infra/observability/linha-de-base.sh
#
# Janela: W=7d por padrão (limite da retenção do Prometheus, `storageRetention`).
#
# GOTCHA — não aumente a janela das subqueries de histograma.
# `max_over_time(histogram_quantile(...)[7d:5m])` avalia 2016 passos sobre todos
# os buckets e satura uma das 2 vCPUs: em 15/set/2026 essa query deixou o
# Prometheus sem responder por minutos (nenhum container tem limite de `cpus:`,
# então ele compete de igual para igual com o backend). Use [1d:10m].
# =============================================================================
set -uo pipefail
P=${PROM_URL:-http://127.0.0.1:9090/api/v1}
W=${W:-7d}
PSQL="docker exec jetski-postgres psql -U jetski -d ${BASE_DB:-jetski_prod} -At -c"

q() { # q "rótulo" "promql"
  local titulo="$1" query="$2"
  echo "### $titulo"
  timeout "${Q_TIMEOUT:-25}" curl -sG "$P/query" --data-urlencode "query=$query" \
    | jq -r '
      if .status != "success" then "  ERRO: \(.error // "?")"
      elif (.data.result | length) == 0 then "  (sem dados)"
      else .data.result[]
        | (.metric | del(.__name__) | to_entries | map("\(.value)") | join(" ")) as $l
        | "  \(if $l == "" then "-" else $l end) = \(.value[1] | tonumber | (.*100|round)/100)"
      end' || echo "  (timeout — query cara demais para esta VM)"
  echo
}

echo "==================== 0. CONTEXTO ===================="
echo "  coleta     : $(TZ=America/Sao_Paulo date '+%Y-%m-%d %H:%M %Z (%a)')"
echo "  janela     : $W"
echo "  uptime     : $(uptime -p)"
echo "  vCPUs      : $(nproc)"
free -m | awk 'NR==2{print "  memória    : "$2" MB total, "$7" MB disponível"}'
df -h / | awk 'NR==2{print "  disco /    : "$2" total, "$4" livre ("$5" usado)"}'
echo "  retenção   : $(curl -s "$P/status/runtimeinfo" | jq -r '.data.storageRetention // "?"')"
echo "  containers : $(docker ps -q | wc -l) no total, $(docker ps --format '{{.Names}}' | grep -c '^jetski-') do Meu Jet"
echo

echo "==================== 1. TRÁFEGO HTTP ===================="
q "Total de requests na janela" "sum(increase(http_server_requests_seconds_count[$W]))"
q "Média de req/s" "sum(rate(http_server_requests_seconds_count[$W]))"
q "PICO de req/s (janela de 5m)" "max_over_time(sum(rate(http_server_requests_seconds_count[5m]))[$W:5m])"
q "Requests por método" "sum by (method) (increase(http_server_requests_seconds_count[$W]))"
q "Top 12 endpoints por volume" "topk(12, sum by (uri) (increase(http_server_requests_seconds_count[$W])))"
q "Só endpoints de negócio (/v1/**)" "topk(12, sum by (uri) (increase(http_server_requests_seconds_count{uri=~\"/v1/.*\"}[$W])))"

echo "==================== 2. LATÊNCIA ===================="
q "P95 global (1h)" "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[1h])))"
q "P99 global (1h)" "histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket[1h])))"
q "PICO do P95 nas últimas 24h (passo 10m)" "max_over_time(histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[10m])))[1d:10m])"
q "Latência média por endpoint de negócio (s)" "topk(10, sum by (uri)(increase(http_server_requests_seconds_sum{uri=~\"/v1/.*\"}[$W])) / sum by (uri)(increase(http_server_requests_seconds_count{uri=~\"/v1/.*\"}[$W])))"
q "Latência máxima já observada por endpoint (s)" "topk(10, max by (uri) (http_server_requests_seconds_max))"

echo "==================== 3. ERROS ===================="
q "Requests por desfecho" "sum by (outcome) (increase(http_server_requests_seconds_count[$W]))"
q "5xx por endpoint" "sum by (uri) (increase(http_server_requests_seconds_count{status=~\"5..\"}[$W])) > 0"
q "4xx por endpoint (top 10)" "topk(10, sum by (uri) (increase(http_server_requests_seconds_count{status=~\"4..\"}[$W])))"
q "Exceções por tipo" "sum by (exception) (increase(http_server_requests_seconds_count{exception!=\"none\"}[$W])) > 0"

echo "==================== 4. HOST (custo de ociosidade) ===================="
q "CPU do host - média %" "100 * (1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[$W])))"
q "CPU do host - PICO % (passo 10m, 24h)" "max_over_time((100 * (1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[10m]))))[1d:10m])"
q "Load average 1m - pico" "max_over_time(node_load1[$W])"
q "RAM disponível agora (GB)" "node_memory_MemAvailable_bytes / 1e9"
q "RAM disponível - MÍNIMO (GB)" "min_over_time(node_memory_MemAvailable_bytes[$W]) / 1e9"
q "Disco / usado %" "100 * (1 - node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"})"
q "Disco / livre (GB)" "node_filesystem_avail_bytes{mountpoint=\"/\"} / 1e9"
q "Disco: variação na janela (GB; negativo = encheu)" "(node_filesystem_avail_bytes{mountpoint=\"/\"} - node_filesystem_avail_bytes{mountpoint=\"/\"} offset 6d) / 1e9"

echo "==================== 5. POR CONTAINER ===================="
q "Memória PICO (MB) - top 20" "topk(20, max by (container_name) (docker_container_mem_usage) / 1e6)"
q "Memória PICO / limite (%) - top 15" "topk(15, 100 * max by (container_name) (docker_container_mem_usage) / (max by (container_name) (docker_container_mem_limit) > 0))"
q "CPU PICO % - top 15" "topk(15, max by (container_name) (docker_container_cpu_usage_percent))"

echo "==================== 6. POOL DE CONEXÕES E JVM ===================="
q "Hikari - conexões ativas (pico)" "max_over_time(hikaricp_connections_active[$W])"
q "Hikari - tamanho do pool" "hikaricp_connections_max"
q "Hikari - threads ESPERANDO (pico)" "max_over_time(hikaricp_connections_pending[$W])"
q "Hikari - timeouts acumulados" "hikaricp_connections_timeout_total"
q "Heap usado - pico (MB)" "max_over_time(sum(jvm_memory_used_bytes{area=\"heap\"})[1d:5m]) / 1e6"
q "Não-heap usado (MB)" "sum(jvm_memory_used_bytes{area=\"nonheap\"})/1e6"
q "Pausa de GC máxima (ms)" "1000 * max_over_time(jvm_gc_pause_seconds_max[$W])"
q "Tempo total em GC na janela (s)" "sum(increase(jvm_gc_pause_seconds_sum[$W]))"
echo "### Flags efetivas da JVM do backend (fonte da verdade — a métrica jvm_memory_max_bytes soma pools e superestima)"
docker exec jetski-backend java -XX:+PrintFlagsFinal -version 2>/dev/null \
  | grep -E "MaxHeapSize|UseSerialGC|UseG1GC|UseParallelGC|MaxRAMPercentage" | sed 's/^/  /'
echo

echo "==================== 7. KEYCLOAK ===================="
q "Sessões ativas (pico)" "max_over_time(jetski_keycloak_sessoes_ativas[$W])"
q "Eventos de usuário" "sum by (event, error) (increase(keycloak_user_events_total[$W])) > 0"
q "Trocas de contexto de tenant" "sum(increase(jetski_tenant_context_switch_total[$W]))"

echo "==================== 8. CARGA DE NEGÓCIO ===================="
q "Locações ativas (pico)" "max_over_time(jetski_locacoes_ativas[$W])"
q "Reservas por evento" "sum by (evento) (increase(jetski_reserva_total[$W])) > 0"
q "Emissões por tipo" "sum by (tipo) (increase(jetski_emissao_total[$W])) > 0"
q "Clientes cadastrados" "jetski_clientes_cadastrados > 0"
q "Taxa de ocupação da frota (pico)" "max_over_time(jetski_frota_taxa_ocupacao[$W]) > 0"

echo "==================== 9. VOLUME DE DADOS (Postgres/MinIO) ===================="
echo "### Tamanho do banco"
$PSQL "select pg_size_pretty(pg_database_size(current_database()));" 2>&1 | sed 's/^/  /'
echo
echo "### Conexões: em uso de máximo"
$PSQL "select count(*) || ' de ' || current_setting('max_connections') from pg_stat_activity;" 2>&1 | sed 's/^/  /'
echo
echo "### Conexões por usuário/estado (jetski_app = backend; jetski = Keycloak/admin)"
$PSQL "select usename || ' | ' || coalesce(state,'-') || ' | ' || n from (select usename, state, count(*) n from pg_stat_activity group by 1,2) t order by n desc;" 2>&1 | sed 's/^/  /'
echo
echo "### 12 maiores tabelas"
$PSQL "select c.relname || ' | ' || pg_size_pretty(pg_total_relation_size(c.oid)) || ' | ' || coalesce(s.n_live_tup::text,'?') || ' linhas' from pg_class c join pg_namespace n on n.oid=c.relnamespace left join pg_stat_user_tables s on s.relid=c.oid where n.nspname='public' and c.relkind='r' order by pg_total_relation_size(c.oid) desc limit 12;" 2>&1 | sed 's/^/  /'
echo
echo "### Volume de negócio acumulado"
for PAR in "tenants ativos:select count(*) from tenant where status='ATIVO'" \
           "tenants total:select count(*) from tenant" \
           "locações:select count(*) from locacao" \
           "locações 30d:select count(*) from locacao where created_at > now() - interval '30 days'" \
           "reservas:select count(*) from reserva" \
           "clientes:select count(*) from cliente" \
           "jetskis:select count(*) from jetski" \
           "usuários:select count(*) from usuario" \
           "documentos emitidos:select count(*) from documento_emitido" \
           "linhas de auditoria:select count(*) from auditoria"; do
  echo "  ${PAR%%:*}: $($PSQL "${PAR#*:}" 2>/dev/null | tr -d '\n')"
done
echo
echo "### Ajustes efetivos do Postgres"
$PSQL "select name || ' = ' || setting || coalesce(unit,'') from pg_settings where name in ('shared_buffers','work_mem','effective_cache_size','max_connections');" 2>&1 | sed 's/^/  /'
echo
echo "### Storage (MinIO)"
docker exec jetski-minio du -sh /data 2>/dev/null | sed 's/^/  /' || echo "  (indisponível)"
echo
echo "### Disco consumido pelo Docker"
docker system df 2>/dev/null | sed 's/^/  /'
