-- V078: pré-visualização dos documentos (Prévia Marinha / Prévia Cliente) vira módulo
-- próprio (PREVIA_DOCUMENTOS), vendável por plano.
--
-- Até aqui qualquer plano com emissão à Marinha (própria ou delegada) via a prévia do PDF
-- no balcão e na agenda. Agora a prévia exige o módulo de emissão E o PREVIA_DOCUMENTOS
-- (enforcement no EmissaoService; o path já é gateado pelos módulos de emissão).
--
-- Planos que já emitiam continuam com a prévia: ganham PREVIA_DOCUMENTOS.
-- modulos NULL (todos) segue liberando tudo.

UPDATE public.plano
   SET modulos = modulos || '["PREVIA_DOCUMENTOS"]'::jsonb
 WHERE (modulos @> '["EMISSAO_PROPRIA"]'::jsonb OR modulos @> '["EMISSAO_DELEGADA"]'::jsonb)
   AND NOT modulos @> '["PREVIA_DOCUMENTOS"]'::jsonb;
