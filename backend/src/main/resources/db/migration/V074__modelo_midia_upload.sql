-- V074: fotos de modelo enviadas por upload.
--
-- A imagem enviada pelo backoffice fica no storage da empresa
-- ({tenant}/modelos/{modelo}/midias/{midia}.ext) e é servida publicamente por
-- GET /api/v1/public/midias/{tenant}/{midia} — a coluna url guarda esse caminho,
-- então marketplace, vitrine e portal continuam lendo url como antes.
-- storage_key NULL = mídia por URL externa (comportamento anterior).

ALTER TABLE public.modelo_midia ADD COLUMN IF NOT EXISTS storage_key varchar(512);
ALTER TABLE public.modelo_midia ADD COLUMN IF NOT EXISTS tamanho_bytes integer;

COMMENT ON COLUMN public.modelo_midia.storage_key IS
    'Chave no storage quando a imagem foi enviada por upload; NULL = URL externa';
COMMENT ON COLUMN public.modelo_midia.tamanho_bytes IS
    'Tamanho em bytes do arquivo enviado por upload';
