-- =====================================================================
-- Backfill do folio: pagamentos confirmados ANTES do ledger.
--
-- O caminho remoto (confirmar-sinal, fila de sinais do portal) só passou a
-- lançar PAGAMENTO no folio junto com esta versão. Reservas confirmadas
-- antes ficaram com pagamento_status=CONFIRMADO e folio vazio — o estorno
-- não encontrava recebido e o fechamento por forma não via o dinheiro.
--
-- Regras: forma PIX para canal PORTAL (o pagamento remoto é PIX por
-- construção); OUTRO para o resto (forma real desconhecida). created_at
-- preserva a data do pagamento (regime de caixa do fechamento).
-- =====================================================================

INSERT INTO public.reserva_lancamento
    (tenant_id, reserva_id, tipo, forma, valor, observacao, registrado_por, created_at)
SELECT
    r.tenant_id,
    r.id,
    'PAGAMENTO',
    CASE WHEN r.canal = 'PORTAL' THEN 'PIX' ELSE 'OUTRO' END,
    COALESCE(r.valor_sinal, r.valor_total),
    'backfill V037 — pagamento confirmado antes do folio',
    r.pagamento_validado_por,
    COALESCE(r.sinal_pago_em, r.pagamento_validado_em, now())
FROM public.reserva r
WHERE r.pagamento_status = 'CONFIRMADO'
  AND COALESCE(r.valor_sinal, r.valor_total) > 0
  AND NOT EXISTS (
      SELECT 1 FROM public.reserva_lancamento l
      WHERE l.reserva_id = r.id AND l.tipo = 'PAGAMENTO'
  );
