-- =====================================================
-- V10001: Enable Row Level Security (RLS) on all multi-tenant tables
-- =====================================================
-- This migration enables RLS and creates policies to filter data by tenant_id
-- =====================================================

-- Enable RLS on all operational tables
ALTER TABLE jetski ENABLE ROW LEVEL SECURITY;
ALTER TABLE modelo ENABLE ROW LEVEL SECURITY;
ALTER TABLE reserva ENABLE ROW LEVEL SECURITY;
ALTER TABLE locacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE foto ENABLE ROW LEVEL SECURITY;
ALTER TABLE abastecimento ENABLE ROW LEVEL SECURITY;
ALTER TABLE os_manutencao ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendedor ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE comissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE politica_comissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE fuel_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE fuel_price_day ENABLE ROW LEVEL SECURITY;
ALTER TABLE fechamento_diario ENABLE ROW LEVEL SECURITY;
ALTER TABLE fechamento_mensal ENABLE ROW LEVEL SECURITY;

-- Create RLS policies (skip if already exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'jetski' AND policyname = 'jetski_tenant_isolation') THEN
        CREATE POLICY jetski_tenant_isolation ON jetski USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'modelo' AND policyname = 'modelo_tenant_isolation') THEN
        CREATE POLICY modelo_tenant_isolation ON modelo USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'reserva' AND policyname = 'reserva_tenant_isolation') THEN
        CREATE POLICY reserva_tenant_isolation ON reserva USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'locacao' AND policyname = 'locacao_tenant_isolation') THEN
        CREATE POLICY locacao_tenant_isolation ON locacao USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'foto' AND policyname = 'foto_tenant_isolation') THEN
        CREATE POLICY foto_tenant_isolation ON foto USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'abastecimento' AND policyname = 'abastecimento_tenant_isolation') THEN
        CREATE POLICY abastecimento_tenant_isolation ON abastecimento USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'os_manutencao' AND policyname = 'os_manutencao_tenant_isolation') THEN
        CREATE POLICY os_manutencao_tenant_isolation ON os_manutencao USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'vendedor' AND policyname = 'vendedor_tenant_isolation') THEN
        CREATE POLICY vendedor_tenant_isolation ON vendedor USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'cliente' AND policyname = 'cliente_tenant_isolation') THEN
        CREATE POLICY cliente_tenant_isolation ON cliente USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'comissao' AND policyname = 'comissao_tenant_isolation') THEN
        CREATE POLICY comissao_tenant_isolation ON comissao USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'politica_comissao' AND policyname = 'politica_comissao_tenant_isolation') THEN
        CREATE POLICY politica_comissao_tenant_isolation ON politica_comissao USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'fuel_policy' AND policyname = 'fuel_policy_tenant_isolation') THEN
        CREATE POLICY fuel_policy_tenant_isolation ON fuel_policy USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'fuel_price_day' AND policyname = 'fuel_price_day_tenant_isolation') THEN
        CREATE POLICY fuel_price_day_tenant_isolation ON fuel_price_day USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'fechamento_diario' AND policyname = 'fechamento_diario_tenant_isolation') THEN
        CREATE POLICY fechamento_diario_tenant_isolation ON fechamento_diario USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'fechamento_mensal' AND policyname = 'fechamento_mensal_tenant_isolation') THEN
        CREATE POLICY fechamento_mensal_tenant_isolation ON fechamento_mensal USING (tenant_id::text = current_setting('app.tenant_id', true));
    END IF;
END $$;

-- Note: For INSERT, UPDATE, DELETE operations, Spring Boot's TenantFilter
-- sets app.tenant_id before each query, so RLS will enforce tenant isolation automatically
