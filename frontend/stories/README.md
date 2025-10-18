# Frontend Stories

Histórias (User Stories) específicas do módulo **Frontend** (Next.js 14 Backoffice).

## Épicos Relacionados

- [EPIC-06: Backoffice Web](../../stories/epics/epic-06-backoffice-web.md)
- [EPIC-02: Cadastros Core](../../stories/epics/epic-02-cadastros-core.md) (páginas CRUD)
- [EPIC-03: Reservas e Locações](../../stories/epics/epic-03-reservas-locacoes.md) (calendário, check-in/out)
- [EPIC-04: Manutenção e Fechamentos](../../stories/epics/epic-04-manutencao-fechamentos.md) (relatórios)

## Histórias

### EPIC-06: Backoffice Web (Setup)
- [STORY-001](./story-001-nextjs-setup.md): Next.js 14 + TypeScript + shadcn/ui (3 pts)
- [STORY-002](./story-002-auth-keycloak.md): Autenticação Keycloak com NextAuth.js (5 pts)
- [STORY-003](./story-003-dashboard-layout.md): Layout Dashboard com Sidebar (3 pts)

**Total Setup:** 11 story points

### Próximas Histórias (EPIC-02)
- STORY-004: Página de Modelos (CRUD) (5 pts)
- STORY-005: Página de Jetskis (CRUD) (5 pts)
- STORY-006: Página de Vendedores (CRUD) (3 pts)
- STORY-007: Página de Clientes (CRUD) (3 pts)

### Próximas Histórias (EPIC-03)
- STORY-008: Calendário de Agenda (8 pts)
- STORY-009: Formulário de Reserva (5 pts)
- STORY-010: Interface de Check-in (8 pts)
- STORY-011: Interface de Check-out (8 pts)

## Como Buscar Histórias

```bash
# Por status
grep -r "status: IN_PROGRESS" .

# Por tag
grep -r "tags:.*ui" .
grep -r "tags:.*auth" .

# Por épico
grep -r "epic: EPIC-06" .
```

## Convenções

- IDs seguem formato: `STORY-XXX`
- Prioridade: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`
- Estimativa em story points (Fibonacci: 1, 2, 3, 5, 8, 13)
- Status: `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `TESTING`, `DONE`, `BLOCKED`, `CANCELLED`

## Links

- [Project Board](../../stories/project-board.md)
- [Épicos](../../stories/epics/)
