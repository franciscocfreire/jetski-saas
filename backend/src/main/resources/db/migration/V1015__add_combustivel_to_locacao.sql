-- =====================================================
-- Migration: V1015 - Add combustivel fields to locacao
-- Descrição: Adicionar campos de combustível à tabela locacao
-- =====================================================

ALTER TABLE locacao
    ADD COLUMN combustivel_custo NUMERIC(10,2) DEFAULT 0,
    ADD COLUMN fuel_policy_id BIGINT REFERENCES fuel_policy(id);

CREATE INDEX idx_locacao_fuel_policy ON locacao(tenant_id, fuel_policy_id);

COMMENT ON COLUMN locacao.combustivel_custo IS 'Custo de combustível calculado pela política (RN03), somado ao valor_calculado';
COMMENT ON COLUMN locacao.fuel_policy_id IS 'Política de combustível aplicada nesta locação (para rastreabilidade)';
