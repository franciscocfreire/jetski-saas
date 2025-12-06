-- =====================================================
-- V1004: Create User Invitation Tables
-- =====================================================
-- Creates tables for user invitation flow:
-- - convite: User invitations with activation tokens
-- - Updates usuario table with email_verified flag
-- =====================================================

-- =====================================================
-- Table: convite
-- User invitations sent by ADMIN_TENANT
-- =====================================================
CREATE TABLE convite (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    nome TEXT NOT NULL,
    papeis TEXT[] NOT NULL,
    token TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL REFERENCES usuario(id),
    activated_at TIMESTAMPTZ,
    usuario_id UUID REFERENCES usuario(id),
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVATED', 'EXPIRED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT convite_tenant_email_unique UNIQUE (tenant_id, email),
    CONSTRAINT papeis_not_empty CHECK (array_length(papeis, 1) > 0),
    CONSTRAINT token_not_empty CHECK (char_length(token) > 0)
);

COMMENT ON TABLE convite IS 'User invitations sent by ADMIN_TENANT to invite new members';
COMMENT ON COLUMN convite.tenant_id IS 'Tenant inviting the user';
COMMENT ON COLUMN convite.email IS 'Email of invited user (can be any domain)';
COMMENT ON COLUMN convite.papeis IS 'Roles assigned: GERENTE, OPERADOR, VENDEDOR, MECANICO, FINANCEIRO';
COMMENT ON COLUMN convite.token IS 'JWT activation token (48h validity)';
COMMENT ON COLUMN convite.expires_at IS 'Token expiration timestamp';
COMMENT ON COLUMN convite.created_by IS 'ADMIN_TENANT who sent the invitation';
COMMENT ON COLUMN convite.activated_at IS 'When user activated the account';
COMMENT ON COLUMN convite.usuario_id IS 'Created usuario after activation';
COMMENT ON COLUMN convite.status IS 'PENDING, ACTIVATED, EXPIRED, CANCELLED';

-- =====================================================
-- Add email_verified to usuario table
-- (Optional verification - user can login without verifying)
-- =====================================================
ALTER TABLE usuario
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN email_verified_at TIMESTAMPTZ;

COMMENT ON COLUMN usuario.email_verified IS 'Optional email verification (does not block login)';
COMMENT ON COLUMN usuario.email_verified_at IS 'When email was verified';

-- =====================================================
-- Indexes for performance
-- =====================================================
CREATE INDEX idx_convite_tenant ON convite(tenant_id);
CREATE INDEX idx_convite_email ON convite(email);
CREATE INDEX idx_convite_status ON convite(status);
CREATE INDEX idx_convite_status_pending ON convite(tenant_id, status) WHERE status = 'PENDING';
CREATE INDEX idx_convite_expires_at ON convite(expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_convite_token ON convite(token) WHERE status = 'PENDING';
CREATE INDEX idx_convite_created_by ON convite(created_by);

CREATE INDEX idx_usuario_email_verified ON usuario(email_verified) WHERE email_verified = FALSE;

-- =====================================================
-- Updated_at trigger
-- =====================================================
CREATE TRIGGER update_convite_updated_at BEFORE UPDATE ON convite
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
