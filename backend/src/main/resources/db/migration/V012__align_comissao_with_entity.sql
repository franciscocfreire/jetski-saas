-- V012: Align comissao table with Comissao.java entity
-- Missing columns: data_locacao, tipo_comissao, valor_total_locacao, valor_combustivel,
--                  valor_multas, valor_taxas, politica_nome, politica_nivel, observacoes,
--                  aprovado_por, aprovado_em, pago_por, referencia_pagamento
-- Column renames: percentual -> percentual_aplicado, valor_locacao -> valor_total_locacao
-- Type changes: id bigint -> uuid, politica_id bigint -> uuid

-- Step 1: Drop existing constraints that reference old column types
ALTER TABLE comissao DROP CONSTRAINT IF EXISTS comissao_politica_id_fkey;

-- Step 2: Add new columns first (can be nullable initially)
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS data_locacao TIMESTAMP WITH TIME ZONE;
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS tipo_comissao VARCHAR(20);
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS valor_combustivel NUMERIC(10,2) DEFAULT 0;
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS valor_multas NUMERIC(10,2) DEFAULT 0;
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS valor_taxas NUMERIC(10,2) DEFAULT 0;
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS politica_nome VARCHAR(100);
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS politica_nivel VARCHAR(20);
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS observacoes VARCHAR(500);
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS aprovado_por UUID;
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS aprovado_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS pago_por UUID;
ALTER TABLE comissao ADD COLUMN IF NOT EXISTS referencia_pagamento VARCHAR(100);

-- Step 3: Rename columns if they exist with old names
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'comissao' AND column_name = 'percentual') THEN
        ALTER TABLE comissao RENAME COLUMN percentual TO percentual_aplicado;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'comissao' AND column_name = 'valor_locacao') THEN
        ALTER TABLE comissao RENAME COLUMN valor_locacao TO valor_total_locacao;
    END IF;
END $$;

-- Step 4: Migrate data - set data_locacao from locacao.data_check_out if available
UPDATE comissao c
SET data_locacao = COALESCE(l.data_check_out, l.data_check_in, c.created_at)
FROM locacao l
WHERE c.locacao_id = l.id
AND c.data_locacao IS NULL;

-- Set default tipo_comissao for existing records
UPDATE comissao SET tipo_comissao = 'PERCENTUAL' WHERE tipo_comissao IS NULL;

-- Step 5: Change id column from bigint to uuid
-- This is complex, so we'll create a new table and migrate data

-- Create new id column as uuid
ALTER TABLE comissao ADD COLUMN new_id UUID DEFAULT gen_random_uuid();
UPDATE comissao SET new_id = gen_random_uuid() WHERE new_id IS NULL;

-- Drop old primary key and add new one
ALTER TABLE comissao DROP CONSTRAINT IF EXISTS comissao_pkey;
ALTER TABLE comissao DROP COLUMN id;
ALTER TABLE comissao RENAME COLUMN new_id TO id;
ALTER TABLE comissao ADD PRIMARY KEY (id);

-- Step 6: Change politica_id from bigint to uuid
-- First, drop the column and recreate as UUID (losing the reference for now)
ALTER TABLE comissao DROP COLUMN IF EXISTS politica_id;
ALTER TABLE comissao ADD COLUMN politica_id UUID;

-- Step 7: Make data_locacao and tipo_comissao NOT NULL after migration
ALTER TABLE comissao ALTER COLUMN data_locacao SET NOT NULL;
ALTER TABLE comissao ALTER COLUMN tipo_comissao SET NOT NULL;

-- Step 8: Create indexes for new columns
CREATE INDEX IF NOT EXISTS idx_comissao_tenant_data ON comissao(tenant_id, data_locacao);
CREATE INDEX IF NOT EXISTS idx_comissao_tenant_status ON comissao(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_comissao_tenant_locacao ON comissao(tenant_id, locacao_id);
CREATE INDEX IF NOT EXISTS idx_comissao_tenant_vendedor ON comissao(tenant_id, vendedor_id);

-- Step 9: Drop old indexes that might conflict
DROP INDEX IF EXISTS idx_comissao_locacao;
DROP INDEX IF EXISTS idx_comissao_tenant;
