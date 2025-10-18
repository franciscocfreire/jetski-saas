-- =====================================================
-- V007: Performance indexes for tenant access
-- =====================================================
-- Creates specialized indexes for:
-- - Fast tenant access validation (membro table)
-- - Tenant search autocomplete (tenant table)
-- - CNPJ lookups (tenant table)
-- =====================================================

-- =====================================================
-- Index: Composite for tenant access validation
-- Most common query: check if user has access to tenant
-- =====================================================
CREATE INDEX idx_membro_usuario_tenant_ativo
    ON membro(usuario_id, tenant_id, ativo)
    WHERE ativo = TRUE;

COMMENT ON INDEX idx_membro_usuario_tenant_ativo IS 'Optimizes tenant access validation query (most frequent operation)';

-- =====================================================
-- Index: Full-text search for tenant autocomplete
-- Supports search by razao_social and slug
-- =====================================================
CREATE INDEX idx_tenant_search
    ON tenant USING gin(to_tsvector('portuguese', razao_social || ' ' || slug))
    WHERE status = 'ativo';

COMMENT ON INDEX idx_tenant_search IS 'Enables fast full-text search for tenant autocomplete (super admin feature)';

-- =====================================================
-- Index: CNPJ lookup (case-insensitive)
-- =====================================================
CREATE INDEX idx_tenant_cnpj_lower
    ON tenant(LOWER(cnpj))
    WHERE cnpj IS NOT NULL AND status = 'ativo';

COMMENT ON INDEX idx_tenant_cnpj_lower IS 'Fast case-insensitive CNPJ lookup';

-- =====================================================
-- Index: List tenants by user (for /user/tenants API)
-- =====================================================
CREATE INDEX idx_membro_usuario_ativo_created
    ON membro(usuario_id, created_at DESC)
    WHERE ativo = TRUE;

COMMENT ON INDEX idx_membro_usuario_ativo_created IS 'Optimizes listing all tenants for a user (ordered by most recent)';
