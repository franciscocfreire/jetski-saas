-- ============================================================================
-- V005: Create Tenant Signup Table
-- ============================================================================
-- Stores tenant signup requests that are pending activation.
-- Similar to convite table but for new tenant creation (self-service signup).
-- ============================================================================

CREATE TABLE IF NOT EXISTS tenant_signup (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    nome VARCHAR(200) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    temporary_password VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMPTZ,
    CONSTRAINT tenant_signup_status_check CHECK (status IN ('PENDING', 'ACTIVATED', 'EXPIRED'))
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_tenant_signup_token ON tenant_signup(token);
CREATE INDEX IF NOT EXISTS idx_tenant_signup_email ON tenant_signup(email);
CREATE INDEX IF NOT EXISTS idx_tenant_signup_tenant ON tenant_signup(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_signup_status ON tenant_signup(status);

-- Trigger for updated_at
DROP TRIGGER IF EXISTS update_tenant_signup_updated_at ON tenant_signup;
CREATE TRIGGER update_tenant_signup_updated_at
    BEFORE UPDATE ON tenant_signup
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE tenant_signup IS 'Stores tenant signup requests pending activation';
COMMENT ON COLUMN tenant_signup.token IS 'Unique activation token sent to user email';
COMMENT ON COLUMN tenant_signup.temporary_password IS 'BCrypt hash of temporary password';
COMMENT ON COLUMN tenant_signup.status IS 'PENDING: waiting activation, ACTIVATED: completed, EXPIRED: token expired';
