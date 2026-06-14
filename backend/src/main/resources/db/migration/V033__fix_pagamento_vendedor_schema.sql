-- Migration V033: Fix pagamento_vendedor schema
-- Adds missing vendedor_nome column and makes PIX fields nullable for DINHEIRO payments

-- 1. Add missing vendedor_nome column
ALTER TABLE pagamento_vendedor
ADD COLUMN vendedor_nome VARCHAR(150);

-- 2. Update existing records with vendedor name from vendedor table
UPDATE pagamento_vendedor pv
SET vendedor_nome = v.nome
FROM vendedor v
WHERE pv.vendedor_id = v.id AND pv.vendedor_nome IS NULL;

-- 3. Make vendedor_nome NOT NULL after population
ALTER TABLE pagamento_vendedor
ALTER COLUMN vendedor_nome SET NOT NULL;

-- 4. Make PIX fields nullable (for DINHEIRO payments)
ALTER TABLE pagamento_vendedor
ALTER COLUMN chave_pix DROP NOT NULL;

ALTER TABLE pagamento_vendedor
ALTER COLUMN tipo_chave_pix DROP NOT NULL;

ALTER TABLE pagamento_vendedor
ALTER COLUMN referencia_pagamento DROP NOT NULL;

-- 5. Make pago_por NOT NULL (as per entity requirement)
-- First update any NULL values if they exist
UPDATE pagamento_vendedor
SET pago_por = (SELECT id FROM usuario LIMIT 1)
WHERE pago_por IS NULL;

ALTER TABLE pagamento_vendedor
ALTER COLUMN pago_por SET NOT NULL;

-- 6. Add comment for vendedor_nome
COMMENT ON COLUMN pagamento_vendedor.vendedor_nome IS 'Snapshot of seller name at time of payment';
