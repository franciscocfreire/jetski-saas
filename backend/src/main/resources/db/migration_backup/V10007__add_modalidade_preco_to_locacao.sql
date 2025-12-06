-- V10007: Add modalidade_preco column to locacao table
-- Purpose: Allow operator to select pricing mode at check-in
--
-- Pricing Modes:
-- - PRECO_FECHADO (default): Fixed/negotiated price or calculated from hourly rate
-- - DIARIA: Full day rental price
-- - MEIA_DIARIA: Half day rental price

ALTER TABLE locacao
ADD COLUMN modalidade_preco VARCHAR(20) NOT NULL DEFAULT 'PRECO_FECHADO';

-- Add check constraint for valid values
ALTER TABLE locacao
ADD CONSTRAINT chk_locacao_modalidade_preco
CHECK (modalidade_preco IN ('PRECO_FECHADO', 'DIARIA', 'MEIA_DIARIA'));

COMMENT ON COLUMN locacao.modalidade_preco IS 'Pricing mode: PRECO_FECHADO, DIARIA, MEIA_DIARIA';
