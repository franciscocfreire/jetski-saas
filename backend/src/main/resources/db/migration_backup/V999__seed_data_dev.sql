-- =====================================================
-- V999: Seed Data (Development)
-- =====================================================
-- Sample data for local development and testing
-- IMPORTANT: This migration should NOT run in production
-- Use Flyway profiles to exclude: flyway.locations=db/migration/prod
-- =====================================================

-- =====================================================
-- Fix Tenant Status CHECK constraint (must run before inserts)
-- =====================================================
-- Drop old CHECK constraint and add new one with UPPERCASE values
ALTER TABLE tenant DROP CONSTRAINT IF EXISTS tenant_status_check;
ALTER TABLE tenant ADD CONSTRAINT tenant_status_check
    CHECK (status = ANY (ARRAY['TRIAL', 'ATIVO', 'SUSPENSO', 'CANCELADO', 'INATIVO']));

-- =====================================================
-- Planos (Subscription Plans)
-- =====================================================
INSERT INTO plano (nome, descricao, limites_json, preco_mensal, ativo) VALUES
('Basic', 'Plano básico para operações pequenas',
 '{"frota_max": 5, "usuarios_max": 3, "storage_gb": 5, "locacoes_mes": 100}'::jsonb,
 99.00, TRUE),

('Pro', 'Plano profissional para operações médias',
 '{"frota_max": 20, "usuarios_max": 10, "storage_gb": 50, "locacoes_mes": 500}'::jsonb,
 299.00, TRUE),

('Enterprise', 'Plano empresarial para operações grandes',
 '{"frota_max": 100, "usuarios_max": 50, "storage_gb": 500, "locacoes_mes": -1}'::jsonb,
 999.00, TRUE);

-- =====================================================
-- Tenant (Sample Company)
-- =====================================================
INSERT INTO tenant (id, slug, razao_social, cnpj, timezone, moeda, status, contato, branding_json)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'praia-do-sol', 'Praia do Sol Jet Ski Ltda', '12.345.678/0001-90',
 'America/Sao_Paulo', 'BRL', 'ATIVO',
 '{"telefone": "+55 11 98765-4321", "email": "contato@praiadosol.com.br", "endereco": "Av. Atlântica, 1000 - Copacabana, Rio de Janeiro/RJ"}'::jsonb,
 '{"logo_url": "https://example.com/logo.png", "cor_primaria": "#0066CC", "cor_secundaria": "#FFD700"}'::jsonb),

('b0000000-0000-0000-0000-000000000001', 'marina-bay-club', 'Marina Bay Club Ltda', '22.345.678/0001-91',
 'America/Sao_Paulo', 'BRL', 'ATIVO',
 '{"telefone": "+55 21 98888-1111", "email": "contato@marinabay.com.br", "endereco": "Av. das Américas, 5000 - Barra da Tijuca, Rio de Janeiro/RJ"}'::jsonb,
 '{"logo_url": "https://example.com/marina-logo.png", "cor_primaria": "#003366", "cor_secundaria": "#66CCFF"}'::jsonb),

('b0000000-0000-0000-0000-000000000002', 'copacabana-jet-ski', 'Copacabana Jet Ski Rentals Ltda', '32.345.678/0001-92',
 'America/Sao_Paulo', 'BRL', 'ATIVO',
 '{"telefone": "+55 21 97777-2222", "email": "contato@copajets.com.br", "endereco": "Av. Atlântica, 3500 - Copacabana, Rio de Janeiro/RJ"}'::jsonb,
 '{"logo_url": "https://example.com/copa-logo.png", "cor_primaria": "#FF6600", "cor_secundaria": "#FFCC00"}'::jsonb),

('b0000000-0000-0000-0000-000000000003', 'ipanema-beach-rentals', 'Ipanema Beach Rentals Ltda', '42.345.678/0001-93',
 'America/Sao_Paulo', 'BRL', 'ATIVO',
 '{"telefone": "+55 21 96666-3333", "email": "contato@ipanemabeach.com.br", "endereco": "Av. Vieira Souto, 500 - Ipanema, Rio de Janeiro/RJ"}'::jsonb,
 '{"logo_url": "https://example.com/ipanema-logo.png", "cor_primaria": "#9933CC", "cor_secundaria": "#FF99CC"}'::jsonb),

('b0000000-0000-0000-0000-000000000005', 'buzios-jet-adventures', 'Búzios Jet Adventures Ltda', '52.345.678/0001-95',
 'America/Sao_Paulo', 'BRL', 'ATIVO',
 '{"telefone": "+55 22 95555-5555", "email": "contato@buziosjets.com.br", "endereco": "Rua das Pedras, 200 - Búzios, RJ"}'::jsonb,
 '{"logo_url": "https://example.com/buzios-logo.png", "cor_primaria": "#00CC99", "cor_secundaria": "#FFFF00"}'::jsonb),

('b0000000-0000-0000-0000-000000000009', 'angra-paradise-jets', 'Angra Paradise Jets Ltda', '62.345.678/0001-99',
 'America/Sao_Paulo', 'BRL', 'ATIVO',
 '{"telefone": "+55 24 94444-9999", "email": "contato@angraparadise.com.br", "endereco": "Praia do Abraão, s/n - Angra dos Reis, RJ"}'::jsonb,
 '{"logo_url": "https://example.com/angra-logo.png", "cor_primaria": "#006633", "cor_secundaria": "#99FF66"}'::jsonb);

-- =====================================================
-- Assinaturas (Active Subscriptions)
-- =====================================================
INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, dt_fim, status, pagamento_cfg_json)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', (SELECT id FROM plano WHERE nome = 'Pro'), 'mensal', '2025-01-01', NULL, 'ativa',
 '{"metodo": "cartao", "ultimos_digitos": "4321", "vencimento_dia": 1}'::jsonb),

('b0000000-0000-0000-0000-000000000001', (SELECT id FROM plano WHERE nome = 'Pro'), 'mensal', '2025-01-01', NULL, 'ativa',
 '{"metodo": "cartao", "ultimos_digitos": "1111", "vencimento_dia": 5}'::jsonb),

('b0000000-0000-0000-0000-000000000002', (SELECT id FROM plano WHERE nome = 'Basic'), 'mensal', '2025-01-01', NULL, 'ativa',
 '{"metodo": "cartao", "ultimos_digitos": "2222", "vencimento_dia": 10}'::jsonb),

('b0000000-0000-0000-0000-000000000003', (SELECT id FROM plano WHERE nome = 'Pro'), 'mensal', '2025-01-01', NULL, 'ativa',
 '{"metodo": "cartao", "ultimos_digitos": "3333", "vencimento_dia": 15}'::jsonb),

('b0000000-0000-0000-0000-000000000005', (SELECT id FROM plano WHERE nome = 'Enterprise'), 'mensal', '2025-01-01', NULL, 'ativa',
 '{"metodo": "cartao", "ultimos_digitos": "5555", "vencimento_dia": 20}'::jsonb),

('b0000000-0000-0000-0000-000000000009', (SELECT id FROM plano WHERE nome = 'Basic'), 'mensal', '2025-01-01', NULL, 'ativa',
 '{"metodo": "cartao", "ultimos_digitos": "9999", "vencimento_dia": 25}'::jsonb);

-- =====================================================
-- Usuarios (Sample Users)
-- =====================================================
-- IMPORTANTE: PostgreSQL UUIDs são INDEPENDENTES dos Keycloak UUIDs
-- Mapeamento feito via tabela usuario_identity_provider
-- Ver: /infra/keycloak-setup/setup-keycloak-local.sh
-- =====================================================

-- 5 Usuarios principais do Keycloak (emails @acme.com para Postman collections)
INSERT INTO usuario (id, email, nome, ativo) VALUES
('00000000-aaaa-aaaa-aaaa-000000000001', 'admin@acme.com', 'Admin ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000002', 'gerente@acme.com', 'Gerente ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000003', 'operador@acme.com', 'Operador ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000004', 'vendedor@acme.com', 'Vendedor ACME', TRUE),
('00000000-aaaa-aaaa-aaaa-000000000005', 'mecanico@acme.com', 'Mecanico ACME', TRUE),

-- Usuários adicionais do tenant (internos - exemplo com emails @praiadosol)
('11111111-1111-1111-1111-111111111111', 'carlos.admin@praiadosol.com.br', 'Carlos Admin Silva', TRUE),
('22222222-2222-2222-2222-222222222222', 'marina.gerente@praiadosol.com.br', 'Marina Santos Oliveira', TRUE),
('33333333-3333-3333-3333-333333333333', 'joao.operador@praiadosol.com.br', 'João Operador Costa', TRUE),
('44444444-4444-4444-4444-444444444444', 'ana.vendedora@praiadosol.com.br', 'Ana Vendedora Lima', TRUE),
('55555555-5555-5555-5555-555555555555', 'pedro.mecanico@praiadosol.com.br', 'Pedro Mecânico Souza', TRUE),
('66666666-6666-6666-6666-666666666666', 'luciana.financeiro@praiadosol.com.br', 'Luciana Financeira Pereira', TRUE);

-- =====================================================
-- Identity Provider Mapping (Keycloak)
-- =====================================================
-- Maps internal PostgreSQL UUIDs to external Keycloak UUIDs
-- IMPORTANT: Keycloak UUIDs are from setup-keycloak-local.sh
-- If Keycloak is reset, only this table needs to be updated
-- =====================================================
-- NOTE: Keycloak UUIDs will be updated by setup-keycloak-local.sh after user creation
-- Placeholder UUIDs below - will be replaced on first Keycloak setup
INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) VALUES
('00000000-aaaa-aaaa-aaaa-000000000001', 'keycloak', '00000000-0000-0000-0000-000000000001', NOW()),
('00000000-aaaa-aaaa-aaaa-000000000002', 'keycloak', '00000000-0000-0000-0000-000000000002', NOW()),
('00000000-aaaa-aaaa-aaaa-000000000003', 'keycloak', '00000000-0000-0000-0000-000000000003', NOW()),
('00000000-aaaa-aaaa-aaaa-000000000004', 'keycloak', '00000000-0000-0000-0000-000000000004', NOW()),
('00000000-aaaa-aaaa-aaaa-000000000005', 'keycloak', '00000000-0000-0000-0000-000000000005', NOW());

-- =====================================================
-- Membros (User-Tenant Relationships with Roles)
-- =====================================================
-- 5 Membros principais do Keycloak (@acme.com)
INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000001', ARRAY['ADMIN_TENANT'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000002', ARRAY['GERENTE'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000003', ARRAY['OPERADOR'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000004', ARRAY['VENDEDOR'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-aaaa-aaaa-aaaa-000000000005', ARRAY['MECANICO'], TRUE),

-- Membros adicionais do tenant (@praiadosol)
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '11111111-1111-1111-1111-111111111111', ARRAY['ADMIN_TENANT'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '22222222-2222-2222-2222-222222222222', ARRAY['GERENTE'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '33333333-3333-3333-3333-333333333333', ARRAY['OPERADOR'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '44444444-4444-4444-4444-444444444444', ARRAY['VENDEDOR'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '55555555-5555-5555-5555-555555555555', ARRAY['MECANICO'], TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '66666666-6666-6666-6666-666666666666', ARRAY['FINANCEIRO'], TRUE);

-- =====================================================
-- Modelos (Jetski Models)
-- =====================================================
INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                    preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                    inclui_combustivel, foto_referencia_url, ativo)
VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Sea-Doo Spark', 'Sea-Doo', 90, 2, 150.00, 5, 50.00, 300.00, FALSE,
 'https://example.com/seadoo-spark.jpg', TRUE),

('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Yamaha VX Cruiser', 'Yamaha', 110, 3, 200.00, 5, 75.00, 500.00, FALSE,
 'https://example.com/yamaha-vx.jpg', TRUE),

('cccccccc-cccc-cccc-cccc-cccccccccccc', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Kawasaki Ultra 310', 'Kawasaki', 310, 3, 350.00, 5, 120.00, 800.00, FALSE,
 'https://example.com/kawasaki-ultra.jpg', TRUE);

-- =====================================================
-- Jetskis (Individual Units)
-- =====================================================
INSERT INTO jetski (id, tenant_id, modelo_id, serie, placa, ano, horimetro_atual, status, ativo)
VALUES
('d1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'SPARK001', 'JET-0001', 2023, 120.5, 'DISPONIVEL', TRUE),

('d2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'SPARK002', 'JET-0002', 2023, 95.3, 'DISPONIVEL', TRUE),

('d3333333-3333-3333-3333-333333333333', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'VXCRU001', 'JET-0003', 2022, 250.8, 'DISPONIVEL', TRUE),

('d4444444-4444-4444-4444-444444444444', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'VXCRU002', 'JET-0004', 2022, 310.2, 'DISPONIVEL', TRUE),

('d5555555-5555-5555-5555-555555555555', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'ULTRA001', 'JET-0005', 2024, 45.7, 'MANUTENCAO', TRUE),

-- Jetskis for Marina Bay Club (b0000000-0000-0000-0000-000000000001)
('e1111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000001',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'MARINA-001', 'BAY-001', 2024, 50.0, 'DISPONIVEL', TRUE),
('e2222222-2222-2222-2222-222222222222', 'b0000000-0000-0000-0000-000000000001',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'MARINA-002', 'BAY-002', 2023, 180.5, 'DISPONIVEL', TRUE),

-- Jetskis for Copacabana Jet Ski (b0000000-0000-0000-0000-000000000002)
('e3333333-3333-3333-3333-333333333333', 'b0000000-0000-0000-0000-000000000002',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'COPA-001', 'CPJ-001', 2023, 95.3, 'DISPONIVEL', TRUE),
('e4444444-4444-4444-4444-444444444444', 'b0000000-0000-0000-0000-000000000002',
 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'COPA-002', 'CPJ-002', 2024, 30.0, 'DISPONIVEL', TRUE),

-- Jetskis for Ipanema Beach Rentals (b0000000-0000-0000-0000-000000000003)
('e5555555-5555-5555-5555-555555555555', 'b0000000-0000-0000-0000-000000000003',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'IPA-001', 'IPB-001', 2022, 310.0, 'DISPONIVEL', TRUE),
('e6666666-6666-6666-6666-666666666666', 'b0000000-0000-0000-0000-000000000003',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'IPA-002', 'IPB-002', 2023, 120.0, 'MANUTENCAO', TRUE),

-- Jetskis for Búzios Jet Adventures (b0000000-0000-0000-0000-000000000005)
('e7777777-7777-7777-7777-777777777777', 'b0000000-0000-0000-0000-000000000005',
 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'BUZIOS-001', 'BJA-001', 2024, 25.5, 'DISPONIVEL', TRUE),
('e8888888-8888-8888-8888-888888888888', 'b0000000-0000-0000-0000-000000000005',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'BUZIOS-002', 'BJA-002', 2023, 150.0, 'DISPONIVEL', TRUE),
('e9999999-9999-9999-9999-999999999999', 'b0000000-0000-0000-0000-000000000005',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'BUZIOS-003', 'BJA-003', 2023, 200.0, 'DISPONIVEL', TRUE),

-- Jetskis for Angra Paradise Jets (b0000000-0000-0000-0000-000000000009)
('f1111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000009',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ANGRA-001', 'APJ-001', 2024, 15.0, 'DISPONIVEL', TRUE),
('f2222222-2222-2222-2222-222222222222', 'b0000000-0000-0000-0000-000000000009',
 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'ANGRA-002', 'APJ-002', 2023, 85.0, 'DISPONIVEL', TRUE);

-- =====================================================
-- Vendedores (Sellers/Partners)
-- =====================================================
INSERT INTO vendedor (id, tenant_id, nome, documento, tipo, regra_comissao_json, ativo)
VALUES
('a1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Ana Vendedora Lima', '123.456.789-00', 'interno',
 '{"tipo": "percentual", "percentual": 10.0}'::jsonb, TRUE),

('a2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Roberto Parceiro Santos', '987.654.321-00', 'parceiro',
 '{"tipo": "percentual", "percentual": 15.0}'::jsonb, TRUE),

('a3333333-3333-3333-3333-333333333333', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Hotel Copacabana Beach', '11.222.333/0001-44', 'parceiro',
 '{"tipo": "fixo", "valor_fixo": 25.00}'::jsonb, TRUE);

-- =====================================================
-- Clientes (Customers)
-- =====================================================
INSERT INTO cliente (id, tenant_id, nome, documento, email, telefone, whatsapp, endereco, termo_aceite, ativo)
VALUES
('c1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Ricardo Turista Silva', '111.222.333-44',
 'ricardo@email.com', '+5511999887766', NULL,
 '{"cep": "01310-100", "logradouro": "Av. Paulista", "numero": "1000", "cidade": "São Paulo", "estado": "SP"}'::jsonb,
 TRUE, TRUE),

('c2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Juliana Costa Oliveira', '222.333.444-55',
 'juliana@email.com', '+5521988776655', '+5521988776655',
 '{"cep": "20000-000", "logradouro": "Rua das Flores", "numero": "200", "cidade": "Rio de Janeiro", "estado": "RJ"}'::jsonb,
 TRUE, TRUE),

('c3333333-3333-3333-3333-333333333333', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Fernando Santos Almeida', '333.444.555-66',
 'fernando@email.com', '+5511977665544', NULL,
 '{"cep": "22000-000", "logradouro": "Av. Atlântica", "numero": "500", "cidade": "Rio de Janeiro", "estado": "RJ"}'::jsonb,
 TRUE, TRUE),

('c4444444-4444-4444-4444-444444444444', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Patricia Rodrigues Lima', '444.555.666-77',
 'patricia@email.com', '+5521966554433', NULL,
 '{"cep": "24000-000", "logradouro": "Rua do Comércio", "numero": "300", "cidade": "Niterói", "estado": "RJ"}'::jsonb,
 TRUE, TRUE),

('c5555555-5555-5555-5555-555555555555', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Marcos Vieira Costa', '555.666.777-88',
 'marcos@email.com', '+5511955443322', NULL,
 '{"cep": "01046-000", "logradouro": "Av. Ipiranga", "numero": "1500", "cidade": "São Paulo", "estado": "SP"}'::jsonb,
 TRUE, TRUE);

-- =====================================================
-- Fuel Policy (Default Configuration)
-- =====================================================
-- Global default: Measured (charge by liters consumed)
INSERT INTO fuel_policy (tenant_id, modelo_id, jetski_id, modo, taxa_fixa_hora, comissionavel, ativo)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', NULL, NULL, 'medido', NULL, FALSE, TRUE);

-- Model-specific: Kawasaki Ultra includes fuel (premium model)
INSERT INTO fuel_policy (tenant_id, modelo_id, jetski_id, modo, taxa_fixa_hora, comissionavel, ativo)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'cccccccc-cccc-cccc-cccc-cccccccccccc', NULL, 'incluso', NULL, FALSE, TRUE);

-- =====================================================
-- Fuel Price Day (Recent prices)
-- =====================================================
INSERT INTO fuel_price_day (tenant_id, dt_referencia, preco_litro, fonte, observacoes)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE - INTERVAL '7 days', 6.85, 'Posto Ipiranga', 'Gasolina comum'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE - INTERVAL '6 days', 6.90, 'Posto Shell', 'Gasolina comum'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE - INTERVAL '5 days', 6.88, 'Posto Ipiranga', 'Gasolina comum'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE - INTERVAL '4 days', 6.92, 'Posto BR', 'Gasolina comum'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE - INTERVAL '3 days', 6.95, 'Posto Shell', 'Gasolina comum'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE - INTERVAL '2 days', 6.93, 'Posto Ipiranga', 'Gasolina comum'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE - INTERVAL '1 day', 6.89, 'Posto BR', 'Gasolina comum'),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', CURRENT_DATE, 6.90, 'Posto Ipiranga', 'Gasolina comum');

-- =====================================================
-- Commission Policy (Hierarchical Rules)
-- =====================================================
-- Default for all sellers: 10%
INSERT INTO commission_policy (tenant_id, tipo_regra, prioridade, tipo_comissao, valor_percentual, ativo)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'vendedor_padrao', 100, 'percentual', 10.0, TRUE);

-- Partner sellers: 15%
INSERT INTO commission_policy (tenant_id, tipo_regra, prioridade, vendedor_id, tipo_comissao, valor_percentual, ativo)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'vendedor_padrao', 90, 'a2222222-2222-2222-2222-222222222222', 'percentual', 15.0, TRUE),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'vendedor_padrao', 90, 'a3333333-3333-3333-3333-333333333333', 'fixo', 25.0, TRUE);

-- Premium model: Higher commission (12%)
INSERT INTO commission_policy (tenant_id, tipo_regra, prioridade, modelo_id, tipo_comissao, valor_percentual, ativo)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'modelo', 80, 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'percentual', 12.0, TRUE);

-- Long duration bonus: 15% for rentals > 2 hours
INSERT INTO commission_policy (tenant_id, tipo_regra, prioridade, duracao_min_min, tipo_comissao, valor_percentual, ativo)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'duracao', 70, 120, 'percentual', 15.0, TRUE);

-- Campaign: Summer 2025 - 20% commission
INSERT INTO commission_policy (tenant_id, tipo_regra, prioridade, campanha_nome, campanha_dt_inicio, campanha_dt_fim,
                               tipo_comissao, valor_percentual, ativo)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'campanha', 10, 'Verão 2025', '2025-01-01', '2025-03-31', 'percentual', 20.0, TRUE);

-- =====================================================
-- Sample Reserva (Upcoming reservation)
-- =====================================================
INSERT INTO reserva (id, tenant_id, cliente_id, jetski_id, vendedor_id,
                     data_inicio, data_fim_prevista, status, observacoes, ativo)
VALUES
('e1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'c1111111-1111-1111-1111-111111111111', 'd1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111',
 NOW() + INTERVAL '2 hours', NOW() + INTERVAL '3 hours', 'CONFIRMADA', 'Reserva confirmada para turista', TRUE);

-- =====================================================
-- Sample Locacao (Active rental)
-- =====================================================
INSERT INTO locacao (id, tenant_id, reserva_id, cliente_id, jetski_id, vendedor_id,
                     dt_checkin, horimetro_inicio, operador_checkin_id,
                     checklist_saida_json, status)
VALUES
('f1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 NULL, 'c2222222-2222-2222-2222-222222222222', 'd4444444-4444-4444-4444-444444444444', 'a1111111-1111-1111-1111-111111111111',
 NOW() - INTERVAL '30 minutes', 310.2, '33333333-3333-3333-3333-333333333333',
 '["motor_ok", "casco_ok", "gasolina_ok", "colete_entregue"]'::jsonb, 'EM_CURSO');

-- =====================================================
-- Sample OS Manutencao (Open maintenance order)
-- =====================================================
INSERT INTO os_manutencao (id, tenant_id, jetski_id, mecanico_id, tipo, prioridade,
                           dt_abertura, descricao_problema, horimetro_abertura, status)
VALUES
('f2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'd5555555-5555-5555-5555-555555555555', '55555555-5555-5555-5555-555555555555',
 'preventiva', 'media', NOW() - INTERVAL '1 day',
 'Revisão preventiva 50 horas - verificar velas, óleo, filtros', 45.7, 'em_andamento');

-- =====================================================
-- Multi-Tenant User (Simulate 10+ tenants)
-- =====================================================
INSERT INTO usuario (id, email, nome, ativo) VALUES
('00000000-0000-0000-0000-000000000002', 'gerente.multi@example.com', 'Gerente Multi-Tenant', TRUE);

-- Multi-tenant memberships (same user in 6 tenants with different roles)
INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '00000000-0000-0000-0000-000000000002', ARRAY['ADMIN_TENANT'], TRUE),
('b0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', ARRAY['GERENTE'], TRUE),
('b0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', ARRAY['OPERADOR'], TRUE),
('b0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', ARRAY['GERENTE', 'OPERADOR'], TRUE),
('b0000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002', ARRAY['ADMIN_TENANT'], TRUE),
('b0000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000002', ARRAY['OPERADOR'], TRUE);

COMMENT ON DATABASE jetski_dev IS 'Jetski SaaS - Development database with seed data including multi-tenant test users';
