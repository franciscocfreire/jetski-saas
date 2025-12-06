-- ============================================================================
-- V001: Schema Consolidado - Jetski SaaS Multi-tenant
-- ============================================================================
-- Este arquivo contém todo o schema do banco de dados consolidado.
-- Gerado automaticamente a partir das migrations anteriores.
-- ============================================================================

-- ============================================================================
-- EXTENSÕES
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- FUNÇÃO DE ATUALIZAÇÃO AUTOMÁTICA DE updated_at
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TABELAS MULTI-TENANT (Planos, Tenants, Usuários)
-- ============================================================================

-- Planos de assinatura
CREATE TABLE plano (
    id SERIAL PRIMARY KEY,
    nome VARCHAR(50) NOT NULL UNIQUE,
    limites JSONB DEFAULT '{}',
    preco_mensal NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT nome_unique UNIQUE (nome)
);

-- Tenants (empresas/clientes)
CREATE TABLE tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(50) NOT NULL UNIQUE,
    razao_social VARCHAR(200) NOT NULL,
    cnpj VARCHAR(18),
    timezone VARCHAR(50) DEFAULT 'America/Sao_Paulo',
    moeda VARCHAR(3) DEFAULT 'BRL',
    status VARCHAR(20) DEFAULT 'ATIVO',
    branding JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Assinaturas de tenants
CREATE TABLE assinatura (
    id SERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    plano_id INTEGER NOT NULL REFERENCES plano(id),
    ciclo VARCHAR(20) DEFAULT 'mensal',
    dt_inicio DATE NOT NULL,
    dt_fim DATE,
    status VARCHAR(20) DEFAULT 'ativa',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Usuários (independente de tenant)
CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    nome VARCHAR(200) NOT NULL,
    ativo BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Membros (relação usuário-tenant com papéis)
CREATE TABLE membro (
    id SERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    papeis TEXT[] NOT NULL DEFAULT '{}',
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, usuario_id)
);

-- Mapeamento de identity providers (Keycloak)
CREATE TABLE usuario_identity_provider (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_user_id)
);

-- Roles globais da plataforma
CREATE TABLE global_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Global platform roles for super admins (PLATFORM_ADMIN, SUPPORT, etc.)
CREATE TABLE usuario_global_roles (
    usuario_id UUID PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    roles TEXT[] NOT NULL,
    unrestricted_access BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT roles_not_empty CHECK (array_length(roles, 1) > 0)
);

COMMENT ON TABLE usuario_global_roles IS 'Global platform roles for super admins (PLATFORM_ADMIN, SUPPORT, etc.)';
COMMENT ON COLUMN usuario_global_roles.roles IS 'Global roles: PLATFORM_ADMIN, SUPPORT, AUDITOR';
COMMENT ON COLUMN usuario_global_roles.unrestricted_access IS 'If true, user can access ANY tenant without explicit membership';

-- Index for unrestricted users (small set, highly selective)
CREATE INDEX idx_global_roles_unrestricted ON usuario_global_roles(unrestricted_access) WHERE unrestricted_access = TRUE;

-- Trigger for updated_at
CREATE TRIGGER update_usuario_global_roles_updated_at
    BEFORE UPDATE ON usuario_global_roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Acesso de usuário a tenants
CREATE TABLE tenant_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    roles TEXT[] DEFAULT '{}',
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(usuario_id, tenant_id)
);

-- Convites para tenants
CREATE TABLE convite (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    nome VARCHAR(200),
    papeis TEXT[] DEFAULT '{}',
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    usuario_id UUID REFERENCES usuario(id),
    created_by UUID REFERENCES usuario(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    email_sent_at TIMESTAMPTZ,
    email_sent_count INTEGER NOT NULL DEFAULT 0,
    email_error TEXT,
    password_reset_link TEXT,
    temporary_password_hash VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVATED', 'EXPIRED', 'CANCELLED')),
    UNIQUE(tenant_id, email)
);

-- ============================================================================
-- TABELAS OPERACIONAIS (tenant-scoped)
-- ============================================================================

-- Modelos de jetski
CREATE TABLE modelo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL,
    fabricante VARCHAR(100),
    potencia_hp INTEGER,
    capacidade_pessoas INTEGER DEFAULT 2,
    preco_base_hora NUMERIC(10,2) NOT NULL DEFAULT 0,
    tolerancia_min INTEGER DEFAULT 5,
    taxa_hora_extra NUMERIC(10,2) DEFAULT 0,
    inclui_combustivel BOOLEAN DEFAULT false,
    caucao NUMERIC(10,2) DEFAULT 0,
    foto_referencia_url TEXT,
    pacotes_json JSONB,
    ativo BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, nome)
);

-- Jetskis
CREATE TABLE jetski (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    modelo_id UUID NOT NULL REFERENCES modelo(id),
    serie VARCHAR(50) NOT NULL,
    ano INTEGER,
    horimetro_atual NUMERIC(10,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DISPONIVEL',
    ativo BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, serie),
    CONSTRAINT jetski_status_check CHECK (status IN ('DISPONIVEL', 'LOCADO', 'MANUTENCAO', 'INDISPONIVEL'))
);

-- Clientes
CREATE TABLE cliente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    nome VARCHAR(200) NOT NULL,
    documento VARCHAR(20),
    data_nascimento DATE,
    genero VARCHAR(20),
    email VARCHAR(255),
    telefone VARCHAR(30),
    whatsapp VARCHAR(30),
    endereco JSONB,
    termo_aceite BOOLEAN DEFAULT false,
    ativo BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Vendedores
CREATE TABLE vendedor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    nome VARCHAR(200) NOT NULL,
    documento VARCHAR(20),
    tipo VARCHAR(20) NOT NULL DEFAULT 'INTERNO',
    regra_comissao_json JSONB,
    ativo BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT vendedor_tipo_check CHECK (tipo IN ('INTERNO', 'PARCEIRO'))
);

-- Reservas
CREATE TABLE reserva (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    modelo_id UUID NOT NULL REFERENCES modelo(id),
    jetski_id UUID REFERENCES jetski(id),
    cliente_id UUID NOT NULL REFERENCES cliente(id),
    vendedor_id UUID REFERENCES vendedor(id),
    locacao_id UUID,
    data_inicio TIMESTAMPTZ NOT NULL,
    data_fim_prevista TIMESTAMPTZ NOT NULL,
    prioridade VARCHAR(20) NOT NULL DEFAULT 'BAIXA',
    sinal_pago BOOLEAN NOT NULL DEFAULT FALSE,
    valor_sinal DECIMAL(10,2),
    sinal_pago_em TIMESTAMPTZ,
    expira_em TIMESTAMPTZ,
    status VARCHAR(20) DEFAULT 'PENDENTE',
    observacoes TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT reserva_status_check CHECK (status IN ('PENDENTE', 'CONFIRMADA', 'CANCELADA', 'FINALIZADA', 'EXPIRADA')),
    CONSTRAINT reserva_prioridade_check CHECK (prioridade IN ('ALTA', 'BAIXA')),
    CONSTRAINT reserva_sinal_consistency CHECK (
        (sinal_pago = FALSE) OR
        (sinal_pago = TRUE AND valor_sinal IS NOT NULL AND sinal_pago_em IS NOT NULL)
    ),
    CONSTRAINT reserva_prioridade_sinal CHECK (
        (sinal_pago = FALSE) OR
        (sinal_pago = TRUE AND prioridade = 'ALTA')
    )
);

-- Configuração de reservas por tenant
CREATE TABLE reserva_config (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id) ON DELETE CASCADE,
    grace_period_minutos INT NOT NULL DEFAULT 30,
    percentual_sinal DECIMAL(5,2) NOT NULL DEFAULT 30.00,
    fator_overbooking DECIMAL(5,2) NOT NULL DEFAULT 1.0,
    max_reservas_sem_sinal_por_modelo INT NOT NULL DEFAULT 8,
    notificar_antes_expiracao BOOLEAN NOT NULL DEFAULT TRUE,
    notificar_minutos_antecedencia INT NOT NULL DEFAULT 15,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT grace_period_positive CHECK (grace_period_minutos > 0),
    CONSTRAINT percentual_sinal_valid CHECK (percentual_sinal >= 0 AND percentual_sinal <= 100),
    CONSTRAINT fator_overbooking_valid CHECK (fator_overbooking >= 1.0 AND fator_overbooking <= 10.0),
    CONSTRAINT max_reservas_positive CHECK (max_reservas_sem_sinal_por_modelo > 0)
);

COMMENT ON TABLE reserva_config IS 'Per-tenant configuration for reservation/booking policies';

-- Políticas de combustível
CREATE TABLE fuel_policy (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    aplicavel_a VARCHAR(20) NOT NULL,
    referencia_id UUID,
    valor_taxa_por_hora NUMERIC(10,2),
    comissionavel BOOLEAN NOT NULL DEFAULT false,
    ativo BOOLEAN NOT NULL DEFAULT true,
    prioridade INTEGER NOT NULL DEFAULT 0,
    descricao VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fuel_policy_tipo_check CHECK (tipo IN ('INCLUSO', 'MEDIDO', 'TAXA_FIXA')),
    CONSTRAINT fuel_policy_aplicavel_check CHECK (aplicavel_a IN ('GLOBAL', 'MODELO', 'JETSKI'))
);

CREATE INDEX idx_fuel_policy_tenant ON fuel_policy(tenant_id);
CREATE INDEX idx_fuel_policy_aplicacao ON fuel_policy(tenant_id, aplicavel_a, referencia_id);
CREATE INDEX idx_fuel_policy_ativo ON fuel_policy(tenant_id, ativo);

-- Preço do combustível por dia
CREATE TABLE fuel_price_day (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    data DATE NOT NULL,
    preco_litro NUMERIC(10,3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, data)
);

-- Locações (operação principal)
CREATE TABLE locacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    reserva_id UUID REFERENCES reserva(id),
    jetski_id UUID NOT NULL REFERENCES jetski(id),
    cliente_id UUID REFERENCES cliente(id),
    vendedor_id UUID REFERENCES vendedor(id),
    fuel_policy_id BIGINT REFERENCES fuel_policy(id),
    data_check_in TIMESTAMPTZ NOT NULL,
    data_check_out TIMESTAMPTZ,
    horimetro_inicio NUMERIC(10,2) NOT NULL,
    horimetro_fim NUMERIC(10,2),
    duracao_prevista INTEGER NOT NULL,
    minutos_usados INTEGER,
    minutos_faturaveis INTEGER,
    valor_base NUMERIC(10,2),
    valor_negociado NUMERIC(10,2),
    valor_total NUMERIC(10,2),
    combustivel_custo NUMERIC(10,2) DEFAULT 0,
    modalidade_preco VARCHAR(20) NOT NULL DEFAULT 'PRECO_FECHADO',
    motivo_desconto TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'EM_CURSO',
    observacoes TEXT,
    checklist_saida_json JSONB,
    checklist_entrada_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT locacao_status_check CHECK (status IN ('EM_CURSO', 'FINALIZADA', 'CANCELADA')),
    CONSTRAINT chk_locacao_modalidade_preco CHECK (modalidade_preco IN ('PRECO_FECHADO', 'DIARIA', 'MEIA_DIARIA')),
    CONSTRAINT check_duracao_prevista CHECK (duracao_prevista > 0),
    CONSTRAINT check_horimetro_fim CHECK (horimetro_fim IS NULL OR horimetro_fim >= horimetro_inicio)
);

-- Fotos (check-in/check-out)
CREATE TABLE foto (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    locacao_id UUID REFERENCES locacao(id) ON DELETE CASCADE,
    tipo VARCHAR(20) NOT NULL,
    url TEXT NOT NULL,
    hash_sha256 VARCHAR(64),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT foto_tipo_check CHECK (tipo IN ('CHECK_IN', 'CHECK_OUT', 'INCIDENTE', 'ABASTECIMENTO'))
);

-- Abastecimentos
CREATE TABLE abastecimento (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    jetski_id UUID NOT NULL REFERENCES jetski(id),
    locacao_id UUID REFERENCES locacao(id),
    responsavel_id UUID REFERENCES usuario(id),
    data_hora TIMESTAMPTZ NOT NULL,
    litros NUMERIC(10,3) NOT NULL,
    preco_litro NUMERIC(10,2) NOT NULL,
    custo_total NUMERIC(10,2) NOT NULL,
    tipo VARCHAR(20),
    foto_id BIGINT REFERENCES foto(id),
    observacoes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT abastecimento_tipo_check CHECK (tipo IN ('PRE_LOCACAO', 'POS_LOCACAO', 'FROTA')),
    CONSTRAINT abastecimento_litros_check CHECK (litros > 0),
    CONSTRAINT abastecimento_preco_litro_check CHECK (preco_litro > 0),
    CONSTRAINT abastecimento_custo_total_check CHECK (custo_total >= 0)
);

-- Itens opcionais
CREATE TABLE item_opcional (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(500),
    preco_base NUMERIC(10,2) NOT NULL DEFAULT 0,
    ativo BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, nome)
);

-- Itens opcionais por locação
CREATE TABLE locacao_item_opcional (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    locacao_id UUID NOT NULL REFERENCES locacao(id) ON DELETE CASCADE,
    item_opcional_id UUID NOT NULL REFERENCES item_opcional(id),
    valor_cobrado NUMERIC(10,2) NOT NULL,
    valor_original NUMERIC(10,2) NOT NULL,
    observacao VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(locacao_id, item_opcional_id)
);

-- Políticas de comissão
CREATE TABLE politica_comissao (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    valor NUMERIC(10,2) NOT NULL,
    vendedor_id UUID REFERENCES vendedor(id),
    modelo_id UUID REFERENCES modelo(id),
    duracao_min INTEGER,
    duracao_max INTEGER,
    campanha_id UUID,
    prioridade INTEGER DEFAULT 0,
    ativo BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT politica_comissao_tipo_check CHECK (tipo IN ('PERCENTUAL', 'FIXO', 'ESCALONADO'))
);

-- Comissões calculadas
CREATE TABLE comissao (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    locacao_id UUID NOT NULL REFERENCES locacao(id),
    vendedor_id UUID NOT NULL REFERENCES vendedor(id),
    politica_id BIGINT REFERENCES politica_comissao(id),
    valor_locacao NUMERIC(10,2) NOT NULL,
    valor_comissionavel NUMERIC(10,2) NOT NULL,
    percentual NUMERIC(5,2),
    valor_comissao NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDENTE',
    pago_em TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT comissao_status_check CHECK (status IN ('PENDENTE', 'APROVADA', 'PAGA', 'CANCELADA'))
);

-- Ordens de manutenção
CREATE TABLE os_manutencao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    jetski_id UUID NOT NULL REFERENCES jetski(id),
    tipo VARCHAR(20) NOT NULL,
    descricao TEXT,
    status VARCHAR(20) DEFAULT 'ABERTA',
    prioridade VARCHAR(10) DEFAULT 'MEDIA',
    custo_estimado NUMERIC(10,2),
    custo_real NUMERIC(10,2),
    aberta_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fechada_em TIMESTAMPTZ,
    responsavel_id UUID REFERENCES usuario(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT os_tipo_check CHECK (tipo IN ('PREVENTIVA', 'CORRETIVA', 'REVISAO')),
    CONSTRAINT os_status_check CHECK (status IN ('ABERTA', 'EM_ANDAMENTO', 'AGUARDANDO_PECAS', 'CONCLUIDA', 'CANCELADA'))
);

-- Fechamentos diários
CREATE TABLE fechamento_diario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    data DATE NOT NULL,
    total_locacoes INTEGER DEFAULT 0,
    total_receita NUMERIC(12,2) DEFAULT 0,
    total_combustivel NUMERIC(12,2) DEFAULT 0,
    total_comissoes NUMERIC(12,2) DEFAULT 0,
    observacoes TEXT,
    fechado_por UUID REFERENCES usuario(id),
    fechado_em TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, data)
);

-- Fechamentos mensais
CREATE TABLE fechamento_mensal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    ano INTEGER NOT NULL,
    mes INTEGER NOT NULL,
    total_locacoes INTEGER DEFAULT 0,
    total_receita NUMERIC(12,2) DEFAULT 0,
    total_combustivel NUMERIC(12,2) DEFAULT 0,
    total_comissoes NUMERIC(12,2) DEFAULT 0,
    total_manutencao NUMERIC(12,2) DEFAULT 0,
    observacoes TEXT,
    fechado_por UUID REFERENCES usuario(id),
    fechado_em TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, ano, mes)
);

-- Auditoria
CREATE TABLE auditoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenant(id) ON DELETE SET NULL,
    usuario_id UUID REFERENCES usuario(id) ON DELETE SET NULL,
    acao VARCHAR(50) NOT NULL,
    entidade VARCHAR(50) NOT NULL,
    entidade_id UUID,
    dados_anteriores JSONB,
    dados_novos JSONB,
    ip VARCHAR(45),
    user_agent TEXT,
    trace_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- ÍNDICES COMPOSTOS (tenant_id + FK para performance com RLS)
-- ============================================================================

CREATE INDEX idx_membro_tenant ON membro(tenant_id);
CREATE INDEX idx_membro_usuario ON membro(usuario_id);
CREATE INDEX idx_assinatura_tenant ON assinatura(tenant_id);
CREATE INDEX idx_modelo_tenant ON modelo(tenant_id);
CREATE INDEX idx_jetski_tenant ON jetski(tenant_id);
CREATE INDEX idx_jetski_tenant_modelo ON jetski(tenant_id, modelo_id);
CREATE INDEX idx_jetski_tenant_status ON jetski(tenant_id, status);
CREATE INDEX idx_cliente_tenant ON cliente(tenant_id);
CREATE INDEX idx_vendedor_tenant ON vendedor(tenant_id);
CREATE INDEX idx_reserva_tenant ON reserva(tenant_id);
CREATE INDEX idx_reserva_tenant_modelo ON reserva(tenant_id, modelo_id);
CREATE INDEX idx_reserva_tenant_cliente ON reserva(tenant_id, cliente_id);
CREATE INDEX idx_reserva_tenant_status ON reserva(tenant_id, status);
CREATE INDEX idx_reserva_tenant_data ON reserva(tenant_id, data_inicio, data_fim_prevista);
CREATE INDEX idx_locacao_tenant ON locacao(tenant_id);
CREATE INDEX idx_locacao_tenant_jetski ON locacao(tenant_id, jetski_id);
CREATE INDEX idx_locacao_tenant_cliente ON locacao(tenant_id, cliente_id);
CREATE INDEX idx_locacao_tenant_status ON locacao(tenant_id, status);
CREATE INDEX idx_locacao_tenant_reserva ON locacao(tenant_id, reserva_id) WHERE reserva_id IS NOT NULL;
CREATE INDEX idx_locacao_data_check_in ON locacao(tenant_id, data_check_in DESC);
CREATE INDEX idx_locacao_fuel_policy ON locacao(tenant_id, fuel_policy_id);
CREATE INDEX idx_foto_tenant ON foto(tenant_id);
CREATE INDEX idx_foto_locacao ON foto(locacao_id);
CREATE INDEX idx_abastecimento_tenant ON abastecimento(tenant_id);
CREATE INDEX idx_abastecimento_locacao ON abastecimento(locacao_id);
CREATE INDEX idx_fuel_policy_tenant ON fuel_policy(tenant_id);
CREATE INDEX idx_fuel_price_day_tenant ON fuel_price_day(tenant_id);
CREATE INDEX idx_comissao_tenant ON comissao(tenant_id);
CREATE INDEX idx_comissao_locacao ON comissao(locacao_id);
CREATE INDEX idx_comissao_vendedor ON comissao(tenant_id, vendedor_id);
CREATE INDEX idx_politica_comissao_tenant ON politica_comissao(tenant_id);
CREATE INDEX idx_os_manutencao_tenant ON os_manutencao(tenant_id);
CREATE INDEX idx_os_manutencao_jetski ON os_manutencao(tenant_id, jetski_id);
CREATE INDEX idx_fechamento_diario_tenant ON fechamento_diario(tenant_id);
CREATE INDEX idx_fechamento_mensal_tenant ON fechamento_mensal(tenant_id);
CREATE INDEX idx_auditoria_tenant ON auditoria(tenant_id);
CREATE INDEX idx_auditoria_usuario ON auditoria(usuario_id);
CREATE INDEX idx_auditoria_entidade ON auditoria(entidade, entidade_id);
CREATE INDEX idx_tenant_access_usuario ON tenant_access(usuario_id);
CREATE INDEX idx_tenant_access_tenant ON tenant_access(tenant_id);
CREATE INDEX idx_convite_tenant ON convite(tenant_id);
CREATE INDEX idx_convite_token ON convite(token);
CREATE INDEX idx_item_opcional_tenant ON item_opcional(tenant_id);
CREATE INDEX idx_locacao_item_opcional_locacao ON locacao_item_opcional(locacao_id);

-- ============================================================================
-- TRIGGERS DE UPDATED_AT
-- ============================================================================

CREATE TRIGGER update_plano_updated_at BEFORE UPDATE ON plano FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_tenant_updated_at BEFORE UPDATE ON tenant FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_assinatura_updated_at BEFORE UPDATE ON assinatura FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_usuario_updated_at BEFORE UPDATE ON usuario FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_membro_updated_at BEFORE UPDATE ON membro FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_modelo_updated_at BEFORE UPDATE ON modelo FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_jetski_updated_at BEFORE UPDATE ON jetski FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_cliente_updated_at BEFORE UPDATE ON cliente FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_vendedor_updated_at BEFORE UPDATE ON vendedor FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_reserva_updated_at BEFORE UPDATE ON reserva FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_locacao_updated_at BEFORE UPDATE ON locacao FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_fuel_policy_updated_at BEFORE UPDATE ON fuel_policy FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_abastecimento_updated_at BEFORE UPDATE ON abastecimento FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_item_opcional_updated_at BEFORE UPDATE ON item_opcional FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_politica_comissao_updated_at BEFORE UPDATE ON politica_comissao FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_comissao_updated_at BEFORE UPDATE ON comissao FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_os_manutencao_updated_at BEFORE UPDATE ON os_manutencao FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_fechamento_diario_updated_at BEFORE UPDATE ON fechamento_diario FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_fechamento_mensal_updated_at BEFORE UPDATE ON fechamento_mensal FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_tenant_access_updated_at BEFORE UPDATE ON tenant_access FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_convite_updated_at BEFORE UPDATE ON convite FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_usuario_identity_provider_updated_at BEFORE UPDATE ON usuario_identity_provider FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_global_role_updated_at BEFORE UPDATE ON global_role FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- ROW LEVEL SECURITY (RLS)
-- ============================================================================

-- Habilitar RLS em todas as tabelas tenant-scoped
ALTER TABLE modelo ENABLE ROW LEVEL SECURITY;
ALTER TABLE jetski ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendedor ENABLE ROW LEVEL SECURITY;
ALTER TABLE reserva ENABLE ROW LEVEL SECURITY;
ALTER TABLE locacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE foto ENABLE ROW LEVEL SECURITY;
ALTER TABLE abastecimento ENABLE ROW LEVEL SECURITY;
ALTER TABLE fuel_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE fuel_price_day ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_opcional ENABLE ROW LEVEL SECURITY;
ALTER TABLE locacao_item_opcional ENABLE ROW LEVEL SECURITY;
ALTER TABLE politica_comissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE comissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE os_manutencao ENABLE ROW LEVEL SECURITY;
ALTER TABLE fechamento_diario ENABLE ROW LEVEL SECURITY;
ALTER TABLE fechamento_mensal ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria ENABLE ROW LEVEL SECURITY;
-- membro and tenant_access do NOT have RLS - they are used for access validation before tenant context is set
ALTER TABLE assinatura ENABLE ROW LEVEL SECURITY;
ALTER TABLE convite ENABLE ROW LEVEL SECURITY;

-- Forçar RLS mesmo para owners
ALTER TABLE modelo FORCE ROW LEVEL SECURITY;
ALTER TABLE jetski FORCE ROW LEVEL SECURITY;
ALTER TABLE cliente FORCE ROW LEVEL SECURITY;
ALTER TABLE vendedor FORCE ROW LEVEL SECURITY;
ALTER TABLE reserva FORCE ROW LEVEL SECURITY;
ALTER TABLE locacao FORCE ROW LEVEL SECURITY;
ALTER TABLE foto FORCE ROW LEVEL SECURITY;
ALTER TABLE abastecimento FORCE ROW LEVEL SECURITY;
ALTER TABLE fuel_policy FORCE ROW LEVEL SECURITY;
ALTER TABLE fuel_price_day FORCE ROW LEVEL SECURITY;
ALTER TABLE item_opcional FORCE ROW LEVEL SECURITY;
ALTER TABLE locacao_item_opcional FORCE ROW LEVEL SECURITY;
ALTER TABLE politica_comissao FORCE ROW LEVEL SECURITY;
ALTER TABLE comissao FORCE ROW LEVEL SECURITY;
ALTER TABLE os_manutencao FORCE ROW LEVEL SECURITY;
ALTER TABLE fechamento_diario FORCE ROW LEVEL SECURITY;
ALTER TABLE fechamento_mensal FORCE ROW LEVEL SECURITY;
ALTER TABLE auditoria FORCE ROW LEVEL SECURITY;
ALTER TABLE assinatura FORCE ROW LEVEL SECURITY;
ALTER TABLE convite FORCE ROW LEVEL SECURITY;

-- Policies RLS (isolamento por tenant)
CREATE POLICY tenant_isolation_modelo ON modelo USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_jetski ON jetski USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_cliente ON cliente USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_vendedor ON vendedor USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_reserva ON reserva USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_locacao ON locacao USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_foto ON foto USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_abastecimento ON abastecimento USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_fuel_policy ON fuel_policy USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_fuel_price_day ON fuel_price_day USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_item_opcional ON item_opcional USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_politica_comissao ON politica_comissao USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_comissao ON comissao USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_os_manutencao ON os_manutencao USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_fechamento_diario ON fechamento_diario USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_fechamento_mensal ON fechamento_mensal USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_auditoria ON auditoria USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_assinatura ON assinatura USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
CREATE POLICY tenant_isolation_convite ON convite USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Policy para locacao_item_opcional (via locacao)
CREATE POLICY tenant_isolation_locacao_item_opcional ON locacao_item_opcional 
    USING (locacao_id IN (SELECT id FROM locacao WHERE tenant_id = current_setting('app.tenant_id', true)::uuid));

