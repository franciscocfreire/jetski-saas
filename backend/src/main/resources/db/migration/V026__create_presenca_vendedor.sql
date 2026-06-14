-- V026: Criar tabela de presença diária de vendedores
-- Controle de presença com suporte a integral/meia-diária e ajuste manual

CREATE TABLE presenca_vendedor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    vendedor_id UUID NOT NULL REFERENCES vendedor(id) ON DELETE CASCADE,
    dt_referencia DATE NOT NULL,
    tipo VARCHAR(20) NOT NULL DEFAULT 'INTEGRAL',
    valor_diaria NUMERIC(10,2) NOT NULL,
    valor_ajustado NUMERIC(10,2),
    motivo_ajuste VARCHAR(255),
    registrado_por UUID REFERENCES usuario(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT presenca_tipo_check CHECK (tipo IN ('INTEGRAL', 'MEIA_DIARIA')),
    CONSTRAINT presenca_valor_positivo CHECK (valor_diaria >= 0),
    CONSTRAINT presenca_unique UNIQUE (tenant_id, vendedor_id, dt_referencia)
);

-- Indexes para performance
CREATE INDEX idx_presenca_vendedor_tenant ON presenca_vendedor(tenant_id);
CREATE INDEX idx_presenca_vendedor_data ON presenca_vendedor(tenant_id, dt_referencia);
CREATE INDEX idx_presenca_vendedor_vendedor ON presenca_vendedor(tenant_id, vendedor_id);

-- RLS (Row Level Security)
ALTER TABLE presenca_vendedor ENABLE ROW LEVEL SECURITY;
ALTER TABLE presenca_vendedor FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_presenca_vendedor ON presenca_vendedor
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Trigger de updated_at
CREATE OR REPLACE FUNCTION update_presenca_vendedor_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_presenca_vendedor_updated_at_trigger
    BEFORE UPDATE ON presenca_vendedor
    FOR EACH ROW EXECUTE FUNCTION update_presenca_vendedor_updated_at();

-- Comments
COMMENT ON TABLE presenca_vendedor IS 'Registro de presenca diaria dos vendedores';
COMMENT ON COLUMN presenca_vendedor.tipo IS 'Tipo: INTEGRAL (diaria completa 100%), MEIA_DIARIA (50%)';
COMMENT ON COLUMN presenca_vendedor.valor_diaria IS 'Valor calculado da diaria (diariaBase * fator do tipo)';
COMMENT ON COLUMN presenca_vendedor.valor_ajustado IS 'Valor final se houver ajuste manual';
COMMENT ON COLUMN presenca_vendedor.motivo_ajuste IS 'Motivo do ajuste (obrigatorio se valor_ajustado != null)';
