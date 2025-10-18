# Backend Stories

Histórias (User Stories) específicas do módulo **Backend** (API Spring Boot).

## Épicos Relacionados

- [EPIC-01: Multi-tenant Foundation](../../stories/epics/epic-01-multi-tenant-foundation.md)
- [EPIC-02: Cadastros Core](../../stories/epics/epic-02-cadastros-core.md)
- [EPIC-03: Reservas e Locações](../../stories/epics/epic-03-reservas-locacoes.md)
- [EPIC-04: Manutenção e Fechamentos](../../stories/epics/epic-04-manutencao-fechamentos.md)
- [EPIC-05: Observabilidade e CI/CD](../../stories/epics/epic-05-observabilidade-cicd.md)

## Histórias

### EPIC-01: Multi-tenant Foundation
- [STORY-001](./story-001-tenant-context-filter.md): TenantContext e TenantFilter (5 pts)
- [STORY-002](./story-002-rls-implementation.md): Row Level Security (RLS) (8 pts)
- [STORY-003](./story-003-keycloak-integration.md): Integração Keycloak (5 pts)
- [STORY-004](./story-004-docker-compose-setup.md): Docker Compose Setup (3 pts)
- [STORY-005](./story-005-flyway-migrations-base.md): Migrations Flyway Base (5 pts)

**Total EPIC-01:** 26 story points

### Próximas Histórias (EPIC-02)
- STORY-006: Modelo Entity + CRUD (8 pts)
- STORY-007: Jetski Entity + CRUD (5 pts)
- STORY-008: Vendedor Entity + CRUD (5 pts)
- STORY-009: Cliente Entity + CRUD (3 pts)

## Como Buscar Histórias

### Por status
```bash
grep -r "status: IN_PROGRESS" .
grep -r "status: TODO" .
grep -r "status: DONE" .
```

### Por tag
```bash
grep -r "tags:.*multi-tenant" .
grep -r "tags:.*security" .
```

### Por épico
```bash
grep -r "epic: EPIC-01" .
```

### Por estimativa
```bash
grep -r "estimate: 8" .
```

## Convenções

- IDs seguem formato: `STORY-XXX`
- Prioridade: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`
- Estimativa em story points (Fibonacci: 1, 2, 3, 5, 8, 13)
- Status: `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `TESTING`, `DONE`, `BLOCKED`, `CANCELLED`

## Links

- [Project Board](../../stories/project-board.md)
- [Templates](../../stories/templates/)
- [Épicos](../../stories/epics/)
