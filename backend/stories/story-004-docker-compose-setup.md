---
story_id: STORY-004
epic: EPIC-01
title: Setup Docker Compose (PostgreSQL + Redis + Keycloak)
status: DONE
priority: HIGH
estimate: 3
assignee: Claude
started_at: 2025-01-15
completed_at: 2025-01-15
tags: [backend, infra, docker, postgresql, keycloak, redis]
dependencies: []
---

# STORY-004: Setup Docker Compose (PostgreSQL + Redis + Keycloak)

## Como
Backend Developer

## Quero
Ambiente de desenvolvimento local completo rodando com um único comando

## Para que
Qualquer desenvolvedor consiga configurar o ambiente em menos de 10 minutos

## Critérios de Aceite

- [ ] **CA1:** `docker-compose up` sobe PostgreSQL 16 + Redis + Keycloak 26
- [ ] **CA2:** PostgreSQL expõe porta 5432 com database `jetski_dev` criado
- [ ] **CA3:** Keycloak expõe porta 8080 com realm `jetski-saas` pré-configurado
- [ ] **CA4:** Redis expõe porta 6379
- [ ] **CA5:** Volumes persistem dados (banco e Keycloak) entre reinicializações
- [ ] **CA6:** README.md com instruções de setup atualizadas

## Tarefas Técnicas

- [ ] Criar `docker-compose.yml` na raiz do projeto
- [ ] Configurar serviço `postgres` (imagem postgres:16)
- [ ] Configurar serviço `redis` (imagem redis:7-alpine)
- [ ] Configurar serviço `keycloak` (imagem quay.io/keycloak/keycloak:26.0)
- [ ] Criar script `scripts/init-keycloak.sh` para configurar realm
- [ ] Criar volumes para persistência: `postgres_data`, `keycloak_data`
- [ ] Testar: `docker-compose up`, validar que todos os serviços sobem
- [ ] Atualizar `/docs/setup-local.md`

## Definição de Pronto (DoD)

- [ ] Docker Compose funciona em Linux, macOS e Windows
- [ ] Documentação permite setup em < 10 minutos
- [ ] Serviços health check OK

## Notas Técnicas

### docker-compose.yml

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16
    container_name: jetski-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: jetski
      POSTGRES_PASSWORD: dev123
      POSTGRES_DB: jetski_dev
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U jetski"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: jetski-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    container_name: jetski-keycloak
    command: start-dev
    ports:
      - "8080:8080"
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak_dev
      KC_DB_USERNAME: jetski
      KC_DB_PASSWORD: dev123
      KC_HOSTNAME_STRICT: false
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - keycloak_data:/opt/keycloak/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
      interval: 30s
      timeout: 10s
      retries: 5

volumes:
  postgres_data:
  keycloak_data:
```

## Links

- **Epic:** [EPIC-01](../../stories/epics/epic-01-multi-tenant-foundation.md)

## Changelog

- 2025-01-15: História criada
