-- Migration V006: Separate contact fields from JSONB and add personal data
--
-- Refactor cliente table to use individual typed columns instead of generic JSONB:
-- - email TEXT (nullable)
-- - telefone TEXT (nullable, E.164 format: +5511987654321)
-- - whatsapp TEXT (nullable, E.164 format)
-- - endereco JSONB (nullable, flexible address structure)
-- - data_nascimento DATE (nullable, for age validation)
-- - genero TEXT (nullable, for demographics)
--
-- Business justification:
-- - Type safety and validation at database level
-- - Simpler queries (WHERE email = ? vs WHERE contato->>'email' = ?)
-- - Indexed searches for email (composite index with tenant_id)
-- - Maintains flexibility for address (varies by country)
-- - Birth date useful for age restrictions and statistics
-- - Gender useful for demographics and marketing
--
-- Data migration strategy:
-- 1. Add new columns
-- 2. Migrate existing data from contato JSONB
-- 3. Drop old contato column
-- 4. Create performance indexes

-- ============================================================================
-- STEP 1: Add new contact and personal data columns
-- ============================================================================

ALTER TABLE cliente ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS telefone TEXT;
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS whatsapp TEXT;
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS endereco JSONB;
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS data_nascimento DATE;
ALTER TABLE cliente ADD COLUMN IF NOT EXISTS genero TEXT;

COMMENT ON COLUMN cliente.email IS 'Customer email (optional, validated with @Email in application)';
COMMENT ON COLUMN cliente.telefone IS 'Customer phone in E.164 international format: +5511987654321 (optional)';
COMMENT ON COLUMN cliente.whatsapp IS 'Customer WhatsApp in E.164 international format: +5511987654321 (optional)';
COMMENT ON COLUMN cliente.endereco IS 'Customer address in JSONB format (flexible structure for different countries): {"cep": "01310-100", "logradouro": "Av. Paulista", "numero": "1000", "cidade": "São Paulo", "estado": "SP"}';
COMMENT ON COLUMN cliente.data_nascimento IS 'Customer birth date (optional, useful for age validation and statistics)';
COMMENT ON COLUMN cliente.genero IS 'Customer gender (optional): MASCULINO, FEMININO, OUTRO, NAO_INFORMADO';

-- ============================================================================
-- STEP 2: Migrate existing data from contato JSONB to new columns
-- ============================================================================

-- Migrate only if contato column exists and has data
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'cliente' AND column_name = 'contato') THEN

        -- Extract data from JSONB and populate new columns
        UPDATE cliente
        SET
            email = contato->>'email',
            telefone = contato->>'telefone',
            whatsapp = contato->>'whatsapp',
            endereco = contato->'endereco'
        WHERE contato IS NOT NULL;

        RAISE NOTICE 'Data migrated from contato JSONB to individual columns';
    END IF;
END $$;

-- ============================================================================
-- STEP 3: Drop old contato column
-- ============================================================================

ALTER TABLE cliente DROP COLUMN IF EXISTS contato CASCADE;

COMMENT ON TABLE cliente IS 'Customer entity with individual contact fields (email, telefone, whatsapp) and flexible address JSONB';

-- ============================================================================
-- STEP 4: Create performance indexes
-- ============================================================================

-- Composite index for searching customers by email within tenant
-- Supports queries: WHERE tenant_id = ? AND email = ?
CREATE INDEX IF NOT EXISTS idx_cliente_email
ON cliente(tenant_id, email)
WHERE email IS NOT NULL;

COMMENT ON INDEX idx_cliente_email IS 'Performance index for searching customers by email within tenant (partial index, only non-null emails)';

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================

-- Verification query (run manually if needed):
-- SELECT id, nome, email, telefone, whatsapp, data_nascimento, genero, endereco::text
-- FROM cliente
-- WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
-- LIMIT 5;
