-- Verificação de pagamento do PIX (PagTesouro pix-stn/sonda) + comprovante PDF.
-- gru_id_sessao: idSessao do PagTesouro (consultado para saber se foi pago).
-- gru_comprovante_s3_key: PDF do comprovante gerado quando a situação = CONCLUIDO.
ALTER TABLE reserva_habilitacao
  ADD COLUMN IF NOT EXISTS gru_id_sessao          text,
  ADD COLUMN IF NOT EXISTS gru_comprovante_s3_key text;
