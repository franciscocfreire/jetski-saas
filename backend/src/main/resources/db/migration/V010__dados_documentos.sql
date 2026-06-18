-- =====================================================================
-- V010 — Campos para preenchimento dos documentos NORMAM-212 (balcão)
-- ---------------------------------------------------------------------
-- Sem OCR: os dados que aparecem nos anexos (Declaração de Residência 1-C e
-- Autodeclaração de Saúde 5-C) são preenchidos manualmente no atendimento.
--   cliente: rg/órgão emissor (identidade), nacionalidade, naturalidade
--   reserva_habilitacao: uso de lentes / aparelho auditivo (5-C)
-- =====================================================================

ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS rg            character varying(30);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS orgao_emissor character varying(30);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS nacionalidade character varying(60);
ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS naturalidade  character varying(120);

ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS usa_lentes   boolean DEFAULT false NOT NULL;
ALTER TABLE public.reserva_habilitacao ADD COLUMN IF NOT EXISTS usa_aparelho boolean DEFAULT false NOT NULL;
