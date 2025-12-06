-- =====================================================
-- V002: Create Operational Tables (Tenant-scoped)
-- =====================================================
-- Creates main operational entities:
-- - modelo: Jetski models with pricing
-- - jetski: Individual jetski units
-- - vendedor: Sales partners
-- - cliente: Customers
--
-- ALL tables include tenant_id for multi-tenant isolation
-- =====================================================

-- =====================================================
-- Table: modelo
-- Jetski models with pricing configuration
-- =====================================================
CREATE TABLE modelo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Model details
    nome TEXT NOT NULL,
    fabricante TEXT,
    potencia_hp INT,
    capacidade_pessoas INT DEFAULT 2,

    -- Pricing
    preco_base_hora NUMERIC(10,2) NOT NULL CHECK (preco_base_hora > 0),
    tolerancia_min INT NOT NULL DEFAULT 5 CHECK (tolerancia_min >= 0),
    taxa_hora_extra NUMERIC(10,2) DEFAULT 0 CHECK (taxa_hora_extra >= 0),
    caucao NUMERIC(10,2) DEFAULT 0 CHECK (caucao >= 0),

    -- Fuel policy (will be detailed in future migrations)
    inclui_combustivel BOOLEAN NOT NULL DEFAULT FALSE,

    -- Metadata
    foto_referencia_url TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE modelo IS 'Jetski models with pricing configuration (tenant-scoped)';
COMMENT ON COLUMN modelo.tolerancia_min IS 'Grace period in minutes before charging (e.g., 5 min free)';
COMMENT ON COLUMN modelo.taxa_hora_extra IS 'Extra fee per hour beyond base price';
COMMENT ON COLUMN modelo.caucao IS 'Security deposit amount';

-- =====================================================
-- Table: jetski
-- Individual jetski units
-- =====================================================
CREATE TABLE jetski (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    modelo_id UUID NOT NULL REFERENCES modelo(id),

    -- Identification
    serie TEXT NOT NULL,
    placa TEXT,
    ano INT,

    -- Operational data
    horimetro_atual NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (horimetro_atual >= 0),
    status TEXT NOT NULL CHECK (status IN ('disponivel', 'locado', 'manutencao', 'indisponivel')) DEFAULT 'disponivel',

    -- Metadata
    observacoes TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Unique serie per tenant
    CONSTRAINT jetski_serie_unique UNIQUE (tenant_id, serie)
);

COMMENT ON TABLE jetski IS 'Individual jetski units (tenant-scoped)';
COMMENT ON COLUMN jetski.horimetro_atual IS 'Current hour meter reading';
COMMENT ON COLUMN jetski.status IS 'disponivel, locado, manutencao, indisponivel';

-- =====================================================
-- Table: vendedor
-- Sales partners with commission rules
-- =====================================================
CREATE TABLE vendedor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Personal data
    nome TEXT NOT NULL,
    documento TEXT,
    tipo TEXT CHECK (tipo IN ('interno', 'parceiro')) DEFAULT 'interno',

    -- Contact
    telefone TEXT,
    email TEXT,

    -- Commission configuration (simple for MVP, will be detailed in commission_policy table)
    regra_comissao_json JSONB,

    -- Metadata
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE vendedor IS 'Sales partners with commission rules (tenant-scoped)';
COMMENT ON COLUMN vendedor.tipo IS 'interno (employee) or parceiro (partner)';
COMMENT ON COLUMN vendedor.regra_comissao_json IS 'Default commission rule: {"percentual": 10.0, "tipo": "percentual"}';

-- =====================================================
-- Table: cliente
-- Customers (renters)
-- =====================================================
CREATE TABLE cliente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,

    -- Personal data
    nome TEXT NOT NULL,
    documento TEXT,
    data_nascimento DATE,

    -- Contact
    telefone TEXT,
    email TEXT,
    endereco JSONB,

    -- Legal
    termo_aceite BOOLEAN NOT NULL DEFAULT FALSE,
    termo_aceite_data TIMESTAMP,

    -- Metadata
    observacoes TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT email_format CHECK (email IS NULL OR email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

COMMENT ON TABLE cliente IS 'Customers who rent jetskis (tenant-scoped)';
COMMENT ON COLUMN cliente.termo_aceite IS 'Whether customer accepted liability terms';

-- =====================================================
-- Indexes for operational tables
-- =====================================================
-- Modelo
CREATE INDEX idx_modelo_tenant ON modelo(tenant_id);
CREATE INDEX idx_modelo_tenant_ativo ON modelo(tenant_id, ativo) WHERE ativo = TRUE;

-- Jetski
CREATE INDEX idx_jetski_tenant ON jetski(tenant_id);
CREATE INDEX idx_jetski_modelo ON jetski(modelo_id);
CREATE INDEX idx_jetski_tenant_status ON jetski(tenant_id, status);
CREATE INDEX idx_jetski_serie ON jetski(serie);

-- Vendedor
CREATE INDEX idx_vendedor_tenant ON vendedor(tenant_id);
CREATE INDEX idx_vendedor_tenant_ativo ON vendedor(tenant_id, ativo) WHERE ativo = TRUE;

-- Cliente
CREATE INDEX idx_cliente_tenant ON cliente(tenant_id);
CREATE INDEX idx_cliente_documento ON cliente(documento);
CREATE INDEX idx_cliente_email ON cliente(email);

-- =====================================================
-- Updated_at triggers
-- =====================================================
CREATE TRIGGER update_modelo_updated_at BEFORE UPDATE ON modelo
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_jetski_updated_at BEFORE UPDATE ON jetski
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_vendedor_updated_at BEFORE UPDATE ON vendedor
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_cliente_updated_at BEFORE UPDATE ON cliente
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
