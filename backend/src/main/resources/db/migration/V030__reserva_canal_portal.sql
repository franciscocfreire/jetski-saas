-- =====================================================================
-- P1 (Portal do Cliente) — canal de origem da reserva
--
-- Distingue reservas criadas pelo staff (BALCAO) das criadas pelo próprio
-- cliente no portal (PORTAL). O job de expiração de pré-reserva (24h sem
-- pagamento) só alcança o canal PORTAL — reservas de balcão seguem a
-- semântica de no-show existente (expira_em = início + tolerância).
-- =====================================================================

ALTER TABLE public.reserva
    ADD COLUMN IF NOT EXISTS canal varchar(20) NOT NULL DEFAULT 'BALCAO';

ALTER TABLE public.reserva DROP CONSTRAINT IF EXISTS reserva_canal_check;
ALTER TABLE public.reserva
    ADD CONSTRAINT reserva_canal_check
        CHECK ((canal)::text = ANY (ARRAY['BALCAO'::text, 'PORTAL'::text]));

CREATE INDEX IF NOT EXISTS idx_reserva_canal_pagamento
    ON public.reserva (tenant_id, canal, pagamento_status)
    WHERE ativo = true;
