-- Tabela despesa_manutencao para controle de despesas de manutenção com parcelamento
CREATE TABLE despesa_manutencao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    os_manutencao_id UUID NOT NULL REFERENCES os_manutencao(id),
    dt_vencimento DATE NOT NULL,
    numero_parcela INTEGER NOT NULL DEFAULT 1,
    total_parcelas INTEGER NOT NULL DEFAULT 1,
    valor NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    aprovado_por INTEGER REFERENCES membro(id),
    aprovado_em TIMESTAMPTZ,
    pago_por INTEGER REFERENCES membro(id),
    pago_em TIMESTAMPTZ,
    referencia_pagamento VARCHAR(100),
    observacoes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT despesa_manutencao_status_check
        CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA', 'PAGA', 'CANCELADA')),
    CONSTRAINT despesa_manutencao_valor_positivo CHECK (valor > 0),
    CONSTRAINT despesa_manutencao_parcela_valida CHECK (numero_parcela > 0 AND numero_parcela <= total_parcelas)
);

-- RLS
ALTER TABLE despesa_manutencao ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_despesa_manutencao ON despesa_manutencao
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Indexes
CREATE INDEX idx_despesa_manutencao_tenant ON despesa_manutencao(tenant_id);
CREATE INDEX idx_despesa_manutencao_os ON despesa_manutencao(os_manutencao_id);
CREATE INDEX idx_despesa_manutencao_vencimento ON despesa_manutencao(tenant_id, dt_vencimento);
CREATE INDEX idx_despesa_manutencao_status ON despesa_manutencao(tenant_id, status);

-- Comment
COMMENT ON TABLE despesa_manutencao IS 'Despesas de manutenção com suporte a parcelamento e workflow de aprovação/pagamento';
