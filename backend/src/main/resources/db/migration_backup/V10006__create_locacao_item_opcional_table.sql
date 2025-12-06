-- V10006: Create locacao_item_opcional table
-- Purpose: Link optional items to specific rentals with actual charged price
--
-- Business Rules:
-- - Items can be added at any time (check-in, during rental, or after check-out)
-- - valorCobrado may differ from valorOriginal (negotiation allowed)
-- - Same item cannot be added twice to the same rental

CREATE TABLE locacao_item_opcional (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    locacao_id UUID NOT NULL REFERENCES locacao(id) ON DELETE CASCADE,
    item_opcional_id UUID NOT NULL REFERENCES item_opcional(id),
    valor_cobrado DECIMAL(10, 2) NOT NULL,
    valor_original DECIMAL(10, 2) NOT NULL,
    observacao VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_locacao_item UNIQUE (locacao_id, item_opcional_id)
);

-- Indexes for common queries
CREATE INDEX idx_locacao_item_opcional_tenant_id ON locacao_item_opcional(tenant_id);
CREATE INDEX idx_locacao_item_opcional_locacao_id ON locacao_item_opcional(locacao_id);
CREATE INDEX idx_locacao_item_opcional_item_id ON locacao_item_opcional(item_opcional_id);

-- RLS policy for tenant isolation
ALTER TABLE locacao_item_opcional ENABLE ROW LEVEL SECURITY;
ALTER TABLE locacao_item_opcional FORCE ROW LEVEL SECURITY;

CREATE POLICY locacao_item_opcional_tenant_isolation ON locacao_item_opcional
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

COMMENT ON TABLE locacao_item_opcional IS 'Optional items attached to specific rentals';
COMMENT ON COLUMN locacao_item_opcional.valor_cobrado IS 'Actual price charged (may be negotiated)';
COMMENT ON COLUMN locacao_item_opcional.valor_original IS 'Catalog price at time of addition';
COMMENT ON COLUMN locacao_item_opcional.observacao IS 'Note explaining price adjustment if negotiated';
