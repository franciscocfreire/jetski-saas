---
story_id: STORY-005
epic: EPIC-01
title: Migrations Flyway - Tabelas Multi-tenant Base
status: TODO
priority: HIGH
estimate: 5
assignee: Unassigned
started_at: null
completed_at: null
tags: [backend, database, flyway, migrations, multi-tenant]
dependencies: [STORY-004]
---

# STORY-005: Migrations Flyway - Tabelas Multi-tenant Base

## Como
Backend Developer

## Quero
Migrations Flyway que criam o schema completo de tabelas multi-tenant

## Para que
O banco de dados esteja estruturado corretamente para suportar múltiplos tenants

## Critérios de Aceite

- [ ] **CA1:** Migrations criam tabelas: `tenant`, `plano`, `assinatura`, `usuario`, `membro`
- [ ] **CA2:** Migrations criam tabelas operacionais: `modelo`, `jetski`, `vendedor`, `cliente`
- [ ] **CA3:** Todas as tabelas operacionais têm coluna `tenant_id UUID NOT NULL`
- [ ] **CA4:** Foreign keys estão configuradas corretamente
- [ ] **CA5:** Constraints e validações estão aplicadas (CHECK, UNIQUE)
- [ ] **CA6:** Flyway executa migrations em ordem correta
- [ ] **CA7:** Script de seed cria tenant de exemplo para desenvolvimento

## Tarefas Técnicas

- [ ] Criar `V001__create_multi_tenant_tables.sql`
- [ ] Criar `V002__create_operational_tables.sql`
- [ ] Criar `V003__create_support_tables.sql`
- [ ] Criar `V004__create_indexes.sql`
- [ ] Criar `V999__seed_dev_data.sql` (apenas para perfil dev)
- [ ] Testar migrations: `mvn flyway:migrate`
- [ ] Testar rollback: `mvn flyway:clean && mvn flyway:migrate`

## Definição de Pronto (DoD)

- [ ] Migrations executam sem erros
- [ ] Schema validado com `pg_dump`
- [ ] Seed data permite testar aplicação

## Notas Técnicas

### V001__create_multi_tenant_tables.sql

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE tenant (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT UNIQUE NOT NULL,
  razao_social TEXT NOT NULL,
  cnpj TEXT,
  timezone TEXT DEFAULT 'America/Sao_Paulo',
  moeda TEXT DEFAULT 'BRL',
  contato JSONB,
  status TEXT CHECK (status IN ('trial','ativo','suspenso','cancelado')) DEFAULT 'trial',
  branding_json JSONB,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE plano (
  id SERIAL PRIMARY KEY,
  nome TEXT NOT NULL,
  limites_json JSONB,
  preco_mensal NUMERIC(10,2)
);

CREATE TABLE assinatura (
  id SERIAL PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  plano_id INT REFERENCES plano(id),
  ciclo TEXT CHECK (ciclo IN ('mensal','anual')) DEFAULT 'mensal',
  dt_inicio DATE NOT NULL,
  dt_fim DATE,
  status TEXT CHECK (status IN ('ativa','suspensa','cancelada')) DEFAULT 'ativa',
  pagamento_cfg_json JSONB
);

CREATE TABLE usuario (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  nome TEXT,
  ativo BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE membro (
  id SERIAL PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  papeis TEXT[] NOT NULL,
  UNIQUE(tenant_id, usuario_id)
);

CREATE INDEX idx_assinatura_tenant ON assinatura(tenant_id);
CREATE INDEX idx_membro_tenant ON membro(tenant_id);
CREATE INDEX idx_membro_usuario ON membro(usuario_id);
```

### V002__create_operational_tables.sql

```sql
CREATE TABLE modelo (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  nome TEXT NOT NULL,
  fabricante TEXT,
  potencia_hp INT,
  preco_base_hora NUMERIC(10,2) NOT NULL CHECK (preco_base_hora > 0),
  tolerancia_min INT DEFAULT 5 CHECK (tolerancia_min >= 0),
  caucao NUMERIC(10,2) DEFAULT 0 CHECK (caucao >= 0),
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE jetski (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  modelo_id UUID REFERENCES modelo(id),
  serie TEXT NOT NULL,
  ano INT,
  horimetro_atual NUMERIC(10,2) DEFAULT 0 CHECK (horimetro_atual >= 0),
  status TEXT CHECK (status IN ('disponivel','locado','manutencao','indisponivel')) DEFAULT 'disponivel',
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  UNIQUE(tenant_id, serie)
);

CREATE TABLE vendedor (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  nome TEXT NOT NULL,
  documento TEXT,
  tipo TEXT CHECK (tipo IN ('interno','parceiro')),
  regra_comissao_json JSONB,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE cliente (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  nome TEXT NOT NULL,
  documento TEXT,
  contato JSONB,
  termo_aceite BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_modelo_tenant ON modelo(tenant_id);
CREATE INDEX idx_jetski_tenant ON jetski(tenant_id);
CREATE INDEX idx_vendedor_tenant ON vendedor(tenant_id);
CREATE INDEX idx_cliente_tenant ON cliente(tenant_id);
```

### V999__seed_dev_data.sql

```sql
-- Tenant de exemplo
INSERT INTO tenant (id, slug, razao_social, cnpj, status)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'acme', 'ACME Jetski Rentals', '12345678000100', 'ativo');

-- Plano básico
INSERT INTO plano (nome, limites_json, preco_mensal)
VALUES ('Basic', '{"frota_max": 10, "usuarios_max": 5}', 99.90);

-- Usuário admin
INSERT INTO usuario (id, email, nome)
VALUES ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'admin@acme.com', 'Admin ACME');

-- Membro (admin)
INSERT INTO membro (tenant_id, usuario_id, papeis)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', '{"ADMIN_TENANT", "GERENTE"}');
```

## Links

- **Epic:** [EPIC-01](../../stories/epics/epic-01-multi-tenant-foundation.md)
- **Depends on:** [STORY-004](./story-004-docker-compose-setup.md)

## Changelog

- 2025-01-15: História criada
