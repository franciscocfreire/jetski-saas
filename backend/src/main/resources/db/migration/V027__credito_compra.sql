-- ============================================================================
-- V027: Compra de créditos por PIX (fluxo manual v1)
--
-- Tenant transfere para a chave PIX fixa da plataforma e registra a solicitação
-- com o número da transação (txid). O super admin confere o extrato bancário e
-- aprova (gera lançamento no ledger credito_lancamento, auditado) ou rejeita.
-- ============================================================================

CREATE TABLE public.credito_compra (
    id            uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE RESTRICT,
    quantidade    integer NOT NULL CHECK (quantidade > 0),
    pix_txid      varchar(80) NOT NULL,
    status        varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA')),
    criado_por    uuid,
    decidido_por  uuid,
    decidido_em   timestamptz,
    observacao    varchar(200),      -- nota do admin (ex.: motivo da rejeição)
    lancamento_id uuid,              -- credito_lancamento gerado na aprovação
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.credito_compra IS 'Solicitações de compra de créditos via PIX (aprovação manual do super admin)';

-- Mesmo comprovante não pode ser usado duas vezes pelo mesmo tenant
CREATE UNIQUE INDEX ux_credito_compra_txid ON public.credito_compra (tenant_id, pix_txid);
CREATE INDEX idx_credito_compra_tenant_status ON public.credito_compra (tenant_id, status);

ALTER TABLE public.credito_compra ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credito_compra FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_credito_compra ON public.credito_compra
    USING (tenant_id = public.get_current_tenant_id());
