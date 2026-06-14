-- Create pagamento_vendedor table for batch payment tracking
-- Records payment batches that include commissions and daily allowances

CREATE TABLE pagamento_vendedor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    vendedor_id UUID NOT NULL REFERENCES vendedor(id),

    -- Payment amounts
    valor_comissoes NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_diarias NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_total NUMERIC(12,2) NOT NULL,

    -- PIX information (snapshot at time of payment)
    chave_pix VARCHAR(100) NOT NULL,
    tipo_chave_pix VARCHAR(20) NOT NULL,

    -- Payment reference
    referencia_pagamento VARCHAR(100) NOT NULL,
    comprovante_url TEXT,
    comprovante_s3_key TEXT,

    -- Counts for audit
    qtd_comissoes INTEGER NOT NULL DEFAULT 0,
    qtd_diarias INTEGER NOT NULL DEFAULT 0,

    -- Period reference
    periodo_inicio DATE,
    periodo_fim DATE,

    -- Metadata
    pago_por UUID REFERENCES usuario(id),
    observacoes TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT pagamento_valor_positivo CHECK (valor_total > 0),
    CONSTRAINT pagamento_tipo_pix_check CHECK (tipo_chave_pix IN ('CPF', 'CNPJ', 'EMAIL', 'TELEFONE', 'ALEATORIA'))
);

-- Indexes for common queries
CREATE INDEX idx_pagamento_vendedor_tenant ON pagamento_vendedor(tenant_id);
CREATE INDEX idx_pagamento_vendedor_vendedor ON pagamento_vendedor(tenant_id, vendedor_id);
CREATE INDEX idx_pagamento_vendedor_data ON pagamento_vendedor(tenant_id, created_at DESC);

-- Enable RLS
ALTER TABLE pagamento_vendedor ENABLE ROW LEVEL SECURITY;
ALTER TABLE pagamento_vendedor FORCE ROW LEVEL SECURITY;

-- RLS policy for tenant isolation
CREATE POLICY tenant_isolation_pagamento_vendedor ON pagamento_vendedor
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Trigger for updated_at
CREATE TRIGGER update_pagamento_vendedor_updated_at
    BEFORE UPDATE ON pagamento_vendedor
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE pagamento_vendedor IS 'Batch payment records for seller commissions and daily allowances';
COMMENT ON COLUMN pagamento_vendedor.valor_comissoes IS 'Total of approved commissions included in this payment';
COMMENT ON COLUMN pagamento_vendedor.valor_diarias IS 'Total of unpaid daily allowances included in this payment';
COMMENT ON COLUMN pagamento_vendedor.chave_pix IS 'Snapshot of PIX key at time of payment';
COMMENT ON COLUMN pagamento_vendedor.referencia_pagamento IS 'PIX transaction reference/ID';
COMMENT ON COLUMN pagamento_vendedor.comprovante_url IS 'S3 URL for payment receipt';
