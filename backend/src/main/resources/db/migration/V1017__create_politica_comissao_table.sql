-- =====================================================
-- Migration: V1017 - Create politica_comissao table
-- Description: Políticas hierárquicas de comissão (RN04)
-- Author: Jetski Team
-- Date: 2025-10-29
-- =====================================================

CREATE TABLE IF NOT EXISTS politica_comissao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,

    -- Hierarquia e tipo
    nivel VARCHAR(20) NOT NULL CHECK (nivel IN ('CAMPANHA', 'MODELO', 'DURACAO', 'VENDEDOR')),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('PERCENTUAL', 'VALOR_FIXO', 'ESCALONADO')),

    -- Identificação
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(500),

    -- Filtros/critérios de aplicação (apenas um deve ser preenchido por nível)
    vendedor_id UUID,
    modelo_id UUID,
    codigo_campanha VARCHAR(50),
    duracao_min_minutos INTEGER,
    duracao_max_minutos INTEGER,

    -- Valores de comissão
    percentual_comissao DECIMAL(5,2),  -- Ex: 10.50 para 10,5%
    valor_fixo DECIMAL(10,2),           -- Ex: 50.00 para R$ 50,00
    percentual_extra DECIMAL(5,2),      -- Para tipo ESCALONADO

    -- Vigência (para campanhas)
    vigencia_inicio TIMESTAMP,
    vigencia_fim TIMESTAMP,

    -- Controle
    ativa BOOLEAN NOT NULL DEFAULT TRUE,

    -- Auditoria
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,

    -- Foreign Keys
    CONSTRAINT fk_politica_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_politica_vendedor FOREIGN KEY (vendedor_id) REFERENCES vendedor(id),
    CONSTRAINT fk_politica_modelo FOREIGN KEY (modelo_id) REFERENCES modelo(id),
    CONSTRAINT fk_politica_created_by FOREIGN KEY (created_by) REFERENCES usuario(id),

    -- Business rules validations
    CONSTRAINT check_politica_vendedor CHECK (
        nivel != 'VENDEDOR' OR vendedor_id IS NOT NULL
    ),
    CONSTRAINT check_politica_modelo CHECK (
        nivel != 'MODELO' OR modelo_id IS NOT NULL
    ),
    CONSTRAINT check_politica_campanha CHECK (
        nivel != 'CAMPANHA' OR (codigo_campanha IS NOT NULL AND vigencia_inicio IS NOT NULL)
    ),
    CONSTRAINT check_politica_duracao CHECK (
        nivel != 'DURACAO' OR duracao_min_minutos IS NOT NULL
    ),
    CONSTRAINT check_tipo_percentual CHECK (
        tipo != 'PERCENTUAL' OR percentual_comissao IS NOT NULL
    ),
    CONSTRAINT check_tipo_valor_fixo CHECK (
        tipo != 'VALOR_FIXO' OR valor_fixo IS NOT NULL
    ),
    CONSTRAINT check_tipo_escalonado CHECK (
        tipo != 'ESCALONADO' OR (percentual_comissao IS NOT NULL AND percentual_extra IS NOT NULL AND duracao_min_minutos IS NOT NULL)
    )
);

-- Índices para otimização de queries hierárquicas
CREATE INDEX idx_politica_tenant_nivel ON politica_comissao (tenant_id, nivel, ativa);
CREATE INDEX idx_politica_tenant_vendedor ON politica_comissao (tenant_id, vendedor_id) WHERE vendedor_id IS NOT NULL;
CREATE INDEX idx_politica_tenant_modelo ON politica_comissao (tenant_id, modelo_id) WHERE modelo_id IS NOT NULL;
CREATE INDEX idx_politica_tenant_campanha ON politica_comissao (tenant_id, codigo_campanha) WHERE codigo_campanha IS NOT NULL;
CREATE INDEX idx_politica_vigencia ON politica_comissao (vigencia_inicio, vigencia_fim) WHERE nivel = 'CAMPANHA';

-- Comentários
COMMENT ON TABLE politica_comissao IS 'Políticas hierárquicas de comissão (CAMPANHA > MODELO > DURACAO > VENDEDOR)';
COMMENT ON COLUMN politica_comissao.nivel IS 'Nível hierárquico: CAMPANHA(1), MODELO(2), DURACAO(3), VENDEDOR(4) - primeiro match ganha';
COMMENT ON COLUMN politica_comissao.tipo IS 'PERCENTUAL (%), VALOR_FIXO (R$), ESCALONADO (% até X min, % extra acima)';
COMMENT ON COLUMN politica_comissao.percentual_comissao IS 'Percentual base (ex: 10.50 = 10,5%)';
COMMENT ON COLUMN politica_comissao.percentual_extra IS 'Percentual adicional para ESCALONADO acima de duracao_min_minutos';
COMMENT ON COLUMN politica_comissao.duracao_min_minutos IS 'Duração mínima para aplicar (DURACAO ou ESCALONADO)';
COMMENT ON COLUMN politica_comissao.duracao_max_minutos IS 'Duração máxima para aplicar (DURACAO), null = sem limite';
