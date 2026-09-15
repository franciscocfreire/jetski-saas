-- =====================================================================
-- V076: condição comercial por empresa (piloto, cortesia, parceria, negociado).
--
-- A assinatura diz QUAL plano a empresa usa (módulos, limites, preço de
-- tabela). A condição diz QUANTO ela paga de fato, por quê e até quando:
--
--   forma ISENCAO    → não paga a mensalidade (valor NULL)
--   forma PERCENTUAL → desconto de `valor` % sobre o preço do plano
--   forma VALOR_FIXO → paga `valor` por mês, qualquer que seja o plano
--
-- Presa à EMPRESA, não à assinatura: trocar de plano durante o piloto não
-- derruba a isenção. Só cobre a MENSALIDADE — créditos de emissão seguem à
-- parte (cortesia de crédito é lançamento próprio no ledger, V077).
--
-- Vigente num dia D quando: inicio <= D, (fim IS NULL OU fim >= D) e não foi
-- encerrada até D (encerrada_em no fuso de operação > D). Encerrar não apaga:
-- a linha fica como histórico comercial, PRESERVADA no reset (como fatura).
--
-- Efeitos: faturamento usa o valor efetivo (isenção não gera fatura), isenção
-- não é suspensa por inadimplência, e o read model separa MRR contratado de
-- MRR de tabela (a diferença é o renunciado).
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.condicao_comercial (
    id                  uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id           uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    tipo                varchar(20) NOT NULL
                        CHECK (tipo IN ('PILOTO', 'CORTESIA', 'PARCERIA', 'NEGOCIADO')),
    forma               varchar(20) NOT NULL
                        CHECK (forma IN ('ISENCAO', 'PERCENTUAL', 'VALOR_FIXO')),
    valor               numeric(10,2),
    inicio              date NOT NULL,
    fim                 date,
    motivo              varchar(300) NOT NULL,
    concedida_por       uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    encerrada_em        timestamptz,
    encerrada_por       uuid,
    motivo_encerramento varchar(300),
    CONSTRAINT condicao_comercial_valor_por_forma CHECK (
        (forma = 'ISENCAO' AND valor IS NULL)
        OR (forma = 'PERCENTUAL' AND valor > 0 AND valor <= 100)
        OR (forma = 'VALOR_FIXO' AND valor >= 0)),
    CONSTRAINT condicao_comercial_periodo CHECK (fim IS NULL OR fim >= inicio)
);

CREATE INDEX IF NOT EXISTS idx_condicao_comercial_tenant_inicio
    ON public.condicao_comercial (tenant_id, inicio);

-- Padrão V069: uma policy ALL com USING e WITH CHECK pelo tenant da sessão.
ALTER TABLE public.condicao_comercial ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.condicao_comercial FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_condicao_comercial ON public.condicao_comercial;
CREATE POLICY tenant_isolation_condicao_comercial ON public.condicao_comercial
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

COMMENT ON TABLE public.condicao_comercial IS
    'Condição comercial da mensalidade por empresa (isenção/desconto com vigência e motivo). Histórico preservado no reset.';

-- ---- read model: MRR contratado × tabela ----
ALTER TABLE public.plataforma_metrica_diaria
    ADD COLUMN IF NOT EXISTS mrr_tabela     numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS condicao_tipo  varchar(20);

COMMENT ON COLUMN public.plataforma_metrica_diaria.mrr IS
    'MRR contratado: valor efetivo da mensalidade (plano com a condição comercial vigente), só empresa em operação.';
COMMENT ON COLUMN public.plataforma_metrica_diaria.mrr_tabela IS
    'MRR de tabela: preço cheio do plano, só empresa em operação. mrr_tabela - mrr = renunciado.';
