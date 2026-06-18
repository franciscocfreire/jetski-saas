-- =====================================================================
-- V011 — Cadastro de instrutores (EAMA) p/ o Atestado de Demonstração 5-B-1
-- ---------------------------------------------------------------------
-- O instrutor que ministra a demonstração prática (CHA-MTA-E) assina o
-- Anexo 5-B-1. Seus dados (nome, identidade, órgão emissor, CPF, nº da CHA)
-- são reutilizáveis entre locações → cadastro próprio, tenant-scoped (RLS).
-- A reserva_habilitacao referencia o instrutor escolhido no atendimento.
-- =====================================================================

CREATE TABLE public.instrutor (
    id            uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id     uuid NOT NULL,
    nome          character varying(200) NOT NULL,
    rg            character varying(30),
    orgao_emissor character varying(30),
    cpf           character varying(20),
    cha           character varying(60),
    ativo         boolean DEFAULT true NOT NULL,
    created_at    timestamp with time zone DEFAULT now() NOT NULL,
    updated_at    timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT instrutor_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_instrutor_tenant ON public.instrutor (tenant_id, ativo);

ALTER TABLE public.instrutor ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.instrutor FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_instrutor ON public.instrutor
    USING ((tenant_id = public.get_current_tenant_id()));

ALTER TABLE public.reserva_habilitacao
    ADD COLUMN IF NOT EXISTS instrutor_id uuid;
