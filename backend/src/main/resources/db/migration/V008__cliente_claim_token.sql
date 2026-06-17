-- =====================================================================
-- V008 — Claim-token de ativação de conta do cliente (balcão F2.7)
-- ---------------------------------------------------------------------
-- Permite que um cliente criado no balcão (PRE_CONTA) ative o próprio
-- login: o staff gera um token + senha temporária (enviados por e-mail/
-- SMS/WhatsApp); o cliente valida (endpoint público), o que provisiona o
-- usuário no Keycloak (role CLIENTE, SEM Membro) e vincula via
-- cliente_identity_provider. Append-only por reenvio: o token anterior é
-- desativado (ativo=false) e um novo é emitido.
-- =====================================================================

CREATE TABLE public.cliente_claim_token (
    id                      uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id               uuid NOT NULL,
    cliente_id              uuid NOT NULL,
    token                   character varying(64) NOT NULL,
    temporary_password_hash character varying(255) NOT NULL,
    canais                  character varying(100),
    expira_em               timestamp with time zone NOT NULL,
    usado_em                timestamp with time zone,
    ativo                   boolean DEFAULT true NOT NULL,
    criado_por              uuid,
    created_at              timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT cliente_claim_token_pkey PRIMARY KEY (id),
    CONSTRAINT cliente_claim_token_token_uq UNIQUE (token),
    CONSTRAINT cliente_claim_token_cliente_fk FOREIGN KEY (cliente_id)
        REFERENCES public.cliente(id) ON DELETE CASCADE
);

CREATE INDEX idx_cliente_claim_token_cliente
    ON public.cliente_claim_token (cliente_id, ativo);

ALTER TABLE public.cliente_claim_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cliente_claim_token FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_cliente_claim_token ON public.cliente_claim_token
    USING ((tenant_id = public.get_current_tenant_id()));
