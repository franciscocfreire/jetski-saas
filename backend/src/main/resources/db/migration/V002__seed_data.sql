-- ============================================================================
-- V002: Seed Data (Development)
-- ============================================================================
-- Sample data for local development and testing
-- IMPORTANT: This migration should NOT run in production
-- ============================================================================

-- ============================================================================
-- PLANOS (Subscription Plans)
-- ============================================================================
INSERT INTO plano (nome, limites, preco_mensal) VALUES
('Basic', '{"frota_max": 5, "usuarios_max": 3, "storage_gb": 5, "locacoes_mes": 100}'::jsonb, 99.00),
('Pro', '{"frota_max": 20, "usuarios_max": 10, "storage_gb": 50, "locacoes_mes": 500}'::jsonb, 299.00),
('Enterprise', '{"frota_max": 100, "usuarios_max": 50, "storage_gb": 500, "locacoes_mes": -1}'::jsonb, 999.00);

-- ============================================================================
-- TENANTS (Sample Companies)
-- ============================================================================
INSERT INTO tenant (id, slug, razao_social, cnpj, timezone, moeda, status, branding) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'acme', 'ACME Jet Ski Ltda', '12.345.678/0001-90',
 'America/Sao_Paulo', 'BRL', 'ATIVO', '{"cor_primaria": "#0066CC"}'::jsonb),
('b0000000-0000-0000-0000-000000000001', 'marina-bay', 'Marina Bay Club Ltda', '22.345.678/0001-91',
 'America/Sao_Paulo', 'BRL', 'ATIVO', '{"cor_primaria": "#003366"}'::jsonb),
('b0000000-0000-0000-0000-000000000002', 'copa-jets', 'Copacabana Jet Ski Rentals Ltda', '32.345.678/0001-92',
 'America/Sao_Paulo', 'BRL', 'ATIVO', '{"cor_primaria": "#FF6600"}'::jsonb);

-- ============================================================================
-- ASSINATURAS (Subscriptions)
-- ============================================================================
INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', (SELECT id FROM plano WHERE nome = 'Pro'), 'mensal', '2025-01-01', 'ativa'),
('b0000000-0000-0000-0000-000000000001', (SELECT id FROM plano WHERE nome = 'Pro'), 'mensal', '2025-01-01', 'ativa'),
('b0000000-0000-0000-0000-000000000002', (SELECT id FROM plano WHERE nome = 'Basic'), 'mensal', '2025-01-01', 'ativa');

-- ============================================================================
-- USUARIOS (Users)
-- ============================================================================
-- Main Keycloak users (@acme.com for Postman collections)
INSERT INTO usuario (id, email, nome, ativo) VALUES
('00000000-aaaa-aaaa-aaaa-000000000001', 'admin@acme.com', 'Admin ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000002', 'gerente@acme.com', 'Gerente ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000003', 'operador@acme.com', 'Operador ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000004', 'vendedor@acme.com', 'Vendedor ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000005', 'mecanico@acme.com', 'Mecanico ACME', TRUE),
-- Multi-tenant user
('00000000-0000-0000-0000-000000000002', 'gerente.multi@example.com', 'Gerente Multi-Tenant', TRUE);

-- ============================================================================
-- MEMBROS (User-Tenant Relationships with Roles)
-- ============================================================================
INSERT INTO membro (tenant_id, usuario_id, papeis) VALUES
-- ACME users
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000001', ARRAY['ADMIN_TENANT']),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000002', ARRAY['GERENTE']),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000003', ARRAY['OPERADOR']),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000004', ARRAY['VENDEDOR']),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000005', ARRAY['MECANICO']),
-- Multi-tenant memberships
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-0000-0000-0000-000000000002', ARRAY['ADMIN_TENANT']),
('b0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', ARRAY['GERENTE']),
('b0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', ARRAY['OPERADOR']);

-- ============================================================================
-- IDENTITY PROVIDER MAPPING (Keycloak)
-- ============================================================================
-- Placeholder UUIDs - will be replaced by setup-keycloak script
INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) VALUES
('00000000-aaaa-aaaa-aaaa-000000000001', 'keycloak', '00000000-0000-0000-0000-000000000001', NOW()),
('00000000-aaaa-aaaa-aaaa-000000000002', 'keycloak', '00000000-0000-0000-0000-000000000002', NOW()),
('00000000-aaaa-aaaa-aaaa-000000000003', 'keycloak', '00000000-0000-0000-0000-000000000003', NOW()),
('00000000-aaaa-aaaa-aaaa-000000000004', 'keycloak', '00000000-0000-0000-0000-000000000004', NOW()),
('00000000-aaaa-aaaa-aaaa-000000000005', 'keycloak', '00000000-0000-0000-0000-000000000005', NOW());

-- ============================================================================
-- TENANT ACCESS (User access to tenants with roles)
-- ============================================================================
INSERT INTO tenant_access (usuario_id, tenant_id, roles, is_default) VALUES
-- ACME users
('00000000-aaaa-aaaa-aaaa-000000000001', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', ARRAY['ADMIN_TENANT'], TRUE),
('00000000-aaaa-aaaa-aaaa-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', ARRAY['GERENTE'], TRUE),
('00000000-aaaa-aaaa-aaaa-000000000003', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', ARRAY['OPERADOR'], TRUE),
('00000000-aaaa-aaaa-aaaa-000000000004', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', ARRAY['VENDEDOR'], TRUE),
('00000000-aaaa-aaaa-aaaa-000000000005', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', ARRAY['MECANICO'], TRUE),
-- Multi-tenant user
('00000000-0000-0000-0000-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', ARRAY['ADMIN_TENANT'], TRUE),
('00000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001', ARRAY['GERENTE'], FALSE),
('00000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', ARRAY['OPERADOR'], FALSE);

-- ============================================================================
-- MODELOS (Jetski Models)
-- ============================================================================
INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas, preco_base_hora, tolerancia_min, pacotes_json, ativo) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Sea-Doo Spark', 'Sea-Doo', 90, 2, 150.00, 5, '[{"duracao_min": 30, "preco": 100.00}, {"duracao_min": 60, "preco": 150.00}]'::jsonb, TRUE),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Yamaha VX Cruiser', 'Yamaha', 110, 3, 200.00, 5, '[{"duracao_min": 30, "preco": 130.00}, {"duracao_min": 60, "preco": 200.00}]'::jsonb, TRUE),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Kawasaki Ultra 310', 'Kawasaki', 310, 3, 350.00, 5, '[{"duracao_min": 30, "preco": 220.00}, {"duracao_min": 60, "preco": 350.00}]'::jsonb, TRUE);

-- ============================================================================
-- JETSKIS (Individual Units)
-- ============================================================================
INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, horimetro_atual, status, ativo) VALUES
('d1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'SPARK001', 2023, 120.5, 'DISPONIVEL', TRUE),
('d2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'SPARK002', 2023, 95.3, 'DISPONIVEL', TRUE),
('d3333333-3333-3333-3333-333333333333', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'VXCRU001', 2022, 250.8, 'DISPONIVEL', TRUE),
('d4444444-4444-4444-4444-444444444444', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'VXCRU002', 2022, 310.2, 'DISPONIVEL', TRUE),
('d5555555-5555-5555-5555-555555555555', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'ULTRA001', 2024, 45.7, 'MANUTENCAO', TRUE);

-- ============================================================================
-- VENDEDORES (Sellers)
-- ============================================================================
INSERT INTO vendedor (id, tenant_id, nome, documento, tipo, regra_comissao_json, ativo) VALUES
('a1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Vendedor ACME', '111.222.333-44', 'INTERNO', '{"percentual_padrao": 10.0}'::jsonb, TRUE),
('a2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Roberto Parceiro Santos', '22.333.444/0001-55', 'PARCEIRO', '{"percentual_padrao": 15.0}'::jsonb, TRUE);

-- ============================================================================
-- CLIENTES (Customers)
-- ============================================================================
INSERT INTO cliente (id, tenant_id, nome, documento, email, telefone, whatsapp, termo_aceite, ativo) VALUES
('c1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Ricardo Turista Silva', '111.222.333-44', 'ricardo@email.com', '+5511999887766', '+5511999887766', TRUE, TRUE),
('c2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Juliana Costa Oliveira', '222.333.444-55', 'juliana@email.com', '+5521988776655', '+5521988776655', TRUE, TRUE),
('c3333333-3333-3333-3333-333333333333', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Fernando Santos Almeida', '333.444.555-66', 'fernando@email.com', '+5511977665544', '+5511977665544', FALSE, TRUE);

-- ============================================================================
-- FUEL POLICY (Default Configuration)
-- ============================================================================
INSERT INTO fuel_policy (tenant_id, nome, tipo, aplicavel_a, referencia_id, prioridade, comissionavel, ativo, descricao) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Padrão - Medido', 'MEDIDO', 'GLOBAL', NULL, 0, FALSE, TRUE, 'Política padrão: cobra combustível por litros consumidos'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Premium - Incluso', 'INCLUSO', 'MODELO', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 1, FALSE, TRUE, 'Kawasaki Ultra 310: combustível incluso no preço');

-- ============================================================================
-- FUEL PRICE DAY (Recent prices)
-- ============================================================================
INSERT INTO fuel_price_day (tenant_id, data, preco_litro) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE - INTERVAL '2 days', 6.93),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE - INTERVAL '1 day', 6.89),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE, 6.90);

-- ============================================================================
-- GLOBAL ROLES (Platform-level roles)
-- ============================================================================
INSERT INTO global_role (name, description) VALUES
('PLATFORM_ADMIN', 'Full platform administrator'),
('SUPPORT', 'Customer support staff');
