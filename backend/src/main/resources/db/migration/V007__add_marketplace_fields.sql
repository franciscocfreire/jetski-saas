-- ============================================================================
-- V007: Adicionar campos para Marketplace Público
-- ============================================================================
-- Campos de contato/localização do Tenant e controle de visibilidade
-- para o marketplace público multi-tenant.
-- ============================================================================

-- Campos de contato e localização do Tenant
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS whatsapp VARCHAR(20);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS cidade VARCHAR(100);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS uf VARCHAR(2);

-- Campos de visibilidade e prioridade no marketplace
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS exibir_no_marketplace BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS prioridade_marketplace INTEGER NOT NULL DEFAULT 0;

-- Campo de visibilidade individual por modelo
ALTER TABLE modelo ADD COLUMN IF NOT EXISTS exibir_no_marketplace BOOLEAN NOT NULL DEFAULT true;

-- Comentários para documentação
COMMENT ON COLUMN tenant.whatsapp IS 'WhatsApp para contato no marketplace (formato: 5548999999999)';
COMMENT ON COLUMN tenant.cidade IS 'Cidade para exibição no marketplace';
COMMENT ON COLUMN tenant.uf IS 'UF para exibição no marketplace (ex: SC, SP)';
COMMENT ON COLUMN tenant.exibir_no_marketplace IS 'Se true, tenant aparece no marketplace público';
COMMENT ON COLUMN tenant.prioridade_marketplace IS 'Prioridade para ordenação no marketplace (0-100, maior = mais destaque)';
COMMENT ON COLUMN modelo.exibir_no_marketplace IS 'Se true, modelo aparece no marketplace público (sujeito ao tenant também estar visível)';

-- Index para queries de marketplace (modelos visíveis de tenants visíveis)
CREATE INDEX IF NOT EXISTS idx_tenant_marketplace
ON tenant(status, exibir_no_marketplace, prioridade_marketplace DESC)
WHERE status = 'ATIVO' AND exibir_no_marketplace = true;

CREATE INDEX IF NOT EXISTS idx_modelo_marketplace
ON modelo(tenant_id, ativo, exibir_no_marketplace)
WHERE ativo = true AND exibir_no_marketplace = true;
