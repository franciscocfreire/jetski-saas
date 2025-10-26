# Jetski SaaS - Monitoring Stack

Stack completo de observabilidade para Jetski SaaS com Prometheus, Grafana e Loki.

## 📊 Componentes

### Prometheus (Métricas)
- **URL**: http://localhost:9090
- **Função**: Coleta e armazena métricas do backend
- **Scrape Interval**: 15 segundos
- **Retenção**: 30 dias

### Grafana (Visualização)
- **URL**: http://localhost:3000
- **Credenciais**: `admin` / `admin`
- **Função**: Dashboards e visualização de métricas/logs
- **Datasources**: Prometheus + Loki (auto-provisionados)

### Loki (Logs)
- **URL**: http://localhost:3100
- **Função**: Agregação e indexação de logs
- **Retenção**: 7 dias

## 🚀 Quick Start

### Iniciar Stack Completo

```bash
# A partir do diretório infra/
./monitoring-stack.sh start
```

Ou diretamente com docker-compose:

```bash
docker-compose -f docker-compose-monitoring.yml up -d
```

### Parar Stack

```bash
./monitoring-stack.sh stop
```

### Ver Status

```bash
./monitoring-stack.sh status
```

### Ver Logs

```bash
./monitoring-stack.sh logs
```

### Limpar Volumes (⚠️ Deleta Dados)

```bash
./monitoring-stack.sh clean
```

## 📈 Métricas Disponíveis

### Métricas de Negócio (Jetski)

**Rentals:**
- `jetski_rental_checkin_total` - Total de check-ins
- `jetski_rental_checkout_total` - Total de check-outs
- `jetski_rental_duration_seconds` - Duração das locações (histogram)

**Reservations:**
- `jetski_reservation_total` - Total de reservas
- `jetski_reservation_confirmed_total` - Reservas confirmadas
- `jetski_reservation_cancelled_total` - Reservas canceladas

**Authentication:**
- `jetski_auth_login_success_total` - Logins bem-sucedidos
- `jetski_auth_login_failure_total` - Falhas de login
- `jetski_auth_token_refresh_total` - Renovações de token

**Multi-tenant:**
- `jetski_tenant_context_switch_total` - Trocas de contexto de tenant

**OPA:**
- `jetski_opa_decision_total` - Decisões de autorização
- `jetski_opa_decision_duration_seconds` - Duração das decisões

### Métricas Padrão (Spring Boot)

- `http_server_requests_seconds` - Latência HTTP
- `jvm_memory_used_bytes` - Uso de memória JVM
- `jvm_gc_pause_seconds` - Pausas do Garbage Collector
- `hikaricp_connections_active` - Conexões de banco ativas
- E muitas outras...

## 🔍 Queries Prometheus Úteis

### Taxa de Requisições HTTP

```promql
rate(http_server_requests_seconds_count[5m])
```

### Percentil 95 de Latência

```promql
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
```

### Taxa de Erros

```promql
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
```

### Check-ins por Tenant

```promql
sum by (tenant_id) (jetski_rental_checkin_total)
```

### Decisões OPA por Resultado

```promql
sum by (decision) (rate(jetski_opa_decision_total[5m]))
```

## 📊 Acessar Grafana

1. Abrir http://localhost:3000
2. Login: `admin` / `admin`
3. Datasources já configurados:
   - **Prometheus** (métricas)
   - **Loki** (logs)

## 🔧 Configurações

### Prometheus

Arquivo: `prometheus/prometheus.yml`

- Scrape do backend: `http://host.docker.internal:8090/api/actuator/prometheus`
- Interval: 15s
- Labels automáticos: `application`, `environment`

### Grafana

Provisioning automático:
- **Datasources**: `grafana/provisioning/datasources/datasources.yml`
- **Dashboards**: `grafana/provisioning/dashboards/` (vazio - criar manualmente)

### Loki

Arquivo: `loki/loki-config.yml`

- Retenção: 7 dias
- Storage: filesystem local
- Ingestion rate: 10MB/s

## 🐛 Troubleshooting

### Prometheus não consegue scraping do backend

**Problema**: "context deadline exceeded" nos targets

**Solução**:
1. Verificar se o backend está rodando em `localhost:8090`
2. Verificar se `/api/actuator/prometheus` está acessível
3. No Mac/Windows: `host.docker.internal` funciona
4. No Linux: usar `172.17.0.1` ou `--network host`

### Grafana não mostra métricas

**Problema**: "No data" nos painéis

**Solução**:
1. Verificar se Prometheus está coletando dados: http://localhost:9090/targets
2. Verificar se datasource está configurado corretamente no Grafana
3. Ajustar time range no dashboard (últimos 15 minutos)

### Loki não recebe logs

**Problema**: Logs não aparecem no Grafana

**Solução**:
1. Backend precisa enviar logs para Loki (ainda não configurado)
2. Alternativa: usar Promtail ou Fluent Bit para coletar logs
3. Por enquanto, Loki está pronto mas sem ingestão ativa

## 📝 Próximos Passos

1. **Criar Dashboards Grafana** para visualizar métricas
2. **Configurar Alertas** no Prometheus (high error rate, services down)
3. **Integrar Promtail** para enviar logs ao Loki
4. **Criar Dashboard de Logs** no Grafana com Loki

## 🔗 Links Úteis

- [Prometheus Docs](https://prometheus.io/docs/)
- [Grafana Docs](https://grafana.com/docs/grafana/latest/)
- [Loki Docs](https://grafana.com/docs/loki/latest/)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
