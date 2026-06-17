-- =====================================================================
-- F2.4 — Aceite/assinatura presencial no balcão (evidências).
-- Append-only (trilha de evidências); o "atual" é o mais recente.
-- A imagem da assinatura é arquivada no storage (assinatura_s3_key).
-- =====================================================================

CREATE TABLE public.reserva_aceite (
    id                uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id         uuid NOT NULL,
    reserva_id        uuid NOT NULL,
    operador_id       uuid,
    metodo            character varying(20) NOT NULL,        -- SIGNATURE_PAD | PAPEL
    assinatura_s3_key character varying(500),
    hash_sha256       character varying(64),                  -- hash da imagem da assinatura
    ip                character varying(64),
    user_agent        text,
    origem            character varying(20) DEFAULT 'BALCAO' NOT NULL,
    aceito_em         timestamp with time zone DEFAULT now() NOT NULL,
    created_at        timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT reserva_aceite_pkey PRIMARY KEY (id),
    CONSTRAINT reserva_aceite_reserva_fk FOREIGN KEY (reserva_id)
        REFERENCES public.reserva(id) ON DELETE CASCADE,
    CONSTRAINT reserva_aceite_metodo_check
        CHECK ((metodo)::text = ANY (ARRAY['SIGNATURE_PAD'::text, 'PAPEL'::text]))
);

CREATE INDEX idx_reserva_aceite_tenant_reserva ON public.reserva_aceite (tenant_id, reserva_id);

ALTER TABLE public.reserva_aceite ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reserva_aceite FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_reserva_aceite ON public.reserva_aceite
    USING ((tenant_id = public.get_current_tenant_id()));
