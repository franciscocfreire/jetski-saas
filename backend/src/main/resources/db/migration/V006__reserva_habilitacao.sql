-- =====================================================================
-- F2.3 — Habilitação do condutor: CHA existente OU emissão CHA-MTA-E (EMA) + GRU.
-- 1:1 com reserva. GRU manual no v1 (operador informa nº/valor/marca paga).
-- =====================================================================

CREATE TABLE public.reserva_habilitacao (
    id               uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id        uuid NOT NULL,
    reserva_id       uuid NOT NULL,
    via              character varying(10) NOT NULL,          -- CHA | EMA
    -- Via CHA (já habilitado)
    cha_categoria    character varying(40),
    cha_numero       character varying(60),
    cha_validade     date,
    -- Via EMA (emissão CHA-MTA-E)
    videoaula_em     timestamp with time zone,
    anexo_saude      boolean DEFAULT false NOT NULL,           -- 5-C
    anexo_regras     boolean DEFAULT false NOT NULL,           -- 5-B
    anexo_residencia boolean DEFAULT false NOT NULL,           -- 1-C (condicional)
    -- GRU (taxa da Marinha) — manual no v1
    gru_numero       character varying(60),
    gru_valor        numeric(10,2),
    gru_pago         boolean DEFAULT false NOT NULL,
    gru_pago_em      timestamp with time zone,
    -- Resolução (CHA coletada OU GRU paga)
    resolvida        boolean DEFAULT false NOT NULL,
    created_at       timestamp with time zone DEFAULT now() NOT NULL,
    updated_at       timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT reserva_habilitacao_pkey PRIMARY KEY (id),
    CONSTRAINT reserva_habilitacao_reserva_uq UNIQUE (reserva_id),
    CONSTRAINT reserva_habilitacao_reserva_fk FOREIGN KEY (reserva_id)
        REFERENCES public.reserva(id) ON DELETE CASCADE,
    CONSTRAINT reserva_habilitacao_via_check
        CHECK ((via)::text = ANY (ARRAY['CHA'::text, 'EMA'::text]))
);

CREATE INDEX idx_reserva_habilitacao_tenant ON public.reserva_habilitacao (tenant_id, reserva_id);

ALTER TABLE public.reserva_habilitacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reserva_habilitacao FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_reserva_habilitacao ON public.reserva_habilitacao
    USING ((tenant_id = public.get_current_tenant_id()));
