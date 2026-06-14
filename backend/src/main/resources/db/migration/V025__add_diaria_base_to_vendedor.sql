-- V025: Adicionar campo diaria_base ao vendedor
-- Permite configurar valor base da diária por vendedor

ALTER TABLE vendedor ADD COLUMN IF NOT EXISTS diaria_base NUMERIC(10,2) DEFAULT 0;

COMMENT ON COLUMN vendedor.diaria_base IS 'Valor base da diaria do vendedor (pode ser ajustado por dia)';

-- Index para queries de vendedores ativos com diária
CREATE INDEX IF NOT EXISTS idx_vendedor_ativo_diaria ON vendedor(tenant_id, ativo) WHERE ativo = true;
