-- Add payment tracking fields to presenca_vendedor
-- Links attendance records to batch payments

ALTER TABLE presenca_vendedor
ADD COLUMN pagamento_id UUID REFERENCES pagamento_vendedor(id),
ADD COLUMN pago_em TIMESTAMPTZ,
ADD COLUMN pago_por UUID REFERENCES usuario(id);

-- Index for finding unpaid diarias
CREATE INDEX idx_presenca_vendedor_pagamento ON presenca_vendedor(pagamento_id);
CREATE INDEX idx_presenca_vendedor_nao_pagas ON presenca_vendedor(tenant_id, vendedor_id)
    WHERE pagamento_id IS NULL;

-- Comments
COMMENT ON COLUMN presenca_vendedor.pagamento_id IS 'Reference to batch payment that included this attendance';
COMMENT ON COLUMN presenca_vendedor.pago_em IS 'Timestamp when marked as paid';
COMMENT ON COLUMN presenca_vendedor.pago_por IS 'User who marked as paid';
