-- =====================================================
-- V1009: Refactor Locacao Table for Sprint 2
-- =====================================================
-- Updates existing locacao table from V003 to Sprint 2 structure
-- with simplified fields aligned with domain entities
-- =====================================================

-- Drop the old table (clean start for Sprint 2)
DROP TABLE IF EXISTS locacao CASCADE;

-- Recreate with Sprint 2 structure
CREATE TABLE locacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    -- Relationships
    reserva_id UUID,              -- Nullable (walk-in without reservation)
    jetski_id UUID NOT NULL,
    cliente_id UUID NOT NULL,
    vendedor_id UUID,

    -- Check-in data
    data_check_in TIMESTAMPTZ NOT NULL,
    horimetro_inicio DECIMAL(10,2) NOT NULL,
    duracao_prevista INT NOT NULL,  -- Minutes

    -- Check-out data
    data_check_out TIMESTAMPTZ,
    horimetro_fim DECIMAL(10,2),
    minutos_usados INT,
    minutos_faturaveis INT,

    -- Values
    valor_base DECIMAL(10,2),
    valor_total DECIMAL(10,2),

    -- Status: EM_CURSO, FINALIZADA, CANCELADA
    status VARCHAR(20) NOT NULL CHECK (status IN ('EM_CURSO', 'FINALIZADA', 'CANCELADA')),

    -- Metadata
    observacoes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT check_horimetro_fim CHECK (horimetro_fim IS NULL OR horimetro_fim >= horimetro_inicio),
    CONSTRAINT check_duracao_prevista CHECK (duracao_prevista > 0),

    -- Foreign keys
    CONSTRAINT fk_locacao_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_locacao_reserva FOREIGN KEY (reserva_id) REFERENCES reserva(id) ON DELETE RESTRICT,
    CONSTRAINT fk_locacao_jetski FOREIGN KEY (jetski_id) REFERENCES jetski(id) ON DELETE RESTRICT,
    CONSTRAINT fk_locacao_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id) ON DELETE RESTRICT,
    CONSTRAINT fk_locacao_vendedor FOREIGN KEY (vendedor_id) REFERENCES vendedor(id) ON DELETE RESTRICT
);

-- Indexes for performance
CREATE INDEX idx_locacao_tenant ON locacao(tenant_id);
CREATE INDEX idx_locacao_tenant_status ON locacao(tenant_id, status);
CREATE INDEX idx_locacao_tenant_jetski ON locacao(tenant_id, jetski_id);
CREATE INDEX idx_locacao_tenant_cliente ON locacao(tenant_id, cliente_id);
CREATE INDEX idx_locacao_tenant_reserva ON locacao(tenant_id, reserva_id) WHERE reserva_id IS NOT NULL;
CREATE INDEX idx_locacao_data_check_in ON locacao(tenant_id, data_check_in DESC);

-- Comments
COMMENT ON TABLE locacao IS 'Sprint 2: Rental operations with check-in/check-out and RN01 billing';
COMMENT ON COLUMN locacao.status IS 'EM_CURSO (active), FINALIZADA (completed), CANCELADA (cancelled)';
COMMENT ON COLUMN locacao.minutos_faturaveis IS 'Billable minutes after applying RN01 (tolerance + 15-min rounding)';
COMMENT ON COLUMN locacao.duracao_prevista IS 'Expected duration in minutes';
