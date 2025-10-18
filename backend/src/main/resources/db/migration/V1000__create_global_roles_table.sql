-- =====================================================
-- V005: Global Roles for Platform Admins
-- =====================================================
-- Creates table for global platform roles (super admins)
-- that can access ANY tenant (unrestricted_access = true)
-- =====================================================

CREATE TABLE usuario_global_roles (
    usuario_id UUID PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    roles TEXT[] NOT NULL,
    unrestricted_access BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT roles_not_empty CHECK (array_length(roles, 1) > 0)
);

COMMENT ON TABLE usuario_global_roles IS 'Global platform roles for super admins (PLATFORM_ADMIN, SUPPORT, etc.)';
COMMENT ON COLUMN usuario_global_roles.roles IS 'Global roles: PLATFORM_ADMIN, SUPPORT, AUDITOR';
COMMENT ON COLUMN usuario_global_roles.unrestricted_access IS 'If true, user can access ANY tenant without explicit membership';

-- Index for unrestricted users (small set, highly selective)
CREATE INDEX idx_global_roles_unrestricted
    ON usuario_global_roles(unrestricted_access)
    WHERE unrestricted_access = TRUE;

-- Trigger for updated_at
CREATE TRIGGER update_usuario_global_roles_updated_at
    BEFORE UPDATE ON usuario_global_roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
