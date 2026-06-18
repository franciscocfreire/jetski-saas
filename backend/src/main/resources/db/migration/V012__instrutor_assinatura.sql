-- =====================================================================
-- V012 — Assinatura e data de emissão do instrutor (Anexo 5-B-1)
-- ---------------------------------------------------------------------
-- O Atestado de Demonstração (5-B-1) traz a assinatura do instrutor e a
-- data de emissão da identidade. A assinatura é capturada no cadastro e
-- arquivada no storage (key); embutida no PDF na emissão.
-- =====================================================================

ALTER TABLE public.instrutor ADD COLUMN IF NOT EXISTS data_emissao    date;
ALTER TABLE public.instrutor ADD COLUMN IF NOT EXISTS assinatura_s3_key character varying(500);
