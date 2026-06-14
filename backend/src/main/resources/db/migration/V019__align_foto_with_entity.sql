-- V019: Align foto table with Foto entity
-- The original V001 schema doesn't match the Java entity structure

-- Step 1: Drop existing table (no important data in dev)
DROP TABLE IF EXISTS foto CASCADE;

-- Step 2: Recreate with correct structure matching the entity
CREATE TABLE foto (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    locacao_id UUID REFERENCES locacao(id) ON DELETE CASCADE,
    jetski_id UUID REFERENCES jetski(id),
    tipo VARCHAR(20) NOT NULL,
    url TEXT NOT NULL,
    s3_key TEXT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT,
    sha256_hash VARCHAR(64),
    uploaded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT foto_tipo_check CHECK (tipo IN ('CHECK_IN', 'CHECK_OUT', 'INCIDENTE', 'ABASTECIMENTO', 'MANUTENCAO'))
);

-- Step 3: Create indexes
CREATE INDEX idx_foto_tenant ON foto(tenant_id);
CREATE INDEX idx_foto_locacao ON foto(tenant_id, locacao_id);
CREATE INDEX idx_foto_jetski ON foto(tenant_id, jetski_id);

-- Step 4: Enable RLS
ALTER TABLE foto ENABLE ROW LEVEL SECURITY;
ALTER TABLE foto FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_foto ON foto
    USING (tenant_id = get_current_tenant_id());

COMMENT ON TABLE foto IS 'Photos taken during rental operations (check-in, check-out, incidents)';
COMMENT ON COLUMN foto.s3_key IS 'S3 object key: {tenant_id}/locacao/{locacao_id}/{tipo}_{timestamp}_{uuid}.ext';
COMMENT ON COLUMN foto.sha256_hash IS 'SHA-256 hash for integrity verification';

-- Step 5: Fix abastecimento.foto_id foreign key
-- Drop old bigint column and recreate with UUID type
ALTER TABLE abastecimento DROP COLUMN IF EXISTS foto_id;
ALTER TABLE abastecimento ADD COLUMN foto_id UUID REFERENCES foto(id);
