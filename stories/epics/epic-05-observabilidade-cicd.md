---
epic_id: EPIC-05
title: Observabilidade e CI/CD
status: TODO
priority: MEDIUM
start_date: 2025-03-19
target_date: 2025-03-25
owner: Team DevOps
dependencies: [EPIC-01, EPIC-02]
---

# EPIC-05: Observabilidade e CI/CD

## Objetivo

Estabelecer observabilidade completa (logs, métricas, traces) com segregação por `tenant_id`, e pipeline de CI/CD automatizado com testes, análise de código e deploy.

## Escopo

### Incluído
- [ ] OpenTelemetry: traces, metrics, logs
- [ ] Logging estruturado (JSON) com `tenant_id` e `traceId`
- [ ] Spring Boot Actuator: health, metrics, prometheus
- [ ] Rate limiting por tenant (Redis)
- [ ] Prometheus + Grafana para métricas
- [ ] ELK stack ou CloudWatch Logs para logs centralizados
- [ ] Dashboards: performance por tenant, erros, latência
- [ ] GitHub Actions: build, test, SCA, SAST
- [ ] Docker image build e push para registry
- [ ] Deploy automático para ambiente de desenvolvimento
- [ ] SonarQube para análise de código
- [ ] OWASP Dependency Check
- [ ] Versionamento semântico automático

### Excluído (Out of Scope)
- Deploy em produção (EKS) - será manual no MVP
- Distributed tracing complexo (será evolução futura)
- APM comercial (Datadog, New Relic) - usar ferramentas OSS

## Histórias Relacionadas

### Backend
- `backend/stories/story-023-opentelemetry-setup.md` (8 pts)
- `backend/stories/story-024-structured-logging.md` (5 pts)
- `backend/stories/story-025-rate-limiting-redis.md` (5 pts)
- `backend/stories/story-026-actuator-metrics.md` (3 pts)

### Infra/DevOps
- `infra/stories/story-001-github-actions-pipeline.md` (8 pts)
- `infra/stories/story-002-docker-build.md` (5 pts)
- `infra/stories/story-003-sonarqube-integration.md` (5 pts)
- `infra/stories/story-004-prometheus-grafana.md` (8 pts)

**Total estimado:** 47 story points (~2-3 sprints)

## Critérios de Aceite

### Observabilidade
- [ ] Todos os logs incluem `tenant_id`, `traceId`, `spanId`, `userId`
- [ ] Logs são estruturados em JSON para fácil parsing
- [ ] Métricas Prometheus incluem label `tenant_id`
- [ ] Dashboard Grafana mostra: requisições/s, latência P50/P95/P99, taxa de erro, por tenant
- [ ] Traces distribuídos funcionam end-to-end (request → database)
- [ ] Rate limit bloqueia requisições excessivas por tenant (ex: 1000 req/min)

### CI/CD
- [ ] Pipeline executa: compile → test → SCA → SAST → build → push
- [ ] Testes unitários e de integração rodam automaticamente em cada PR
- [ ] SonarQube reporta cobertura > 80% e sem code smells críticos
- [ ] OWASP Dependency Check não encontra vulnerabilidades críticas
- [ ] Docker image é buildada e enviada para registry (Docker Hub ou ECR)
- [ ] Versionamento segue SemVer (major.minor.patch)
- [ ] Deploy em dev é automático após merge na branch `develop`

## Riscos

**Risco Médio:**
- **Overhead de traces**: OpenTelemetry pode impactar performance.
  - **Mitigação**: Sampling rate configurável (ex: 10% em prod, 100% em dev)

**Risco Baixo:**
- **Custo de armazenamento de logs**: Logs crescem rapidamente.
  - **Mitigação**: Retenção de 30 dias, compressão, sampling

## Dependências

- Prometheus/Grafana (pode ser Docker Compose local ou cloud)
- GitHub Actions (incluído no GitHub)
- SonarQube (pode ser SonarCloud gratuito para OSS)

## Métricas de Sucesso

- Tempo médio para detectar erro crítico em prod: < 5 minutos
- Tempo do pipeline CI/CD: < 10 minutos
- Cobertura de código mantida > 80%

## Notas

### Structured Logging (Exemplo)

```json
{
  "timestamp": "2025-03-20T14:30:00.123Z",
  "level": "INFO",
  "logger": "com.jetski.service.LocacaoService",
  "message": "Check-out realizado com sucesso",
  "tenant_id": "acme-corp",
  "trace_id": "1234567890abcdef",
  "span_id": "fedcba0987654321",
  "user_id": "user@acme.com",
  "locacao_id": "uuid-locacao",
  "jetski_id": "uuid-jetski",
  "valor_calculado": 450.00
}
```

### Métricas Prometheus (Exemplo)

```
# Request rate por tenant
http_requests_total{tenant="acme-corp", endpoint="/api/v1/locacoes", method="POST"} 1234

# Latência
http_request_duration_seconds_bucket{tenant="acme-corp", le="0.1"} 950
http_request_duration_seconds_bucket{tenant="acme-corp", le="0.3"} 1200

# Erros
http_requests_errors_total{tenant="acme-corp", status="500"} 5
```

### GitHub Actions Workflow (Esboço)

```yaml
name: CI/CD Pipeline

on:
  pull_request:
  push:
    branches: [develop, main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: mvn clean test
      - run: mvn verify  # Integration tests with Testcontainers

  sonarqube:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - run: mvn sonar:sonar -Dsonar.token=${{ secrets.SONAR_TOKEN }}

  build:
    needs: [test, sonarqube]
    runs-on: ubuntu-latest
    steps:
      - run: mvn clean package
      - run: docker build -t jetski-api:${{ github.sha }} .
      - run: docker push jetski-api:${{ github.sha }}

  deploy-dev:
    if: github.ref == 'refs/heads/develop'
    needs: build
    runs-on: ubuntu-latest
    steps:
      - run: kubectl set image deployment/jetski-api jetski-api=jetski-api:${{ github.sha }}
```

## Changelog

- 2025-01-15: Épico criado
