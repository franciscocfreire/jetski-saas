-- =====================================================
-- V10008: Standardize jetski status to UPPERCASE
-- =====================================================
-- Ensures consistency: enum values are stored in UPPERCASE
-- to match Java enum conventions and seed data.
-- =====================================================

-- 1. Update any existing lowercase status values to uppercase
UPDATE jetski SET status = UPPER(status) WHERE status != UPPER(status);

-- 2. Drop old constraint that allowed lowercase
ALTER TABLE jetski DROP CONSTRAINT IF EXISTS jetski_status_check;

-- 3. Add new constraint requiring uppercase values
ALTER TABLE jetski ADD CONSTRAINT jetski_status_check
    CHECK (status IN ('DISPONIVEL', 'LOCADO', 'MANUTENCAO', 'INDISPONIVEL', 'INATIVO'));

-- 4. Update comment
COMMENT ON COLUMN jetski.status IS 'Jetski status: DISPONIVEL, LOCADO, MANUTENCAO, INDISPONIVEL, INATIVO (uppercase)';
