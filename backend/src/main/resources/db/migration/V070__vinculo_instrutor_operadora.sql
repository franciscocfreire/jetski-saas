-- =====================================================================
-- Emissão delegada: instrutores da OPERADORA aprovados pela EAMA (V070).
--
-- NORMAM-212: o instrutor que atesta a demonstração (Anexo 5-B-1) é
-- cadastrado na EAMA emissora, que responde por ele. A operadora pode
-- cadastrar instrutores próprios, mas eles só assinam emissões delegadas
-- depois de APROVADOS pela EAMA da parceria — e a EAMA pode removê-los.
--
-- Uma linha por (parceria, instrutor da operadora). Estados:
--   PENDENTE  — aguardando a EAMA (novo pedido ou dados alterados)
--   APROVADO  — pode ser escolhido na emissão delegada
--   REJEITADO — a EAMA recusou o pedido
--   REMOVIDO  — a EAMA retirou uma aprovação
--
-- Sem tenant_id próprio: a visibilidade herda do vínculo (RLS via
-- subquery — visível aos dois lados, como a designação da V049).
-- =====================================================================

CREATE TABLE public.vinculo_instrutor_operadora (
    id            uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    vinculo_id    uuid NOT NULL REFERENCES public.vinculo_emissao(id) ON DELETE CASCADE,
    instrutor_id  uuid NOT NULL REFERENCES public.instrutor(id) ON DELETE CASCADE,
    status        varchar(12) NOT NULL DEFAULT 'PENDENTE'
                  CHECK (status IN ('PENDENTE', 'APROVADO', 'REJEITADO', 'REMOVIDO')),
    solicitado_em timestamptz NOT NULL DEFAULT now(),
    solicitado_por uuid,
    decidido_em   timestamptz,
    decidido_por  uuid,
    motivo        varchar(500),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_vinculo_instrutor_operadora UNIQUE (vinculo_id, instrutor_id)
);
COMMENT ON TABLE public.vinculo_instrutor_operadora IS
    'Instrutores da operadora submetidos à aprovação da EAMA emissora da parceria (NORMAM-212)';

CREATE INDEX idx_vinculo_instrutor_operadora_instrutor
    ON public.vinculo_instrutor_operadora (instrutor_id);

ALTER TABLE public.vinculo_instrutor_operadora ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vinculo_instrutor_operadora FORCE ROW LEVEL SECURITY;
CREATE POLICY vinculo_instrutor_operadora_partes ON public.vinculo_instrutor_operadora
    USING (EXISTS (
        SELECT 1 FROM public.vinculo_emissao v
        WHERE v.id = vinculo_id
          AND (v.tenant_operador_id = public.get_current_tenant_id()
            OR v.tenant_emissor_id = public.get_current_tenant_id())
    ));

-- Papel exclusivo (EMISSAO_DELEGADA_SPEC §8.M): quem já é operadora de uma
-- parceria viva deixa de ser emissora. Corrige dados anteriores à regra.
UPDATE public.tenant t
   SET emissora_habilitada = false
 WHERE t.emissora_habilitada = true
   AND EXISTS (SELECT 1 FROM public.vinculo_emissao v
               WHERE v.tenant_operador_id = t.id AND v.status IN ('ATIVO', 'BLOQUEADO'));

-- Designação obrigatória (EMISSAO_DELEGADA_SPEC §8.L, revista): sem designação a
-- operadora não usa instrutor da EAMA. Parcerias em vigor que dependiam do antigo
-- "vazio = todos os ativos" recebem a designação explícita desses instrutores, para
-- nada mudar nelas no deploy; a EAMA ajusta depois.
INSERT INTO public.vinculo_emissao_instrutor (vinculo_id, instrutor_id)
SELECT v.id, i.id
  FROM public.vinculo_emissao v
  JOIN public.instrutor i ON i.tenant_id = v.tenant_emissor_id AND i.ativo = true
 WHERE v.status IN ('ATIVO', 'BLOQUEADO')
   AND NOT EXISTS (SELECT 1 FROM public.vinculo_emissao_instrutor d WHERE d.vinculo_id = v.id)
ON CONFLICT (vinculo_id, instrutor_id) DO NOTHING;
