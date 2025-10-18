# 📚 Documentação Jetski Backend

Bem-vindo à documentação completa do backend Jetski SaaS!

---

## 🚀 Início Rápido

**Novo no projeto?** Comece aqui:

1. **[../README.md](../README.md)** - Documentação principal
2. **[MODULAR_SUMMARY.md](./MODULAR_SUMMARY.md)** - Resumo da arquitetura modular
3. **[INDEX.md](./INDEX.md)** - Índice completo de toda documentação

---

## 📖 Documentos Disponíveis

### 🎯 Essenciais

| Documento | Descrição | Público-alvo |
|-----------|-----------|--------------|
| **[README.md](../README.md)** | Documentação principal do projeto | Todos |
| **[INDEX.md](./INDEX.md)** | Índice navegável de toda documentação | Todos |

### 🏗️ Arquitetura

| Documento | Descrição | Público-alvo |
|-----------|-----------|--------------|
| **[ARCHITECTURE.md](../ARCHITECTURE.md)** | Análise detalhada AS IS vs TO BE | Arquitetos, Devs Sênior |
| **[MODULAR_SUMMARY.md](./MODULAR_SUMMARY.md)** | Sumário executivo da migração modular | Gestores, Arquitetos |

### 🔧 Desenvolvimento

| Documento | Descrição | Público-alvo |
|-----------|-----------|--------------|
| **[MIGRATION_GUIDE.md](../MIGRATION_GUIDE.md)** | Guia passo a passo de migração | Desenvolvedores |

---

## 📊 Diagramas

### Visão Geral

```
┌──────────────────────────────────────────────────────────┐
│                    JETSKI BACKEND                        │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │ shared   │  │ usuarios │  │ locacoes │  (módulos)  │
│  └──────────┘  └──────────┘  └──────────┘             │
│                                                          │
│  Spring Modulith + PostgreSQL RLS + Keycloak OIDC      │
└──────────────────────────────────────────────────────────┘
```

### Diagramas Detalhados

Todos os diagramas estão nos documentos:
- **AS IS → TO BE**: [README.md](../README.md#-evolução-arquitetural)
- **Módulos**: [ARCHITECTURE.md](../ARCHITECTURE.md)
- **Fluxos**: [README.md Multi-tenancy](../README.md#fluxo-de-requisição)

---

## 🎓 Guias de Uso

### Por Cenário

1. **Primeiro Dia no Projeto**
   - Ler [README.md](../README.md)
   - Seguir setup
   - Rodar testes
   - Explorar código

2. **Adicionar Nova Funcionalidade**
   - Consultar [MIGRATION_GUIDE.md](../MIGRATION_GUIDE.md)
   - Seguir template de módulo
   - Validar com testes de arquitetura

3. **Entender Decisões Arquiteturais**
   - Ler [ARCHITECTURE.md](../ARCHITECTURE.md)
   - Ver comparações AS IS vs TO BE
   - Revisar [MODULAR_SUMMARY.md](./MODULAR_SUMMARY.md)

4. **Resolver Problemas**
   - Consultar [MIGRATION_GUIDE.md § Problemas Comuns](../MIGRATION_GUIDE.md#-problemas-comuns)
   - Ver exemplos de código
   - Rodar testes de validação

---

## 📈 Status do Projeto

### Versão Atual: v0.2.0-SNAPSHOT

✅ **Concluído:**
- Arquitetura modular implementada
- 2 módulos em produção (shared, usuarios)
- 89 testes passando
- 0 ciclos de dependência
- Documentação completa

🚧 **Em Progresso:**
- Módulo locacoes
- Comunicação via eventos

📋 **Próximo:**
- Mais módulos de domínio
- Otimizações de performance

---

## 🔗 Links Rápidos

### Código

- [Testes de Arquitetura](../src/test/java/com/jetski/modulith/ModuleStructureTest.java)
- [Módulo shared](../src/main/java/com/jetski/shared/)
- [Módulo usuarios](../src/main/java/com/jetski/usuarios/)

### Configuração

- [application.yml](../src/main/resources/application.yml)
- [pom.xml](../pom.xml)
- [Migrations Flyway](../src/main/resources/db/migration/)

### Infraestrutura

- [Docker Compose](../../docker-compose.yml)
- [Keycloak Setup](../../infra/keycloak-setup/)
- [OPA Policies](../../infra/opa/policies/)

---

## 🛠️ Ferramentas

### Gerar Documentação

```bash
# Diagramas PlantUML
mvn test -Dtest=ModuleStructureTest#shouldGenerateModuleDocumentation

# Visualizar
ls ../target/spring-modulith-docs/
```

### Validar Arquitetura

```bash
# Testes de arquitetura
mvn test -Dtest=ModuleStructureTest

# Todos os testes
mvn test
```

---

## 📞 Suporte

### Problemas Comuns

Ver: [MIGRATION_GUIDE.md § Problemas Comuns](../MIGRATION_GUIDE.md#-problemas-comuns)

### Perguntas Frequentes

**Q: Como adiciono um novo módulo?**
A: Ver [MIGRATION_GUIDE.md § Próximos Módulos](../MIGRATION_GUIDE.md#-próximos-módulos)

**Q: Por que Spring Modulith?**
A: Ver [MODULAR_SUMMARY.md § Decisões](./MODULAR_SUMMARY.md#-decisões-arquiteturais)

**Q: Como funciona o multi-tenancy?**
A: Ver [README.md § Multi-tenancy](../README.md#-multi-tenancy)

---

## 📝 Contribuindo com Documentação

Ao adicionar ou modificar documentação:

1. ✅ Atualizar [INDEX.md](./INDEX.md)
2. ✅ Adicionar exemplos de código
3. ✅ Incluir diagramas (Mermaid quando possível)
4. ✅ Atualizar métricas em [MODULAR_SUMMARY.md](./MODULAR_SUMMARY.md)

---

## 🎯 Navegação

```
docs/
├── README.md            ← Você está aqui
├── INDEX.md             ← Índice navegável completo
├── MODULAR_SUMMARY.md   ← Sumário executivo
│
../
├── README.md            ← Documentação principal
├── ARCHITECTURE.md      ← Detalhes de arquitetura
└── MIGRATION_GUIDE.md   ← Guia de migração
```

---

**Última atualização:** 2025-10-18
**Versão da documentação:** 1.0
