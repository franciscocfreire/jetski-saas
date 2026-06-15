# Jetski SaaS - Sistema de Gestão de Locações

Sistema SaaS B2B multi-tenant para gestão completa de locações de jetski, incluindo: controle de frota, agenda, operação com fotos, manutenção, abastecimento, comissões e fechamentos financeiros.

## 🏗️ Arquitetura

- **Backend:** Spring Boot 3.3 + Java 21 + PostgreSQL 16
- **Frontend:** Next.js 15 + React 19 + TypeScript + shadcn/ui
- **Mobile:** Kotlin Multiplatform Mobile (KMM)
- **Auth:** Keycloak 26 (OIDC + PKCE)
- **Cache:** Redis 7
- **Storage:** local (`LocalStorageController`); S3 planejado
- **Modularidade:** Spring Modulith 1.2 (fronteiras de módulo verificadas em build)

## 🚀 Quick Start

### Pré-requisitos

- Docker & Docker Compose
- Java 21 (para desenvolvimento backend)
- Node.js 18+ (para desenvolvimento frontend)
- Make (opcional, mas recomendado)

### 1. Subir ambiente local

```bash
# Clone o repositório
git clone <repo-url>
cd jetski

# Subir serviços (PostgreSQL + Redis + Keycloak + OPA + backend + frontend)
make up

# Ou sem Make:
docker compose up -d

# Reset completo do ambiente dev (recria DB + Keycloak + seed):
./reset-ambiente-dev.sh
```

Aguarde ~1-2 minutos para o Keycloak ficar pronto.

### 2. Verificar serviços

```bash
make test-all

# Ou manualmente:
make test-db        # Testa PostgreSQL
make test-redis     # Testa Redis
make test-keycloak  # Testa Keycloak
```

### 3. Acessar serviços

- **PostgreSQL:** `localhost:5432`
  - Database: `jetski_dev`
  - User: `jetski`
  - Password: `dev123`

- **Redis:** `localhost:6379`

- **Keycloak:** http://localhost:8080
  - Admin Console: http://localhost:8080/admin
  - Credentials: `admin` / `Mazuca@123`
  - Realm: `jetski-saas`

- **OPA:** http://localhost:8181

### 4. Usuários de teste (tenant `acme`)

| Email | Senha | Role |
|-------|-------|------|
| admin@acme.com | admin123 | ADMIN_TENANT |
| gerente@acme.com | gerente123 | GERENTE |
| operador@acme.com | operador123 | OPERADOR |
| vendedor@acme.com | vendedor123 | VENDEDOR |
| mecanico@acme.com | mecanico123 | MECANICO |

## 📁 Estrutura do Projeto

```
jetski/
├── backend/              # API Spring Boot
│   ├── src/
│   ├── stories/         # User stories do backend
│   └── pom.xml
│
├── frontend/            # Backoffice web
│   ├── jetski-backoffice/  # App Next.js (app/, components/, e2e/...)
│   └── stories/         # User stories do frontend
│
├── mobile/             # KMM app (Android/iOS) — apenas documentação
│   ├── ARCHITECTURE.md
│   ├── KMM-INTRO.md
│   ├── SETUP-WINDOWS.md
│   └── stories/        # User stories do mobile
│
├── stories/            # Gestão de épicos e sprints
│   ├── epics/         # 7 épicos principais
│   ├── sprints/       # Planejamento de sprints
│   ├── templates/     # Templates reutilizáveis
│   └── project-board.md  # Dashboard central
│
├── infra/              # Infraestrutura
│   ├── init-db.sql
│   ├── keycloak-realm.json
│   └── *.sh           # Scripts de setup
│
├── docs/               # Documentação técnica
├── docker-compose.yml
├── Makefile
└── README.md
```

## 🛠️ Comandos Úteis

### Docker

```bash
make up              # Subir serviços
make down            # Parar serviços
make restart         # Reiniciar serviços
make logs            # Ver logs de todos
make logs-keycloak   # Ver logs do Keycloak
make ps              # Status dos serviços
make clean           # Parar e remover volumes (CUIDADO!)
```

### Database

```bash
make shell-postgres  # Abrir psql
make shell-redis     # Abrir redis-cli
make test-db         # Testar conexão
```

### Backend

```bash
make backend-build   # Build (Maven)
make backend-test    # Rodar testes
make backend-run     # Rodar aplicação
```

## 📚 Documentação

- **[CLAUDE.md](./CLAUDE.md)** - Guia completo para desenvolvimento
- **[Implementation Status](./IMPLEMENTATION_STATUS.md)** - Status atual da implementação
- **[Project Board](./stories/project-board.md)** - Quadro de épicos/stories
- **[Épicos](./stories/epics/)** - 7 épicos principais
- **[Especificação Completa](./inicial.md)** - Requisitos detalhados

## 🎯 Épicos

| ID | Épico | Status | Story Points |
|----|-------|--------|-------------|
| [EPIC-01](./stories/epics/epic-01-multi-tenant-foundation.md) | Multi-tenant Foundation | ✅ CONCLUÍDO | 26 pts |
| [EPIC-02](./stories/epics/epic-02-cadastros-core.md) | Cadastros Core | ✅ CONCLUÍDO | 37 pts |
| [EPIC-03](./stories/epics/epic-03-reservas-locacoes.md) | Reservas e Locações | ✅ CONCLUÍDO¹ | 105 pts |
| [EPIC-04](./stories/epics/epic-04-manutencao-fechamentos.md) | Manutenção e Fechamentos | ✅ CONCLUÍDO | 65 pts |
| [EPIC-05](./stories/epics/epic-05-observabilidade-cicd.md) | Observabilidade e CI/CD | 🔄 PARCIAL² | 47 pts |
| [EPIC-06](./stories/epics/epic-06-backoffice-web.md) | Backoffice Web | ✅ CONCLUÍDO | 82 pts |
| [EPIC-07](./stories/epics/epic-07-mobile-kmm-poc.md) | Mobile KMM POC | 📋 TODO³ | 47 pts |

¹ Núcleo completo (reservas, check-in/out, billing RN01). Pendente: armazenamento de fotos em S3 (hoje storage local).
² Observabilidade implementada (Grafana/Loki/Prometheus, Actuator). Pendente: pipeline CI/CD e deploy.
³ Apenas documentação (`mobile/*.md`); código no working dir separado.

**Total:** 409 story points

## 🔐 Multi-tenancy

O sistema usa **isolamento lógico de dados** com:
- Coluna `tenant_id` em todas as tabelas operacionais
- **Row Level Security (RLS)** no PostgreSQL
- Validação de `tenant_id` no JWT (Keycloak claim)
- `TenantFilter` no Spring Boot para injetar contexto

## 🧪 Testes

```bash
# Backend (749 testes; unit + integração com Testcontainers; requer Docker)
cd backend
mvn test                          # Suíte completa
mvn test -Dtest=ModuleStructureTest   # Verifica fronteiras de módulos (Spring Modulith)
mvn test -Dtest=LocacaoControllerTest # Rodar uma classe específica

# Frontend (Playwright e2e)
cd frontend/jetski-backoffice
npx playwright test

# API end-to-end (Newman/Postman)
cd backend/postman
newman run Jetski-Jornadas.postman_collection.json -e environments/Dev.postman_environment.json
```

## 📊 Observabilidade

- **Logs:** JSON estruturado com `tenant_id` e `traceId` (MDC)
- **Métricas:** Prometheus + Grafana (`infra/monitoring/`, `monitoring-stack.sh`)
- **Health:** Spring Boot Actuator (`/api/actuator/health`)
- **Auditoria:** trilha "quem/o quê/quando" via eventos (módulo `audit`)

Pendente: traces (OpenTelemetry) e validação dos dashboards.

## 🤝 Contribuindo

1. Crie uma branch: `git checkout -b feature/STORY-XXX-descricao`
2. Desenvolva seguindo os critérios de aceite da história
3. Commit com referência: `git commit -m "feat: implementa STORY-001 - TenantFilter"`
4. Push e abra Pull Request
5. Aguarde code review

## 📝 Convenções

- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/)
- **Branches:** `feature/STORY-XXX`, `bugfix/ISSUE-XXX`, `hotfix/XXX`
- **Code Style:** Google Java Style Guide (backend), Prettier (frontend)

## 🐛 Troubleshooting

### Keycloak não sobe

```bash
docker compose logs keycloak
# Verificar se PostgreSQL está rodando
docker compose ps postgres
```

### PostgreSQL connection refused

```bash
# Verificar se porta 5432 está livre
lsof -i :5432
# Recriar container
docker compose down
docker compose up -d postgres
```

### Realm não foi importado

```bash
# Importar manualmente
make setup-keycloak
```

## 📄 Licença

TBD

## 👥 Time

- **Product Owner:** TBD
- **Scrum Master:** TBD
- **Tech Lead:** TBD

---

**Status:** 🚧 Em desenvolvimento ativo (v0.8.0)
**Módulos:** Multi-tenant, usuários/convites, frota, reservas, locações (check-in/out + billing), manutenção, comissões, fechamentos, combustível, despesas, pagamentos, bônus, dashboard, marketplace
**Última atualização:** 2026-06-14
