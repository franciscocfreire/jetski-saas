-- V059: Identidade única — F0 (fundação de schema).
-- Spec: IDENTIDADE_UNICA_SPEC.md §5/F0.
--
-- usuario passa a ser a raiz única da pessoa; estas colunas criam o caminho
-- novo SEM desligar o antigo (cliente_identity_provider segue vigente até a
-- F4). Todas nullable: ficha de balcão/lead sem conta é legítima (a pessoa
-- ainda não tem identidade na plataforma).

-- Ficha comercial → pessoa (o vínculo que substituirá cliente_identity_provider)
ALTER TABLE public.cliente
    ADD COLUMN IF NOT EXISTS usuario_id uuid REFERENCES public.usuario (id);
-- Parcial: a maioria das fichas (balcão/lead sem conta) fica NULL
CREATE INDEX IF NOT EXISTS idx_cliente_usuario
    ON public.cliente (usuario_id) WHERE usuario_id IS NOT NULL;

-- Identidade civil do consumidor → pessoa (deixa de ser raiz própria na F3)
ALTER TABLE public.customer_profile
    ADD COLUMN IF NOT EXISTS usuario_id uuid REFERENCES public.usuario (id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_customer_profile_usuario
    ON public.customer_profile (usuario_id) WHERE usuario_id IS NOT NULL;

-- Habilitação → pessoa (CPF continua a chave humana; FK preenchida na F3)
ALTER TABLE public.customer_habilitacao
    ADD COLUMN IF NOT EXISTS usuario_id uuid REFERENCES public.usuario (id);

-- Guarda da janela de migração: a F0 NÃO faz backfill porque produção tem
-- ZERO vínculos hoje (30/jul/2026). Se aparecer vínculo antes deste deploy,
-- é melhor falhar alto e tratar manualmente do que criar fichas órfãs de
-- usuario_id em silêncio. (Bancos novos — CI/reset — passam: tabela vazia.)
DO $$
DECLARE
    vinculos bigint;
BEGIN
    SELECT count(*) INTO vinculos FROM public.cliente_identity_provider;
    IF vinculos > 0 THEN
        RAISE EXCEPTION 'IDENTIDADE_UNICA F0: cliente_identity_provider tem % linha(s) — '
            'backfill manual necessário antes desta migration (ver IDENTIDADE_UNICA_SPEC.md §5/F0)',
            vinculos;
    END IF;
END
$$;
