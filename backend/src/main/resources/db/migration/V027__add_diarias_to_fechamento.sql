-- V027: Adicionar campo para total de diárias de vendedores no fechamento diário
-- Separado de despesas operacionais para melhor controle

ALTER TABLE fechamento_diario
    ADD COLUMN IF NOT EXISTS total_diarias_vendedores NUMERIC(12,2) DEFAULT 0;

COMMENT ON COLUMN fechamento_diario.total_diarias_vendedores IS 'Total de diarias pagas aos vendedores no dia';
