-- =====================================================
-- Migration: V1013 - Create fuel_policy table
-- Descrição: Políticas de cobrança de combustível (RN03)
-- =====================================================

-- Drop if exists (para corrigir estrutura antiga com BIGINT)
DROP TABLE IF EXISTS fuel_policy CASCADE;

CREATE TABLE fuel_policy (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    nome VARCHAR(100) NOT NULL,

    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('INCLUSO', 'MEDIDO', 'TAXA_FIXA')),
    aplicavel_a VARCHAR(20) NOT NULL CHECK (aplicavel_a IN ('GLOBAL', 'MODELO', 'JETSKI')),
    referencia_id UUID,

    valor_taxa_por_hora NUMERIC(10,2) CHECK (valor_taxa_por_hora >= 0),
    comissionavel BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    prioridade INTEGER NOT NULL DEFAULT 0,
    descricao VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_taxa_fixa_valor CHECK (
        tipo != 'TAXA_FIXA' OR valor_taxa_por_hora IS NOT NULL
    ),
    CONSTRAINT chk_referencia_id CHECK (
        (aplicavel_a = 'GLOBAL' AND referencia_id IS NULL) OR
        (aplicavel_a != 'GLOBAL' AND referencia_id IS NOT NULL)
    )
);

-- Índices para hierarquia de busca
CREATE INDEX idx_fuel_policy_tenant ON fuel_policy(tenant_id);
CREATE INDEX idx_fuel_policy_aplicacao ON fuel_policy(tenant_id, aplicavel_a, referencia_id);
CREATE INDEX idx_fuel_policy_ativo ON fuel_policy(tenant_id, ativo);

-- Row Level Security (RLS)
ALTER TABLE fuel_policy ENABLE ROW LEVEL SECURITY;

CREATE POLICY fuel_policy_tenant_isolation ON fuel_policy
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

COMMENT ON TABLE fuel_policy IS 'Políticas de cobrança de combustível (RN03): INCLUSO, MEDIDO, TAXA_FIXA';
COMMENT ON COLUMN fuel_policy.tipo IS 'Modo de cobrança: INCLUSO (grátis), MEDIDO (por litro), TAXA_FIXA (por hora)';
COMMENT ON COLUMN fuel_policy.aplicavel_a IS 'Hierarquia: JETSKI > MODELO > GLOBAL (primeira match ganha)';
COMMENT ON COLUMN fuel_policy.referencia_id IS 'ID do jetski ou modelo (NULL se GLOBAL)';
COMMENT ON COLUMN fuel_policy.comissionavel IS 'Se custo de combustível gera comissão (RN04: default FALSE)';
