-- =====================================================================
-- Identidade GLOBAL do cliente do portal (segue a pessoa, não a loja)
--
-- Apenas dados de IDENTIDADE: CPF, RG, nascimento, nacionalidade etc.
-- Endereço, telefones, fotos e comprovantes permanecem tenant-scoped no
-- Cliente/cliente_anexo de cada loja (decisão de produto).
--
-- Tabela global (sem tenant, como usuario) — sem RLS: o acesso é apenas
-- pelos endpoints self do portal, sempre filtrados pelo sub do JWT.
-- CPF é único entre contas (anti-takeover) e define-only pelo portal.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.customer_profile (
    id               uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    provider         varchar(50) NOT NULL,
    provider_user_id varchar(255) NOT NULL,
    nome             varchar(120),
    cpf              varchar(20),
    rg               varchar(30),
    orgao_emissor    varchar(20),
    nacionalidade    varchar(60),
    naturalidade     varchar(80),
    estrangeiro      boolean NOT NULL DEFAULT false,
    data_nascimento  date,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT customer_profile_provider_uq UNIQUE (provider, provider_user_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_profile_cpf_uq
    ON public.customer_profile (cpf) WHERE cpf IS NOT NULL;
