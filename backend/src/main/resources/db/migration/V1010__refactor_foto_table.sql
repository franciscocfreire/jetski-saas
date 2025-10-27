-- =====================================================
-- V1010: Refactor Foto Table for Sprint 2
-- =====================================================
-- Updates existing foto table from V003 to Sprint 2 structure
-- with S3 integration fields and proper typing
-- =====================================================

-- Drop the old table (clean start for Sprint 2)
DROP TABLE IF EXISTS foto CASCADE;

-- Recreate with Sprint 2 structure
CREATE TABLE foto (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    -- Relationships
    locacao_id UUID,              -- Nullable (photos can be standalone)
    jetski_id UUID,               -- Nullable (for maintenance photos)

    -- Photo type: CHECKIN_*, CHECKOUT_*, INCIDENTE, MANUTENCAO
    tipo VARCHAR(50) NOT NULL CHECK (tipo IN (
        'CHECKIN_FRENTE', 'CHECKIN_LATERAL_ESQ', 'CHECKIN_LATERAL_DIR', 'CHECKIN_HORIMETRO',
        'CHECKOUT_FRENTE', 'CHECKOUT_LATERAL_ESQ', 'CHECKOUT_LATERAL_DIR', 'CHECKOUT_HORIMETRO',
        'INCIDENTE', 'MANUTENCAO'
    )),

    -- S3 storage
    url TEXT NOT NULL,            -- Full S3 URL
    s3_key TEXT NOT NULL,         -- S3 object key (bucket prefix + filename)
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT,

    -- Integrity check
    sha256_hash VARCHAR(64),      -- SHA-256 hash for integrity verification

    -- Metadata
    uploaded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Foreign keys
    CONSTRAINT fk_foto_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id) ON DELETE CASCADE,
    CONSTRAINT fk_foto_locacao FOREIGN KEY (locacao_id) REFERENCES locacao(id) ON DELETE CASCADE,
    CONSTRAINT fk_foto_jetski FOREIGN KEY (jetski_id) REFERENCES jetski(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_foto_tenant ON foto(tenant_id);
CREATE INDEX idx_foto_tenant_locacao ON foto(tenant_id, locacao_id) WHERE locacao_id IS NOT NULL;
CREATE INDEX idx_foto_tenant_jetski ON foto(tenant_id, jetski_id) WHERE jetski_id IS NOT NULL;
CREATE INDEX idx_foto_tenant_tipo ON foto(tenant_id, tipo);
CREATE UNIQUE INDEX idx_foto_s3_key ON foto(tenant_id, s3_key);

-- Comments
COMMENT ON TABLE foto IS 'Sprint 2: Photo metadata with S3 storage integration';
COMMENT ON COLUMN foto.tipo IS 'CHECKIN_FRENTE, CHECKIN_LATERAL_ESQ, CHECKIN_LATERAL_DIR, CHECKIN_HORIMETRO, CHECKOUT_FRENTE, CHECKOUT_LATERAL_ESQ, CHECKOUT_LATERAL_DIR, CHECKOUT_HORIMETRO, INCIDENTE, MANUTENCAO';
COMMENT ON COLUMN foto.s3_key IS 'S3 object key: tenant_id/locacao/id/tipo_timestamp_uuid.ext';
COMMENT ON COLUMN foto.sha256_hash IS 'SHA-256 hash for integrity verification';
