# Observabilidade — Meu Jet

> **A documentação viva está em [`infra/observability/README.md`](infra/observability/README.md).**
> Este arquivo descrevia um stack antigo de dev (Jaeger/Alertmanager/
> `monitoring-stack.sh`), aposentado em jul/2026.

## Resumo do stack atual (dev = prod, o mesmo compose)

```bash
docker compose --env-file .env -f infra/observability/docker-compose.observability.yml up -d
```

| Componente | Função |
|---|---|
| **Grafana** | dashboards + alertas por e-mail (`https://HOST/grafana`) |
| **Loki** | logs de todos os containers (7 dias) |
| **Alloy** | coleta de logs via **API do Docker** — funciona em prod e em dev/WSL2 |
| **Prometheus** | métricas do backend + host + containers (7 dias) |
| **node-exporter** | CPU/RAM/disco/rede do host |
| **Telegraf** | memória/CPU por container (API do Docker) |

Dashboards provisionados: Saúde de Produção, Logs & Erros, Visão por Tenant,
Endpoints do Backend, Infraestrutura, Visão Operacional, Performance do Sistema.

Métricas de negócio: módulo `metrics` do backend (eventos de domínio → contadores
por tenant) + gauges do `BusinessMetricsService`. Detalhes, alertas, gotchas e
troubleshooting: ver o README acima.
