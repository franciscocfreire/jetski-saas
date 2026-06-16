-- =====================================================================
-- F1.A — Modelo de pagamento (sinal/total) + comprovante + documento emitido
-- Generaliza "sinal" para "pagamento". Mantém sinal_pago/valor_sinal por
-- retrocompatibilidade (derivados). RLS por tenant via get_current_tenant_id().
-- =====================================================================

-- ---- reserva: campos de pagamento + total + emissão -----------------
ALTER TABLE public.reserva
    ADD COLUMN pagamento_tipo            character varying(10),
    ADD COLUMN pagamento_status          character varying(20) NOT NULL DEFAULT 'AGUARDANDO',
    ADD COLUMN pagamento_valor_informado numeric(10,2),
    ADD COLUMN pagamento_validado_por    uuid,
    ADD COLUMN pagamento_validado_em     timestamp with time zone,
    ADD COLUMN pagamento_motivo_recusa   text,
    ADD COLUMN valor_total               numeric(10,2),
    ADD COLUMN documento_emitido_em      timestamp with time zone;

ALTER TABLE public.reserva
    ADD CONSTRAINT reserva_pagamento_tipo_check
        CHECK (pagamento_tipo IS NULL OR (pagamento_tipo)::text = ANY (ARRAY['SINAL'::text, 'TOTAL'::text])),
    ADD CONSTRAINT reserva_pagamento_status_check
        CHECK ((pagamento_status)::text = ANY (ARRAY['AGUARDANDO'::text, 'EM_ANALISE'::text, 'CONFIRMADO'::text, 'RECUSADO'::text]));

-- Backfill a partir do estado legado de sinal
UPDATE public.reserva
   SET pagamento_status          = CASE WHEN sinal_pago THEN 'CONFIRMADO' ELSE 'AGUARDANDO' END,
       pagamento_tipo            = CASE WHEN sinal_pago THEN 'SINAL' ELSE NULL END,
       pagamento_valor_informado = valor_sinal,
       pagamento_validado_em     = sinal_pago_em;

-- ---- reserva_comprovante: comprovante PIX (reserva remota) -----------
CREATE TABLE public.reserva_comprovante (
    id          uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id   uuid NOT NULL,
    reserva_id  uuid NOT NULL,
    s3_key      character varying(500) NOT NULL,
    url         text,
    hash_sha256 character varying(64),
    tipo        character varying(20) DEFAULT 'PIX' NOT NULL,
    enviado_em  timestamp with time zone DEFAULT now() NOT NULL,
    ativo       boolean DEFAULT true NOT NULL,
    created_at  timestamp with time zone DEFAULT now() NOT NULL,
    updated_at  timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT reserva_comprovante_pkey PRIMARY KEY (id),
    CONSTRAINT reserva_comprovante_reserva_fk FOREIGN KEY (reserva_id)
        REFERENCES public.reserva(id) ON DELETE CASCADE
);
CREATE INDEX idx_reserva_comprovante_tenant_reserva
    ON public.reserva_comprovante (tenant_id, reserva_id);

ALTER TABLE public.reserva_comprovante ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reserva_comprovante FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_reserva_comprovante ON public.reserva_comprovante
    USING ((tenant_id = public.get_current_tenant_id()));

-- ---- documento_emitido: PDF consolidado arquivado -------------------
CREATE TABLE public.documento_emitido (
    id          uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id   uuid NOT NULL,
    reserva_id  uuid NOT NULL,
    s3_key      character varying(500) NOT NULL,
    hash_sha256 character varying(64) NOT NULL,
    destinos    jsonb,
    emitido_em  timestamp with time zone DEFAULT now() NOT NULL,
    created_at  timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT documento_emitido_pkey PRIMARY KEY (id),
    CONSTRAINT documento_emitido_reserva_fk FOREIGN KEY (reserva_id)
        REFERENCES public.reserva(id) ON DELETE CASCADE
);
CREATE INDEX idx_documento_emitido_tenant_reserva
    ON public.documento_emitido (tenant_id, reserva_id);

ALTER TABLE public.documento_emitido ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.documento_emitido FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_documento_emitido ON public.documento_emitido
    USING ((tenant_id = public.get_current_tenant_id()));
