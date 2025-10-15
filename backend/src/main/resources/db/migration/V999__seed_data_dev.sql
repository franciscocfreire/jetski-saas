-- =====================================================
-- V999: Seed Data (Development)
-- =====================================================
-- Sample data for local development and testing
-- IMPORTANT: This migration should NOT run in production
-- Use Flyway profiles to exclude: flyway.locations=db/migration/prod
-- =====================================================

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
 'America/Sao_Paulo', 'BRL', 'ativo',
 '{"telefone": "+55 11 98765-4321", "email": "contato@praiadosol.com.br", "endereco": "Av. Atlântica, 1000 - Copacabana, Rio de Janeiro/RJ"}'::jsonb,
 '{"logo_url": "https://example.com/logo.png", "cor_primaria": "#0066CC", "cor_secundaria": "#FFD700"}'::jsonb);

-- =====================================================
-- Assinatura (Active Subscription)
-- =====================================================
INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, dt_fim, status, pagamento_cfg_json)
VALUES
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 2, 'mensal', '2025-01-01', NULL, 'ativa',
 '{"metodo": "cartao", "ultimos_digitos": "4321", "vencimento_dia": 1}'::jsonb);

-- =====================================================
-- Usuarios (Sample Users)
-- =====================================================
INSERT INTO usuario (id, email, nome, ativo) VALUES
('11111111-1111-1111-1111-111111111111', 'admin@praiadosol.com.br', 'Carlos Admin Silva', TRUE),
('22222222-2222-2222-2222-222222222222', 'gerente@praiadosol.com.br', 'Marina Santos Oliveira', TRUE),
('33333333-3333-3333-3333-333333333333', 'operador@praiadosol.com.br', 'João Operador Costa', TRUE),
('44444444-4444-4444-4444-444444444444', 'vendedor@praiadosol.com.br', 'Ana Vendedora Lima', TRUE),
('55555555-5555-5555-5555-555555555555', 'mecanico@praiadosol.com.br', 'Pedro Mecânico Souza', TRUE),
('66666666-6666-6666-6666-666666666666', 'financeiro@praiadosol.com.br', 'Luciana Financeira Pereira', TRUE);

-- =====================================================
-- Membros (User-Tenant Relationships with Roles)
-- =====================================================
INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES
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
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'SPARK001', 'JET-0001', 2023, 120.5, 'disponivel', TRUE),

('d2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'SPARK002', 'JET-0002', 2023, 95.3, 'disponivel', TRUE),

('d3333333-3333-3333-3333-333333333333', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'VXCRU001', 'JET-0003', 2022, 250.8, 'disponivel', TRUE),

('d4444444-4444-4444-4444-444444444444', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'VXCRU002', 'JET-0004', 2022, 310.2, 'locado', TRUE),

('d5555555-5555-5555-5555-555555555555', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'ULTRA001', 'JET-0005', 2024, 45.7, 'manutencao', TRUE);

-- =====================================================
-- Vendedores (Sellers/Partners)
-- =====================================================
INSERT INTO vendedor (id, tenant_id, nome, documento, tipo, telefone, email,
                      regra_comissao_json, ativo)
VALUES
('a1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Ana Vendedora Lima', '123.456.789-00', 'interno', '+55 11 91234-5678', 'vendedor@praiadosol.com.br',
 '{"tipo": "percentual", "percentual": 10.0}'::jsonb, TRUE),

('a2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Roberto Parceiro Santos', '987.654.321-00', 'parceiro', '+55 21 98765-4321', 'roberto@parceiro.com.br',
 '{"tipo": "percentual", "percentual": 15.0}'::jsonb, TRUE),

('a3333333-3333-3333-3333-333333333333', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Hotel Copacabana Beach', '11.222.333/0001-44', 'parceiro', '+55 21 3333-4444', 'vendas@hotelcopacabana.com.br',
 '{"tipo": "fixo", "valor_fixo": 25.00}'::jsonb, TRUE);

-- =====================================================
-- Clientes (Customers)
-- =====================================================
INSERT INTO cliente (id, tenant_id, nome, documento, data_nascimento, telefone, email,
                     endereco, termo_aceite, termo_aceite_data, ativo)
VALUES
('c1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Ricardo Turista Silva', '111.222.333-44', '1985-03-15', '+55 11 99988-7766', 'ricardo@email.com',
 '{"rua": "Av. Paulista, 1000", "cidade": "São Paulo", "estado": "SP", "cep": "01310-100"}'::jsonb,
 TRUE, NOW(), TRUE),

('c2222222-2222-2222-2222-222222222222', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Juliana Costa Oliveira', '222.333.444-55', '1990-07-20', '+55 21 98877-6655', 'juliana@email.com',
 '{"rua": "Rua das Flores, 200", "cidade": "Rio de Janeiro", "estado": "RJ", "cep": "20000-000"}'::jsonb,
 TRUE, NOW(), TRUE),

('c3333333-3333-3333-3333-333333333333', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Fernando Santos Almeida', '333.444.555-66', '1988-11-10', '+55 11 97766-5544', 'fernando@email.com',
 '{"rua": "Av. Atlântica, 500", "cidade": "Rio de Janeiro", "estado": "RJ", "cep": "22000-000"}'::jsonb,
 TRUE, NOW(), TRUE),

('c4444444-4444-4444-4444-444444444444', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Patricia Rodrigues Lima', '444.555.666-77', '1992-02-28', '+55 21 96655-4433', 'patricia@email.com',
 '{"rua": "Rua do Comércio, 300", "cidade": "Niterói", "estado": "RJ", "cep": "24000-000"}'::jsonb,
 TRUE, NOW(), TRUE),

('c5555555-5555-5555-5555-555555555555', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'Marcos Vieira Costa', '555.666.777-88', '1983-09-05', '+55 11 95544-3322', 'marcos@email.com',
 '{"rua": "Av. Ipiranga, 1500", "cidade": "São Paulo", "estado": "SP", "cep": "01046-000"}'::jsonb,
 TRUE, NOW(), TRUE);

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
                     dt_inicio_prevista, dt_fim_prevista, duracao_prevista_min,
                     valor_previsto, caucao, status)
VALUES
('e1111111-1111-1111-1111-111111111111', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
 'c1111111-1111-1111-1111-111111111111', 'd1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111',
 NOW() + INTERVAL '2 hours', NOW() + INTERVAL '3 hours', 60, 150.00, 300.00, 'confirmada');

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
 '["motor_ok", "casco_ok", "gasolina_ok", "colete_entregue"]'::jsonb, 'em_andamento');

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

COMMENT ON DATABASE jetski_dev IS 'Jetski SaaS - Development database with seed data';
