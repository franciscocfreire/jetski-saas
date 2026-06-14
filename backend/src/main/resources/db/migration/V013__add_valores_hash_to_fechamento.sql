-- V013: Adicionar coluna valores_hash para detecção de divergências
-- Permite detectar quando locações foram alteradas após consolidação

-- Adicionar coluna para armazenar hash SHA-256 dos valores consolidados
ALTER TABLE fechamento_diario
ADD COLUMN IF NOT EXISTS valores_hash VARCHAR(64);

ALTER TABLE fechamento_mensal
ADD COLUMN IF NOT EXISTS valores_hash VARCHAR(64);

-- Índice para performance em verificações de divergência
CREATE INDEX IF NOT EXISTS idx_fechamento_diario_tenant_hash
ON fechamento_diario(tenant_id, valores_hash);

CREATE INDEX IF NOT EXISTS idx_fechamento_mensal_tenant_hash
ON fechamento_mensal(tenant_id, valores_hash);

-- Comentários nas colunas
COMMENT ON COLUMN fechamento_diario.valores_hash IS 'Hash SHA-256 dos valores consolidados para detecção de divergências';
COMMENT ON COLUMN fechamento_mensal.valores_hash IS 'Hash SHA-256 dos valores consolidados para detecção de divergências';
