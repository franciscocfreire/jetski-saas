-- =====================================================
-- V1003: Seed Platform Admin
-- =====================================================
-- Create platform super admin with unrestricted access
-- This user can access all tenants in the system
-- =====================================================

-- Platform Admin User
INSERT INTO usuario (id, email, nome, ativo) VALUES
('00000000-0000-0000-0000-000000000001', 'admin@plataforma.com', 'Admin Plataforma', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Platform Admin Global Roles (Unrestricted Access)
INSERT INTO usuario_global_roles (usuario_id, roles, unrestricted_access) VALUES
('00000000-0000-0000-0000-000000000001', ARRAY['PLATFORM_ADMIN'], TRUE)
ON CONFLICT (usuario_id) DO UPDATE
SET
    roles = EXCLUDED.roles,
    unrestricted_access = EXCLUDED.unrestricted_access,
    updated_at = NOW();

COMMENT ON TABLE usuario_global_roles IS 'V1003: Seeded platform admin with unrestricted access';
