-- ============================================================================
-- V014: Tabela de Despesas Operacionais
-- ============================================================================
-- Despesas do dia a dia nao vinculadas a locacoes especificas.
-- Ex: diarias de funcionarios, refeicao, combustivel proprio, limpeza, etc.
-- ============================================================================

-- Tabela de despesas operacionais
CREATE TABLE despesa_operacional (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    dt_referencia DATE NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    descricao VARCHAR(255),
    valor NUMERIC(10,2) NOT NULL,
    responsavel_id UUID REFERENCES usuario(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    aprovado_por UUID REFERENCES usuario(id),
    aprovado_em TIMESTAMPTZ,
    pago_por UUID REFERENCES usuario(id),
    pago_em TIMESTAMPTZ,
    referencia_pagamento VARCHAR(100),
    observacoes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT despesa_categoria_check CHECK (categoria IN (
        'DIARIA_FUNCIONARIO',
        'REFEICAO',
        'COMBUSTIVEL_PROPRIO',
        'LIMPEZA',
        'TAXA_ADMINISTRATIVA',
        'TRANSPORTE',
        'MATERIAL_ESCRITORIO',
        'OUTROS'
    )),
    CONSTRAINT despesa_status_check CHECK (status IN (
        'PENDENTE',
        'APROVADA',
        'REJEITADA',
        'PAGA'
    )),
    CONSTRAINT despesa_valor_positivo CHECK (valor > 0)
);

-- Indexes
CREATE INDEX idx_despesa_operacional_tenant ON despesa_operacional(tenant_id);
CREATE INDEX idx_despesa_operacional_data ON despesa_operacional(tenant_id, dt_referencia);
CREATE INDEX idx_despesa_operacional_categoria ON despesa_operacional(tenant_id, categoria);
CREATE INDEX idx_despesa_operacional_status ON despesa_operacional(tenant_id, status);
CREATE INDEX idx_despesa_operacional_responsavel ON despesa_operacional(tenant_id, responsavel_id);

-- RLS
ALTER TABLE despesa_operacional ENABLE ROW LEVEL SECURITY;
ALTER TABLE despesa_operacional FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_despesa_operacional ON despesa_operacional
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Trigger de updated_at
CREATE TRIGGER update_despesa_operacional_updated_at
    BEFORE UPDATE ON despesa_operacional
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE despesa_operacional IS 'Despesas operacionais do dia a dia (diarias, refeicao, etc)';
COMMENT ON COLUMN despesa_operacional.dt_referencia IS 'Data de referencia da despesa';
COMMENT ON COLUMN despesa_operacional.categoria IS 'Categoria: DIARIA_FUNCIONARIO, REFEICAO, COMBUSTIVEL_PROPRIO, LIMPEZA, etc';
COMMENT ON COLUMN despesa_operacional.valor IS 'Valor da despesa (sempre positivo)';
COMMENT ON COLUMN despesa_operacional.responsavel_id IS 'Usuario/membro responsavel pela despesa';
COMMENT ON COLUMN despesa_operacional.status IS 'Status: PENDENTE, APROVADA, REJEITADA, PAGA';
