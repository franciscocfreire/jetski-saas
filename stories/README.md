# Gestão de Épicos e Histórias - Jetski SaaS

Este diretório contém a gestão de épicos, histórias e sprints do projeto usando uma abordagem simples baseada em Markdown + Git.

## Estrutura de Diretórios

```
stories/
├── epics/                    # Épicos (features de alto nível)
│   ├── epic-01-multi-tenant-foundation.md
│   ├── epic-02-cadastros-core.md
│   └── ...
├── sprints/                  # Planejamento de sprints
│   ├── sprint-01-planning.md
│   └── ...
├── templates/                # Templates reutilizáveis
│   ├── epic-template.md
│   └── story-template.md
├── project-board.md          # Dashboard central do projeto
└── README.md                 # Este arquivo
```

## Épicos

Épicos representam iniciativas grandes que agrupam múltiplas histórias relacionadas. Cada épico deve:

- Ter um objetivo claro de negócio
- Definir escopo (o que está incluído e excluído)
- Listar histórias relacionadas
- Definir critérios de aceite mensuráveis
- Identificar riscos e dependências

### Estados de Épicos

- `TODO` - Não iniciado
- `IN_PROGRESS` - Em andamento
- `BLOCKED` - Bloqueado por dependência ou impedimento
- `DONE` - Concluído
- `CANCELLED` - Cancelado

## Histórias (User Stories)

Histórias vivem nos módulos específicos (`backend/stories/`, `frontend/stories/`, `mobile/stories/`) e seguem o formato:

**Como** [persona]
**Quero** [funcionalidade]
**Para que** [benefício/valor]

### Estados de Histórias

- `TODO` - Não iniciado
- `IN_PROGRESS` - Em desenvolvimento
- `IN_REVIEW` - Em code review
- `BLOCKED` - Bloqueado
- `TESTING` - Em teste
- `DONE` - Concluído
- `CANCELLED` - Cancelado

### Prioridades

- `CRITICAL` - Bloqueante, precisa ser resolvido imediatamente
- `HIGH` - Alta prioridade, necessário para o MVP
- `MEDIUM` - Prioridade média
- `LOW` - Baixa prioridade, nice-to-have

## Como Criar um Novo Épico

1. Copie o template: `cp stories/templates/epic-template.md stories/epics/epic-XX-nome.md`
2. Preencha os metadados YAML no topo do arquivo
3. Complete todas as seções obrigatórias
4. Adicione o épico ao `project-board.md`
5. Crie as histórias relacionadas nos módulos apropriados

## Como Criar uma Nova História

1. Copie o template para o módulo apropriado:
   ```bash
   cp stories/templates/story-template.md backend/stories/story-XXX-nome.md
   ```
2. Preencha os metadados YAML
3. Escreva a história no formato Como/Quero/Para que
4. Defina critérios de aceite claros e testáveis
5. Liste as tarefas técnicas necessárias
6. Adicione a história ao épico relacionado
7. Atualize o `project-board.md`

## Fluxo de Trabalho

```
TODO → IN_PROGRESS → IN_REVIEW → TESTING → DONE
                          ↓
                      BLOCKED (se necessário)
```

### Quando Mover Estados

- **TODO → IN_PROGRESS**: Quando você começa a trabalhar na história
- **IN_PROGRESS → IN_REVIEW**: Quando abre um Pull Request
- **IN_REVIEW → TESTING**: Quando o PR é aprovado e merged
- **TESTING → DONE**: Quando validado em ambiente de desenvolvimento/staging
- **Qualquer → BLOCKED**: Quando há um impedimento que bloqueia o progresso

## Convenções de Nomenclatura

### Épicos
- Formato: `epic-XX-nome-descritivo.md`
- Exemplo: `epic-01-multi-tenant-foundation.md`
- ID no arquivo: `EPIC-01`

### Histórias
- Formato: `story-XXX-nome-descritivo.md`
- Exemplo: `story-001-tenant-context-filter.md`
- ID no arquivo: `STORY-001`

### Commits
Ao trabalhar em uma história, referencie no commit:
```
feat(backend): implementa TenantFilter (STORY-001)

- Extrai tenant_id do header X-Tenant-Id
- Valida contra claim do JWT
- Armazena em ThreadLocal via TenantContext
```

### Pull Requests
- Título: `[STORY-001] Implementa TenantFilter`
- Descrição deve linkar a história: `Closes: backend/stories/story-001-tenant-context-filter.md`

## Definition of Done (DoD)

Toda história só pode ser marcada como `DONE` quando:

- [ ] Code review aprovado (mínimo 1 reviewer)
- [ ] Todos os testes passando (unit + integration)
- [ ] Cobertura de código > 80%
- [ ] Sem vulnerabilidades críticas (SonarQube/OWASP)
- [ ] Documentação técnica atualizada
- [ ] Deploy validado em ambiente de desenvolvimento
- [ ] Critérios de aceite validados

## Estimativas

Usamos Story Points (escala Fibonacci):

- **1 pt**: Tarefa trivial, < 2 horas
- **2 pts**: Tarefa simples, 2-4 horas
- **3 pts**: Tarefa pequena, ~1 dia
- **5 pts**: Tarefa média, 1-2 dias
- **8 pts**: Tarefa grande, 2-3 dias
- **13 pts**: Muito grande, considerar quebrar em histórias menores

## Sprints

Sprints têm duração de **2 semanas** e incluem:

- Sprint Planning (definir objetivo e selecionar histórias)
- Daily Updates (atualizar status no project-board.md)
- Sprint Review (demonstrar o que foi feito)
- Retrospective (lições aprendidas)

Planejamentos ficam em `stories/sprints/sprint-XX-planning.md`

## Project Board

O arquivo `stories/project-board.md` é o dashboard central com:

- Status de todos os épicos
- Sprint atual e progresso
- Histórias por status
- Métricas (velocity, burndown)
- Riscos e impedimentos

**Atualizar diariamente!**

## Ferramentas

Tudo é gerenciado via:
- **Markdown**: formato simples e versionado
- **Git**: histórico completo de mudanças
- **Grep/Ripgrep**: buscar histórias por status, tag, assignee
- **Scripts**: automação (opcional)

### Exemplos de Buscas

```bash
# Todas as histórias em progresso
grep -r "status: IN_PROGRESS" backend/stories/ frontend/stories/ mobile/stories/

# Histórias de alta prioridade
grep -r "priority: HIGH" backend/stories/

# Histórias do EPIC-01
grep -r "epic: EPIC-01" backend/stories/
```

## Benefícios desta Abordagem

✅ **Simplicidade**: Apenas Markdown + Git
✅ **Versionado**: Histórico completo no Git
✅ **Rastreável**: Fácil linkar histórias a commits/PRs
✅ **Sem Lock-in**: Não depende de ferramentas proprietárias
✅ **Offline-first**: Trabalhe sem internet
✅ **Code-centric**: Histórias vivem perto do código

## Links Úteis

- [Templates](./templates/)
- [Épicos](./epics/)
- [Project Board](./project-board.md)
- [Sprint Atual](./sprints/)
