-- V039: registrar o RESULTADO do envio dos e-mails da emissão
--
-- O envio (Marinha + cliente) é best-effort e o resultado se perdia no log —
-- o módulo GRUs precisa responder "o e-mail à Marinha foi enviado?".
-- null = não enviado, falha OU emissão anterior a este registro.

ALTER TABLE documento_emitido ADD COLUMN IF NOT EXISTS marinha_enviado_em timestamptz;
ALTER TABLE documento_emitido ADD COLUMN IF NOT EXISTS cliente_enviado_em timestamptz;

COMMENT ON COLUMN documento_emitido.marinha_enviado_em IS
    'Último envio à Marinha com sucesso (null = não enviado ou emissão anterior ao registro)';
COMMENT ON COLUMN documento_emitido.cliente_enviado_em IS
    'Último envio ao cliente com sucesso (null = não enviado ou emissão anterior ao registro)';
