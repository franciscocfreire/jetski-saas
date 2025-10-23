-- =====================================================
-- V1008: Create Identity Provider Mapping Table
-- =====================================================
-- Purpose: Decouple PostgreSQL UUIDs from Identity Provider UUIDs
--
-- Use cases:
-- 1. Multiple identity providers (Keycloak, Google, Azure AD, etc.)
-- 2. Provider migration (Keycloak → Auth0) without downtime
-- 3. Account linking (same user, multiple login methods)
-- 4. Audit trail (which provider was used for login)
-- 5. Security isolation (hide provider IDs from application layer)
--
-- Design:
-- - usuario_id: Internal PostgreSQL UUID (source of truth)
-- - provider: Identity provider name ('keycloak', 'google', 'azure-ad', etc.)
-- - provider_user_id: External UUID/ID from the provider
-- - UNIQUE (provider, provider_user_id): Prevent duplicate mappings
-- =====================================================

CREATE TABLE usuario_identity_provider (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Foreign keys
    CONSTRAINT fk_usuario_identity_provider_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id)
        ON DELETE CASCADE,

    -- Prevent duplicate mappings
    CONSTRAINT uq_usuario_identity_provider_provider_user
        UNIQUE (provider, provider_user_id)
);

-- Indexes for performance
CREATE INDEX idx_usuario_identity_provider_usuario_id
    ON usuario_identity_provider(usuario_id);

CREATE INDEX idx_usuario_identity_provider_provider_user
    ON usuario_identity_provider(provider, provider_user_id);

-- Comments for documentation
COMMENT ON TABLE usuario_identity_provider IS
    'Maps internal user IDs to external identity provider IDs. '
    'Supports multiple providers per user (account linking) and '
    'provider migration without data loss.';

COMMENT ON COLUMN usuario_identity_provider.provider IS
    'Identity provider name. Examples: keycloak, google, azure-ad, auth0, github';

COMMENT ON COLUMN usuario_identity_provider.provider_user_id IS
    'External user ID from the identity provider (UUID, sub claim, etc.)';

COMMENT ON COLUMN usuario_identity_provider.linked_at IS
    'Timestamp when this provider was linked to the user account';
