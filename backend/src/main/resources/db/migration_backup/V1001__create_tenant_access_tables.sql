-- =====================================================
-- V006: Tenant Access Audit and UX tables
-- =====================================================
-- Creates tables for:
-- - usuario_last_tenant: Track last accessed tenant per user (UX)
-- - tenant_access_audit: Complete audit log of access attempts
-- =====================================================

-- =====================================================
-- Table: usuario_last_tenant
-- Tracks last tenant accessed by user for better UX
-- =====================================================
CREATE TABLE usuario_last_tenant (
    usuario_id UUID PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    accessed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usuario_last_tenant_tenant ON usuario_last_tenant(tenant_id);

COMMENT ON TABLE usuario_last_tenant IS 'Tracks last tenant accessed per user - improves UX by pre-selecting tenant';
COMMENT ON COLUMN usuario_last_tenant.accessed_at IS 'Last access timestamp';

-- =====================================================
-- Table: tenant_access_audit
-- Complete audit log of all tenant access validation attempts
-- =====================================================
CREATE TABLE tenant_access_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    allowed BOOLEAN NOT NULL,
    ip_address INET,
    user_agent TEXT,
    roles TEXT[],
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT action_valid CHECK (action IN ('ACCESS', 'DENIED', 'REVOKED'))
);

-- Indexes for audit queries
CREATE INDEX idx_audit_usuario ON tenant_access_audit(usuario_id, created_at DESC);
CREATE INDEX idx_audit_tenant ON tenant_access_audit(tenant_id, created_at DESC);
CREATE INDEX idx_audit_denied ON tenant_access_audit(allowed, created_at DESC)
    WHERE allowed = FALSE;
CREATE INDEX idx_audit_action ON tenant_access_audit(action, created_at DESC);

COMMENT ON TABLE tenant_access_audit IS 'Audit log of all tenant access validation attempts';
COMMENT ON COLUMN tenant_access_audit.action IS 'Type of action: ACCESS (attempt), DENIED (rejected), REVOKED (membership revoked)';
COMMENT ON COLUMN tenant_access_audit.allowed IS 'Whether access was granted (true) or denied (false)';
COMMENT ON COLUMN tenant_access_audit.roles IS 'Roles that were checked/granted during this access';
