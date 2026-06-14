-- V028: Adicionar total_diarias_vendedores ao fechamento mensal
-- Author: Jetski Team
-- Date: 2025-01-16

-- Adiciona coluna para total de diárias de vendedores no fechamento mensal
ALTER TABLE fechamento_mensal
    ADD COLUMN IF NOT EXISTS total_diarias_vendedores NUMERIC(12,2) DEFAULT 0;

-- Comentário na coluna
COMMENT ON COLUMN fechamento_mensal.total_diarias_vendedores IS 'Total de diárias de vendedores consolidadas no mês';
