-- V065: status do envio dos documentos emitidos (Marinha e cliente).
--
-- Até aqui o único registro era marinha_enviado_em / cliente_enviado_em, e NULL
-- era ambíguo: "não se aplica" (habilitação por CHA), "bloqueado por documentação
-- incompleta", "falhou no SMTP" e "ainda não saiu" colapsavam no mesmo valor.
-- Com o envio saindo do request (assíncrono), a tela precisa distinguir "enviando"
-- de "falhou" — e o operador precisa saber o motivo.
--
-- Os timestamps CONTINUAM: status é estado, timestamp é fato. GruConsultaService e
-- ReservaFichaService leem os timestamps e não mudam. Migration puramente aditiva.

ALTER TABLE public.documento_emitido
    ADD COLUMN IF NOT EXISTS marinha_envio_status varchar(20),
    ADD COLUMN IF NOT EXISTS cliente_envio_status varchar(20),
    ADD COLUMN IF NOT EXISTS marinha_envio_erro   text,
    ADD COLUMN IF NOT EXISTS cliente_envio_erro   text,
    ADD COLUMN IF NOT EXISTS envio_atualizado_em  timestamptz;

COMMENT ON COLUMN public.documento_emitido.marinha_envio_status IS
    'NAO_APLICAVEL|BLOQUEADO|SEM_DESTINATARIO|PENDENTE|ENVIADO|FALHOU — estado do ofício à Capitania';
COMMENT ON COLUMN public.documento_emitido.cliente_envio_status IS
    'NAO_APLICAVEL|SEM_DESTINATARIO|PENDENTE|ENVIADO|FALHOU — estado do e-mail ao cliente';
COMMENT ON COLUMN public.documento_emitido.envio_atualizado_em IS
    'Última mudança de status de envio — usado para achar PENDENTE órfão (backend reiniciado)';

-- Colunas nullable e sem DEFAULT de propósito: linhas legadas têm estado
-- genuinamente desconhecido, e testes de integração fazem INSERT direto na tabela.
-- Sem CHECK de coerência status×timestamp: as linhas backfilladas não o satisfariam.
-- A invariante (ENVIADO <=> *_enviado_em IS NOT NULL) passa a ser garantida em
-- código, por um único escritor (DocumentoEnvioService).

-- Backfill: o que dá para afirmar das linhas existentes.
-- Marinha: enviada => ENVIADO; via CHA => NAO_APLICAVEL (nunca houve ofício);
-- resto => FALHOU, com o motivo registrado como desconhecido em vez de inventado.
UPDATE public.documento_emitido d SET
    cliente_envio_status = CASE WHEN d.cliente_enviado_em IS NOT NULL THEN 'ENVIADO' ELSE 'FALHOU' END,
    marinha_envio_status = CASE
        WHEN d.marinha_enviado_em IS NOT NULL THEN 'ENVIADO'
        WHEN h.via = 'CHA'                    THEN 'NAO_APLICAVEL'
        ELSE 'FALHOU' END,
    cliente_envio_erro = CASE WHEN d.cliente_enviado_em IS NULL
        THEN 'Emissão anterior ao registro de status (V065) — motivo não registrado' END,
    marinha_envio_erro = CASE WHEN d.marinha_enviado_em IS NULL AND h.via IS DISTINCT FROM 'CHA'
        THEN 'Emissão anterior ao registro de status (V065) — motivo não registrado' END,
    envio_atualizado_em = COALESCE(d.marinha_enviado_em, d.cliente_enviado_em, d.emitido_em)
FROM public.reserva_habilitacao h
WHERE h.reserva_id = d.reserva_id
  AND d.cliente_envio_status IS NULL;

-- Documentos sem linha de habilitação (não deveria existir; o UPDATE ... FROM acima
-- os ignoraria em silêncio).
UPDATE public.documento_emitido SET
    cliente_envio_status = CASE WHEN cliente_enviado_em IS NOT NULL THEN 'ENVIADO' ELSE 'FALHOU' END,
    marinha_envio_status = CASE WHEN marinha_enviado_em IS NOT NULL THEN 'ENVIADO' ELSE 'FALHOU' END,
    envio_atualizado_em  = COALESCE(marinha_enviado_em, cliente_enviado_em, emitido_em)
WHERE cliente_envio_status IS NULL;

-- Índice parcial (minúsculo — só o que está na fila) para localizar envios que
-- ficaram PENDENTE, ex.: backend reiniciado antes do worker rodar.
CREATE INDEX IF NOT EXISTS idx_documento_emitido_envio_pendente
    ON public.documento_emitido (envio_atualizado_em)
    WHERE marinha_envio_status = 'PENDENTE' OR cliente_envio_status = 'PENDENTE';
