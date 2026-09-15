-- V073: informações do modelo escritas pela empresa e exibidas no marketplace
-- (/embarcacao/{id}) e no portal do cliente.
--
-- descricao: texto livre. NULL = a página usa o texto genérico de antes.
-- duracao_minima_min: locação mínima em minutos. NULL = sem mínimo (o card
-- "Mínimo" some e o portal oferece a partir de 1h).

ALTER TABLE public.modelo ADD COLUMN IF NOT EXISTS descricao text;
ALTER TABLE public.modelo ADD COLUMN IF NOT EXISTS duracao_minima_min integer;

ALTER TABLE public.modelo DROP CONSTRAINT IF EXISTS modelo_duracao_minima_positiva;
ALTER TABLE public.modelo
    ADD CONSTRAINT modelo_duracao_minima_positiva
    CHECK (duracao_minima_min IS NULL OR duracao_minima_min > 0);
