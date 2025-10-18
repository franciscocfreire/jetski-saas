---
story_id: STORY-001
epic: EPIC-06
title: Setup Next.js 14 + TypeScript + shadcn/ui
status: TODO
priority: HIGH
estimate: 3
assignee: Unassigned
started_at: null
completed_at: null
tags: [frontend, nextjs, typescript, setup]
dependencies: []
---

# STORY-001: Setup Next.js 14 + TypeScript + shadcn/ui

## Como
Frontend Developer

## Quero
Projeto Next.js 14 configurado com TypeScript, TailwindCSS e shadcn/ui

## Para que
Tenhamos uma base sólida para construir o backoffice web

## Critérios de Aceite

- [ ] **CA1:** Projeto Next.js 14 criado com App Router
- [ ] **CA2:** TypeScript configurado com strict mode
- [ ] **CA3:** TailwindCSS e shadcn/ui instalados e funcionando
- [ ] **CA4:** ESLint e Prettier configurados
- [ ] **CA5:** Estrutura de diretórios definida (`app/`, `components/`, `lib/`, `hooks/`)
- [ ] **CA6:** `npm run dev` inicia servidor em desenvolvimento

## Tarefas Técnicas

- [ ] `npx create-next-app@latest frontend --typescript --tailwind --app --eslint`
- [ ] Instalar shadcn/ui: `npx shadcn-ui@latest init`
- [ ] Configurar aliases de importação (`@/`)
- [ ] Instalar deps: `react-hook-form`, `zod`, `axios`, `date-fns`
- [ ] Criar estrutura de diretórios
- [ ] Criar README.md com instruções

## Definição de Pronto (DoD)

- [ ] Projeto builda sem erros: `npm run build`
- [ ] README atualizado

## Links

- **Epic:** [EPIC-06](../../stories/epics/epic-06-backoffice-web.md)

## Changelog

- 2025-01-15: História criada
