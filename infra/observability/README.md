# Observabilidade — logs e métricas (dev = prod)

Stack **enxuto** e idêntico nos dois ambientes: no servidor (Oracle ARM, 11 GB)
e no dev/WSL2 + Docker Desktop. Logs de todos os containers e métricas de
backend/host/containers no Grafana.

| Serviço | Função | Memória | Acesso |
|---|---|---|---|
| **Grafana** | UI de logs + dashboards | 320 MB | `https://SEU_HOST/grafana` (admin / `GRAFANA_ADMIN_PASSWORD`) |
| **Loki** | armazenamento de logs (7 dias) | 512 MB | interno (`:3100` só em 127.0.0.1) |
| **Alloy** | coleta os logs de todos os containers via **API do Docker** | 256 MB | interno |
| **Prometheus** | métricas do backend (7 dias) | 512 MB | interno (`:9090` só em 127.0.0.1) |
| **node-exporter** | CPU/RAM/disco/rede do host | 64 MB | interno |
| **Telegraf** | memória/CPU por container (API do Docker) | 192 MB | interno |

Total: ~2 GB no pior caso. Alertas usam o alerting nativo do Grafana (sem
Alertmanager); **sem Jaeger** (tracing) nesta versão — o stack de dev em
`infra/docker-compose-monitoring.yml` continua sendo a referência completa.

## Subir (servidor OU dev — mesmo comando)

```bash
# 1) garanta no .env:  GRAFANA_ADMIN_PASSWORD=...
#    (em dev, opcional: GRAFANA_SMTP_HOST=mailpit:1025 → alertas caem no Mailpit :8025)
# 2) stack principal precisa estar no ar (a rede do app é externa p/ este compose)
# --env-file é obrigatório: o compose fica em infra/observability/, então o
# Compose v2 NÃO acha o .env da raiz sozinho (GRAFANA_ADMIN_PASSWORD, PUBLIC_URL)
docker compose --env-file .env -f infra/observability/docker-compose.observability.yml up -d

# nginx precisa conhecer a rota /grafana (após atualizar nginx.conf):
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --force-recreate nginx
```

Em dev, acesse direto: Grafana em `http://localhost:3300` (ou via nginx em
`/grafana`). node-exporter em dev mede a VM do Docker Desktop (limites do
`.wslconfig`), não o Windows.

> **Migração Promtail → Alloy (jul/2026):** `git pull` e
> `docker compose --env-file .env -f infra/observability/docker-compose.observability.yml up -d --remove-orphans`
> — o `--remove-orphans` remove o container do Promtail; Loki e Grafana são
> recriados automaticamente (mounts mudaram). Pode haver uma pequena
> duplicação de logs recentes na virada (o Alloy relê a posição corrente
> dos containers). O stack antigo de dev (`infra/docker-compose-monitoring.yml`,
> com Jaeger/Alertmanager) foi aposentado junto.

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
| **Endpoints do Backend** | top 10 por tráfego/latência/4xx/5xx + detalhe por endpoint (status, P50/P95, exceções, ERROR no Loki) |
| **Infraestrutura** | host (CPU/RAM/disco/rede) e memória/CPU por container — quem está comendo os 11 GB |
| **Visão Operacional** | gauges de negócio (locações ativas, reservas, frota, ocupação) |
| **Performance do Sistema** | JVM/threads/HTTP/Hikari em detalhe |

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

## Login (Keycloak OIDC)

O botão **"Entrar com Keycloak"** federa com o realm `jetski-saas`. Acesso é
restrito às roles de realm **`grafana_admin`** / **`grafana_viewer`**
(`role_attribute_strict`): o realm é compartilhado com os CLIENTES do portal,
então autenticar não basta — sem a role, o login é negado. O login local do
`admin` (GRAFANA_ADMIN_PASSWORD) continua como quebra-vidra.

- Client/roles são convergidos pelo `infra/prod/configure-keycloak-grafana.sh`
  (roda no deploy; secret gerado e gravado no `.env` na 1ª vez). Dê a role a
  alguém via `GRAFANA_ADMIN_EMAILS` no `.env` ou pelo admin do Keycloak.
- Em dev o realm.json já traz o client (`grafana-dev-secret`) e o
  `admin@acme.com` com `grafana_admin`.

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

O **Alloy** (`alloy/config.alloy`) coleta os logs de todos os containers do
compose pela **API do Docker** (docker.sock) — por isso funciona igualmente em
prod e em dev/WSL2 (o Promtail antigo lia `/var/lib/docker`, inacessível no
Docker Desktop). Labels: `service` (nome do serviço no compose), `container` e
`project`; logs do backend (JSON estruturado) ganham ainda o label `level`.
Containers fora do compose (ex.: k8s do Docker Desktop em dev) são ignorados.

> Painel do backend sem o label `level` em dev? A imagem local do backend está
> velha (logback antigo com pretty-print) — rode `./rebuild.sh backend`.

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
