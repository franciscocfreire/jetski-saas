-- =====================================================================
-- F1.C — Configuração do tenant para emissão de documentos
-- marinha_email: destino do PDF consolidado (Capitania/contato), por loja.
-- pix_chave: chave PIX da loja (sinal/total manual + dados nos termos).
-- (Seed dos valores do tenant de dev fica no reset-ambiente-dev.sh.)
-- =====================================================================

ALTER TABLE public.tenant
    ADD COLUMN marinha_email character varying(255),
    ADD COLUMN pix_chave     character varying(140);
