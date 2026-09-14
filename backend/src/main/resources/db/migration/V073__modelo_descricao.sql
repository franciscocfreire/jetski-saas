-- V073: descrição livre do modelo, escrita pela empresa no cadastro e exibida no
-- marketplace (/embarcacao/{id}) e no portal do cliente. NULL = a página usa o
-- texto genérico de antes.

ALTER TABLE public.modelo ADD COLUMN IF NOT EXISTS descricao text;
