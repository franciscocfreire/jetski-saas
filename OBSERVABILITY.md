# Jetski SaaS - Observability Guide

Documentação completa do sistema de observabilidade do Jetski SaaS.

## 📋 Índice

- [Visão Geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Componentes](#componentes)
- [Métricas Customizadas](#métricas-customizadas)
- [Dashboards Grafana](#dashboards-grafana)
- [Alertas Prometheus](#alertas-prometheus)
- [Logs Estruturados](#logs-estruturados)
- [Health Checks](#health-checks)
- [Quick Start](#quick-start)
- [Troubleshooting](#troubleshooting)

---

## Visão Geral

O Jetski SaaS implementa um stack completo de observabilidade seguindo as três pilares:

1. **Métricas** → Prometheus + Grafana
2. **Logs** → Logback Structured JSON + Loki (ready)
3. **Traces** → Correlation ID via MDC (Distributed tracing ready for Jaeger/Tempo)

### Stack Tecnológico

- **Backend**: Spring Boot 3.3 + Micrometer + Actuator
- **Metrics Storage**: Prometheus v2.47.0
- **Visualization**: Grafana 10.1.5
- **Log Aggregation**: Loki 2.9.2
- **Orchestration**: Docker Compose

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│               Jetski SaaS Backend (Spring Boot)              │
│                                                               │
│  ┌────────────────────┐  ┌─────────────────────────────┐   │
│  │ RequestCorrelation │  │   Business Metrics          │   │
│  │ Filter             │  │   - Rentals                 │   │
│  │ (Trace ID)         │  │   - Reservations            │   │
│  └────────────────────┘  │   - Auth                    │   │
│           │              │   - OPA                     │   │
│           │              │   - Multi-tenant            │   │
│           ▼              └─────────────────────────────┘   │
│  ┌────────────────────┐              │                     │
│  │ MDC Context        │              │                     │
│  │ - traceId          │              │                     │
│  │ - tenantId         │              ▼                     │
│  │ - userId           │  ┌─────────────────────────────┐   │
│  └────────────────────┘  │   Health Indicators         │   │
│           │              │   - OPA                     │   │
│           ▼              │   - Keycloak                │   │
│  ┌────────────────────┐  └─────────────────────────────┘   │
│  │ Structured Logs    │              │                     │
│  │ (JSON)             │              │                     │
│  └────────────────────┘              │                     │
│                                       │                     │
│  Endpoints:                           │                     │
│  - /api/actuator/prometheus ──────────┤                     │
│  - /api/actuator/health ──────────────┘                     │
└─────────────────────────────────────────────────────────────┘
                    │                         │
                    │ HTTP Scrape (15s)       │ HTTP Health Check
                    ▼                         ▼
         ┌──────────────────┐      ┌──────────────────┐
         │   Prometheus     │      │   Monitoring     │
         │   :9090          │      │   External       │
         │                  │      └──────────────────┘
         │ - Metrics Store  │
         │ - Alert Engine   │
         │ - 30d Retention  │
         └──────────────────┘
                    │
                    │ Datasource
                    ▼
         ┌──────────────────┐      ┌──────────────────┐
         │    Grafana       │◄─────│      Loki        │
         │    :3000         │      │      :3100       │
         │                  │      │                  │
         │ - Dashboards (2) │      │ - Log Storage    │
         │ - Visualization  │      │ - 7d Retention   │
         └──────────────────┘      └──────────────────┘
```

---

## Componentes

### 1. Backend (Spring Boot)

#### Correlation ID Filter

**Arquivo**: `RequestCorrelationFilter.java`

Injeta um `traceId` único em cada requisição HTTP para rastreamento end-to-end.

```java
// Automático via Filter
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
```

**MDC Keys disponíveis**:
- `traceId` - UUID único da requisição
- `tenantId` - ID do tenant (multi-tenant)
- `userId` - ID do usuário autenticado

#### Business Metrics Service

**Arquivo**: `BusinessMetrics.java`

Expõe 12 métricas customizadas de negócio:

| Métrica | Tipo | Descrição |
|---------|------|-----------|
| `jetski_rental_checkin_total` | Counter | Total de check-ins |
| `jetski_rental_checkout_total` | Counter | Total de check-outs |
| `jetski_rental_duration_seconds` | Histogram | Duração das locações |
| `jetski_reservation_total` | Counter | Total de reservas |
| `jetski_reservation_confirmed_total` | Counter | Reservas confirmadas |
| `jetski_reservation_cancelled_total` | Counter | Reservas canceladas |
| `jetski_auth_login_success_total` | Counter | Logins bem-sucedidos |
| `jetski_auth_login_failure_total` | Counter | Falhas de login |
| `jetski_auth_token_refresh_total` | Counter | Renovações de token |
| `jetski_tenant_context_switch_total` | Counter | Trocas de contexto de tenant |
| `jetski_opa_decision_total` | Counter | Decisões de autorização OPA |
| `jetski_opa_decision_duration_seconds` | Histogram | Latência das decisões OPA |

#### Custom Health Indicators

**OpaHealthIndicator** (`OpaHealthIndicator.java`):
- Verifica conectividade com OPA
- Status: UP/DOWN

**KeycloakHealthIndicator** (`KeycloakHealthIndicator.java`):
- Verifica status do Keycloak
- Status: UP/DOWN

---

### 2. Prometheus

**Porta**: 9090
**URL**: http://localhost:9090

**Configuração**:
- Scrape interval: 15 segundos
- Evaluation interval: 15 segundos
- Retention: 30 dias
- Targets: `jetski-backend`, `prometheus`, `grafana`

**Endpoints úteis**:
- Targets: http://localhost:9090/targets
- Alerts: http://localhost:9090/alerts
- Rules: http://localhost:9090/rules
- Graph: http://localhost:9090/graph

---

### 3. Grafana

**Porta**: 3000
**URL**: http://localhost:3000
**Credenciais**: `admin` / `admin`

**Datasources auto-provisionados**:
- Prometheus (default)
- Loki

**Dashboards**:
- Jetski SaaS - Overview
- Jetski SaaS - Business Metrics

---

### 4. Loki

**Porta**: 3100
**URL**: http://localhost:3100

**Configuração**:
- Retention: 7 dias
- Storage: Filesystem local
- Ingestion rate: 10 MB/s

---

## Métricas Customizadas

### Rental Metrics

```java
// Incrementar check-in
businessMetrics.incrementRentalCheckIn(tenantId, jetskiId);

// Incrementar check-out
businessMetrics.incrementRentalCheckOut(tenantId, jetskiId);

// Registrar duração
businessMetrics.recordRentalDuration(tenantId, Duration.ofMinutes(120));
```

### Reservation Metrics

```java
businessMetrics.incrementReservation(tenantId);
businessMetrics.incrementReservationConfirmed(tenantId);
businessMetrics.incrementReservationCancelled(tenantId);
```

### Authentication Metrics

```java
businessMetrics.incrementLoginSuccess(tenantId, username);
businessMetrics.incrementLoginFailure(tenantId, username);
businessMetrics.incrementTokenRefresh(tenantId);
```

### OPA Metrics

```java
businessMetrics.recordOpaDecision(tenantId, "allow", Duration.ofMillis(50));
businessMetrics.recordOpaDecision(tenantId, "deny", Duration.ofMillis(30));
```

### Multi-tenant Metrics

```java
businessMetrics.incrementTenantContextSwitch(fromTenant, toTenant);
```

---

## Dashboards Grafana

### Dashboard 1: Overview

**Painéis**:
1. **Request Rate** - Taxa de requisições por segundo
2. **Error Rate** - Taxa de erros 5xx e 4xx
3. **Response Time** - Latência P95 e P50
4. **Active Requests** - Requisições ativas
5. **Uptime** - Tempo de atividade do serviço
6. **JVM Memory** - Uso de memória heap
7. **Database Connections** - Pool de conexões
8. **GC Pause Time** - Tempo de pausa do GC

**Queries importantes**:

```promql
# Taxa de requisições
rate(http_server_requests_seconds_count{application="jetski-api"}[5m])

# Taxa de erros
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) /
sum(rate(http_server_requests_seconds_count[5m])) * 100

# P95 Latência
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
```

---

### Dashboard 2: Business Metrics

**Painéis**:
1. **Total Rentals** - Check-ins totais
2. **Total Check-outs** - Check-outs totais
3. **Total Reservations** - Reservas totais
4. **Reservation Cancellations** - Cancelamentos
5. **Check-in Rate** - Taxa de check-ins por hora
6. **Reservation Rate** - Taxa de reservas por hora
7. **Rental Duration** - Distribuição de duração (heatmap)
8. **Auth Success Rate** - Taxa de sucesso de login
9. **OPA Decisions** - Decisões por resultado
10. **OPA Latency** - Latência P95/P50
11. **Tenant Context Switches** - Trocas de tenant

**Queries importantes**:

```promql
# Check-ins por tenant
sum by (tenant_id) (jetski_rental_checkin_total)

# Taxa de cancelamento
jetski_reservation_cancelled_total / jetski_reservation_total * 100

# Taxa de falha de autenticação
rate(jetski_auth_login_failure_total[5m]) /
(rate(jetski_auth_login_success_total[5m]) + rate(jetski_auth_login_failure_total[5m]))
```

---

## Alertas Prometheus

### Alertas Críticos

#### 1. HighErrorRate

```yaml
alert: HighErrorRate
expr: (sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) /
       sum(rate(http_server_requests_seconds_count[5m]))) * 100 > 5
for: 5m
severity: critical
```

**Ação**: Investigar logs, verificar dependências (DB, OPA, Keycloak)

#### 2. ServiceDown

```yaml
alert: ServiceDown
expr: up{job="jetski-backend"} == 0
for: 1m
severity: critical
```

**Ação**: Verificar se o backend está rodando, checar logs de startup

---

### Alertas de Warning

#### 3. HighResponseTime

```yaml
alert: HighResponseTime
expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2
for: 5m
severity: warning
```

**Ação**: Analisar slow queries, otimizar endpoints

#### 4. HighMemoryUsage

```yaml
alert: HighMemoryUsage
expr: (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100 > 85
for: 5m
severity: warning
```

**Ação**: Analisar heap dump, verificar memory leaks

#### 5. DatabaseConnectionPoolLow

```yaml
alert: DatabaseConnectionPoolLow
expr: (hikaricp_connections_active / hikaricp_connections_max) * 100 > 80
for: 2m
severity: warning
```

**Ação**: Aumentar pool size ou otimizar queries

#### 6. HighOPALatency

```yaml
alert: HighOPALatency
expr: histogram_quantile(0.95, rate(jetski_opa_decision_duration_seconds_bucket[5m])) > 0.5
for: 5m
severity: warning
```

**Ação**: Verificar OPA, otimizar policies

#### 7. HighAuthFailureRate

```yaml
alert: HighAuthFailureRate
expr: rate(jetski_auth_login_failure_total[5m]) /
      (rate(jetski_auth_login_success_total[5m]) + rate(jetski_auth_login_failure_total[5m])) * 100 > 20
for: 5m
severity: warning
```

**Ação**: Possível ataque, verificar IPs suspeitos

#### 8. HighGCPauseTime

```yaml
alert: HighGCPauseTime
expr: rate(jvm_gc_pause_seconds_sum[5m]) > 0.1
for: 5m
severity: warning
```

**Ação**: Tuning de GC, ajustar heap size

---

## Logs Estruturados

### Configuração Logback

**Arquivo**: `logback-spring.xml`

Logs em formato JSON com campos estruturados:

```json
{
  "timestamp": "2025-10-26T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.jetski.locacoes.api.LocacaoController",
  "message": "Check-in realizado com sucesso",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "tenant-123",
  "userId": "user-456",
  "thread": "http-nio-8090-exec-1"
}
```

### MDC Keys

Configurados automaticamente pelo `RequestCorrelationFilter`:

- **traceId**: UUID da requisição (sempre presente)
- **tenantId**: ID do tenant (quando disponível)
- **userId**: ID do usuário (quando autenticado)

---

## Health Checks

### Endpoint Padrão

```bash
GET /api/actuator/health
```

**Resposta**:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "keycloak": {"status": "UP"},
    "opa": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### Health Detalhado

```bash
GET /api/actuator/health/keycloak
GET /api/actuator/health/opa
```

---

## Quick Start

### 1. Iniciar Monitoring Stack

```bash
# A partir do diretório raiz do projeto
cd /home/franciscocfreire/repos/jetski

# Iniciar stack
./infra/monitoring-stack.sh start

# Verificar status
./infra/monitoring-stack.sh status
```

### 2. Acessar Serviços

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Loki**: http://localhost:3100
- **Backend Metrics**: http://localhost:8090/api/actuator/prometheus
- **Backend Health**: http://localhost:8090/api/actuator/health

### 3. Visualizar Dashboards

1. Acesse http://localhost:3000
2. Login: `admin` / `admin`
3. Navegue até **Dashboards** → **Jetski SaaS**
4. Escolha:
   - **Overview** - Métricas técnicas
   - **Business Metrics** - Métricas de negócio

### 4. Verificar Alertas

1. Acesse http://localhost:9090/alerts
2. Verifique status dos alertas (Inactive/Pending/Firing)

---

## Troubleshooting

### Problema: Prometheus não está coletando métricas

**Sintomas**: No data nos dashboards do Grafana

**Soluções**:
1. Verificar se o backend está rodando: http://localhost:8090/api/actuator/prometheus
2. Verificar targets no Prometheus: http://localhost:9090/targets
3. Verificar logs do Prometheus: `docker logs jetski-prometheus`
4. No Linux, use `172.17.0.1` ao invés de `host.docker.internal`

### Problema: Grafana não mostra dashboards

**Sintomas**: Pasta "Jetski SaaS" vazia

**Soluções**:
1. Verificar se os arquivos JSON estão em `infra/monitoring/grafana/provisioning/dashboards/json/`
2. Reiniciar Grafana: `docker restart jetski-grafana`
3. Verificar logs: `docker logs jetski-grafana`

### Problema: Loki não inicia

**Sintomas**: Container reiniciando constantemente

**Soluções**:
1. Verificar permissões: Loki roda como root (user: root no docker-compose)
2. Verificar logs: `docker logs jetski-loki`
3. Verificar configuração: `infra/monitoring/loki/loki-config.yml`

### Problema: Alertas não estão disparando

**Sintomas**: Alerts sempre em "Inactive"

**Soluções**:
1. Verificar regras carregadas: http://localhost:9090/rules
2. Simular condições de alerta (ex: causar erros 5xx)
3. Verificar `for` duration nas regras (pode levar alguns minutos)
4. Verificar logs: `docker logs jetski-prometheus`

---

## Comandos Úteis

### Monitoring Stack

```bash
# Iniciar
./infra/monitoring-stack.sh start

# Parar
./infra/monitoring-stack.sh stop

# Reiniciar
./infra/monitoring-stack.sh restart

# Status
./infra/monitoring-stack.sh status

# Logs
./infra/monitoring-stack.sh logs

# Limpar volumes (⚠️ deleta dados)
./infra/monitoring-stack.sh clean
```

### Docker

```bash
# Ver containers
docker ps

# Logs individuais
docker logs jetski-prometheus
docker logs jetski-grafana
docker logs jetski-loki

# Restart individual
docker restart jetski-prometheus
docker restart jetski-grafana
docker restart jetski-loki
```

### Prometheus Queries

```bash
# Testar query
curl -G 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=up{job="jetski-backend"}'

# Listar métricas disponíveis
curl http://localhost:8090/api/actuator/prometheus | grep jetski_
```

---

## Próximos Passos

### Curto Prazo

1. **Integrar Promtail** para enviar logs ao Loki
2. **Configurar Alertmanager** para notificações
3. **Criar dashboard de logs** com Loki

### Médio Prazo

4. **Adicionar distributed tracing** (Jaeger/Tempo)
5. **Exporters adicionais** (PostgreSQL, Redis)
6. **SLOs/SLIs** para métricas de negócio

### Longo Prazo

7. **Anomaly detection** com machine learning
8. **Auto-scaling** baseado em métricas
9. **Chaos engineering** para resiliência

---

## Referências

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/grafana/latest/)
- [Loki Documentation](https://grafana.com/docs/loki/latest/)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [Micrometer Documentation](https://micrometer.io/docs)

---

**Versão**: 1.0.0
**Última atualização**: 2025-10-26
**Autor**: Jetski SaaS Team
