-- ============================================================================
-- V011: Alinhamento da tabela fechamento_diario com a entidade JPA
-- ============================================================================
-- A entidade FechamentoDiario.java espera colunas que não existem no schema.
-- Esta migration adiciona/renomeia as colunas necessárias.
-- ============================================================================

-- 1. Renomear 'data' para 'dt_referencia'
ALTER TABLE fechamento_diario RENAME COLUMN data TO dt_referencia;

-- 2. Adicionar coluna 'bloqueado' (default false, não bloqueado)
ALTER TABLE fechamento_diario ADD COLUMN IF NOT EXISTS bloqueado BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Adicionar coluna 'operador_id' (quem fez o fechamento)
ALTER TABLE fechamento_diario ADD COLUMN IF NOT EXISTS operador_id UUID REFERENCES usuario(id);

-- 4. Renomear 'total_receita' para 'total_faturado'
ALTER TABLE fechamento_diario RENAME COLUMN total_receita TO total_faturado;

-- 5. Adicionar colunas de totais por forma de pagamento
ALTER TABLE fechamento_diario ADD COLUMN IF NOT EXISTS total_dinheiro NUMERIC(12,2) DEFAULT 0;
ALTER TABLE fechamento_diario ADD COLUMN IF NOT EXISTS total_cartao NUMERIC(12,2) DEFAULT 0;
ALTER TABLE fechamento_diario ADD COLUMN IF NOT EXISTS total_pix NUMERIC(12,2) DEFAULT 0;

-- 6. Renomear 'fechado_em' para 'dt_fechamento' (se existir)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'fechamento_diario' AND column_name = 'fechado_em') THEN
        ALTER TABLE fechamento_diario RENAME COLUMN fechado_em TO dt_fechamento;
    END IF;
END $$;

-- 7. Adicionar coluna 'status' se não existir
ALTER TABLE fechamento_diario ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'aberto';

-- 8. Adicionar coluna 'divergencias_json' para armazenar discrepâncias encontradas
ALTER TABLE fechamento_diario ADD COLUMN IF NOT EXISTS divergencias_json JSONB;

-- 9. Atualizar constraint único para usar dt_referencia
ALTER TABLE fechamento_diario DROP CONSTRAINT IF EXISTS fechamento_diario_tenant_id_data_key;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fechamento_diario_unique') THEN
        ALTER TABLE fechamento_diario ADD CONSTRAINT fechamento_diario_unique UNIQUE (tenant_id, dt_referencia);
    END IF;
EXCEPTION WHEN others THEN
    -- Constraint já existe ou falhou por outra razão, continua
    RAISE NOTICE 'Constraint fechamento_diario_unique already exists or failed: %', SQLERRM;
END $$;

-- 10. Marcar fechamentos existentes como bloqueados (se já foram fechados)
UPDATE fechamento_diario SET bloqueado = TRUE WHERE dt_fechamento IS NOT NULL;

-- 11. Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON fechamento_diario TO jetski_app;

COMMENT ON COLUMN fechamento_diario.dt_referencia IS 'Data de referência do fechamento (dia consolidado)';
COMMENT ON COLUMN fechamento_diario.bloqueado IS 'Se true, impede edições retroativas nas locações desta data';
COMMENT ON COLUMN fechamento_diario.operador_id IS 'Usuário que realizou/consolidou o fechamento';
COMMENT ON COLUMN fechamento_diario.status IS 'Status do fechamento: aberto, fechado, aprovado';
