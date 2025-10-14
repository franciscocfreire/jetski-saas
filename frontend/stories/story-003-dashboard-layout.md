---
story_id: STORY-003
epic: EPIC-06
title: Layout do Dashboard com Sidebar e Navegação
status: TODO
priority: HIGH
estimate: 3
assignee: Unassigned
started_at: null
completed_at: null
tags: [frontend, ui, dashboard, layout]
dependencies: [STORY-002]
---

# STORY-003: Layout do Dashboard com Sidebar e Navegação

## Como
Frontend Developer

## Quero
Layout responsivo com sidebar, header e área de conteúdo

## Para que
Usuários tenham navegação consistente em todas as páginas

## Critérios de Aceite

- [ ] **CA1:** Sidebar com links para: Dashboard, Modelos, Jetskis, Vendedores, Clientes, Agenda, Relatórios
- [ ] **CA2:** Header com nome do usuário e botão de logout
- [ ] **CA3:** Layout responsivo (colapsa sidebar em mobile)
- [ ] **CA4:** Navegação funciona (Next.js Link)
- [ ] **CA5:** Item ativo destacado na sidebar

## Tarefas Técnicas

- [ ] Criar `app/(dashboard)/layout.tsx`
- [ ] Criar `components/layout/Sidebar.tsx`
- [ ] Criar `components/layout/Header.tsx`
- [ ] Usar shadcn/ui: `npx shadcn-ui add sheet navigation-menu`
- [ ] Implementar navegação com `usePathname`
- [ ] Testar responsividade (desktop, tablet, mobile)

## Links

- **Epic:** [EPIC-06](../../stories/epics/epic-06-backoffice-web.md)

## Changelog

- 2025-01-15: História criada
