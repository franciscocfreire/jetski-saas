-- V038: confirmação da CHA-MTA-E pela devolutiva da Marinha
--
-- A Marinha responde MANUALMENTE por e-mail à loja após a emissão; o staff
-- anexa a devolutiva (PDF) na reserva de origem. Enquanto não confirmada, a
-- temporária aparece como "aguardando confirmação" e NÃO é elegível para
-- reuso em novas reservas.

ALTER TABLE reserva_habilitacao ADD COLUMN IF NOT EXISTS marinha_confirmada_em  timestamptz;
ALTER TABLE reserva_habilitacao ADD COLUMN IF NOT EXISTS marinha_confirmada_por uuid;
ALTER TABLE reserva_habilitacao ADD COLUMN IF NOT EXISTS cha_mtae_s3_key        text;

COMMENT ON COLUMN reserva_habilitacao.marinha_confirmada_em IS
    'Quando a loja anexou a devolutiva da Marinha (null = aguardando confirmação)';
COMMENT ON COLUMN reserva_habilitacao.cha_mtae_s3_key IS
    'PDF da CHA-MTA-E confirmada devolvida pela Marinha (por reserva)';
