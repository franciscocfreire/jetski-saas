---
story_id: STORY-002
epic: EPIC-01
title: Implementar Row Level Security (RLS) no PostgreSQL
status: TODO
priority: CRITICAL
estimate: 8
assignee: Unassigned
started_at: null
completed_at: null
tags: [backend, database, multi-tenant, security, postgresql]
dependencies: [STORY-001]
---

# STORY-002: Implementar Row Level Security (RLS) no PostgreSQL

## Como
Backend Developer

## Quero
Que todas as queries SQL sejam automaticamente filtradas por `tenant_id` usando RLS

## Para que
Garantir isolamento absoluto de dados entre tenants sem depender de WHERE clauses manuais

## Critérios de Aceite

- [ ] **CA1:** RLS habilitado em todas as tabelas operacionais (exceto `tenant`, `plano`, `usuario`)
- [ ] **CA2:** Política RLS criada: `USING (tenant_id = current_setting('app.tenant_id')::uuid)`
- [ ] **CA3:** `TenantInterceptor` executa `SET LOCAL app.tenant_id = '<uuid>'` antes de cada query
- [ ] **CA4:** Testes comprovam que tenant A não consegue acessar dados do tenant B via SQL direto
- [ ] **CA5:** Índices compostos `(tenant_id, *)` criados para performance
- [ ] **CA6:** Explain analyze mostra que RLS não degrada performance significativamente (< 10%)

## Tarefas Técnicas

- [ ] Criar migration Flyway `V006__enable_rls.sql`
- [ ] Habilitar RLS: `ALTER TABLE <table> ENABLE ROW LEVEL SECURITY;`
- [ ] Criar política para cada tabela
- [ ] Criar `TenantInterceptor` implements `StatementInspector` (Hibernate)
- [ ] Executar `SET LOCAL app.tenant_id` no início de cada transação
- [ ] Criar índices compostos `(tenant_id, id)`, `(tenant_id, fk)`
- [ ] Testes de integração: criar 2 tenants, inserir dados, validar isolamento
- [ ] Benchmark de performance com/sem RLS

## Definição de Pronto (DoD)

- [ ] Code review aprovado
- [ ] Testes de isolamento passando (100% de isolamento)
- [ ] Performance validada (< 10% overhead)
- [ ] Documentação de RLS atualizada

## Notas Técnicas

### Migration RLS

```sql
-- V006__enable_rls.sql

-- Habilitar RLS em tabelas operacionais
ALTER TABLE modelo ENABLE ROW LEVEL SECURITY;
ALTER TABLE jetski ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendedor ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE reserva ENABLE ROW LEVEL SECURITY;
ALTER TABLE locacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE foto ENABLE ROW LEVEL SECURITY;
ALTER TABLE abastecimento ENABLE ROW LEVEL SECURITY;
ALTER TABLE os_manutencao ENABLE ROW LEVEL SECURITY;
ALTER TABLE fechamento_diario ENABLE ROW LEVEL SECURITY;
ALTER TABLE fechamento_mensal ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria ENABLE ROW LEVEL SECURITY;

-- Criar políticas
CREATE POLICY modelo_tenant_isolation ON modelo
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

CREATE POLICY jetski_tenant_isolation ON jetski
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

-- Repetir para todas as tabelas...

-- Índices compostos para performance
CREATE INDEX idx_modelo_tenant_id ON modelo(tenant_id, id);
CREATE INDEX idx_jetski_tenant_id ON jetski(tenant_id, id);
-- Repetir para todas as tabelas...
```

### TenantInterceptor

```java
@Component
public class TenantInterceptor implements StatementInspector {

    @Override
    public String inspect(String sql) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant ID not set in context");
        }

        // Executar SET LOCAL antes da primeira query da transação
        Connection conn = // obter connection do EntityManager
        try (PreparedStatement stmt = conn.prepareStatement(
                "SET LOCAL app.tenant_id = ?")) {
            stmt.setObject(1, tenantId);
            stmt.execute();
        }

        return sql;  // SQL inalterado, RLS cuida do filtro
    }
}
```

## Blockers

- [ ] Depende de STORY-001 (TenantContext precisa estar funcionando)

## Links

- **Epic:** [EPIC-01](../../stories/epics/epic-01-multi-tenant-foundation.md)
- **Depends on:** [STORY-001](./story-001-tenant-context-filter.md)

## Changelog

- 2025-01-15: História criada
