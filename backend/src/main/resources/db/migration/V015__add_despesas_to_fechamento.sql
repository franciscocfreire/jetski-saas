-- ============================================================================
-- V015: Adicionar total_despesas_operacionais aos fechamentos
-- ============================================================================
-- Inclui o total de despesas operacionais no fechamento diario e mensal
-- para consolidacao financeira completa.
-- ============================================================================

-- Adicionar coluna ao fechamento_diario
ALTER TABLE fechamento_diario
    ADD COLUMN IF NOT EXISTS total_despesas_operacionais NUMERIC(12,2) DEFAULT 0;

-- Adicionar coluna ao fechamento_mensal
ALTER TABLE fechamento_mensal
    ADD COLUMN IF NOT EXISTS total_despesas_operacionais NUMERIC(12,2) DEFAULT 0;

-- Comments
COMMENT ON COLUMN fechamento_diario.total_despesas_operacionais IS 'Total de despesas operacionais do dia (diarias, refeicao, etc)';
COMMENT ON COLUMN fechamento_mensal.total_despesas_operacionais IS 'Total de despesas operacionais do mes';
