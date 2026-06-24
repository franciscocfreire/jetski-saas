-- Boleto da GRU (PDF gerado via atualiza_gru.asp → imprime_gru.asp) armazenado no storage.
ALTER TABLE reserva_habilitacao
  ADD COLUMN IF NOT EXISTS gru_pdf_s3_key text;
