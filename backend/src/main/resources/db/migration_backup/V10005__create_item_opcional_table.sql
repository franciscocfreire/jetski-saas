-- V10005: Create item_opcional table
-- Purpose: Catalog of optional add-on items that can be attached to rentals
-- Examples: Drone recording, Action cam, GPS tracker, etc.
--
-- Business Rules:
-- - Each tenant manages their own catalog of optional items
-- - Each item has a base price that can be negotiated when adding to rental
-- - Inactive items cannot be added to new rentals

CREATE TABLE item_opcional (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(500),
    preco_base DECIMAL(10, 2) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_item_opcional_tenant_nome UNIQUE (tenant_id, nome)
);

-- Index for common queries
CREATE INDEX idx_item_opcional_tenant_id ON item_opcional(tenant_id);
CREATE INDEX idx_item_opcional_tenant_ativo ON item_opcional(tenant_id, ativo);

-- RLS policy for tenant isolation
ALTER TABLE item_opcional ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_opcional FORCE ROW LEVEL SECURITY;

CREATE POLICY item_opcional_tenant_isolation ON item_opcional
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

COMMENT ON TABLE item_opcional IS 'Catalog of optional add-on items for rentals';
COMMENT ON COLUMN item_opcional.nome IS 'Item name (e.g., Gravação Drone, Action Cam)';
COMMENT ON COLUMN item_opcional.preco_base IS 'Default price for this item';
COMMENT ON COLUMN item_opcional.ativo IS 'Whether item can be added to new rentals';
