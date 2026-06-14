-- V020: Align fechamento_mensal table with FechamentoMensal entity
-- Add missing columns that the entity expects

-- Add missing columns
ALTER TABLE fechamento_mensal ADD COLUMN IF NOT EXISTS bloqueado BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE fechamento_mensal ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'aberto';
ALTER TABLE fechamento_mensal ADD COLUMN IF NOT EXISTS operador_id UUID;
ALTER TABLE fechamento_mensal ADD COLUMN IF NOT EXISTS relatorio_url TEXT;
ALTER TABLE fechamento_mensal ADD COLUMN IF NOT EXISTS resultado_liquido NUMERIC(12,2) DEFAULT 0;
ALTER TABLE fechamento_mensal ADD COLUMN IF NOT EXISTS total_faturado NUMERIC(12,2) DEFAULT 0;
ALTER TABLE fechamento_mensal ADD COLUMN IF NOT EXISTS total_custos NUMERIC(12,2) DEFAULT 0;
ALTER TABLE fechamento_mensal ADD COLUMN IF NOT EXISTS total_manutencoes NUMERIC(12,2) DEFAULT 0;
ALTER TABLE fechamento_mensal ADD COLUMN IF NOT EXISTS dt_fechamento TIMESTAMPTZ;

-- Migrate data from old columns to new columns (if applicable)
UPDATE fechamento_mensal SET total_faturado = total_receita WHERE total_faturado = 0 OR total_faturado IS NULL;
UPDATE fechamento_mensal SET total_custos = total_combustivel WHERE total_custos = 0 OR total_custos IS NULL;
UPDATE fechamento_mensal SET total_manutencoes = total_manutencao WHERE total_manutencoes = 0 OR total_manutencoes IS NULL;
UPDATE fechamento_mensal SET dt_fechamento = fechado_em WHERE dt_fechamento IS NULL;

COMMENT ON TABLE fechamento_mensal IS 'Monthly financial closure with all consolidated data';
