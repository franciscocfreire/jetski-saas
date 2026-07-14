-- =====================================================================
-- Emissão delegada: instrutores DESIGNADOS por parceria (V049).
--
-- A EAMA emissora escolhe quais dos seus instrutores atendem cada
-- operadora parceira. Semântica opt-in: SEM designação, a operadora vê
-- todos os instrutores ativos da EAMA (comportamento V048); COM
-- designação, só os designados — na listagem E na emissão.
--
-- Sem tenant_id próprio: a visibilidade herda do vínculo (RLS via
-- subquery — visível aos dois lados da parceria). FKs em cascata:
-- some junto do vínculo ou do instrutor.
-- =====================================================================

CREATE TABLE public.vinculo_emissao_instrutor (
    id           uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    vinculo_id   uuid NOT NULL REFERENCES public.vinculo_emissao(id) ON DELETE CASCADE,
    instrutor_id uuid NOT NULL REFERENCES public.instrutor(id) ON DELETE CASCADE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_vinculo_emissao_instrutor UNIQUE (vinculo_id, instrutor_id)
);
COMMENT ON TABLE public.vinculo_emissao_instrutor IS
    'Instrutores da EAMA designados para atender a parceria (vazio = todos os ativos)';

CREATE INDEX idx_vinculo_emissao_instrutor_vinculo
    ON public.vinculo_emissao_instrutor (vinculo_id);

ALTER TABLE public.vinculo_emissao_instrutor ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vinculo_emissao_instrutor FORCE ROW LEVEL SECURITY;
CREATE POLICY vinculo_emissao_instrutor_partes ON public.vinculo_emissao_instrutor
    USING (EXISTS (
        SELECT 1 FROM public.vinculo_emissao v
        WHERE v.id = vinculo_id
          AND (v.tenant_operador_id = public.get_current_tenant_id()
            OR v.tenant_emissor_id = public.get_current_tenant_id())
    ));
