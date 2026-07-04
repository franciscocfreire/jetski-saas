# Observabilidade de Produção — logs e métricas

Stack **enxuto** para o servidor (Oracle ARM, 11 GB): ver os logs de todos os
containers e as métricas do backend no Grafana, pelo mesmo host público.

| Serviço | Função | Memória | Acesso |
|---|---|---|---|
| **Grafana** | UI de logs + dashboards | 320 MB | `https://SEU_HOST/grafana` (admin / `GRAFANA_ADMIN_PASSWORD`) |
| **Loki** | armazenamento de logs (7 dias) | 512 MB | interno (`:3100` só em 127.0.0.1) |
| **Promtail** | coleta os logs de TODOS os containers via Docker | 256 MB | interno |
| **Prometheus** | métricas do backend (7 dias) | 512 MB | interno (`:9090` só em 127.0.0.1) |

Total: ~1,6 GB no pior caso. **Sem Jaeger/Alertmanager** nesta v1 (adicionar
quando precisar de tracing/alertas — o stack de dev em
`infra/docker-compose-monitoring.yml` continua sendo a referência completa).

## Subir (no servidor)

```bash
cd /home/ubuntu/jetski
# 1) garanta no .env:  GRAFANA_ADMIN_PASSWORD=...
# 2) stack principal precisa estar no ar (a rede do app é externa p/ este compose)
docker compose -f infra/observability/docker-compose.observability.yml up -d

# nginx precisa conhecer a rota /grafana (após atualizar nginx.conf):
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --force-recreate nginx
```

Acesse `https://SEU_HOST/grafana` → Explore → datasource **Loki** →
query `{service="backend"}` (ou `nginx`, `keycloak`, `portal`…).

## Como os logs chegam

O Promtail lê o **json-file do próprio Docker** (socket + `/var/lib/docker/containers`),
com labels `service` (nome do serviço no compose), `container` e `project` —
nenhuma aplicação precisou ser alterada. Logs do backend (JSON estruturado)
ganham ainda o label `level` e campos `tenant_id`/`trace_id` extraídos.

> **Dev/WSL2 + Docker Desktop:** este desenho NÃO funciona localmente — os
> arquivos de log ficam dentro da VM do Docker Desktop. Em dev, use
> `docker compose logs -f <serviço>` (ou o stack antigo de dev p/ métricas).

## Queries úteis (Explore → Loki)

```logql
{service="backend"} |= "ERROR"                 # erros do backend
{service="backend"} | json | tenant_id="..."   # por tenant
{service="nginx"} |= "POST /api"               # tráfego de escrita
{container=~"jetski-.*"} |= "Exception"        # exceções em qualquer serviço
```

## Parar / remover

```bash
docker compose -f infra/observability/docker-compose.observability.yml down   # mantém dados
docker compose -f infra/observability/docker-compose.observability.yml down -v # apaga logs/métricas
```
