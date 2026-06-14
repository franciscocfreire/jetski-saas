-- V022: Add venda_acima_preco_base flag to comissao
-- Tracks whether the sale was at/above the base price (for bonus calculation)

ALTER TABLE comissao ADD COLUMN IF NOT EXISTS venda_acima_preco_base BOOLEAN DEFAULT true;

-- Index for counting bonus-eligible sales
CREATE INDEX IF NOT EXISTS idx_comissao_venda_acima_base
ON comissao(tenant_id, vendedor_id, venda_acima_preco_base)
WHERE venda_acima_preco_base = true;

COMMENT ON COLUMN comissao.venda_acima_preco_base IS 'True if the rental was sold at or above the base price (eligible for bonus)';
