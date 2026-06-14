-- V018: Align politica_comissao table with PoliticaComissao entity
-- The original V001 schema doesn't match the Java entity structure

-- Step 1: Backup and drop the old table (no data to preserve)
DROP TABLE IF EXISTS politica_comissao CASCADE;

-- Step 2: Recreate with correct structure matching the entity
CREATE TABLE politica_comissao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Hierarchical level (determines priority)
    nivel VARCHAR(20) NOT NULL,

    -- Commission calculation type
    tipo VARCHAR(20) NOT NULL,

    -- Descriptive info
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(500),

    -- Filter criteria
    vendedor_id UUID REFERENCES vendedor(id),
    modelo_id UUID REFERENCES modelo(id),
    codigo_campanha VARCHAR(50),
    duracao_min_minutos INTEGER,
    duracao_max_minutos INTEGER,

    -- Commission values
    percentual_comissao NUMERIC(5,2),
    valor_fixo NUMERIC(10,2),
    percentual_extra NUMERIC(5,2),

    -- Validity period (for campaigns)
    vigencia_inicio TIMESTAMPTZ,
    vigencia_fim TIMESTAMPTZ,

    -- Control
    ativa BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,

    -- Constraints
    CONSTRAINT politica_comissao_nivel_check CHECK (nivel IN ('CAMPANHA', 'MODELO', 'DURACAO', 'VENDEDOR')),
    CONSTRAINT politica_comissao_tipo_check CHECK (tipo IN ('PERCENTUAL', 'VALOR_FIXO', 'ESCALONADO'))
);

-- Step 3: Create indexes
CREATE INDEX idx_politica_tenant_nivel ON politica_comissao(tenant_id, nivel);
CREATE INDEX idx_politica_tenant_vendedor ON politica_comissao(tenant_id, vendedor_id);
CREATE INDEX idx_politica_tenant_modelo ON politica_comissao(tenant_id, modelo_id);
CREATE INDEX idx_politica_tenant_campanha ON politica_comissao(tenant_id, codigo_campanha);

-- Step 4: Enable RLS
ALTER TABLE politica_comissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE politica_comissao FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_politica_comissao ON politica_comissao
    USING (tenant_id = get_current_tenant_id());

-- Step 5: Create update trigger
CREATE TRIGGER update_politica_comissao_updated_at
    BEFORE UPDATE ON politica_comissao
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Step 6: Update comissao.politica_id foreign key (if exists, recreate)
-- First check if the constraint exists and drop it
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'comissao_politica_id_fkey') THEN
        ALTER TABLE comissao DROP CONSTRAINT comissao_politica_id_fkey;
    END IF;
END $$;

-- Now add the correct foreign key
ALTER TABLE comissao
    ADD CONSTRAINT comissao_politica_id_fkey
    FOREIGN KEY (politica_id) REFERENCES politica_comissao(id);

COMMENT ON TABLE politica_comissao IS 'Commission policies with hierarchical evaluation (RN04: CAMPANHA > MODELO > DURACAO > VENDEDOR)';
COMMENT ON COLUMN politica_comissao.nivel IS 'Hierarchy level: CAMPANHA (1), MODELO (2), DURACAO (3), VENDEDOR (4)';
COMMENT ON COLUMN politica_comissao.tipo IS 'Calculation type: PERCENTUAL, VALOR_FIXO, ESCALONADO';

-- =============================================================================
-- SEED DATA: Commission Policies for ACME tenant
-- Required for commission calculation to work (RN04)
-- =============================================================================

DO $$
DECLARE
    admin_id UUID := '00000000-aaaa-aaaa-aaaa-000000000001';
    tenant_acme UUID := 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';
    vendedor_interno UUID := 'a1111111-1111-1111-1111-111111111111';
    vendedor_parceiro UUID := 'a2222222-2222-2222-2222-222222222222';
BEGIN
    -- 1. Default policy for internal seller (10%)
    INSERT INTO politica_comissao (
        id, tenant_id, nivel, tipo, nome, descricao,
        vendedor_id, percentual_comissao, ativa, created_at, updated_at, created_by
    ) VALUES (
        'b1111111-1111-1111-1111-111111111111',
        tenant_acme,
        'VENDEDOR',
        'PERCENTUAL',
        'Comissão Padrão - Vendedor Interno',
        'Comissão de 10% para vendedor interno (ACME)',
        vendedor_interno,
        10.00,
        TRUE,
        NOW(),
        NOW(),
        admin_id
    ) ON CONFLICT DO NOTHING;

    -- 2. Policy for external partner (15%)
    INSERT INTO politica_comissao (
        id, tenant_id, nivel, tipo, nome, descricao,
        vendedor_id, percentual_comissao, ativa, created_at, updated_at, created_by
    ) VALUES (
        'b2222222-2222-2222-2222-222222222222',
        tenant_acme,
        'VENDEDOR',
        'PERCENTUAL',
        'Comissão Padrão - Roberto Parceiro',
        'Comissão de 15% para parceiro externo',
        vendedor_parceiro,
        15.00,
        TRUE,
        NOW(),
        NOW(),
        admin_id
    ) ON CONFLICT DO NOTHING;

    -- 3. Duration-based policy (bonus for long rentals)
    INSERT INTO politica_comissao (
        id, tenant_id, nivel, tipo, nome, descricao,
        duracao_min_minutos, percentual_comissao, percentual_extra,
        ativa, created_at, updated_at, created_by
    ) VALUES (
        'b3333333-3333-3333-3333-333333333333',
        tenant_acme,
        'DURACAO',
        'ESCALONADO',
        'Bonus Locação Longa',
        'Comissão escalonada: 10% até 2h, 12% acima de 2h',
        120,  -- duracao_min_minutos
        10.00,  -- percentual base
        12.00,  -- percentual extra acima de 120min
        TRUE,
        NOW(),
        NOW(),
        admin_id
    ) ON CONFLICT DO NOTHING;

END $$;
