-- V063: Videoaula obrigatória no balcão — prova de exibição.
-- A videoaula da Marinha passa a ser assistida num player integrado (step
-- "Orientações" do balcão). Além do carimbo videoaula_em (V006), registramos
-- COMO ela foi cumprida (PLAYER = término detectado pelo player; DECLARACAO =
-- checkbox/declaração manual — Termos legado, portal ou fallback do player)
-- e o idioma escolhido. Alimenta a página de auditoria do PDF.

ALTER TABLE public.reserva_habilitacao
    ADD COLUMN IF NOT EXISTS videoaula_modo   varchar(12),   -- PLAYER | DECLARACAO
    ADD COLUMN IF NOT EXISTS videoaula_idioma varchar(5);    -- pt | en | es

ALTER TABLE public.reserva_habilitacao
    DROP CONSTRAINT IF EXISTS reserva_habilitacao_videoaula_modo_check;
ALTER TABLE public.reserva_habilitacao
    ADD CONSTRAINT reserva_habilitacao_videoaula_modo_check
    CHECK (videoaula_modo IS NULL OR videoaula_modo IN ('PLAYER', 'DECLARACAO'));

COMMENT ON COLUMN public.reserva_habilitacao.videoaula_modo IS
    'Como a videoaula foi cumprida: PLAYER (término detectado no balcão) ou DECLARACAO (checkbox/declaração manual)';
