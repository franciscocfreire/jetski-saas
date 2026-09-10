-- V066: tipifica e normaliza o documento do cliente.
--
-- Até aqui `documento` era texto livre e a busca do balcão comparava a string
-- crua: "847.215.903-50" e "84721590350" eram duas pessoas diferentes para o
-- sistema. Isso gerava ficha duplicada a cada operador que digitasse noutro
-- formato e, pior, contornava a trava anti-takeover do criarPreConta (que usa
-- o mesmo match exato). Além disso não havia como buscar um estrangeiro: o
-- tipo do documento só aparecia três passos depois, no checkbox `estrangeiro`.
--
-- Passa a valer: o valor é guardado NORMALIZADO (dígitos para CPF/CNPJ,
-- alfanumérico maiúsculo para passaporte) e a formatação é assunto de
-- apresentação. O índice único fica para depois — ver a nota no fim.

ALTER TABLE public.cliente
    ADD COLUMN IF NOT EXISTS documento_tipo varchar(12);

-- Inferência do acervo existente pelo formato do que está gravado.
UPDATE public.cliente
   SET documento_tipo = CASE
        WHEN documento ~ '[A-Za-z]'                                    THEN 'PASSAPORTE'
        WHEN length(regexp_replace(documento, '[^0-9]', '', 'g')) = 11 THEN 'CPF'
        WHEN length(regexp_replace(documento, '[^0-9]', '', 'g')) = 14 THEN 'CNPJ'
        -- Só dígitos mas nem 11 nem 14: CPF digitado errado, não passaporte.
        -- Fica NULL (não tipificado) em vez de chutar — chutar PASSAPORTE
        -- ligaria `estrangeiro` e o ofício sairia com os anexos em inglês.
        ELSE NULL
       END
 WHERE documento_tipo IS NULL
   AND documento IS NOT NULL
   AND btrim(documento) <> '';

-- Normalização do valor. É esta linha que faz as duas grafias do mesmo CPF
-- colapsarem numa só.
UPDATE public.cliente
   SET documento = regexp_replace(documento, '[^0-9]', '', 'g')
 WHERE documento_tipo IN ('CPF', 'CNPJ');

UPDATE public.cliente
   SET documento = upper(regexp_replace(documento, '[^A-Za-z0-9]', '', 'g'))
 WHERE documento_tipo = 'PASSAPORTE';

ALTER TABLE public.cliente
    DROP CONSTRAINT IF EXISTS cliente_documento_tipo_check;
ALTER TABLE public.cliente
    ADD CONSTRAINT cliente_documento_tipo_check
    CHECK (documento_tipo IS NULL OR documento_tipo IN ('CPF', 'CNPJ', 'PASSAPORTE'));

-- Passaporte implica estrangeiro (anexos 5-B em inglês). A recíproca NÃO vale:
-- estrangeiro residente tem CPF e continua precisando dos anexos em inglês —
-- por isso só ligamos a flag, nunca desligamos.
UPDATE public.cliente
   SET estrangeiro = true
 WHERE documento_tipo = 'PASSAPORTE'
   AND estrangeiro IS DISTINCT FROM true;

-- Busca do balcão: (tenant, tipo, documento). Não-único de propósito.
CREATE INDEX IF NOT EXISTS idx_cliente_tenant_documento
    ON public.cliente (tenant_id, documento_tipo, documento)
 WHERE documento IS NOT NULL;

-- A unicidade real fica para uma migration seguinte: normalizar pode revelar
-- duplicatas que já existiam disfarçadas pela formatação, e um CREATE UNIQUE
-- INDEX otimista aqui derrubaria o deploy. Avisamos e seguimos.
DO $$
DECLARE grupos int;
BEGIN
    SELECT count(*) INTO grupos FROM (
        SELECT tenant_id, documento_tipo, documento
          FROM public.cliente
         WHERE documento IS NOT NULL AND btrim(documento) <> ''
         GROUP BY 1, 2, 3
        HAVING count(*) > 1
    ) d;

    IF grupos > 0 THEN
        RAISE WARNING 'V066: % grupo(s) de clientes com documento duplicado após a normalização. '
                      'Concilie as fichas antes de criar o índice único.', grupos;
    END IF;
END $$;

COMMENT ON COLUMN public.cliente.documento_tipo IS
    'CPF | CNPJ | PASSAPORTE. Define como o documento é buscado, rotulado no ofício '
    'à Capitania (NORMAM-212 5.4.2) e nomeado no PDF anexo.';
COMMENT ON COLUMN public.cliente.documento IS
    'Valor NORMALIZADO: só dígitos (CPF/CNPJ) ou alfanumérico maiúsculo (passaporte). '
    'A formatação é aplicada na apresentação, nunca no armazenamento.';
