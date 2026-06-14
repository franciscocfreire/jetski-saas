-- V023: Create bonus_vendedor table
-- Tracks bonus achievements for sellers based on sales above base price

CREATE TABLE bonus_vendedor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    vendedor_id UUID NOT NULL REFERENCES vendedor(id) ON DELETE CASCADE,
    meta_atingida INTEGER NOT NULL,
    valor_bonus NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    aprovado_por UUID,
    aprovado_em TIMESTAMPTZ,
    pago_por UUID,
    pago_em TIMESTAMPTZ,
    referencia_pagamento VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT bonus_status_check CHECK (status IN ('PENDENTE', 'APROVADO', 'PAGO', 'CANCELADO'))
);

-- Indexes
CREATE INDEX idx_bonus_vendedor_tenant ON bonus_vendedor(tenant_id);
CREATE INDEX idx_bonus_vendedor_vendedor ON bonus_vendedor(tenant_id, vendedor_id);
CREATE INDEX idx_bonus_vendedor_status ON bonus_vendedor(tenant_id, status);

-- RLS
ALTER TABLE bonus_vendedor ENABLE ROW LEVEL SECURITY;
ALTER TABLE bonus_vendedor FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_bonus_vendedor ON bonus_vendedor
    USING (tenant_id = get_current_tenant_id());

COMMENT ON TABLE bonus_vendedor IS 'Bonus achievements for sellers based on sales above base price. Cumulative and never resets.';
COMMENT ON COLUMN bonus_vendedor.meta_atingida IS 'The milestone achieved (e.g., 50, 100, 150 for every 50 sales)';
COMMENT ON COLUMN bonus_vendedor.valor_bonus IS 'Bonus value in tenant currency';
COMMENT ON COLUMN bonus_vendedor.status IS 'PENDENTE -> APROVADO -> PAGO (or CANCELADO)';
