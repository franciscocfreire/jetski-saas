-- =====================================================
-- V001: Create Multi-tenant Base Tables
-- =====================================================
-- Creates core tables for multi-tenancy:
-- - tenant: Companies using the system
-- - plano: Subscription plans
-- - assinatura: Tenant subscriptions
-- - usuario: User accounts
-- - membro: User-tenant relationships with roles
-- =====================================================

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set timezone
SET timezone = 'America/Sao_Paulo';

-- =====================================================
-- Table: tenant
-- Represents a company (client) in the SaaS system
-- =====================================================
CREATE TABLE tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug TEXT UNIQUE NOT NULL,
    razao_social TEXT NOT NULL,
    cnpj TEXT,
    timezone TEXT NOT NULL DEFAULT 'America/Sao_Paulo',
    moeda TEXT NOT NULL DEFAULT 'BRL',
    contato JSONB,
    status TEXT NOT NULL CHECK (status IN ('trial', 'ativo', 'suspenso', 'cancelado')) DEFAULT 'trial',
    branding_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT slug_format CHECK (slug ~ '^[a-z0-9-]+$'),
    CONSTRAINT slug_length CHECK (char_length(slug) >= 2 AND char_length(slug) <= 50)
);

COMMENT ON TABLE tenant IS 'Companies (clients) using the SaaS system';
COMMENT ON COLUMN tenant.slug IS 'URL-friendly identifier (e.g., acme for acme.jetski.com)';
COMMENT ON COLUMN tenant.status IS 'Subscription status: trial, ativo, suspenso, cancelado';
COMMENT ON COLUMN tenant.branding_json IS 'Custom branding: logo, colors, etc.';

-- =====================================================
-- Table: plano
-- Subscription plans (Basic, Pro, Enterprise)
-- =====================================================
CREATE TABLE plano (
    id SERIAL PRIMARY KEY,
    nome TEXT NOT NULL,
    descricao TEXT,
    limites_json JSONB NOT NULL,
    preco_mensal NUMERIC(10,2) NOT NULL CHECK (preco_mensal >= 0),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT nome_unique UNIQUE (nome)
);

COMMENT ON TABLE plano IS 'Subscription plans with limits and pricing';
COMMENT ON COLUMN plano.limites_json IS 'Plan limits: {"frota_max": 10, "usuarios_max": 5, "storage_gb": 10}';

-- =====================================================
-- Table: assinatura
-- Tenant subscription to a plan
-- =====================================================
CREATE TABLE assinatura (
    id SERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    plano_id INT NOT NULL REFERENCES plano(id),
    ciclo TEXT NOT NULL CHECK (ciclo IN ('mensal', 'anual')) DEFAULT 'mensal',
    dt_inicio DATE NOT NULL,
    dt_fim DATE,
    status TEXT NOT NULL CHECK (status IN ('ativa', 'suspensa', 'cancelada')) DEFAULT 'ativa',
    pagamento_cfg_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT dt_fim_after_inicio CHECK (dt_fim IS NULL OR dt_fim > dt_inicio),
    CONSTRAINT one_active_per_tenant UNIQUE (tenant_id, status)
        DEFERRABLE INITIALLY DEFERRED
);

COMMENT ON TABLE assinatura IS 'Tenant subscription to a plan';
COMMENT ON COLUMN assinatura.pagamento_cfg_json IS 'Payment configuration: card, boleto, etc.';

-- =====================================================
-- Table: usuario
-- User accounts (can belong to multiple tenants)
-- =====================================================
CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT UNIQUE NOT NULL,
    nome TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

COMMENT ON TABLE usuario IS 'User accounts (email-based, synced with Keycloak)';

-- =====================================================
-- Table: membro
-- User membership in a tenant with roles
-- =====================================================
CREATE TABLE membro (
    id SERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    papeis TEXT[] NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT membro_unique UNIQUE (tenant_id, usuario_id),
    CONSTRAINT papeis_not_empty CHECK (array_length(papeis, 1) > 0)
);

COMMENT ON TABLE membro IS 'User membership in tenant with roles';
COMMENT ON COLUMN membro.papeis IS 'Roles: ADMIN_TENANT, GERENTE, OPERADOR, VENDEDOR, MECANICO, FINANCEIRO';

-- =====================================================
-- Indexes for performance
-- =====================================================
CREATE INDEX idx_assinatura_tenant ON assinatura(tenant_id);
CREATE INDEX idx_assinatura_status ON assinatura(status) WHERE status = 'ativa';
CREATE INDEX idx_membro_tenant ON membro(tenant_id);
CREATE INDEX idx_membro_usuario ON membro(usuario_id);
CREATE INDEX idx_tenant_slug ON tenant(slug);
CREATE INDEX idx_tenant_status ON tenant(status);

-- =====================================================
-- Updated_at trigger function
-- =====================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to tables with updated_at
CREATE TRIGGER update_tenant_updated_at BEFORE UPDATE ON tenant
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_assinatura_updated_at BEFORE UPDATE ON assinatura
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_usuario_updated_at BEFORE UPDATE ON usuario
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_membro_updated_at BEFORE UPDATE ON membro
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
