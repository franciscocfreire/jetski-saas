# Observabilidade de Produção — logs e métricas

Stack **enxuto** para o servidor (Oracle ARM, 11 GB): ver os logs de todos os
containers e as métricas do backend no Grafana, pelo mesmo host público.

| Serviço | Função | Memória | Acesso |
|---|---|---|---|
| **Grafana** | UI de logs + dashboards | 320 MB | `https://SEU_HOST/grafana` (admin / `GRAFANA_ADMIN_PASSWORD`) |
| **Loki** | armazenamento de logs (7 dias) | 512 MB | interno (`:3100` só em 127.0.0.1) |
| **Promtail** | coleta os logs de TODOS os containers via Docker | 256 MB | interno |
| **Prometheus** | métricas do backend (7 dias) | 512 MB | interno (`:9090` só em 127.0.0.1) |
| **node-exporter** | CPU/RAM/disco/rede do host | 64 MB | interno |
| **Telegraf** | memória/CPU por container (API do Docker) | 192 MB | interno |

Total: ~2 GB no pior caso. Alertas usam o alerting nativo do Grafana (sem
Alertmanager); **sem Jaeger** (tracing) nesta versão — o stack de dev em
`infra/docker-compose-monitoring.yml` continua sendo a referência completa.

## Subir (no servidor)

```bash
cd /home/ubuntu/jetski
# 1) garanta no .env:  GRAFANA_ADMIN_PASSWORD=...
# 2) stack principal precisa estar no ar (a rede do app é externa p/ este compose)
# --env-file é obrigatório: o compose fica em infra/observability/, então o
# Compose v2 NÃO acha o .env da raiz sozinho (GRAFANA_ADMIN_PASSWORD, PUBLIC_URL)
docker compose --env-file .env -f infra/observability/docker-compose.observability.yml up -d

# nginx precisa conhecer a rota /grafana (após atualizar nginx.conf):
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --force-recreate nginx
```

> **Após um `git pull` que mude config montada** (prometheus-prod.yml,
> promtail-prod.yml, datasources/alerting do Grafana): `up -d` NÃO recria o
> container — mudança de conteúdo de arquivo montado não conta como mudança de
> serviço. Rode `... restart prometheus` / `restart grafana` / `restart promtail`
> conforme o arquivo alterado (dashboards JSON são a exceção: recarregam a cada 30s).

Acesse `https://SEU_HOST/grafana` → Explore → datasource **Loki** →
query `{service="backend"}` (ou `nginx`, `keycloak`, `portal`…).

## Dashboards provisionados (pasta "Jetski SaaS")

Os JSONs vivem em `infra/monitoring/grafana/provisioning/dashboards/`
(compartilhados com o stack de dev); o provisioning de datasources de prod é
próprio (`grafana/provisioning/` — **sem Jaeger**, que não existe nesta v1).

| Dashboard | Para quê |
|---|---|
| **Saúde de Produção** | o primeiro a abrir: backend up, req/s, 5xx, P95, Hikari, heap/CPU + erros nos logs por serviço, 4xx/5xx do nginx e últimos ERROR do backend |
| **Logs & Erros** | investigação: variáveis serviço/busca/tenant, volume por serviço/nível, top exceções, falhas de login do Keycloak, logs ao vivo (cole um `trace_id` na busca p/ correlacionar nginx ↔ backend) |
| **Visão por Tenant** | variável `$tenant`: saldo de créditos, check-ins, reservas, receita, pagamentos, emissões por tipo e logs/erros daquele tenant |
| **Infraestrutura** | host (CPU/RAM/disco/rede) e memória/CPU por container — quem está comendo os 11 GB |
| **Visão Operacional** | gauges de negócio (locações ativas, reservas, frota, ocupação) |
| **Performance do Sistema** | JVM/threads/HTTP/Hikari em detalhe |

> Os painéis Loki desses dashboards só têm dados em **prod** (em dev/WSL2 o
> Promtail não coleta — ver limitação abaixo).

### De onde vêm as métricas de negócio

- **Eventos por tenant** (`jetski_rental_*`, `jetski_reserva_total{evento=…}`,
  `jetski_emissao_total{tipo=…}`, `jetski_pagamento_*`,
  `jetski_creditos_movimento_total`): incrementados pelo
  `MetricsEventListener` (módulo `metrics` do backend) a partir dos eventos de
  domínio — mesma fonte da auditoria.
- **Estado atual** (`jetski_locacoes_ativas`, `jetski_frota_*`,
  `jetski_creditos_saldo{tenant_id=…}`): gauges consultados no banco a cada 30s
  pelo `BusinessMetricsService`.
- Cuidado ao criar métrica nova: nome que termina em sufixo reservado do
  Prometheus (`.created`, `.count`, `.sum`, `.total`…) é decepado pelo client —
  o teste `MetricsEventListenerTest#testPrometheusExposedNames` trava o
  contrato de nomes que os dashboards consomem.

> Por que Telegraf e não cAdvisor? O Docker do servidor usa o containerd
> image store (storage driver "overlayfs") e o cAdvisor não suporta esse
> layout (google/cadvisor#3643, fechado como not-planned) — ele falhava ao
> registrar todos os containers. O Telegraf lê a API do Docker (docker.sock),
> imune ao storage. O deploy.sh também faz `docker builder prune` pós-build:
> os builds --no-cache do CD acumulavam build cache sem limite (155GB!).

## Alertas (e-mail)

Cinco regras provisionadas (pasta **Alertas** na UI; arquivo
`grafana/provisioning/alerting/alertas.yml` — regra provisionada não é editável
pela UI, edite o arquivo e recrie o Grafana):

| Alerta | Condição | Severidade |
|---|---|---|
| Backend fora do ar | `up == 0` por 3 min | critical |
| Taxa de erros 5xx | > 5% por 5 min | critical |
| Disco do host | > 80% por 10 min | warning |
| Memória do host | > 90% por 10 min | warning |
| Pico de ERROR nos logs | > 20 linhas/5min por 10 min (Loki) | warning |

Notificação por e-mail via SMTP do Gmail (reaproveita `GMAIL_USER` /
`GMAIL_APP_PASSWORD` do `.env`); destinatário em `GRAFANA_ALERT_EMAIL`
(sem valor → o próprio `GMAIL_USER`). Para desligar: `GRAFANA_SMTP_ENABLED=false`.

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
docker compose --env-file .env -f infra/observability/docker-compose.observability.yml down    # mantém dados
docker compose --env-file .env -f infra/observability/docker-compose.observability.yml down -v  # apaga logs/métricas
```
