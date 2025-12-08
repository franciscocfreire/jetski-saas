-- V009: Create modelo_midia table for multiple images/videos per model
-- Allows each model to have multiple media items (images, videos) for marketplace display

-- =====================================================
-- Table: modelo_midia
-- =====================================================
CREATE TABLE modelo_midia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    modelo_id UUID NOT NULL REFERENCES modelo(id) ON DELETE CASCADE,

    -- Media type: IMAGEM, VIDEO
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('IMAGEM', 'VIDEO')),

    -- URL of the media (S3, YouTube, Vimeo, etc.)
    url TEXT NOT NULL,

    -- Optional thumbnail for videos
    thumbnail_url TEXT,

    -- Display order (lower = first)
    ordem INTEGER NOT NULL DEFAULT 0,

    -- Is this the main/featured image?
    principal BOOLEAN NOT NULL DEFAULT false,

    -- Optional title/alt text
    titulo VARCHAR(255),

    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- =====================================================
-- Indexes
-- =====================================================

-- Composite index for tenant + modelo queries (most common)
CREATE INDEX idx_modelo_midia_tenant_modelo ON modelo_midia(tenant_id, modelo_id);

-- Index for ordering
CREATE INDEX idx_modelo_midia_ordem ON modelo_midia(modelo_id, ordem);

-- Index for finding principal image
CREATE INDEX idx_modelo_midia_principal ON modelo_midia(modelo_id, principal) WHERE principal = true;

-- =====================================================
-- RLS Policies
-- =====================================================

ALTER TABLE modelo_midia ENABLE ROW LEVEL SECURITY;
ALTER TABLE modelo_midia FORCE ROW LEVEL SECURITY;

-- Tenant isolation policy (for authenticated requests with tenant context)
CREATE POLICY tenant_isolation_modelo_midia ON modelo_midia
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    );

-- Marketplace public read policy (for public API without tenant context)
-- Allows reading midias when the modelo and tenant are visible in marketplace
CREATE POLICY marketplace_public_read ON modelo_midia
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM modelo m
            JOIN tenant t ON m.tenant_id = t.id
            WHERE m.id = modelo_midia.modelo_id
            AND m.ativo = true
            AND m.exibir_no_marketplace = true
            AND t.status = 'ATIVO'
            AND t.exibir_no_marketplace = true
        )
    );

-- =====================================================
-- Comments
-- =====================================================

COMMENT ON TABLE modelo_midia IS 'Media items (images/videos) for jetski models';
COMMENT ON COLUMN modelo_midia.tipo IS 'Type: IMAGEM or VIDEO';
COMMENT ON COLUMN modelo_midia.url IS 'URL of the media (S3 for images, YouTube/Vimeo for videos)';
COMMENT ON COLUMN modelo_midia.thumbnail_url IS 'Thumbnail URL for videos';
COMMENT ON COLUMN modelo_midia.ordem IS 'Display order (0 = first)';
COMMENT ON COLUMN modelo_midia.principal IS 'Is this the main/featured image for the model';
COMMENT ON COLUMN modelo_midia.titulo IS 'Optional title or alt text for accessibility';

COMMENT ON POLICY tenant_isolation_modelo_midia ON modelo_midia IS 'Tenant isolation using NULLIF for safe UUID handling';
COMMENT ON POLICY marketplace_public_read ON modelo_midia IS 'Allows public read access to media of marketplace-visible models';
