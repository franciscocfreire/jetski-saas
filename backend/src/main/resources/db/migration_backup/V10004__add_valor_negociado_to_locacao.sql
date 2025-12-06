-- V10004: Add valor_negociado and motivo_desconto columns to locacao table
-- Purpose: Allow operators to set negotiated prices at check-in instead of using
--          the calculated value from hourly rate.
--
-- Business Rule:
-- - If valor_negociado is set at check-in, it will be used as valor_base at check-out
-- - If valor_negociado is null, valor_base is calculated from (minutos_faturaveis / 60) * preco_base_hora
-- - motivo_desconto provides audit trail for why the price was negotiated

ALTER TABLE locacao
ADD COLUMN valor_negociado DECIMAL(10, 2),
ADD COLUMN motivo_desconto VARCHAR(255);

COMMENT ON COLUMN locacao.valor_negociado IS 'Negotiated price set at check-in. If set, used as valor_base instead of calculated value.';
COMMENT ON COLUMN locacao.motivo_desconto IS 'Reason for negotiated price/discount (e.g., "Cliente frequente", "Promoção verão")';
