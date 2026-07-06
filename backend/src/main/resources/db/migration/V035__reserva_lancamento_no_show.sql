-- =====================================================================
-- Conta financeira da reserva (folio, fase 2) + estado NO_SHOW
--
-- reserva_lancamento: ledger append-only dos fatos financeiros da
-- reserva. Nesta fase registra o pagamento presencial integral do
-- balcão (dinheiro/PIX/cartão); a fase 3 acrescenta cobranças do
-- check-out e alimenta o fechamento diário por forma de pagamento.
-- O campo reserva.pagamento_status segue autoritativo — o lançamento
-- é gravado na mesma transação que o confirma.
--
-- NO_SHOW: cliente não compareceu — marcado MANUALMENTE pelo staff
-- (difere de EXPIRADA, que é automática e só alcança reserva sem sinal).
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.reserva_lancamento (
    id             uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id      uuid NOT NULL,
    reserva_id     uuid NOT NULL REFERENCES public.reserva(id) ON DELETE CASCADE,
    tipo           varchar(20) NOT NULL
                   CHECK (tipo IN ('PAGAMENTO', 'ESTORNO')),
    forma          varchar(20) NOT NULL
                   CHECK (forma IN ('DINHEIRO', 'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'OUTRO')),
    valor          numeric(10,2) NOT NULL CHECK (valor > 0),
    observacao     text,
    registrado_por uuid,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reserva_lancamento_reserva
    ON public.reserva_lancamento (tenant_id, reserva_id);

ALTER TABLE public.reserva_lancamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reserva_lancamento FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_reserva_lancamento ON public.reserva_lancamento;
CREATE POLICY tenant_isolation_reserva_lancamento ON public.reserva_lancamento
    USING (tenant_id = public.get_current_tenant_id());

-- NO_SHOW no ciclo de vida da reserva (refaz o CHECK da V018)
ALTER TABLE public.reserva DROP CONSTRAINT IF EXISTS reserva_status_check;

ALTER TABLE public.reserva
    ADD CONSTRAINT reserva_status_check
    CHECK ((status)::text = ANY (ARRAY[
        'RASCUNHO'::varchar,
        'PENDENTE'::varchar,
        'CONFIRMADA'::varchar,
        'CANCELADA'::varchar,
        'FINALIZADA'::varchar,
        'EXPIRADA'::varchar,
        'NO_SHOW'::varchar
    ]::text[]));
