# Sprint 01 - Planning

**Sprint:** 01
**Período:** 2025-01-15 a 2025-01-28 (2 semanas)
**Épico Principal:** [EPIC-01: Multi-tenant Foundation](../epics/epic-01-multi-tenant-foundation.md)

---

## 🎯 Sprint Goal

**Estabelecer infraestrutura multi-tenant base com isolamento de dados por RLS, autenticação Keycloak funcionando e ambiente de desenvolvimento local completo.**

Ao final deste sprint, um desenvolvedor deve conseguir:
1. Subir o ambiente local com `docker-compose up`
2. Fazer login via Keycloak e receber JWT com `tenant_id`
3. Criar uma entidade e validar que RLS filtra por tenant automaticamente

---

## 👥 Time

- **Product Owner:** TBD
- **Scrum Master:** TBD
- **Developers:**
  - Backend: TBD
  - DevOps: TBD

---

## 📊 Capacidade

- **Disponibilidade:** 2 semanas (10 dias úteis)
- **Desenvolvedores:** 2 (backend + devops)
- **Velocity esperada:** ~26 story points
- **Story points comprometidos:** 26 pts

---

## 📝 Histórias Selecionadas

### Backend - EPIC-01

| ID | História | Estimativa | Assignee | Prioridade |
|----|----------|-----------|----------|-----------|
| [STORY-001](../../backend/stories/story-001-tenant-context-filter.md) | TenantContext e TenantFilter | 5 pts | TBD | CRITICAL |
| [STORY-002](../../backend/stories/story-002-rls-implementation.md) | Row Level Security (RLS) | 8 pts | TBD | CRITICAL |
| [STORY-003](../../backend/stories/story-003-keycloak-integration.md) | Integração Keycloak | 5 pts | TBD | CRITICAL |
| [STORY-004](../../backend/stories/story-004-docker-compose-setup.md) | Docker Compose Setup | 3 pts | TBD | HIGH |
| [STORY-005](../../backend/stories/story-005-flyway-migrations-base.md) | Migrations Flyway Base | 5 pts | TBD | HIGH |

**Total:** 26 story points

---

## 🎲 Dependências

### Externas
- Nenhuma dependência externa crítica

### Entre Histórias
- **STORY-002** depende de **STORY-001** (TenantContext precisa existir para RLS funcionar)
- **STORY-005** depende de **STORY-004** (PostgreSQL precisa estar rodando)

**Ordem sugerida de desenvolvimento:**
1. STORY-004 (Docker Compose) - Paralelo com outras
2. STORY-001 (TenantContext) - Fundação
3. STORY-005 (Migrations) - Após Docker
4. STORY-002 (RLS) - Após TenantContext
5. STORY-003 (Keycloak) - Pode ser paralelo

---

## ✅ Definition of Ready (DoR)

Todas as histórias selecionadas atendem aos critérios:
- [x] História possui critérios de aceite claros
- [x] História possui estimativa
- [x] História possui tarefas técnicas detalhadas
- [x] Dependências identificadas
- [x] Epic relacionado está definido

---

## ✅ Definition of Done (DoD)

Para cada história ser considerada DONE:
- [ ] Code review aprovado (mínimo 1 reviewer)
- [ ] Todos os testes passando (unit + integration)
- [ ] Cobertura de código > 80%
- [ ] Sem vulnerabilidades críticas (SonarQube/OWASP)
- [ ] Documentação técnica atualizada
- [ ] Deploy validado em ambiente de desenvolvimento
- [ ] Critérios de aceite validados

---

## 🎯 Critérios de Aceite do Sprint

O sprint será considerado bem-sucedido se:

1. **Ambiente Local Funcionando**
   - `docker-compose up` sobe PostgreSQL + Redis + Keycloak sem erros
   - Serviços passam health check

2. **Autenticação Keycloak**
   - Usuário consegue fazer login via Keycloak
   - Token JWT contém claim `tenant_id`
   - Spring Security valida JWT corretamente

3. **Multi-tenancy com RLS**
   - Requisições têm `tenant_id` extraído e armazenado no `TenantContext`
   - RLS filtra automaticamente queries por `tenant_id`
   - Testes provam isolamento: tenant A não acessa dados do tenant B

4. **Migrations Base**
   - Tabelas multi-tenant criadas: `tenant`, `plano`, `assinatura`, `usuario`, `membro`
   - Tabelas operacionais base criadas: `modelo`, `jetski`, `vendedor`, `cliente`
   - Seed data permite testar aplicação localmente

---

## ⚠️ Riscos Identificados

| Risco | Probabilidade | Impacto | Mitigação |
|-------|--------------|---------|-----------|
| Performance do RLS degradar queries | Média | Alto | Criar índices compostos, validar com explain analyze |
| Complexidade de config do Keycloak | Média | Médio | Documentar passo-a-passo, script de automação |
| Incompatibilidade de versões (Keycloak 26) | Baixa | Médio | Testar em Docker local antes de commits |
| Time subestimou esforço | Baixa | Alto | Monitorar daily, ajustar escopo se necessário |

---

## 📅 Cerimônias

### Sprint Planning
- **Data:** 2025-01-15
- **Participantes:** PO, SM, Developers
- **Resultado:** Este documento

### Daily Standups
- **Quando:** Todos os dias úteis às 9h30
- **Duração:** 15 minutos
- **Formato:** O que fiz ontem? O que farei hoje? Algum impedimento?

### Sprint Review
- **Data:** 2025-01-28 (14h)
- **Participantes:** PO, SM, Developers, Stakeholders
- **Objetivo:** Demo do que foi construído

### Sprint Retrospective
- **Data:** 2025-01-28 (15h30)
- **Participantes:** PO, SM, Developers
- **Formato:** Start/Stop/Continue

---

## 📈 Tracking

### Burndown Chart

```
Story Points Restantes
26 |■■■■■■■■■■■■■■■■■■■■■■■■■■
20 |
15 |
10 |
5  |
0  |___________________________
   D1  D3  D5  D7  D9  D11 D13
```

**Atualização:** Será atualizado diariamente no [Project Board](../project-board.md)

---

## 🔗 Links

- [Project Board](../project-board.md)
- [EPIC-01: Multi-tenant Foundation](../epics/epic-01-multi-tenant-foundation.md)
- [Backend Stories](../../backend/stories/README.md)

---

## 📝 Notas

### Decisões Tomadas
- Começar com RLS (opção 1) ao invés de schemas por tenant (opção 2) para simplificar MVP
- Usar Keycloak 26 OSS com realm único + claim `tenant_id`
- Docker Compose para ambiente local (não usar Kubernetes ainda)

### Itens para Próximo Sprint
- Iniciar EPIC-02 (Cadastros Core)
- Setup de CI/CD básico (se sobrar tempo)

---

**Criado em:** 2025-01-15
**Última atualização:** 2025-01-15
