-- Add PIX key fields to vendedor table
-- PIX is the Brazilian instant payment system

ALTER TABLE vendedor
ADD COLUMN chave_pix VARCHAR(100),
ADD COLUMN tipo_chave_pix VARCHAR(20);

-- Constraint for valid PIX key types
ALTER TABLE vendedor
ADD CONSTRAINT vendedor_tipo_chave_pix_check
CHECK (tipo_chave_pix IS NULL OR tipo_chave_pix IN ('CPF', 'CNPJ', 'EMAIL', 'TELEFONE', 'ALEATORIA'));

-- Constraint: if chave_pix is set, tipo_chave_pix must also be set (and vice-versa)
ALTER TABLE vendedor
ADD CONSTRAINT vendedor_pix_consistency
CHECK ((chave_pix IS NULL AND tipo_chave_pix IS NULL) OR
       (chave_pix IS NOT NULL AND tipo_chave_pix IS NOT NULL));

-- Comments
COMMENT ON COLUMN vendedor.chave_pix IS 'PIX key for payment transfers (CPF, email, phone, or random key)';
COMMENT ON COLUMN vendedor.tipo_chave_pix IS 'Type of PIX key: CPF, CNPJ, EMAIL, TELEFONE, ALEATORIA';
