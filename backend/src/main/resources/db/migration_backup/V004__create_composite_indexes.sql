-- =====================================================
-- V004: Create Composite Indexes
-- =====================================================
-- Creates composite indexes for optimal multi-tenant query performance
-- Pattern: (tenant_id, frequently_filtered_column)
-- Improves queries like: WHERE tenant_id = ? AND status = ?
-- =====================================================

-- =====================================================
-- Multi-tenant Base Tables
-- =====================================================

-- Assinatura: Find active subscription per tenant
CREATE INDEX idx_assinatura_tenant_status_active ON assinatura(tenant_id, status)
    WHERE status = 'ativa';

-- Membro: Find active members per tenant
CREATE INDEX idx_membro_tenant_ativo ON membro(tenant_id, ativo)
    WHERE ativo = TRUE;

-- =====================================================
-- Operational Tables
-- =====================================================

-- Modelo: List models by tenant + fabricante
CREATE INDEX idx_modelo_tenant_fabricante ON modelo(tenant_id, fabricante)
    WHERE ativo = TRUE;

-- Jetski: Critical queries for availability
CREATE INDEX idx_jetski_tenant_status_disponivel ON jetski(tenant_id, status)
    WHERE status = 'disponivel';

CREATE INDEX idx_jetski_tenant_modelo_status ON jetski(tenant_id, modelo_id, status);

-- Vendedor: List sellers by type
CREATE INDEX idx_vendedor_tenant_tipo ON vendedor(tenant_id, tipo)
    WHERE ativo = TRUE;

-- Cliente: Search by document or email
CREATE INDEX idx_cliente_tenant_documento ON cliente(tenant_id, documento)
    WHERE documento IS NOT NULL;

CREATE INDEX idx_cliente_tenant_email ON cliente(tenant_id, email)
    WHERE email IS NOT NULL;

-- =====================================================
-- Rental Flow Tables
-- =====================================================

-- Reserva: Critical for scheduling queries
CREATE INDEX idx_reserva_tenant_status_data ON reserva(tenant_id, status, dt_inicio_prevista)
    WHERE status IN ('pendente', 'confirmada');

CREATE INDEX idx_reserva_tenant_jetski_dt ON reserva(tenant_id, jetski_id, dt_inicio_prevista);

CREATE INDEX idx_reserva_tenant_cliente_dt ON reserva(tenant_id, cliente_id, dt_inicio_prevista DESC);

-- Locacao: Most accessed table - optimize heavily
CREATE INDEX idx_locacao_tenant_status_checkin ON locacao(tenant_id, status, dt_checkin DESC);

CREATE INDEX idx_locacao_tenant_jetski_dt ON locacao(tenant_id, jetski_id, dt_checkin DESC);

CREATE INDEX idx_locacao_tenant_cliente_dt ON locacao(tenant_id, cliente_id, dt_checkin DESC);

CREATE INDEX idx_locacao_tenant_vendedor_dt ON locacao(tenant_id, vendedor_id, dt_checkin DESC)
    WHERE vendedor_id IS NOT NULL;

-- Find open rentals (for dashboard)
CREATE INDEX idx_locacao_tenant_aberta ON locacao(tenant_id, dt_checkin DESC)
    WHERE status IN ('aberta', 'em_andamento');

-- Payment status queries
CREATE INDEX idx_locacao_tenant_pagamento ON locacao(tenant_id, status_pagamento, dt_checkin DESC)
    WHERE status_pagamento IN ('pendente', 'parcial');

-- Foto: Find photos by type
CREATE INDEX idx_foto_tenant_locacao_tipo ON foto(tenant_id, locacao_id, tipo, ordem);

-- =====================================================
-- Operational Support Tables
-- =====================================================

-- Abastecimento: Daily fuel reports
CREATE INDEX idx_abastecimento_tenant_dt_tipo ON abastecimento(tenant_id, dt_abastecimento DESC, tipo);

CREATE INDEX idx_abastecimento_tenant_jetski_dt ON abastecimento(tenant_id, jetski_id, dt_abastecimento DESC);

-- OS Manutencao: Find open maintenance orders
CREATE INDEX idx_os_manutencao_tenant_status_dt ON os_manutencao(tenant_id, status, dt_abertura DESC)
    WHERE status IN ('aberta', 'em_andamento', 'aguardando_pecas');

CREATE INDEX idx_os_manutencao_tenant_jetski_status ON os_manutencao(tenant_id, jetski_id, status);

-- =====================================================
-- Policy Tables
-- =====================================================

-- Commission Policy: Hierarchy lookup (order by prioridade ASC)
CREATE INDEX idx_commission_policy_tenant_active ON commission_policy(tenant_id, prioridade ASC)
    WHERE ativo = TRUE;

CREATE INDEX idx_commission_policy_tenant_modelo_active ON commission_policy(tenant_id, modelo_id, prioridade ASC)
    WHERE ativo = TRUE AND modelo_id IS NOT NULL;

CREATE INDEX idx_commission_policy_tenant_vendedor_active ON commission_policy(tenant_id, vendedor_id, prioridade ASC)
    WHERE ativo = TRUE AND vendedor_id IS NOT NULL;

-- Fuel Policy: Hierarchy lookup
CREATE INDEX idx_fuel_policy_tenant_jetski_active ON fuel_policy(tenant_id, jetski_id)
    WHERE ativo = TRUE AND jetski_id IS NOT NULL;

CREATE INDEX idx_fuel_policy_tenant_modelo_active ON fuel_policy(tenant_id, modelo_id)
    WHERE ativo = TRUE AND modelo_id IS NOT NULL;

-- Fuel Price Day: Date range queries
CREATE INDEX idx_fuel_price_day_tenant_dt_desc ON fuel_price_day(tenant_id, dt_referencia DESC);

-- =====================================================
-- Financial Closure Tables
-- =====================================================

-- Fechamento Diario: Date range queries for reports
CREATE INDEX idx_fechamento_diario_tenant_dt_status ON fechamento_diario(tenant_id, dt_referencia DESC, status);

-- Fechamento Mensal: Year/month queries
CREATE INDEX idx_fechamento_mensal_tenant_ano_mes_desc ON fechamento_mensal(tenant_id, ano DESC, mes DESC);

-- =====================================================
-- Audit Table
-- =====================================================

-- Auditoria: Recent actions per tenant
CREATE INDEX idx_auditoria_tenant_created_desc ON auditoria(tenant_id, created_at DESC);

-- Audit by entity
CREATE INDEX idx_auditoria_tenant_entidade_created ON auditoria(tenant_id, entidade, entidade_id, created_at DESC);

-- Audit by user
CREATE INDEX idx_auditoria_tenant_usuario_created ON auditoria(tenant_id, usuario_id, created_at DESC)
    WHERE usuario_id IS NOT NULL;

-- Audit by action
CREATE INDEX idx_auditoria_tenant_acao_created ON auditoria(tenant_id, acao, created_at DESC);

-- =====================================================
-- Covering Indexes (include columns for index-only scans)
-- =====================================================

-- Jetski availability: Include key fields to avoid table lookup
CREATE INDEX idx_jetski_availability_covering ON jetski(tenant_id, status, modelo_id)
    INCLUDE (serie, horimetro_atual, ativo);

-- Locacao summary: Include totals for reports
CREATE INDEX idx_locacao_summary_covering ON locacao(tenant_id, dt_checkin, status)
    INCLUDE (valor_total, valor_comissao, status_pagamento);

-- Commission calculation: Include commission fields
CREATE INDEX idx_locacao_commission_covering ON locacao(tenant_id, vendedor_id, dt_checkin)
    INCLUDE (valor_comissionavel, valor_comissao)
    WHERE vendedor_id IS NOT NULL;

COMMENT ON INDEX idx_jetski_availability_covering IS 'Covering index for jetski availability queries';
COMMENT ON INDEX idx_locacao_summary_covering IS 'Covering index for rental summary reports';
COMMENT ON INDEX idx_locacao_commission_covering IS 'Covering index for commission calculations';
