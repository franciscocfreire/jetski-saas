-- GRU gerada automaticamente (robô HTTP Marinha/PagTesouro) — dados do PIX.
-- gru_numero/gru_valor/gru_pago já existem (V006). Aqui guardamos o PIX e a expiração.
ALTER TABLE reserva_habilitacao
  ADD COLUMN IF NOT EXISTS gru_pix_copia_e_cola text,
  ADD COLUMN IF NOT EXISTS gru_pix_expiracao    timestamp,
  ADD COLUMN IF NOT EXISTS gru_id_marinha        varchar(40),
  ADD COLUMN IF NOT EXISTS gru_gerada_em         timestamp;
