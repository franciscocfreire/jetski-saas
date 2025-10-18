# 📚 Índice de Documentação - Jetski Backend

Guia completo da documentação do projeto Jetski SaaS Backend.

---

## 🚀 Início Rápido

1. **[README.md](../README.md)** - Comece aqui!
   - Setup e instalação
   - Tecnologias utilizadas
   - Como rodar o projeto
   - Diagramas AS IS → TO BE

---

## 📖 Documentação Principal

### Para Desenvolvedores

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| **[README.md](../README.md)** | Visão geral, setup, comandos | Primeiro acesso ao projeto |
| **[ARCHITECTURE.md](../ARCHITECTURE.md)** | Análise detalhada AS IS vs TO BE | Entender arquitetura modular |
| **[MIGRATION_GUIDE.md](../MIGRATION_GUIDE.md)** | Guia passo a passo de migração | Adicionar novos módulos |
| **[MODULAR_SUMMARY.md](./MODULAR_SUMMARY.md)** | Sumário executivo da migração | Visão rápida das mudanças |

### Para Arquitetos

| Documento | Conteúdo |
|-----------|----------|
| **[ARCHITECTURE.md](../ARCHITECTURE.md)** | Decisões arquiteturais, trade-offs, evolução AS IS → TO BE |
| **[ARCHITECTURE_COMPLETE.md](./ARCHITECTURE_COMPLETE.md)** | 🆕 Arquitetura completa com TODOS os módulos planejados |
| **[MODULAR_SUMMARY.md](./MODULAR_SUMMARY.md)** | Métricas, benefícios, roadmap |

### Para Gestores

| Documento | Conteúdo |
|-----------|----------|
| **[MODULAR_SUMMARY.md](./MODULAR_SUMMARY.md)** | ROI da migração, comparação de tempos, métricas |

---

## 📊 Diagramas

### Mermaid (GitHub-friendly)

Todos os diagramas Mermaid estão incorporados nos documentos e renderizam automaticamente no GitHub:

- **AS IS (Monolito Tradicional)** → [README.md § AS IS](../README.md#as-is---monolito-tradicional-até-v010)
- **TO BE (Monolito Modular)** → [README.md § TO BE](../README.md#to-be---monolito-modular-v020)
- **Regras de Dependência** → [README.md § Regras](../README.md#regras-de-dependência)
- **Fluxo Multi-tenancy** → [README.md § Multi-tenancy](../README.md#fluxo-de-requisição)
- **Dependency Inversion** → [ARCHITECTURE.md § Inversão](../ARCHITECTURE.md#inversão-de-dependência)
- **Comunicação via Eventos** → [ARCHITECTURE.md § Eventos](../ARCHITECTURE.md#comunicação-via-eventos)

### PlantUML (Gerados Automaticamente)

```bash
# Gerar diagramas PlantUML
mvn test -Dtest=ModuleStructureTest#shouldGenerateModuleDocumentation

# Localização
ls target/spring-modulith-docs/
```

Diagramas disponíveis:
- `modules.puml` - Todos os módulos
- `shared.puml` - Detalhes do módulo shared
- `usuarios.puml` - Detalhes do módulo usuarios
- `modules.md` - Documentação Markdown

---

## 🎯 Guias por Cenário

### Cenário 1: Novo Desenvolvedor

**Ordem de leitura:**

1. **[README.md](../README.md)** - Visão geral e setup
   - Ler seções: Tecnologias, Setup, Testes
2. **[README.md § Arquitetura](../README.md#-arquitetura-de-módulos)** - Entender estrutura
3. **[MODULAR_SUMMARY.md](./MODULAR_SUMMARY.md)** - Por que módulos?
4. **[MIGRATION_GUIDE.md § Padrões](../MIGRATION_GUIDE.md#-padrões-de-migração)** - Como adicionar código

⏱️ Tempo estimado: 1-2 horas

### Cenário 2: Adicionar Nova Funcionalidade

**Fluxo:**

1. **[MIGRATION_GUIDE.md § Adicionar Módulo](../MIGRATION_GUIDE.md#-próximos-módulos)** - Template
2. Criar estrutura de módulo
3. Implementar funcionalidade
4. Rodar `ModuleStructureTest` para validar
5. **[README.md § Testes](../README.md#-testes)** - Executar suite

⏱️ Tempo estimado: 30 minutos (setup) + implementação

### Cenário 3: Entender Decisões Arquiteturais

**Leitura recomendada:**

1. **[ARCHITECTURE.md § AS IS](../ARCHITECTURE.md#as-is---monolito-tradicional-até-v010)** - Problemas identificados
2. **[ARCHITECTURE.md § TO BE](../ARCHITECTURE.md#to-be---monolito-modular-v020)** - Soluções aplicadas
3. **[ARCHITECTURE.md § Comparação](../ARCHITECTURE.md#comparação-detalhada-as-is-vs-to-be)** - Trade-offs
4. **[MODULAR_SUMMARY.md § Decisões](./MODULAR_SUMMARY.md#-decisões-arquiteturais)** - Por quê?

⏱️ Tempo estimado: 1 hora

### Cenário 4: Migrar Código Existente

**Passo a passo:**

1. **[MIGRATION_GUIDE.md](../MIGRATION_GUIDE.md)** - Ler guia completo
2. **[MIGRATION_GUIDE.md § Padrões](../MIGRATION_GUIDE.md#-padrões-de-migração)** - Escolher padrão
3. Aplicar migração
4. **[MIGRATION_GUIDE.md § Validar](../MIGRATION_GUIDE.md#6-validar-migração)** - Checklist
5. **[MIGRATION_GUIDE.md § Problemas Comuns](../MIGRATION_GUIDE.md#-problemas-comuns)** - Se houver erros

⏱️ Tempo estimado: 30-60 minutos por módulo

---

## 🧪 Testes

### Executar Testes de Arquitetura

```bash
# Verificar estrutura modular
mvn test -Dtest=ModuleStructureTest

# Gerar documentação
mvn test -Dtest=ModuleStructureTest#shouldGenerateModuleDocumentation
```

### Executar Todos os Testes

```bash
# Suite completa (89 testes)
mvn test

# Com cobertura
mvn clean verify
```

Documentação: **[README.md § Testes](../README.md#-testes)**

---

## 🏗️ Estrutura de Módulos

### Visão Geral

```
com.jetski/
├── shared/          # Infraestrutura compartilhada
│   ├── security/    # ✅ API pública (Named Interface)
│   └── internal/    # 🔒 Implementação privada
│
├── usuarios/        # Gestão de usuários e membros
│   ├── api/         # ✅ Controllers, DTOs
│   ├── domain/      # Entidades
│   └── internal/    # 🔒 Serviços, repositories
│
└── locacoes/        # Gestão de locações (futuro)
    ├── api/
    ├── domain/
    └── internal/
```

Documentação: **[README.md § Estrutura](../README.md#estrutura-atual)**

### Regras de Dependência

```
usuarios → shared::security ✅
locacoes → shared::security ✅
locacoes → usuarios ❌ (usar eventos)
shared → usuarios ❌ (Dependency Inversion)
```

Documentação: **[README.md § Regras](../README.md#regras-de-dependência)**

---

## 📈 Métricas e Status

### Status Atual (v0.2.0)

| Métrica | Valor |
|---------|-------|
| Módulos Implementados | 2 (shared, usuarios) |
| Testes Passando | 89 ✅ |
| Cobertura | 60% |
| Ciclos de Dependência | 0 ✅ |
| Testes de Arquitetura | 6 ✅ |

Documentação: **[MODULAR_SUMMARY.md § Métricas](./MODULAR_SUMMARY.md#2-métricas)**

### Roadmap

- [x] Fundação modular
- [x] Testes de arquitetura
- [x] Documentação completa
- [ ] Módulo locacoes
- [ ] Comunicação via eventos

Documentação: **[MODULAR_SUMMARY.md § Roadmap](./MODULAR_SUMMARY.md#-roadmap)**

---

## 🔧 Ferramentas e Tecnologias

### Stack Principal

- **Java 21** - LTS com Virtual Threads
- **Spring Boot 3.3** - Framework
- **Spring Modulith 1.1.3** - Validação modular
- **PostgreSQL 16** - Database com RLS
- **Maven 3.9+** - Build

Documentação: **[README.md § Tecnologias](../README.md#-tecnologias)**

### Testes

- **JUnit 5** - Unit tests
- **Mockito** - Mocking
- **Testcontainers** - Integration tests
- **Spring Modulith** - Architecture tests

Documentação: **[README.md § Testes](../README.md#-testes)**

---

## 🌐 Multi-tenancy

### Estratégia

**Row Level Security (RLS)** do PostgreSQL:
- Isolamento automático por `tenant_id`
- Políticas RLS em todas as tabelas
- TenantFilter valida acesso

### Fluxo de Requisição

```
Client → TenantFilter → TenantAccessService → PostgreSQL RLS
```

Documentação: **[README.md § Multi-tenancy](../README.md#-multi-tenancy)**

---

## 📞 Referências Externas

### Spring Modulith

- [Documentação Oficial](https://docs.spring.io/spring-modulith/reference/)
- [GitHub Repository](https://github.com/spring-projects/spring-modulith)

### Padrões e Conceitos

- [Domain-Driven Design](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [Modular Monolith](https://www.kamilgrzybek.com/blog/posts/modular-monolith-primer)
- [Dependency Inversion Principle](https://en.wikipedia.org/wiki/Dependency_inversion_principle)

---

## 🔍 Busca Rápida

### Como fazer...

| Tarefa | Documento | Seção |
|--------|-----------|-------|
| Configurar ambiente | README.md | Setup |
| Adicionar módulo | MIGRATION_GUIDE.md | Próximos Módulos |
| Entender arquitetura | ARCHITECTURE.md | TO BE |
| Quebrar ciclo de dependência | MIGRATION_GUIDE.md | Problemas Comuns |
| Gerar diagramas | README.md | Spring Modulith |
| Rodar testes | README.md | Testes |
| Configurar Keycloak | README.md | Setup § Keycloak |
| Migrations Flyway | README.md | Migrations |

---

## 📝 Contribuindo

Ao adicionar documentação:

1. Atualizar este índice
2. Adicionar diagramas Mermaid (GitHub-friendly)
3. Incluir exemplos de código
4. Atualizar métricas e status

---

## 📅 Histórico de Versões

| Versão | Data | Mudanças |
|--------|------|----------|
| 1.0 | 2025-10-18 | Documentação inicial da arquitetura modular |
| 0.2.0 | 2025-10-18 | Migração para monolito modular concluída |
| 0.1.0 | - | Monolito tradicional |

---

**Última atualização:** 2025-10-18
**Mantenedor:** Jetski Development Team
