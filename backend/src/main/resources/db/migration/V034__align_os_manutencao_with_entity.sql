-- =====================================================
-- V034: Align os_manutencao table with OSManutencao entity
-- =====================================================

-- Rename existing columns to match entity
ALTER TABLE os_manutencao RENAME COLUMN descricao TO descricao_problema;
ALTER TABLE os_manutencao RENAME COLUMN aberta_em TO dt_abertura;
ALTER TABLE os_manutencao RENAME COLUMN fechada_em TO dt_conclusao;
ALTER TABLE os_manutencao RENAME COLUMN responsavel_id TO mecanico_id;

-- Drop old columns that are being replaced
ALTER TABLE os_manutencao DROP COLUMN IF EXISTS custo_estimado;
ALTER TABLE os_manutencao DROP COLUMN IF EXISTS custo_real;

-- Update foreign key constraint for mecanico_id
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_manutencao_responsavel_id_fkey;
-- mecanico_id now references vendedor (who are mechanics) instead of usuario
-- For now, we don't add a FK since mechanics might be vendedor or usuario

-- Add new columns required by the entity
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS diagnostico TEXT;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS solucao TEXT;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS pecas_json JSONB;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS valor_pecas DECIMAL(10,2) DEFAULT 0;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS valor_mao_obra DECIMAL(10,2) DEFAULT 0;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS valor_total DECIMAL(10,2) DEFAULT 0;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS horimetro_abertura DECIMAL(10,2);
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS horimetro_conclusao DECIMAL(10,2);
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS observacoes TEXT;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS dt_prevista_inicio TIMESTAMP WITH TIME ZONE;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS dt_inicio_real TIMESTAMP WITH TIME ZONE;
ALTER TABLE os_manutencao ADD COLUMN IF NOT EXISTS dt_prevista_fim TIMESTAMP WITH TIME ZONE;

-- Update prioridade column to match new length
ALTER TABLE os_manutencao ALTER COLUMN prioridade TYPE VARCHAR(20);

-- Make descricao_problema not null (if there's existing data, fill with placeholder)
UPDATE os_manutencao SET descricao_problema = 'Descrição não informada' WHERE descricao_problema IS NULL;
ALTER TABLE os_manutencao ALTER COLUMN descricao_problema SET NOT NULL;

-- Update check constraint for prioridade
ALTER TABLE os_manutencao DROP CONSTRAINT IF EXISTS os_manutencao_prioridade_check;

-- Add index for mecanico_id
CREATE INDEX IF NOT EXISTS idx_os_manutencao_mecanico ON os_manutencao(tenant_id, mecanico_id);

COMMENT ON TABLE os_manutencao IS 'Ordens de Serviço de Manutenção - OSManutencao entity aligned (V034)';
