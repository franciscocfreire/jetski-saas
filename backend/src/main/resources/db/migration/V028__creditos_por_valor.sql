-- ============================================================================
-- V028: Compra de créditos calculada por VALOR (R$) com preço unitário
--        configurável pelo super admin.
--
-- - plataforma_config: chave-valor global da plataforma (sem tenant/RLS);
--   primeira chave: creditos_preco_unitario (R$ por crédito)
-- - credito_compra ganha valor_pago e preco_unitario (snapshot do preço no
--   momento da solicitação — mudança de preço não afeta pedidos pendentes)
-- ============================================================================

CREATE TABLE public.plataforma_config (
    chave      varchar(60) NOT NULL PRIMARY KEY,
    valor      text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid
);

COMMENT ON TABLE public.plataforma_config IS 'Configurações globais da plataforma (ex.: preço do crédito de emissão)';

INSERT INTO public.plataforma_config (chave, valor) VALUES ('creditos_preco_unitario', '5.00');

ALTER TABLE public.credito_compra
    ADD COLUMN valor_pago     numeric(10,2),
    ADD COLUMN preco_unitario numeric(10,2);
