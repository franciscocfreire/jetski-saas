-- =====================================================
-- V003: Create Support Tables (Tenant-scoped)
-- =====================================================
-- Creates rental flow and support entities:
-- - reserva: Bookings/reservations
-- - locacao: Actual rental operations
-- - foto: Images (check-in/out/incidents)
-- - abastecimento: Fuel logs
-- - os_manutencao: Maintenance orders
-- - commission_policy: Commission rules hierarchy
-- - fuel_policy: Fuel pricing configuration
-- - fuel_price_day: Daily fuel prices
-- - fechamento_diario: Daily financial closures
-- - fechamento_mensal: Monthly closures
-- - auditoria: Audit trail
--
-- ALL tables include tenant_id for multi-tenant isolation
-- =====================================================

-- =====================================================
-- Table: reserva
-- Bookings/reservations with predicted start/end
-- =====================================================
CREATE TABLE reserva (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- References
    cliente_id UUID NOT NULL REFERENCES cliente(id),
    jetski_id UUID NOT NULL REFERENCES jetski(id),
    vendedor_id UUID REFERENCES vendedor(id),

    -- Schedule
    dt_inicio_prevista TIMESTAMP NOT NULL,
    dt_fim_prevista TIMESTAMP NOT NULL,
    duracao_prevista_min INT NOT NULL CHECK (duracao_prevista_min > 0),

    -- Pricing preview
    valor_previsto NUMERIC(10,2),
    caucao NUMERIC(10,2) DEFAULT 0 CHECK (caucao >= 0),

    -- Status workflow
    status TEXT NOT NULL CHECK (status IN ('pendente', 'confirmada', 'em_andamento', 'finalizada', 'cancelada')) DEFAULT 'pendente',

    -- Metadata
    observacoes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Business rules
    CONSTRAINT dt_fim_after_inicio CHECK (dt_fim_prevista > dt_inicio_prevista)
);

COMMENT ON TABLE reserva IS 'Bookings/reservations with predicted schedule (tenant-scoped)';
COMMENT ON COLUMN reserva.duracao_prevista_min IS 'Predicted duration in minutes';
COMMENT ON COLUMN reserva.status IS 'pendente, confirmada, em_andamento, finalizada, cancelada';

-- =====================================================
-- Table: locacao
-- Actual rental operations with check-in/out
-- =====================================================
CREATE TABLE locacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- References
    reserva_id UUID REFERENCES reserva(id),
    cliente_id UUID NOT NULL REFERENCES cliente(id),
    jetski_id UUID NOT NULL REFERENCES jetski(id),
    vendedor_id UUID REFERENCES vendedor(id),

    -- Check-in
    dt_checkin TIMESTAMP NOT NULL,
    horimetro_inicio NUMERIC(10,2) NOT NULL CHECK (horimetro_inicio >= 0),
    operador_checkin_id UUID REFERENCES usuario(id),
    checklist_saida_json JSONB,

    -- Check-out
    dt_checkout TIMESTAMP,
    horimetro_fim NUMERIC(10,2) CHECK (horimetro_fim >= horimetro_inicio),
    operador_checkout_id UUID REFERENCES usuario(id),
    checklist_entrada_json JSONB,

    -- Duration & billing
    duracao_real_min INT,
    duracao_faturavel_min INT,

    -- Values
    valor_base NUMERIC(10,2),
    valor_combustivel NUMERIC(10,2) DEFAULT 0,
    valor_extras NUMERIC(10,2) DEFAULT 0,
    valor_desconto NUMERIC(10,2) DEFAULT 0 CHECK (valor_desconto >= 0),
    valor_total NUMERIC(10,2),

    -- Commission
    valor_comissionavel NUMERIC(10,2),
    valor_comissao NUMERIC(10,2),

    -- Deposit
    caucao_paga NUMERIC(10,2) DEFAULT 0,
    caucao_devolvida NUMERIC(10,2) DEFAULT 0,

    -- Payment
    status_pagamento TEXT CHECK (status_pagamento IN ('pendente', 'parcial', 'pago', 'estornado')) DEFAULT 'pendente',
    forma_pagamento TEXT,
    dt_pagamento TIMESTAMP,

    -- Status workflow
    status TEXT NOT NULL CHECK (status IN ('aberta', 'em_andamento', 'finalizada', 'cancelada')) DEFAULT 'aberta',

    -- Metadata
    observacoes TEXT,
    incidentes_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Business rules
    CONSTRAINT dt_checkout_after_checkin CHECK (dt_checkout IS NULL OR dt_checkout >= dt_checkin)
);

COMMENT ON TABLE locacao IS 'Actual rental operations with check-in/check-out (tenant-scoped)';
COMMENT ON COLUMN locacao.duracao_real_min IS 'Actual duration = checkout - checkin (minutes)';
COMMENT ON COLUMN locacao.duracao_faturavel_min IS 'Billable duration after tolerance and rounding';
COMMENT ON COLUMN locacao.valor_comissionavel IS 'Revenue eligible for commission (excludes fuel, fees)';
COMMENT ON COLUMN locacao.checklist_saida_json IS 'Check-in checklist: ["motor_ok", "casco_ok", "gasolina_ok"]';
COMMENT ON COLUMN locacao.checklist_entrada_json IS 'Check-out checklist: ["motor_ok", "casco_ok", "limpeza_ok"]';

-- =====================================================
-- Table: foto
-- Images (check-in/out/incidents) stored in cloud
-- =====================================================
CREATE TABLE foto (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- References
    locacao_id UUID NOT NULL REFERENCES locacao(id) ON DELETE CASCADE,

    -- Image metadata
    tipo TEXT NOT NULL CHECK (tipo IN ('checkin', 'checkout', 'incidente')),
    ordem INT NOT NULL CHECK (ordem > 0),
    descricao TEXT,

    -- Storage
    s3_bucket TEXT NOT NULL,
    s3_key TEXT NOT NULL,
    s3_url TEXT NOT NULL,

    -- Integrity & EXIF
    hash_sha256 TEXT NOT NULL,
    tamanho_bytes INT NOT NULL CHECK (tamanho_bytes > 0),
    formato TEXT NOT NULL,
    exif_json JSONB,

    -- Capture metadata
    dt_captura TIMESTAMP NOT NULL,
    gps_lat NUMERIC(10,7),
    gps_lon NUMERIC(10,7),
    dispositivo TEXT,

    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Unique constraint per rental
    CONSTRAINT foto_locacao_tipo_ordem_unique UNIQUE (locacao_id, tipo, ordem)
);

COMMENT ON TABLE foto IS 'Images (check-in/out/incidents) stored in cloud (tenant-scoped)';
COMMENT ON COLUMN foto.tipo IS 'checkin, checkout, incidente';
COMMENT ON COLUMN foto.ordem IS 'Sequence order (1, 2, 3, 4 for mandatory check-in photos)';
COMMENT ON COLUMN foto.hash_sha256 IS 'SHA-256 hash for integrity verification';
COMMENT ON COLUMN foto.exif_json IS 'EXIF metadata: {camera, timestamp, gps, device}';

-- =====================================================
-- Table: abastecimento
-- Fuel logs per rental or jetski
-- =====================================================
CREATE TABLE abastecimento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- References (locacao_id OR jetski_id must be set)
    locacao_id UUID REFERENCES locacao(id) ON DELETE CASCADE,
    jetski_id UUID NOT NULL REFERENCES jetski(id),

    -- Fuel data
    litros NUMERIC(8,3) NOT NULL CHECK (litros > 0),
    preco_litro NUMERIC(8,3) NOT NULL CHECK (preco_litro > 0),
    valor_total NUMERIC(10,2) NOT NULL CHECK (valor_total > 0),

    -- Timing
    tipo TEXT NOT NULL CHECK (tipo IN ('pre_locacao', 'pos_locacao', 'manutencao', 'operacional')),
    dt_abastecimento TIMESTAMP NOT NULL,

    -- Location & receipt
    posto TEXT,
    nf_numero TEXT,
    nf_url TEXT,

    -- Metadata
    observacoes TEXT,
    operador_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE abastecimento IS 'Fuel logs per rental or jetski (tenant-scoped)';
COMMENT ON COLUMN abastecimento.tipo IS 'pre_locacao, pos_locacao, manutencao, operacional';

-- =====================================================
-- Table: os_manutencao
-- Maintenance orders (preventive/corrective)
-- =====================================================
CREATE TABLE os_manutencao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- References
    jetski_id UUID NOT NULL REFERENCES jetski(id),
    mecanico_id UUID REFERENCES usuario(id),

    -- Type & priority
    tipo TEXT NOT NULL CHECK (tipo IN ('preventiva', 'corretiva', 'revisao')),
    prioridade TEXT NOT NULL CHECK (prioridade IN ('baixa', 'media', 'alta', 'urgente')) DEFAULT 'media',

    -- Schedule
    dt_abertura TIMESTAMP NOT NULL DEFAULT NOW(),
    dt_prevista_inicio TIMESTAMP,
    dt_inicio_real TIMESTAMP,
    dt_prevista_fim TIMESTAMP,
    dt_conclusao TIMESTAMP,

    -- Details
    descricao_problema TEXT NOT NULL,
    diagnostico TEXT,
    solucao TEXT,

    -- Parts & cost
    pecas_json JSONB,
    valor_pecas NUMERIC(10,2) DEFAULT 0 CHECK (valor_pecas >= 0),
    valor_mao_obra NUMERIC(10,2) DEFAULT 0 CHECK (valor_mao_obra >= 0),
    valor_total NUMERIC(10,2) DEFAULT 0 CHECK (valor_total >= 0),

    -- Odometer
    horimetro_abertura NUMERIC(10,2) CHECK (horimetro_abertura >= 0),
    horimetro_conclusao NUMERIC(10,2) CHECK (horimetro_conclusao >= horimetro_abertura),

    -- Status workflow
    status TEXT NOT NULL CHECK (status IN ('aberta', 'em_andamento', 'aguardando_pecas', 'concluida', 'cancelada')) DEFAULT 'aberta',

    -- Metadata
    observacoes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE os_manutencao IS 'Maintenance orders: preventive/corrective (tenant-scoped)';
COMMENT ON COLUMN os_manutencao.tipo IS 'preventiva, corretiva, revisao';
COMMENT ON COLUMN os_manutencao.prioridade IS 'baixa, media, alta, urgente';
COMMENT ON COLUMN os_manutencao.status IS 'aberta, em_andamento, aguardando_pecas, concluida, cancelada';
COMMENT ON COLUMN os_manutencao.pecas_json IS 'Parts used: [{"nome": "Vela", "qtd": 2, "valor": 45.00}]';

-- =====================================================
-- Table: commission_policy
-- Hierarchical commission rules
-- =====================================================
CREATE TABLE commission_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Hierarchy (first match wins)
    tipo_regra TEXT NOT NULL CHECK (tipo_regra IN ('campanha', 'modelo', 'duracao', 'vendedor_padrao')),
    prioridade INT NOT NULL DEFAULT 100 CHECK (prioridade > 0),

    -- Scope
    campanha_nome TEXT,
    campanha_dt_inicio DATE,
    campanha_dt_fim DATE,
    modelo_id UUID REFERENCES modelo(id),
    duracao_min_min INT,
    duracao_max_min INT,
    vendedor_id UUID REFERENCES vendedor(id),

    -- Commission config
    tipo_comissao TEXT NOT NULL CHECK (tipo_comissao IN ('percentual', 'fixo', 'escalonado')),
    valor_percentual NUMERIC(5,2) CHECK (valor_percentual >= 0 AND valor_percentual <= 100),
    valor_fixo NUMERIC(10,2) CHECK (valor_fixo >= 0),
    escalonamento_json JSONB,

    -- Metadata
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE commission_policy IS 'Hierarchical commission rules (tenant-scoped)';
COMMENT ON COLUMN commission_policy.tipo_regra IS 'Hierarchy: campanha > modelo > duracao > vendedor_padrao';
COMMENT ON COLUMN commission_policy.prioridade IS 'Lower number = higher priority (evaluated first)';
COMMENT ON COLUMN commission_policy.tipo_comissao IS 'percentual (%), fixo (BRL), escalonado (tiers)';
COMMENT ON COLUMN commission_policy.escalonamento_json IS 'Tiers: [{"ate_min": 120, "percentual": 10}, {"acima_min": 120, "percentual": 12}]';

-- =====================================================
-- Table: fuel_policy
-- Fuel pricing configuration
-- =====================================================
CREATE TABLE fuel_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Scope (NULL = global default)
    modelo_id UUID REFERENCES modelo(id),
    jetski_id UUID REFERENCES jetski(id),

    -- Fuel mode
    modo TEXT NOT NULL CHECK (modo IN ('incluso', 'medido', 'taxa_fixa')),

    -- Taxa fixa configuration
    taxa_fixa_hora NUMERIC(8,2) CHECK (taxa_fixa_hora >= 0),

    -- Metadata
    comissionavel BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE fuel_policy IS 'Fuel pricing configuration: included/measured/fixed rate (tenant-scoped)';
COMMENT ON COLUMN fuel_policy.modo IS 'incluso (free), medido (liters × price), taxa_fixa (fixed per hour)';
COMMENT ON COLUMN fuel_policy.comissionavel IS 'Whether fuel cost is commissionable (default: false)';

-- =====================================================
-- Table: fuel_price_day
-- Daily average fuel prices
-- =====================================================
CREATE TABLE fuel_price_day (
    id SERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Date & price
    dt_referencia DATE NOT NULL,
    preco_litro NUMERIC(8,3) NOT NULL CHECK (preco_litro > 0),

    -- Metadata
    fonte TEXT,
    observacoes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Unique constraint
    CONSTRAINT fuel_price_day_unique UNIQUE (tenant_id, dt_referencia)
);

COMMENT ON TABLE fuel_price_day IS 'Daily average fuel prices for billing (tenant-scoped)';

-- =====================================================
-- Table: fechamento_diario
-- Daily financial closures
-- =====================================================
CREATE TABLE fechamento_diario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Date & operator
    dt_referencia DATE NOT NULL,
    operador_id UUID NOT NULL REFERENCES usuario(id),

    -- Consolidation
    total_locacoes INT NOT NULL DEFAULT 0,
    total_faturado NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_combustivel NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_comissoes NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_dinheiro NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_cartao NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_pix NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Status & lock
    status TEXT NOT NULL CHECK (status IN ('aberto', 'fechado', 'aprovado')) DEFAULT 'aberto',
    dt_fechamento TIMESTAMP,
    bloqueado BOOLEAN NOT NULL DEFAULT FALSE,

    -- Metadata
    observacoes TEXT,
    divergencias_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Unique constraint per tenant per day
    CONSTRAINT fechamento_diario_unique UNIQUE (tenant_id, dt_referencia)
);

COMMENT ON TABLE fechamento_diario IS 'Daily financial closures with locking (tenant-scoped)';
COMMENT ON COLUMN fechamento_diario.bloqueado IS 'When true, prevents retroactive edits to rentals on this date';
COMMENT ON COLUMN fechamento_diario.divergencias_json IS 'Discrepancies found: [{"tipo": "falta_caixa", "valor": 50.00}]';

-- =====================================================
-- Table: fechamento_mensal
-- Monthly financial closures
-- =====================================================
CREATE TABLE fechamento_mensal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Month & operator
    ano INT NOT NULL CHECK (ano >= 2020),
    mes INT NOT NULL CHECK (mes >= 1 AND mes <= 12),
    operador_id UUID NOT NULL REFERENCES usuario(id),

    -- Consolidation
    total_locacoes INT NOT NULL DEFAULT 0,
    total_faturado NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_custos NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_comissoes NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_manutencoes NUMERIC(12,2) NOT NULL DEFAULT 0,
    resultado_liquido NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Status & lock
    status TEXT NOT NULL CHECK (status IN ('aberto', 'fechado', 'aprovado')) DEFAULT 'aberto',
    dt_fechamento TIMESTAMP,
    bloqueado BOOLEAN NOT NULL DEFAULT FALSE,

    -- Metadata
    observacoes TEXT,
    relatorio_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Unique constraint per tenant per month
    CONSTRAINT fechamento_mensal_unique UNIQUE (tenant_id, ano, mes)
);

COMMENT ON TABLE fechamento_mensal IS 'Monthly financial closures with reports (tenant-scoped)';
COMMENT ON COLUMN fechamento_mensal.bloqueado IS 'When true, prevents retroactive edits for this month';
COMMENT ON COLUMN fechamento_mensal.resultado_liquido IS 'Net result: revenue - costs - commissions - maintenance';

-- =====================================================
-- Table: auditoria
-- Audit trail (who did what, when)
-- =====================================================
CREATE TABLE auditoria (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Actor
    usuario_id UUID REFERENCES usuario(id),
    usuario_email TEXT,

    -- Action
    entidade TEXT NOT NULL,
    entidade_id TEXT NOT NULL,
    acao TEXT NOT NULL CHECK (acao IN ('CREATE', 'UPDATE', 'DELETE', 'READ')),

    -- Changes (for UPDATE)
    campos_alterados TEXT[],
    valores_antes JSONB,
    valores_depois JSONB,

    -- Context
    ip_address INET,
    user_agent TEXT,
    dispositivo TEXT,
    trace_id TEXT,

    -- Timestamp
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE auditoria IS 'Audit trail: who did what, when (tenant-scoped)';
COMMENT ON COLUMN auditoria.acao IS 'CREATE, UPDATE, DELETE, READ (for sensitive data)';
COMMENT ON COLUMN auditoria.trace_id IS 'Distributed tracing ID for correlation';

-- =====================================================
-- Indexes for support tables
-- =====================================================
-- Reserva
CREATE INDEX idx_reserva_tenant ON reserva(tenant_id);
CREATE INDEX idx_reserva_cliente ON reserva(cliente_id);
CREATE INDEX idx_reserva_jetski ON reserva(jetski_id);
CREATE INDEX idx_reserva_vendedor ON reserva(vendedor_id);
CREATE INDEX idx_reserva_status ON reserva(status);
CREATE INDEX idx_reserva_dt_inicio ON reserva(dt_inicio_prevista);

-- Locacao
CREATE INDEX idx_locacao_tenant ON locacao(tenant_id);
CREATE INDEX idx_locacao_reserva ON locacao(reserva_id);
CREATE INDEX idx_locacao_cliente ON locacao(cliente_id);
CREATE INDEX idx_locacao_jetski ON locacao(jetski_id);
CREATE INDEX idx_locacao_vendedor ON locacao(vendedor_id);
CREATE INDEX idx_locacao_status ON locacao(status);
CREATE INDEX idx_locacao_dt_checkin ON locacao(dt_checkin);
CREATE INDEX idx_locacao_dt_checkout ON locacao(dt_checkout);

-- Foto
CREATE INDEX idx_foto_tenant ON foto(tenant_id);
CREATE INDEX idx_foto_locacao ON foto(locacao_id);
CREATE INDEX idx_foto_tipo ON foto(tipo);
CREATE INDEX idx_foto_hash ON foto(hash_sha256);

-- Abastecimento
CREATE INDEX idx_abastecimento_tenant ON abastecimento(tenant_id);
CREATE INDEX idx_abastecimento_locacao ON abastecimento(locacao_id);
CREATE INDEX idx_abastecimento_jetski ON abastecimento(jetski_id);
CREATE INDEX idx_abastecimento_dt ON abastecimento(dt_abastecimento);

-- OS Manutencao
CREATE INDEX idx_os_manutencao_tenant ON os_manutencao(tenant_id);
CREATE INDEX idx_os_manutencao_jetski ON os_manutencao(jetski_id);
CREATE INDEX idx_os_manutencao_mecanico ON os_manutencao(mecanico_id);
CREATE INDEX idx_os_manutencao_status ON os_manutencao(status);
CREATE INDEX idx_os_manutencao_tipo ON os_manutencao(tipo);

-- Commission Policy
CREATE INDEX idx_commission_policy_tenant ON commission_policy(tenant_id);
CREATE INDEX idx_commission_policy_modelo ON commission_policy(modelo_id);
CREATE INDEX idx_commission_policy_vendedor ON commission_policy(vendedor_id);
CREATE INDEX idx_commission_policy_prioridade ON commission_policy(prioridade);

-- Fuel Policy
CREATE INDEX idx_fuel_policy_tenant ON fuel_policy(tenant_id);
CREATE INDEX idx_fuel_policy_modelo ON fuel_policy(modelo_id);
CREATE INDEX idx_fuel_policy_jetski ON fuel_policy(jetski_id);

-- Fuel Price Day
CREATE INDEX idx_fuel_price_day_tenant ON fuel_price_day(tenant_id);
CREATE INDEX idx_fuel_price_day_dt ON fuel_price_day(dt_referencia);

-- Fechamento Diario
CREATE INDEX idx_fechamento_diario_tenant ON fechamento_diario(tenant_id);
CREATE INDEX idx_fechamento_diario_dt ON fechamento_diario(dt_referencia);
CREATE INDEX idx_fechamento_diario_status ON fechamento_diario(status);

-- Fechamento Mensal
CREATE INDEX idx_fechamento_mensal_tenant ON fechamento_mensal(tenant_id);
CREATE INDEX idx_fechamento_mensal_ano_mes ON fechamento_mensal(ano, mes);
CREATE INDEX idx_fechamento_mensal_status ON fechamento_mensal(status);

-- Auditoria
CREATE INDEX idx_auditoria_tenant ON auditoria(tenant_id);
CREATE INDEX idx_auditoria_usuario ON auditoria(usuario_id);
CREATE INDEX idx_auditoria_entidade ON auditoria(entidade, entidade_id);
CREATE INDEX idx_auditoria_acao ON auditoria(acao);
CREATE INDEX idx_auditoria_created_at ON auditoria(created_at);
CREATE INDEX idx_auditoria_trace_id ON auditoria(trace_id);

-- =====================================================
-- Updated_at triggers
-- =====================================================
CREATE TRIGGER update_reserva_updated_at BEFORE UPDATE ON reserva
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_locacao_updated_at BEFORE UPDATE ON locacao
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_abastecimento_updated_at BEFORE UPDATE ON abastecimento
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_os_manutencao_updated_at BEFORE UPDATE ON os_manutencao
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_commission_policy_updated_at BEFORE UPDATE ON commission_policy
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fuel_policy_updated_at BEFORE UPDATE ON fuel_policy
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fechamento_diario_updated_at BEFORE UPDATE ON fechamento_diario
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fechamento_mensal_updated_at BEFORE UPDATE ON fechamento_mensal
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
