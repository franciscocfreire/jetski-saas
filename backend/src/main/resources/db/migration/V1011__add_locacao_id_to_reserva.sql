-- =====================================================
-- V10003: Add locacao_id to Reserva
-- =====================================================
-- Link reservations to rental operations
-- Tracks conversion: Reserva → Locacao
-- =====================================================

-- Add locacao_id column to reserva (only if it doesn't exist)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'reserva' AND column_name = 'locacao_id'
    ) THEN
        ALTER TABLE reserva ADD COLUMN locacao_id UUID;
    END IF;
END $$;

-- Add foreign key constraint (only if it doesn't exist)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_reserva_locacao' AND table_name = 'reserva'
    ) THEN
        ALTER TABLE reserva ADD CONSTRAINT fk_reserva_locacao
            FOREIGN KEY (locacao_id) REFERENCES locacao(id) ON DELETE RESTRICT;
    END IF;
END $$;

-- Create index for lookups (only if it doesn't exist)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_reserva_locacao'
    ) THEN
        CREATE INDEX idx_reserva_locacao ON reserva(tenant_id, locacao_id)
            WHERE locacao_id IS NOT NULL;
    END IF;
END $$;

-- Add comment
COMMENT ON COLUMN reserva.locacao_id IS 'Locacao created from this reservation (tracks conversion from reservation to rental)';
