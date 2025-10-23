-- =====================================================
-- V9999: Test Data for Integration Tests
-- =====================================================
-- Seed data for integration testing:
-- - Test tenant with Basic plan (3 user limit)
-- - Active subscription
-- - Admin user for testing
-- - Sample invitations in various states
-- =====================================================

-- =====================================================
-- Plano (Basic Plan for Testing)
-- =====================================================
INSERT INTO plano (id, nome, descricao, limites_json, preco_mensal, ativo) VALUES
(999, 'Test Basic Plan', 'Plano básico para testes de integração',
 '{"frota_max": 50, "usuarios_max": 100, "storage_gb": 100, "locacoes_mes": 1000}'::jsonb,
 99.00, TRUE)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Tenant (Test Company)
-- =====================================================
INSERT INTO tenant (id, slug, razao_social, cnpj, timezone, moeda, status, contato, branding_json)
VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'test-company', 'Test Company Ltda', '99.999.999/0001-99',
 'America/Sao_Paulo', 'BRL', 'ativo',
 '{"telefone": "+55 11 99999-9999", "email": "contato@testcompany.com"}'::jsonb,
 '{"logo_url": "https://test.com/logo.png", "cor_primaria": "#0066CC"}'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Assinatura (Active Subscription)
-- =====================================================
INSERT INTO assinatura (id, tenant_id, plano_id, ciclo, dt_inicio, dt_fim, status, pagamento_cfg_json)
VALUES
(999, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 999, 'mensal', '2025-01-01', NULL, 'ativa',
 '{"metodo": "cartao", "ultimos_digitos": "9999", "vencimento_dia": 1}'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Usuarios (Test Users)
-- =====================================================
-- Admin user who will send invitations
INSERT INTO usuario (id, email, nome, ativo, email_verified, email_verified_at) VALUES
('10000000-0000-0000-0000-000000000001', 'admin.test@testcompany.com', 'Admin Test User', TRUE, TRUE, NOW())
ON CONFLICT (id) DO NOTHING;

-- Activated user (accepted invitation)
INSERT INTO usuario (id, email, nome, ativo, email_verified, email_verified_at) VALUES
('10000000-0000-0000-0000-000000000002', 'activated.user@example.com', 'Activated Test User', TRUE, TRUE, NOW())
ON CONFLICT (id) DO NOTHING;

-- User for testing multi-invite scenario
INSERT INTO usuario (id, email, nome, ativo, email_verified, email_verified_at) VALUES
('10000000-0000-0000-0000-000000000003', 'existing.user@example.com', 'Existing User', TRUE, FALSE, NULL)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Membros (User-Tenant Relationships)
-- =====================================================
-- Admin member
INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '10000000-0000-0000-0000-000000000001', ARRAY['ADMIN_TENANT'], TRUE)
ON CONFLICT (tenant_id, usuario_id) DO NOTHING;

-- Activated member (from invitation)
INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '10000000-0000-0000-0000-000000000002', ARRAY['GERENTE'], TRUE)
ON CONFLICT (tenant_id, usuario_id) DO NOTHING;

-- =====================================================
-- Convites (Sample Invitations)
-- =====================================================

-- PENDING invitation (valid, not yet activated)
INSERT INTO convite (id, tenant_id, email, nome, papeis, token, expires_at, created_by, status, created_at, updated_at)
VALUES
('20000000-0000-0000-0000-000000000001',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
 'pending.user@example.com',
 'Pending User',
 ARRAY['OPERADOR'],
 'valid-token-pending-12345678901234567890',
 NOW() + INTERVAL '24 hours',
 '10000000-0000-0000-0000-000000000001',
 'PENDING',
 NOW() - INTERVAL '1 hour',
 NOW() - INTERVAL '1 hour')
ON CONFLICT (id) DO NOTHING;

-- EXPIRED invitation (token expired)
INSERT INTO convite (id, tenant_id, email, nome, papeis, token, expires_at, created_by, status, created_at, updated_at)
VALUES
('20000000-0000-0000-0000-000000000002',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
 'expired.user@example.com',
 'Expired User',
 ARRAY['OPERADOR'],
 'expired-token-12345678901234567890',
 NOW() - INTERVAL '2 days',
 '10000000-0000-0000-0000-000000000001',
 'EXPIRED',
 NOW() - INTERVAL '3 days',
 NOW() - INTERVAL '2 days')
ON CONFLICT (id) DO NOTHING;

-- ACTIVATED invitation (successfully activated)
INSERT INTO convite (id, tenant_id, email, nome, papeis, token, expires_at, created_by, activated_at, usuario_id, status, created_at, updated_at)
VALUES
('20000000-0000-0000-0000-000000000003',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
 'activated.user@example.com',
 'Activated Test User',
 ARRAY['GERENTE'],
 'activated-token-12345678901234567890',
 NOW() + INTERVAL '24 hours',
 '10000000-0000-0000-0000-000000000001',
 NOW() - INTERVAL '12 hours',
 '10000000-0000-0000-0000-000000000002',
 'ACTIVATED',
 NOW() - INTERVAL '2 days',
 NOW() - INTERVAL '12 hours')
ON CONFLICT (id) DO NOTHING;

-- PENDING invitation with multiple roles
INSERT INTO convite (id, tenant_id, email, nome, papeis, token, expires_at, created_by, status, created_at, updated_at)
VALUES
('20000000-0000-0000-0000-000000000004',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
 'multi.role@example.com',
 'Multi Role User',
 ARRAY['GERENTE', 'OPERADOR'],
 'multi-role-token-12345678901234567890',
 NOW() + INTERVAL '36 hours',
 '10000000-0000-0000-0000-000000000001',
 'PENDING',
 NOW() - INTERVAL '30 minutes',
 NOW() - INTERVAL '30 minutes')
ON CONFLICT (id) DO NOTHING;

-- CANCELLED invitation (admin cancelled before activation)
INSERT INTO convite (id, tenant_id, email, nome, papeis, token, expires_at, created_by, status, created_at, updated_at)
VALUES
('20000000-0000-0000-0000-000000000005',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
 'cancelled.user@example.com',
 'Cancelled User',
 ARRAY['OPERADOR'],
 'cancelled-token-12345678901234567890',
 NOW() + INTERVAL '24 hours',
 '10000000-0000-0000-0000-000000000001',
 'CANCELLED',
 NOW() - INTERVAL '5 hours',
 NOW() - INTERVAL '1 hour')
ON CONFLICT (id) DO NOTHING;

-- PENDING invitation about to expire (for edge case testing)
INSERT INTO convite (id, tenant_id, email, nome, papeis, token, expires_at, created_by, status, created_at, updated_at)
VALUES
('20000000-0000-0000-0000-000000000006',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
 'almost.expired@example.com',
 'Almost Expired User',
 ARRAY['MECANICO'],
 'almost-expired-token-12345678901234567890',
 NOW() + INTERVAL '30 minutes',
 '10000000-0000-0000-0000-000000000001',
 'PENDING',
 NOW() - INTERVAL '47 hours',
 NOW() - INTERVAL '47 hours')
ON CONFLICT (id) DO NOTHING;

-- PENDING invitation for user that already exists (email collision test)
INSERT INTO convite (id, tenant_id, email, nome, papeis, token, expires_at, created_by, status, created_at, updated_at)
VALUES
('20000000-0000-0000-0000-000000000007',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
 'existing.user@example.com',
 'Existing User Reinvited',
 ARRAY['VENDEDOR'],
 'existing-user-token-12345678901234567890',
 NOW() + INTERVAL '24 hours',
 '10000000-0000-0000-0000-000000000001',
 'PENDING',
 NOW() - INTERVAL '2 hours',
 NOW() - INTERVAL '2 hours')
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- Additional Test Tenant for Cross-Tenant Testing
-- =====================================================
INSERT INTO tenant (id, slug, razao_social, status)
VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'other-company', 'Other Company Ltda', 'ativo')
ON CONFLICT (id) DO NOTHING;

INSERT INTO usuario (id, email, nome, ativo) VALUES
('10000000-0000-0000-0000-000000000099', 'admin.other@othercompany.com', 'Other Admin', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '10000000-0000-0000-0000-000000000099', ARRAY['ADMIN_TENANT'], TRUE)
ON CONFLICT (tenant_id, usuario_id) DO NOTHING;

-- Invitation from other tenant (for cross-tenant isolation testing)
INSERT INTO convite (id, tenant_id, email, nome, papeis, token, expires_at, created_by, status, created_at, updated_at)
VALUES
('20000000-0000-0000-0000-000000000099',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
 'other.tenant.user@example.com',
 'Other Tenant User',
 ARRAY['OPERADOR'],
 'other-tenant-token-12345678901234567890',
 NOW() + INTERVAL '24 hours',
 '10000000-0000-0000-0000-000000000099',
 'PENDING',
 NOW() - INTERVAL '1 hour',
 NOW() - INTERVAL '1 hour')
ON CONFLICT (id) DO NOTHING;

COMMENT ON DATABASE jetski_test IS 'Jetski SaaS - Test database with comprehensive integration test data';
