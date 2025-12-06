-- =====================================================
-- V1009: Refactor Reserva to Support Modelo-Based Booking
-- =====================================================
-- Changes to support:
-- - Reservation by modelo (not specific jetski)
-- - Optional deposit/down payment system
-- - Priority levels (with/without deposit)
-- - Automatic expiration with grace period
-- - Controlled overbooking
-- =====================================================

-- =====================================================
-- Part 1: Create reserva_config table
-- =====================================================
-- Per-tenant configuration for reservation policies
CREATE TABLE IF NOT EXISTS reserva_config (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,

    -- Grace period before reservation expires (in minutes)
    grace_period_minutos INT NOT NULL DEFAULT 30,

    -- Deposit percentage (e.g., 30.00 = 30%)
    percentual_sinal DECIMAL(5,2) NOT NULL DEFAULT 30.00,

    -- Overbooking factor (e.g., 1.5 = accept 50% more reservations)
    fator_overbooking DECIMAL(5,2) NOT NULL DEFAULT 1.0,

    -- Maximum reservations without deposit per modelo
    max_reservas_sem_sinal_por_modelo INT NOT NULL DEFAULT 8,

    -- Enable notifications before expiration
    notificar_antes_expiracao BOOLEAN NOT NULL DEFAULT TRUE,

    -- Minutes before expiration to notify customer
    notificar_minutos_antecedencia INT NOT NULL DEFAULT 15,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT grace_period_positive CHECK (grace_period_minutos > 0),
    CONSTRAINT percentual_sinal_valid CHECK (percentual_sinal >= 0 AND percentual_sinal <= 100),
    CONSTRAINT fator_overbooking_valid CHECK (fator_overbooking >= 1.0 AND fator_overbooking <= 10.0),
    CONSTRAINT max_reservas_positive CHECK (max_reservas_sem_sinal_por_modelo > 0)
);

COMMENT ON TABLE reserva_config IS 'Per-tenant configuration for reservation/booking policies';
COMMENT ON COLUMN reserva_config.grace_period_minutos IS 'Minutes after start time before reservation expires (no-show)';
COMMENT ON COLUMN reserva_config.percentual_sinal IS 'Deposit percentage required to guarantee reservation';
COMMENT ON COLUMN reserva_config.fator_overbooking IS 'Overbooking multiplier for reservations without deposit';
COMMENT ON COLUMN reserva_config.max_reservas_sem_sinal_por_modelo IS 'Maximum reservations without deposit per modelo/timeslot';

-- =====================================================
-- Part 2: Add new columns to reserva table
-- =====================================================

-- Add modelo_id (required - reservation is now by modelo)
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS modelo_id UUID;

-- Make jetski_id optional (allocated at check-in, not at reservation time)
ALTER TABLE reserva ALTER COLUMN jetski_id DROP NOT NULL;

-- Add priority level (ALTA = with deposit, BAIXA = without deposit)
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS prioridade VARCHAR(20) NOT NULL DEFAULT 'BAIXA';

-- Add deposit payment tracking
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS sinal_pago BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS valor_sinal DECIMAL(10,2);
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS sinal_pago_em TIMESTAMPTZ;

-- Add expiration timestamp (data_inicio + grace_period)
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS expira_em TIMESTAMPTZ;

-- =====================================================
-- Part 3: Populate modelo_id from existing jetski_id
-- =====================================================
-- Migrate existing reservations: set modelo_id based on jetski's modelo
UPDATE reserva r
SET modelo_id = j.modelo_id
FROM jetski j
WHERE r.jetski_id = j.id
  AND r.modelo_id IS NULL;

-- Now make modelo_id NOT NULL
ALTER TABLE reserva ALTER COLUMN modelo_id SET NOT NULL;

-- =====================================================
-- Part 4: Add Foreign Key constraints
-- =====================================================
ALTER TABLE reserva ADD CONSTRAINT fk_reserva_modelo
    FOREIGN KEY (modelo_id) REFERENCES modelo(id) ON DELETE RESTRICT;

-- Update existing FK for jetski (now optional)
-- Drop old constraint if exists
ALTER TABLE reserva DROP CONSTRAINT IF EXISTS reserva_jetski_id_fkey;

-- Add new constraint allowing NULL
ALTER TABLE reserva ADD CONSTRAINT fk_reserva_jetski
    FOREIGN KEY (jetski_id) REFERENCES jetski(id) ON DELETE RESTRICT;

-- =====================================================
-- Part 5: Update status constraint to include EXPIRADA
-- =====================================================
ALTER TABLE reserva DROP CONSTRAINT IF EXISTS reserva_status_check;
ALTER TABLE reserva ADD CONSTRAINT reserva_status_check
    CHECK (status IN ('PENDENTE', 'CONFIRMADA', 'CANCELADA', 'FINALIZADA', 'EXPIRADA'));

-- =====================================================
-- Part 6: Add constraint for priority enum
-- =====================================================
ALTER TABLE reserva ADD CONSTRAINT reserva_prioridade_check
    CHECK (prioridade IN ('ALTA', 'BAIXA'));

-- =====================================================
-- Part 7: Add business rule constraints
-- =====================================================
-- If sinal_pago = true, then valor_sinal and sinal_pago_em must be set
ALTER TABLE reserva ADD CONSTRAINT reserva_sinal_consistency
    CHECK (
        (sinal_pago = FALSE) OR
        (sinal_pago = TRUE AND valor_sinal IS NOT NULL AND sinal_pago_em IS NOT NULL)
    );

-- If sinal_pago = true, then prioridade must be ALTA
ALTER TABLE reserva ADD CONSTRAINT reserva_prioridade_sinal
    CHECK (
        (sinal_pago = FALSE) OR
        (sinal_pago = TRUE AND prioridade = 'ALTA')
    );

-- =====================================================
-- Part 8: Create performance indexes
-- =====================================================
-- Index for checking modelo availability in a period
CREATE INDEX IF NOT EXISTS idx_reserva_modelo_periodo
    ON reserva(modelo_id, data_inicio, data_fim_prevista)
    WHERE ativo = TRUE AND status IN ('PENDENTE', 'CONFIRMADA');

-- Index for priority-based queries
CREATE INDEX IF NOT EXISTS idx_reserva_prioridade
    ON reserva(prioridade, status)
    WHERE ativo = TRUE;

-- Index for expiration job
CREATE INDEX IF NOT EXISTS idx_reserva_expiracao
    ON reserva(expira_em)
    WHERE status IN ('PENDENTE', 'CONFIRMADA') AND ativo = TRUE;

-- Index for allocation job (reservations needing jetski assignment)
CREATE INDEX IF NOT EXISTS idx_reserva_alocacao
    ON reserva(modelo_id, data_inicio)
    WHERE jetski_id IS NULL AND status = 'CONFIRMADA' AND ativo = TRUE;

-- Composite index for checking guaranteed reservations
CREATE INDEX IF NOT EXISTS idx_reserva_garantida
    ON reserva(modelo_id, sinal_pago, status, data_inicio, data_fim_prevista)
    WHERE ativo = TRUE AND sinal_pago = TRUE;

-- =====================================================
-- Part 9: Update comments
-- =====================================================
COMMENT ON COLUMN reserva.modelo_id IS 'Modelo being reserved (customer may not know specific jetski yet)';
COMMENT ON COLUMN reserva.jetski_id IS 'Specific jetski allocated (NULL until check-in or manual allocation)';
COMMENT ON COLUMN reserva.prioridade IS 'Priority level: ALTA (with deposit), BAIXA (without deposit)';
COMMENT ON COLUMN reserva.sinal_pago IS 'Whether customer paid deposit to guarantee reservation';
COMMENT ON COLUMN reserva.valor_sinal IS 'Deposit amount paid (if sinal_pago = true)';
COMMENT ON COLUMN reserva.sinal_pago_em IS 'Timestamp when deposit was paid';
COMMENT ON COLUMN reserva.expira_em IS 'Expiration timestamp = data_inicio + grace_period (auto-cancel no-shows)';

-- =====================================================
-- Part 10: Seed default configuration for existing tenant
-- =====================================================
INSERT INTO reserva_config (tenant_id, grace_period_minutos, percentual_sinal, fator_overbooking, max_reservas_sem_sinal_por_modelo)
SELECT id, 30, 30.00, 1.5, 8
FROM tenant
WHERE id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
ON CONFLICT (tenant_id) DO NOTHING;

COMMENT ON COLUMN reserva.status IS 'Reservation status: PENDENTE (awaiting confirmation), CONFIRMADA (confirmed), CANCELADA (cancelled), FINALIZADA (completed/converted to rental), EXPIRADA (no-show expired)';
