-- =====================================================================
-- V013 — Marca de cliente estrangeiro (emissão bilíngue do Anexo 5-B)
-- ---------------------------------------------------------------------
-- Quando o locatário é estrangeiro, o consolidado inclui também as
-- versões em inglês dos anexos 5-B (5-B-3 e 5-B-4), além das em português.
-- =====================================================================

ALTER TABLE public.cliente ADD COLUMN IF NOT EXISTS estrangeiro boolean DEFAULT false NOT NULL;
