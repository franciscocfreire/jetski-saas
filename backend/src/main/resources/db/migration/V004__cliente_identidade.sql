-- =====================================================================
-- F1.B — Identidade do cliente: origem/status da conta + provider
-- Cliente é tenant-scoped (diferente de usuario_identity_provider, global).
-- =====================================================================

-- ---- cliente: origem + status da conta ------------------------------
ALTER TABLE public.cliente
    ADD COLUMN origem       character varying(20) NOT NULL DEFAULT 'PORTAL',
    ADD COLUMN status_conta character varying(20) NOT NULL DEFAULT 'SEM_LOGIN';

ALTER TABLE public.cliente
    ADD CONSTRAINT cliente_origem_check
        CHECK ((origem)::text = ANY (ARRAY['PORTAL'::text, 'BALCAO'::text])),
    ADD CONSTRAINT cliente_status_conta_check
        CHECK ((status_conta)::text = ANY (ARRAY['PRE_CONTA'::text, 'CONVIDADA'::text, 'ATIVA'::text, 'SEM_LOGIN'::text]));

-- ---- cliente_identity_provider (tenant-scoped, com RLS) -------------
-- 1 cliente <-> 1 identidade; 1 sub (provider_user_id) pode mapear a vários
-- clientes (1 por tenant), por isso o unique inclui tenant_id.
CREATE TABLE public.cliente_identity_provider (
    id               uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id        uuid NOT NULL,
    cliente_id       uuid NOT NULL,
    provider         character varying(50) NOT NULL,
    provider_user_id character varying(255) NOT NULL,
    linked_at        timestamp with time zone DEFAULT now() NOT NULL,
    created_at       timestamp with time zone DEFAULT now() NOT NULL,
    updated_at       timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT cliente_identity_provider_pkey PRIMARY KEY (id),
    CONSTRAINT cliente_identity_provider_cliente_fk FOREIGN KEY (cliente_id)
        REFERENCES public.cliente(id) ON DELETE CASCADE,
    CONSTRAINT cliente_identity_provider_cliente_uq UNIQUE (cliente_id),
    CONSTRAINT cliente_identity_provider_provider_uq UNIQUE (tenant_id, provider, provider_user_id)
);
CREATE INDEX idx_cliente_identity_provider_lookup
    ON public.cliente_identity_provider (provider, provider_user_id);

ALTER TABLE public.cliente_identity_provider ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cliente_identity_provider FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_cliente_identity_provider ON public.cliente_identity_provider
    USING ((tenant_id = public.get_current_tenant_id()));
