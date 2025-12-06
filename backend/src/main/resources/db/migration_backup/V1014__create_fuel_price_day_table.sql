-- =====================================================
-- Migration: V1014 - Create fuel_price_day table
-- Descrição: Preços médios diários de combustível
-- =====================================================

-- Drop if exists (para corrigir estrutura antiga)
DROP TABLE IF EXISTS fuel_price_day CASCADE;

CREATE TABLE fuel_price_day (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    data DATE NOT NULL,

    preco_medio_litro NUMERIC(10,2) NOT NULL CHECK (preco_medio_litro > 0),
    total_litros_abastecidos NUMERIC(10,3),
    total_custo NUMERIC(10,2),
    qtd_abastecimentos INTEGER,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_fuel_price_day UNIQUE (tenant_id, data)
);

-- Índice para busca por data
CREATE INDEX idx_fuel_price_tenant_data ON fuel_price_day(tenant_id, data);

-- Row Level Security (RLS)
ALTER TABLE fuel_price_day ENABLE ROW LEVEL SECURITY;

CREATE POLICY fuel_price_day_tenant_isolation ON fuel_price_day
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

COMMENT ON TABLE fuel_price_day IS 'Preço médio diário de combustível calculado a partir dos abastecimentos (RN03)';
COMMENT ON COLUMN fuel_price_day.preco_medio_litro IS 'Calculado: total_custo / total_litros_abastecidos';
