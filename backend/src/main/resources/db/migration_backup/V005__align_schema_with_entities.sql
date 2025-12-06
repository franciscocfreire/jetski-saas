-- Migration V003: Align database schema with JPA entities
-- This migration consolidates contact fields and adds missing JSON columns

-- ============================================================================
-- MODELO: Add pacotes_json column
-- ============================================================================
ALTER TABLE modelo ADD COLUMN IF NOT EXISTS pacotes_json JSONB;

COMMENT ON COLUMN modelo.pacotes_json IS 'Pricing packages by duration in JSONB format: [{"duracao_min": 30, "preco": 180.00}, ...]';

-- ============================================================================
-- CLIENTE: Consolidate contact fields into single JSONB column
-- ============================================================================

-- Add new contato column
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS contato JSONB;

-- Migrate existing data to new column (if data exists)
UPDATE cliente
SET contato = jsonb_build_object(
    'telefone', COALESCE(telefone, ''),
    'email', COALESCE(email, ''),
    'endereco', COALESCE(endereco, '{}'::jsonb)
)
WHERE contato IS NULL;

-- Drop old columns
ALTER TABLE cliente DROP COLUMN IF EXISTS telefone CASCADE;
ALTER TABLE cliente DROP COLUMN IF EXISTS email CASCADE;
ALTER TABLE cliente DROP COLUMN IF EXISTS endereco CASCADE;
ALTER TABLE cliente DROP COLUMN IF EXISTS data_nascimento CASCADE;
ALTER TABLE cliente DROP COLUMN IF EXISTS termo_aceite_data CASCADE;

COMMENT ON COLUMN cliente.contato IS 'Contact information in JSONB format: {"telefone": "...", "email": "...", "endereco": {...}}';

-- ============================================================================
-- VENDEDOR: Consolidate contact and commission fields
-- ============================================================================

-- Drop old separate columns if they exist
ALTER TABLE vendedor DROP COLUMN IF EXISTS telefone CASCADE;
ALTER TABLE vendedor DROP COLUMN IF EXISTS email CASCADE;
ALTER TABLE vendedor DROP COLUMN IF EXISTS percentual_comissao CASCADE;

-- Ensure regra_comissao_json column exists
ALTER TABLE vendedor ADD COLUMN IF NOT EXISTS regra_comissao_json JSONB;

COMMENT ON COLUMN vendedor.regra_comissao_json IS 'Commission rules in JSONB format: {"percentual_padrao": 10.0, "por_modelo": [...], "escalonada": [...]}';

-- ============================================================================
-- ENUM CONSTRAINTS: Update to accept both uppercase and lowercase
-- ============================================================================

-- Drop existing check constraints
ALTER TABLE vendedor DROP CONSTRAINT IF EXISTS vendedor_tipo_check;
ALTER TABLE jetski DROP CONSTRAINT IF EXISTS jetski_status_check;

-- Recreate with case-insensitive values (accept both interno/INTERNO, parceiro/PARCEIRO)
ALTER TABLE vendedor ADD CONSTRAINT vendedor_tipo_check
    CHECK (LOWER(tipo) IN ('interno', 'parceiro'));

-- Recreate jetski status constraint (disponivel, manutencao, reservado, locado, inativo)
ALTER TABLE jetski ADD CONSTRAINT jetski_status_check
    CHECK (LOWER(status) IN ('disponivel', 'manutencao', 'reservado', 'locado', 'inativo'));

COMMENT ON COLUMN vendedor.tipo IS 'Seller type: interno (employee) or parceiro (partner) - case insensitive';
COMMENT ON COLUMN jetski.status IS 'Jetski status: disponivel, manutencao, reservado, locado, inativo - case insensitive';
