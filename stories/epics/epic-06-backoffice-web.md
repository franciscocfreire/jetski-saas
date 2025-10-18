---
epic_id: EPIC-06
title: Backoffice Web
status: TODO
priority: HIGH
start_date: 2025-02-12
target_date: 2025-03-11
owner: Team Frontend
dependencies: [EPIC-01, EPIC-02]
---

# EPIC-06: Backoffice Web

## Objetivo

Desenvolver interface web completa (backoffice) para gestão operacional usando Next.js 14, com autenticação Keycloak, dashboard com KPIs, CRUD de entidades, calendário de agenda e relatórios visuais.

## Escopo

### Incluído
- [ ] Setup Next.js 14 (App Router) + TypeScript + shadcn/ui
- [ ] Autenticação via Keycloak (PKCE) com NextAuth.js
- [ ] Layout responsivo com sidebar e navegação
- [ ] Dashboard: cards de status, agenda do dia, alertas
- [ ] Páginas CRUD: Modelos, Jetskis, Vendedores, Clientes
- [ ] Calendário/Agenda: visualização de reservas, detecção de conflitos
- [ ] Formulários de Check-in e Check-out
- [ ] Página de Abastecimentos
- [ ] Página de Ordens de Serviço (Manutenção)
- [ ] Página de Fechamento Diário com gráficos
- [ ] Página de Fechamento Mensal com KPIs (Recharts ou ECharts)
- [ ] Exportação de relatórios (PDF/CSV)
- [ ] Gestão de perfil de usuário
- [ ] Seleção de tenant (para admins multi-tenant)

### Excluído (Out of Scope)
- App mobile nativo (será EPIC-07)
- Funcionalidades avançadas de BI/Analytics
- Customização de branding por tenant (será futuro)

## Histórias Relacionadas

### Frontend
- `frontend/stories/story-001-nextjs-setup.md` (3 pts)
- `frontend/stories/story-002-auth-keycloak.md` (5 pts)
- `frontend/stories/story-003-dashboard-layout.md` (3 pts)
- `frontend/stories/story-004-modelos-page.md` (5 pts)
- `frontend/stories/story-005-jetskis-page.md` (5 pts)
- `frontend/stories/story-006-vendedores-page.md` (3 pts)
- `frontend/stories/story-007-clientes-page.md` (3 pts)
- `frontend/stories/story-008-calendario-agenda.md` (8 pts)
- `frontend/stories/story-009-reserva-form.md` (5 pts)
- `frontend/stories/story-010-checkin-interface.md` (8 pts)
- `frontend/stories/story-011-checkout-interface.md` (8 pts)
- `frontend/stories/story-012-os-manutencao-page.md` (5 pts)
- `frontend/stories/story-013-fechamento-diario-page.md` (8 pts)
- `frontend/stories/story-014-fechamento-mensal-page.md` (8 pts)
- `frontend/stories/story-015-relatorios-export.md` (5 pts)

**Total estimado:** 82 story points (~4-5 sprints)

## Critérios de Aceite

### Autenticação
- [ ] Usuário faz login via Keycloak e é redirecionado para o dashboard
- [ ] Token JWT é armazenado de forma segura (httpOnly cookie)
- [ ] Refresh token funciona automaticamente
- [ ] Logout limpa sessão e redireciona para login

### Dashboard
- [ ] Cards mostram: Jetskis Disponíveis, Locados, Em Manutenção, Atrasados
- [ ] Agenda do dia exibe reservas e locações em andamento
- [ ] Alertas mostram jetskis próximos de revisão (50h, 100h)

### CRUD
- [ ] Todas as páginas CRUD têm: listagem, criação, edição, exclusão
- [ ] Formulários validam campos obrigatórios
- [ ] Mensagens de erro são claras e amigáveis
- [ ] Tabelas são paginadas e filtráveis

### Calendário/Agenda
- [ ] Visualização em modo semana e dia
- [ ] Drag-and-drop para criar/mover reservas (nice-to-have)
- [ ] Conflitos são destacados visualmente
- [ ] Filtro por modelo/jetski/vendedor

### Relatórios
- [ ] Gráficos de ocupação mensal (barras ou linhas)
- [ ] Gráfico de receita por modelo (pizza)
- [ ] Exportação em PDF e CSV funciona

### Responsividade
- [ ] Interface funciona bem em desktop (1920×1080)
- [ ] Interface funciona em tablet (768×1024)
- [ ] Interface navegável em mobile (375×667)

## Riscos

**Risco Médio:**
- **Complexidade do calendário**: Componentes de calendário podem ser difíceis de customizar.
  - **Mitigação**: Usar biblioteca robusta (React Big Calendar ou FullCalendar)

**Risco Baixo:**
- **Performance com muitos dados**: Tabelas com centenas de linhas.
  - **Mitigação**: Virtualização de listas, paginação server-side

## Dependências

- API Backend com endpoints REST funcionando (EPIC-02, EPIC-03)
- Keycloak configurado (EPIC-01)

## Métricas de Sucesso

- Tempo de carregamento de páginas: < 2 segundos (P95)
- Core Web Vitals: LCP < 2.5s, FID < 100ms, CLS < 0.1
- Acessibilidade (Lighthouse): score > 90

## Notas

### Stack Tecnológico

- **Framework**: Next.js 14 (App Router)
- **Linguagem**: TypeScript
- **UI Library**: shadcn/ui (componentes baseados em Radix UI)
- **Estilização**: TailwindCSS
- **Autenticação**: NextAuth.js com provider Keycloak
- **State Management**: React Context ou Zustand (se necessário)
- **HTTP Client**: Fetch API nativo ou Axios
- **Charts**: Recharts ou ECharts
- **Forms**: React Hook Form + Zod (validação)
- **Tabelas**: TanStack Table (React Table v8)
- **Calendário**: React Big Calendar ou FullCalendar

### Estrutura de Diretórios (Proposta)

```
frontend/
├── app/
│   ├── (auth)/
│   │   └── login/
│   ├── (dashboard)/
│   │   ├── layout.tsx
│   │   ├── page.tsx              # Dashboard
│   │   ├── modelos/
│   │   ├── jetskis/
│   │   ├── vendedores/
│   │   ├── clientes/
│   │   ├── agenda/
│   │   ├── locacoes/
│   │   ├── manutencao/
│   │   └── relatorios/
│   └── api/
│       └── auth/[...nextauth]/
├── components/
│   ├── ui/                       # shadcn/ui components
│   ├── layout/
│   ├── forms/
│   └── charts/
├── lib/
│   ├── auth.ts
│   ├── api-client.ts
│   └── utils.ts
├── hooks/
└── types/
```

### Exemplo de Dashboard (Wire frame textual)

```
+--------------------------------------------------+
| Logo    Jetski SaaS           [User] [Logout]   |
+--------------------------------------------------+
| Dashboard  Agenda  Locações  Relatórios         |
+--------------------------------------------------+
| Disponíveis  Locados  Manutenção  Atrasados     |
|     12          5         2           1          |
+--------------------------------------------------+
| Agenda do Dia - 2025-03-20                       |
| 10:00 | Jetski #101 | João Silva | Check-in     |
| 12:00 | Jetski #105 | Maria Costa | Em curso     |
| 14:30 | Jetski #103 | ATRASADO                   |
+--------------------------------------------------+
| Alertas                                          |
| ⚠️ Jetski #102 está com 95h (revisão em 5h)     |
+--------------------------------------------------+
```

## Changelog

- 2025-01-15: Épico criado
