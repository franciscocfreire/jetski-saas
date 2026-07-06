-- =====================================================================
-- Folio generalizado (fase 3): a tabela reserva_lancamento passa a ser o
-- folio financeiro GERAL — lançamentos pendurados em reserva E/OU locação
-- (walk-in não tem reserva). Novos tipos de COBRANCA (derivadas do sistema
-- no check-out, sem forma de pagamento); PAGAMENTO/ESTORNO seguem exigindo
-- forma (fatos de caixa). O nome da tabela é mantido por compatibilidade.
-- =====================================================================

ALTER TABLE public.reserva_lancamento
    ADD COLUMN IF NOT EXISTS locacao_id uuid REFERENCES public.locacao(id) ON DELETE CASCADE;

ALTER TABLE public.reserva_lancamento ALTER COLUMN reserva_id DROP NOT NULL;
ALTER TABLE public.reserva_lancamento ALTER COLUMN forma DROP NOT NULL;

ALTER TABLE public.reserva_lancamento DROP CONSTRAINT IF EXISTS reserva_lancamento_tipo_check;
ALTER TABLE public.reserva_lancamento ADD CONSTRAINT reserva_lancamento_tipo_check
    CHECK (tipo IN ('PAGAMENTO', 'ESTORNO',
                    'COBRANCA_ALUGUEL', 'COBRANCA_COMBUSTIVEL', 'COBRANCA_EXTRAS'));

-- Forma obrigatória só para fatos de caixa; cobrança é derivada (sem forma).
ALTER TABLE public.reserva_lancamento DROP CONSTRAINT IF EXISTS reserva_lancamento_forma_check;
ALTER TABLE public.reserva_lancamento ADD CONSTRAINT reserva_lancamento_forma_check
    CHECK (
        (tipo IN ('PAGAMENTO', 'ESTORNO')
            AND forma IN ('DINHEIRO', 'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'OUTRO'))
        OR (tipo IN ('COBRANCA_ALUGUEL', 'COBRANCA_COMBUSTIVEL', 'COBRANCA_EXTRAS')
            AND forma IS NULL)
    );

ALTER TABLE public.reserva_lancamento DROP CONSTRAINT IF EXISTS reserva_lancamento_ancora_check;
ALTER TABLE public.reserva_lancamento ADD CONSTRAINT reserva_lancamento_ancora_check
    CHECK (reserva_id IS NOT NULL OR locacao_id IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_reserva_lancamento_locacao
    ON public.reserva_lancamento (tenant_id, locacao_id) WHERE locacao_id IS NOT NULL;

-- Fechamento diário agrega recebimentos por forma pela data do lançamento
CREATE INDEX IF NOT EXISTS idx_reserva_lancamento_dia
    ON public.reserva_lancamento (tenant_id, created_at);
