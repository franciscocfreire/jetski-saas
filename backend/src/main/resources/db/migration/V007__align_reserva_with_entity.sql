-- =====================================================
-- V007: Align Reserva Table with Entity
-- =====================================================
-- Updates existing reserva table to match entity structure:
-- - Add ativo column (soft delete)
-- - Rename date columns
-- - Update status values to uppercase
-- - Remove unused columns
-- =====================================================

-- Add ativo column for soft delete
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE;

-- Rename date columns to match entity
ALTER TABLE reserva RENAME COLUMN dt_inicio_prevista TO data_inicio;
ALTER TABLE reserva RENAME COLUMN dt_fim_prevista TO data_fim_prevista;

-- Update status constraint to match entity enum values (uppercase)
ALTER TABLE reserva DROP CONSTRAINT IF EXISTS reserva_status_check;
ALTER TABLE reserva ADD CONSTRAINT reserva_status_check
    CHECK (status IN ('PENDENTE', 'CONFIRMADA', 'CANCELADA', 'FINALIZADA'));

-- Update existing status values to uppercase
UPDATE reserva SET status = UPPER(status);

-- Drop unused columns that are not in entity
ALTER TABLE reserva DROP COLUMN IF EXISTS duracao_prevista_min;
ALTER TABLE reserva DROP COLUMN IF EXISTS valor_previsto;
ALTER TABLE reserva DROP COLUMN IF EXISTS caucao;

-- Update constraint name to match new column names
ALTER TABLE reserva DROP CONSTRAINT IF EXISTS dt_fim_after_inicio;
ALTER TABLE reserva ADD CONSTRAINT reserva_valid_period
    CHECK (data_fim_prevista > data_inicio);

-- Update comments
COMMENT ON TABLE reserva IS 'Jetski reservations/bookings (tenant-scoped)';
COMMENT ON COLUMN reserva.data_inicio IS 'Predicted start date/time';
COMMENT ON COLUMN reserva.data_fim_prevista IS 'Predicted end date/time';
COMMENT ON COLUMN reserva.status IS 'Reservation status: PENDENTE (pending confirmation), CONFIRMADA (confirmed), CANCELADA (cancelled), FINALIZADA (completed/converted to rental)';
COMMENT ON COLUMN reserva.ativo IS 'Soft delete flag - inactive reservations are cancelled/archived';
