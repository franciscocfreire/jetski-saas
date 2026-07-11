# Newman CLI - Guia de Execução

Este guia explica como executar a collection Postman via Newman CLI para testes automatizados.

## 📋 Pré-requisitos

### 1. Instalar Newman
```bash
npm install -g newman
npm install -g newman-reporter-htmlextra  # Reporter HTML aprimorado
```

### 2. Serviços Rodando
```bash
# Terminal 1: PostgreSQL (via Docker)
docker-compose up -d postgres

# Terminal 2: Keycloak
cd infra/keycloak-setup
./setup-keycloak-local.sh

# Terminal 3: OPA
cd infra
./start-opa-local.sh

# Terminal 4: Backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

### 3. Verificar Saúde dos Serviços
```bash
# Backend
curl http://localhost:8090/api/actuator/health
# Esperado: {"status":"UP"}

# Keycloak
curl http://localhost:8081/health
# Esperado: {"status":"UP"}

# OPA
curl http://localhost:8181/health
# Esperado: {}
```

## 🚀 Executar Collection

### Opção 1: Execução Básica (CLI)
```bash
newman run postman/Jetski-Jornadas.postman_collection.json \
  -e postman/environments/Local.postman_environment.json
```

**Output Esperado:**
```
✓ 60+ assertions passing
✓ All requests completed
✓ Average response time < 100ms
```

### Opção 2: Com Relatórios (JSON + HTML)
```bash
newman run postman/Jetski-Jornadas.postman_collection.json \
  -e postman/environments/Local.postman_environment.json \
  --reporters cli,json,htmlextra \
  --reporter-json-export results.json \
  --reporter-htmlextra-export report.html
```

**Visualizar Relatório:**
```bash
open report.html  # macOS
xdg-open report.html  # Linux
start report.html  # Windows
```

### Opção 3: CI/CD Mode (Sem Cores, Exit Code)
```bash
newman run postman/Jetski-Jornadas.postman_collection.json \
  -e postman/environments/Local.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export results.json \
  --no-color \
  --bail  # Para na primeira falha
```

**Exit Codes:**
- `0`: Todos os testes passaram ✅
- `1`: Algum teste falhou ❌

### Opção 4: Executar Apenas Uma Jornada
```bash
# Apenas Fechamento Diário
newman run postman/Jetski-Jornadas.postman_collection.json \
  -e postman/environments/Local.postman_environment.json \
  --folder "1️⃣ Jornada: Fechamento Diário Completo"

# Apenas Comissões
newman run postman/Jetski-Jornadas.postman_collection.json \
  -e postman/environments/Local.postman_environment.json \
  --folder "2️⃣ Jornada: Comissões - Do Cálculo ao Pagamento"
```

### Opção 5: Múltiplas Iterações (Load Test)
```bash
newman run postman/Jetski-Jornadas.postman_collection.json \
  -e postman/environments/Local.postman_environment.json \
  -n 10  # Executa 10 vezes
```

## 🔧 Troubleshooting

### Erro: "401 Unauthorized" no Auth
**Sintoma:**
```
POST http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token [401 Unauthorized]
error: 'invalid_client'
```

**Solução:**
```bash
# 1. Verificar Keycloak está rodando
curl http://localhost:8081/health

# 2. Reconfigurar Keycloak
cd infra/keycloak-setup
./setup-keycloak-local.sh

# 3. Verificar credenciais no environment
cat postman/environments/Local.postman_environment.json | grep keycloak

# 4. Testar manualmente
curl -X POST http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=jetski-local" \
  -d "username=gerente.teste@example.com" \
  -d "password=senha123"
```

### Erro: "404 Not Found" nas APIs
**Solução:**
```bash
# Verificar backend está rodando
curl http://localhost:8090/api/actuator/health

# Verificar logs do backend
tail -f /tmp/backend-local-test.log
```

### Erro: "newman: command not found"
**Solução:**
```bash
# Instalar Newman globalmente
npm install -g newman

# Verificar instalação
newman --version
```

### Erro: "could not find html reporter"
**Solução:**
```bash
# Instalar reporter HTML
npm install -g newman-reporter-htmlextra

# Usar reporter básico (não recomendado)
newman run ... --reporters cli,json
```

## 📊 Interpretar Resultados

### Exemplo de Saída Bem-Sucedida
```
┌─────────────────────────┬─────────────────┬─────────────────┐
│                         │        executed │          failed │
├─────────────────────────┼─────────────────┼─────────────────┤
│              iterations │               1 │               0 │
├─────────────────────────┼─────────────────┼─────────────────┤
│                requests │              19 │               0 │
├─────────────────────────┼─────────────────┼─────────────────┤
│            test-scripts │              38 │               0 │
├─────────────────────────┼─────────────────┼─────────────────┤
│      prerequest-scripts │              21 │               0 │
├─────────────────────────┼─────────────────┼─────────────────┤
│              assertions │              80 │               0 │  ← 80 testes passaram!
├─────────────────────────┴─────────────────┴─────────────────┤
│ total run duration: 3.2s                                    │
├─────────────────────────────────────────────────────────────┤
│ average response time: 45ms                                 │
└─────────────────────────────────────────────────────────────┘
```

### Métricas Importantes
- **Assertions:** Número total de testes
- **Failed:** Deve ser 0 para sucesso completo
- **Average response time:** Deve ser < 300ms (RNF)
- **Total run duration:** Deve ser < 10s

## 🔄 Integração CI/CD

### GitHub Actions
```yaml
name: API Tests

on: [push, pull_request]

jobs:
  api-tests:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: jetski_test
          POSTGRES_USER: jetski
          POSTGRES_PASSWORD: dev123
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v3

      - name: Set up Java 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'

      - name: Start Backend
        run: |
          SPRING_PROFILES_ACTIVE=test mvn spring-boot:run &
          sleep 30

      - name: Install Newman
        run: npm install -g newman newman-reporter-htmlextra

      - name: Run Postman Tests
        run: |
          newman run postman/Jetski-Jornadas.postman_collection.json \
            -e postman/environments/Local.postman_environment.json \
            --reporters cli,json,htmlextra \
            --reporter-json-export results.json \
            --reporter-htmlextra-export report.html

      - name: Upload Test Results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: newman-results
          path: |
            results.json
            report.html
```

### GitLab CI
```yaml
api-tests:
  stage: test
  image: node:18
  services:
    - postgres:16-alpine
  script:
    - npm install -g newman newman-reporter-htmlextra
    - mvn spring-boot:run &
    - sleep 30
    - newman run postman/Jetski-Jornadas.postman_collection.json \
        -e postman/environments/Local.postman_environment.json \
        --reporters cli,json \
        --reporter-json-export results.json
  artifacts:
    reports:
      junit: results.json
    paths:
      - results.json
```

## 📈 Análise de Resultados JSON

```bash
# Ver total de testes passados
jq '.run.stats.assertions.total' results.json

# Ver testes falhados
jq '.run.failures' results.json

# Ver tempo médio de resposta
jq '.run.timings.responseAverage' results.json

# Ver requisições mais lentas
jq -r '.run.executions[] | select(.response.responseTime > 100) | .item.name' results.json
```

## 🎯 Smoke Tests Rápidos

Para validação rápida após deploy:

```bash
#!/bin/bash
# smoke-test.sh

echo "🚀 Executando smoke tests..."

newman run postman/Jetski-Jornadas.postman_collection.json \
  -e postman/environments/Local.postman_environment.json \
  --folder "0️⃣ Setup - Autenticação" \
  --bail \
  --silent

if [ $? -eq 0 ]; then
  echo "✅ Auth OK"
else
  echo "❌ Auth falhou"
  exit 1
fi

newman run postman/Jetski-Jornadas.postman_collection.json \
  -e postman/environments/Local.postman_environment.json \
  --folder "1️⃣ Jornada: Fechamento Diário Completo" \
  --bail \
  --silent

if [ $? -eq 0 ]; then
  echo "✅ Fechamento OK"
else
  echo "❌ Fechamento falhou"
  exit 1
fi

echo "✅ Todos os smoke tests passaram!"
```

## 📚 Recursos Adicionais

- [Newman Documentation](https://learning.postman.com/docs/running-collections/using-newman-cli/command-line-integration-with-newman/)
- [Newman Reporters](https://www.npmjs.com/search?q=newman-reporter)
- [Postman Collection SDK](https://www.postmanlabs.com/postman-collection/)

## 🆘 Suporte

Se encontrar problemas:
1. Verificar logs do backend: `tail -f /tmp/backend-local-test.log`
2. Verificar Keycloak: `curl http://localhost:8081/health`
3. Executar testes unitários: `mvn test`
4. Consultar documentação: `postman/JORNADAS_COMPLETAS.md`
