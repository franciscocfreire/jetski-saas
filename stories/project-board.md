# Project Board - Jetski SaaS Multi-tenant

**Última atualização:** 2025-01-15

## 🎯 Objetivo do Projeto

Desenvolver sistema SaaS B2B multi-tenant para gestão completa de locações de jetski, incluindo: frota, agenda, operação com fotos, manutenção, abastecimento, comissões e fechamentos financeiros.

---

## 📊 Status Geral dos Épicos

| Epic ID | Título | Status | Progresso | Owner | Target Date | Story Points |
|---------|--------|--------|-----------|-------|-------------|--------------|
| [EPIC-01](./epics/epic-01-multi-tenant-foundation.md) | Multi-tenant Foundation | 📋 TODO | 0% | Team Backend | 2025-01-28 | 26 pts |
| [EPIC-02](./epics/epic-02-cadastros-core.md) | Cadastros Core | 📋 TODO | 0% | Backend + Frontend | 2025-02-11 | 37 pts |
| [EPIC-03](./epics/epic-03-reservas-locacoes.md) | Reservas e Locações | 📋 TODO | 0% | Backend + Mobile | 2025-03-04 | 105 pts |
| [EPIC-04](./epics/epic-04-manutencao-fechamentos.md) | Manutenção e Fechamentos | 📋 TODO | 0% | Backend + Frontend | 2025-03-18 | 65 pts |
| [EPIC-05](./epics/epic-05-observabilidade-cicd.md) | Observabilidade e CI/CD | 📋 TODO | 0% | Team DevOps | 2025-03-25 | 47 pts |
| [EPIC-06](./epics/epic-06-backoffice-web.md) | Backoffice Web | 📋 TODO | 0% | Team Frontend | 2025-03-11 | 82 pts |
| [EPIC-07](./epics/epic-07-mobile-kmm-poc.md) | Mobile KMM POC | 📋 TODO | 0% | Team Mobile | 2025-04-08 | 47 pts |

**Total estimado:** 409 story points (~20 sprints de 2 semanas)

### Legenda de Status
- 📋 TODO - Não iniciado
- 🔄 IN_PROGRESS - Em andamento
- 🚫 BLOCKED - Bloqueado
- ✅ DONE - Concluído
- ❌ CANCELLED - Cancelado

---

## 🏃 Sprint Atual: Sprint 01 (Preparação)

**Período:** 2025-01-15 a 2025-01-28 (2 semanas)

**Objetivo:**
Estabelecer infraestrutura multi-tenant e ambiente de desenvolvimento local funcional

**Capacidade:** 26 story points
**Comprometido:** 0 story points (planejamento em andamento)

### 📋 Histórias Planejadas

Nenhuma história comprometida ainda. Aguardando criação das histórias iniciais.

---

## 📈 Histórias por Status

### 🔄 IN PROGRESS (0 pts)
_Nenhuma história em progresso no momento_

### ✅ DONE (0 pts)
_Nenhuma história concluída no momento_

### 📋 TODO (0 pts)
_Histórias serão adicionadas após criação_

### 🚫 BLOCKED (0 pts)
_Nenhuma história bloqueada no momento_

---

## 📉 Métricas do Projeto

### Velocity
- **Sprint 01:** TBD
- **Média (últimos 3 sprints):** N/A
- **Tendência:** ➡️ Estável (aguardando histórico)

### Qualidade
- **Cobertura de testes:** TBD (meta: > 80%)
- **Bugs críticos abertos:** 0
- **Vulnerabilidades críticas:** 0
- **Code smells críticos:** 0

### Burndown
_Gráfico será atualizado durante o sprint_

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

---

## ⚠️ Riscos e Impedimentos

### Riscos Ativos

**🔴 Risco Alto:**
- Nenhum risco alto identificado no momento

**🟡 Risco Médio:**
1. **Curva de aprendizado de KMM** (EPIC-07)
   - Impacto: Atraso na entrega do mobile
   - Mitigação: Treinamento prévio do time, começar com POC simples
   - Owner: Tech Lead Mobile

2. **Performance do RLS** (EPIC-01)
   - Impacto: Queries lentas podem impactar UX
   - Mitigação: Índices compostos, validação com explain analyze
   - Owner: Tech Lead Backend

**🟢 Risco Baixo:**
- Complexidade de configuração do Keycloak (documentação mitigará)

### Impedimentos
_Nenhum impedimento ativo no momento_

---

## 🎯 Próximas Ações

### Imediatas (Esta Semana)
1. ✅ Criar estrutura de diretórios para stories
2. ✅ Criar templates de épico e história
3. ✅ Criar 7 épicos principais
4. 🔄 Criar histórias iniciais (backend, frontend, mobile)
5. 🔄 Criar planejamento do Sprint 01
6. ⏸️ Setup inicial do projeto Spring Boot
7. ⏸️ Configurar Docker Compose local

### Próxima Semana
1. Começar desenvolvimento do EPIC-01
2. Configurar ambiente de desenvolvimento local
3. Primeiras migrations Flyway
4. Configuração do Keycloak

### Próximo Mês
1. Concluir EPIC-01 (Multi-tenant Foundation)
2. Iniciar EPIC-02 (Cadastros Core)
3. Setup do frontend (Next.js)

---

## 📚 Débito Técnico

### Itens Identificados
_Nenhum débito técnico no momento (projeto em início)_

### Itens Priorizados para Resolver
_N/A_

---

## 🎓 Lições Aprendidas

### Sprint Anterior
_Primeiro sprint do projeto_

### Melhorias Identificadas
_Serão identificadas nas retrospectivas_

---

## 📅 Roadmap de Alto Nível

```
Jan 2025    ████████ EPIC-01: Multi-tenant Foundation
            ████████ Planejamento e setup

Feb 2025    ████████ EPIC-02: Cadastros Core
            ████████ EPIC-06: Backoffice Web (início)

Mar 2025    ████████ EPIC-03: Reservas e Locações
            ████████ EPIC-04: Manutenção e Fechamentos
            ████████ EPIC-05: Observabilidade e CI/CD
            ████████ EPIC-06: Backoffice Web (continuação)

Apr 2025    ████████ EPIC-03: Reservas e Locações (conclusão)
            ████████ EPIC-07: Mobile KMM POC

Mai 2025    ████████ Refinamentos e preparação para produção
```

---

## 📊 Progresso Geral do Projeto

**Épicos Concluídos:** 0 / 7 (0%)
**Story Points Concluídos:** 0 / 409 (0%)
**Sprints Concluídos:** 0 / ~20

```
Progresso do Projeto
[                                        ] 0%
```

---

## 🔗 Links Úteis

- [Épicos](./epics/)
- [Sprints](./sprints/)
- [Templates](./templates/)
- [Especificação Completa](/inicial.md)
- [CLAUDE.md - Guia do Projeto](/CLAUDE.md)

---

## 📝 Notas

- Este project board deve ser atualizado **diariamente** durante os sprints ativos
- Responsável pela atualização: Product Owner ou Scrum Master
- Ferramenta de tracking: Git + Markdown (versionado)
- Para buscar histórias: usar `grep` ou scripts de busca

### Como Atualizar
```bash
# Buscar histórias em progresso
grep -r "status: IN_PROGRESS" backend/stories/ frontend/stories/ mobile/stories/

# Contar story points por status
grep -r "status: DONE" backend/stories/ | wc -l
```

---

**Última revisão:** 2025-01-15
**Próxima revisão:** 2025-01-16
