-- =====================================================================
-- Billing manual assistido (v2, item 1): fatura mensal da assinatura.
--
-- Job diário gera a fatura da competência corrente para tenants com
-- assinatura ativa de plano PAGO (preco_mensal > 0; Trial não fatura),
-- com PIX copia-e-cola da PLATAFORMA (mesma chave dos créditos). A
-- empresa informa o pagamento (txid) → EM_CONFERENCIA; o super admin
-- confere no extrato e confirma (PAGA) — mesmo fluxo humano da compra
-- de créditos. Fatura ABERTA vencida além da carência → suspensão
-- automática (padrão do trial). Gateway automático fica para quando o
-- volume justificar.
--
-- Histórico comercial da plataforma: PRESERVADA no reset/exclusão de
-- empresa (como créditos/metering) — classificada no TenantResetService.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.fatura (
    id              uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id       uuid NOT NULL REFERENCES public.tenant(id),
    competencia     date NOT NULL,                -- 1º dia do mês de referência
    plano_nome      varchar(60) NOT NULL,
    valor           numeric(10,2) NOT NULL CHECK (valor > 0),
    status          varchar(20) NOT NULL DEFAULT 'ABERTA'
                    CHECK (status IN ('ABERTA', 'EM_CONFERENCIA', 'PAGA', 'CANCELADA')),
    vencimento      date NOT NULL,
    pix_copia_e_cola text,
    -- Empresa informa ao pagar (nº da transação PIX p/ conferência no extrato)
    txid_informado  varchar(80),
    informado_em    timestamptz,
    pago_em         timestamptz,
    decidido_por    uuid,                          -- super admin que confirmou/cancelou
    observacao      varchar(300),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    -- Idempotência do job: uma fatura por tenant/competência
    CONSTRAINT fatura_tenant_competencia_uq UNIQUE (tenant_id, competencia)
);

CREATE INDEX IF NOT EXISTS idx_fatura_tenant_status ON public.fatura (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_fatura_status_vencimento ON public.fatura (status, vencimento);

ALTER TABLE public.fatura ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_fatura ON public.fatura;
CREATE POLICY tenant_isolation_fatura ON public.fatura
    USING (tenant_id = public.get_current_tenant_id());
