-- =====================================================
-- Migration: V1012 - Create abastecimento table
-- Descrição: Tabela de registros de abastecimento
-- =====================================================

-- Drop if exists (para corrigir estrutura antiga com BIGINT)
DROP TABLE IF EXISTS abastecimento CASCADE;

CREATE TABLE abastecimento (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    jetski_id UUID NOT NULL REFERENCES jetski(id),
    locacao_id UUID REFERENCES locacao(id),
    responsavel_id UUID,

    data_hora TIMESTAMP NOT NULL,
    litros NUMERIC(10,3) NOT NULL CHECK (litros > 0),
    preco_litro NUMERIC(10,2) NOT NULL CHECK (preco_litro > 0),
    custo_total NUMERIC(10,2) NOT NULL CHECK (custo_total >= 0),
    tipo VARCHAR(20) CHECK (tipo IN ('PRE_LOCACAO', 'POS_LOCACAO', 'FROTA')),

    foto_id BIGINT,
    observacoes VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índices para performance
CREATE INDEX idx_abastecimento_tenant ON abastecimento(tenant_id);
CREATE INDEX idx_abastecimento_jetski ON abastecimento(tenant_id, jetski_id);
CREATE INDEX idx_abastecimento_locacao ON abastecimento(tenant_id, locacao_id);
CREATE INDEX idx_abastecimento_data ON abastecimento(tenant_id, data_hora);

-- Row Level Security (RLS)
ALTER TABLE abastecimento ENABLE ROW LEVEL SECURITY;

CREATE POLICY abastecimento_tenant_isolation ON abastecimento
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

COMMENT ON TABLE abastecimento IS 'Registros de abastecimento de combustível por jetski ou locação (RF06)';
COMMENT ON COLUMN abastecimento.tipo IS 'Tipo: PRE_LOCACAO (antes), POS_LOCACAO (depois), FROTA (manutenção)';
COMMENT ON COLUMN abastecimento.custo_total IS 'Calculado automaticamente: litros × preco_litro';
