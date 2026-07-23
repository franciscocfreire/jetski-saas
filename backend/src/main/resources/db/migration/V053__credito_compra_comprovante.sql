-- ============================================================================
-- V053: Compra de créditos — comprovante PIX por upload (foto/PDF)
--
-- O tenant deixa de digitar o "número da transação PIX" e passa a anexar o
-- comprovante da transferência (imagem ou PDF, guardado no storage por tenant).
-- pix_txid vira opcional (histórico legado continua válido); a proteção
-- "mesmo comprovante não pode ser usado 2x" migra para o sha256 do arquivo.
-- ============================================================================

ALTER TABLE public.credito_compra ALTER COLUMN pix_txid DROP NOT NULL;

ALTER TABLE public.credito_compra
    ADD COLUMN comprovante_key          varchar(255),
    ADD COLUMN comprovante_content_type varchar(100),
    ADD COLUMN comprovante_sha256       varchar(64);

COMMENT ON COLUMN public.credito_compra.comprovante_key IS 'Key do comprovante PIX no storage ({tenant}/creditos/compras/{compra}/comprovante.ext)';
COMMENT ON COLUMN public.credito_compra.comprovante_sha256 IS 'SHA-256 do binário — dedupe: o mesmo comprovante não pode ser usado 2x pelo tenant';

-- Mesmo comprovante não pode ser usado duas vezes pelo mesmo tenant
CREATE UNIQUE INDEX ux_credito_compra_comprovante ON public.credito_compra (tenant_id, comprovante_sha256)
    WHERE comprovante_sha256 IS NOT NULL;
