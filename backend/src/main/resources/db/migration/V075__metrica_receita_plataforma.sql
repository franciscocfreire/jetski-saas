-- =====================================================================
-- V075: receita DA PLATAFORMA no read model (plataforma_metrica_diaria).
--
-- O card "Receita (30d)" do console somava locacao.valor_total — o que as
-- LOJAS movimentaram (GMV), não o que a plataforma recebeu. Faturas pagas e
-- venda de créditos não apareciam em lugar nenhum do dashboard.
--
--   receita_faturas  = SUM(fatura.valor) PAGA no dia (pago_em)
--   receita_creditos = SUM(credito_compra.valor_pago) APROVADA no dia (decidido_em)
--
-- Fatos do dia (como locacoes/receita_bruta): somáveis na janela.
-- =====================================================================

ALTER TABLE public.plataforma_metrica_diaria
    ADD COLUMN IF NOT EXISTS receita_faturas  numeric(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS receita_creditos numeric(12,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN public.plataforma_metrica_diaria.receita_bruta IS
    'Movimentado pelas lojas (SUM locacao.valor_total) — GMV, não receita da plataforma.';
COMMENT ON COLUMN public.plataforma_metrica_diaria.receita_faturas IS
    'Receita da plataforma: faturas de assinatura PAGAS no dia.';
COMMENT ON COLUMN public.plataforma_metrica_diaria.receita_creditos IS
    'Receita da plataforma: compras de créditos APROVADAS no dia (valor_pago).';
