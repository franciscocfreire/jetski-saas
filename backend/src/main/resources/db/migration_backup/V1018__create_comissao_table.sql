-- =====================================================
-- Migration: V1018 - Create comissao table
-- Description: Registro de comissões calculadas por locação
-- Author: Jetski Team
-- Date: 2025-10-29
-- =====================================================

CREATE TABLE IF NOT EXISTS comissao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    locacao_id UUID NOT NULL,
    vendedor_id UUID NOT NULL,
    politica_id UUID,

    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'APROVADA', 'PAGA', 'CANCELADA')),

    -- Data da locação (para agrupamento mensal)
    data_locacao TIMESTAMP NOT NULL,

    -- Valores de cálculo (RN04 - Receita comissionável)
    valor_total_locacao DECIMAL(10,2) NOT NULL,
    valor_combustivel DECIMAL(10,2) DEFAULT 0.00,  -- Não-comissionável
    valor_multas DECIMAL(10,2) DEFAULT 0.00,        -- Não-comissionável
    valor_taxas DECIMAL(10,2) DEFAULT 0.00,         -- Não-comissionável (limpeza, danos)
    valor_comissionavel DECIMAL(10,2) NOT NULL,     -- = total - combustivel - multas - taxas

    -- Tipo e valores calculados
    tipo_comissao VARCHAR(20) NOT NULL CHECK (tipo_comissao IN ('PERCENTUAL', 'VALOR_FIXO', 'ESCALONADO')),
    percentual_aplicado DECIMAL(5,2),                -- Se PERCENTUAL ou ESCALONADO
    valor_comissao DECIMAL(10,2) NOT NULL,

    -- Rastreabilidade (snapshot da política aplicada)
    politica_nome VARCHAR(100),
    politica_nivel VARCHAR(20) CHECK (politica_nivel IN ('CAMPANHA', 'MODELO', 'DURACAO', 'VENDEDOR')),

    -- Observações
    observacoes VARCHAR(500),

    -- Aprovação
    aprovado_por UUID,
    aprovado_em TIMESTAMP,

    -- Pagamento
    pago_por UUID,
    pago_em TIMESTAMP,
    referencia_pagamento VARCHAR(100),

    -- Auditoria
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Foreign Keys
    CONSTRAINT fk_comissao_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_comissao_locacao FOREIGN KEY (locacao_id) REFERENCES locacao(id),
    CONSTRAINT fk_comissao_vendedor FOREIGN KEY (vendedor_id) REFERENCES vendedor(id),
    CONSTRAINT fk_comissao_politica FOREIGN KEY (politica_id) REFERENCES politica_comissao(id),
    CONSTRAINT fk_comissao_aprovado_por FOREIGN KEY (aprovado_por) REFERENCES usuario(id),
    CONSTRAINT fk_comissao_pago_por FOREIGN KEY (pago_por) REFERENCES usuario(id),

    -- Unique: uma comissão por locação
    CONSTRAINT uk_comissao_locacao UNIQUE (tenant_id, locacao_id)
);

-- Índices para queries comuns
CREATE INDEX idx_comissao_tenant_vendedor ON comissao (tenant_id, vendedor_id, status);
CREATE INDEX idx_comissao_tenant_status ON comissao (tenant_id, status, data_locacao DESC);
CREATE INDEX idx_comissao_tenant_data ON comissao (tenant_id, data_locacao DESC);
CREATE INDEX idx_comissao_aprovacao ON comissao (tenant_id, status) WHERE status = 'PENDENTE';
CREATE INDEX idx_comissao_pagamento ON comissao (tenant_id, status) WHERE status = 'APROVADA';

-- Comentários
COMMENT ON TABLE comissao IS 'Registro de comissões calculadas para cada locação';
COMMENT ON COLUMN comissao.valor_comissionavel IS 'Valor base para cálculo (exclui combustível, multas, taxas)';
COMMENT ON COLUMN comissao.status IS 'PENDENTE → APROVADA (gerente) → PAGA (financeiro) | CANCELADA (estorno)';
COMMENT ON COLUMN comissao.politica_nome IS 'Snapshot da política aplicada para histórico';
COMMENT ON COLUMN comissao.politica_nivel IS 'Nível hierárquico da política aplicada (rastreabilidade)';
