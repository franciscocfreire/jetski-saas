-- V078: pré-visualização dos documentos (Prévia Marinha / Prévia Cliente) vira módulo
-- próprio (PREVIA_DOCUMENTOS), vendável por plano — e fim do "todos" implícito.
--
-- 1) Até aqui qualquer plano com emissão à Marinha (própria ou delegada) via a prévia do PDF
--    no balcão e na agenda. Agora a prévia exige o módulo de emissão E o PREVIA_DOCUMENTOS
--    (enforcement no EmissaoService; o path já é gateado pelos módulos de emissão).
--    Planos que já emitiam continuam com a prévia: ganham PREVIA_DOCUMENTOS.
--
-- 2) plano.modulos NULL significava "todos, inclusive os que forem criados depois" — um
--    módulo novo (pensado para vender à parte) entrava de graça nesses planos. A partir
--    daqui todo plano tem a lista EXPLÍCITA: os NULL viram o catálogo completo atual (sem
--    mudança de comportamento hoje) e o console grava exatamente o que está marcado.
--    Cada migration de módulo novo decide quais planos o recebem.
--    Mantenha esta lista igual ao enum ModuloPlano (ModuloPlanoIntegrationTest confere).

UPDATE public.plano
   SET modulos = modulos || '["PREVIA_DOCUMENTOS"]'::jsonb
 WHERE (modulos @> '["EMISSAO_PROPRIA"]'::jsonb OR modulos @> '["EMISSAO_DELEGADA"]'::jsonb)
   AND NOT modulos @> '["PREVIA_DOCUMENTOS"]'::jsonb;

UPDATE public.plano
   SET modulos = '["EMISSAO_PROPRIA","EMISSAO_DELEGADA","COMISSOES","MANUTENCAO","FECHAMENTOS",
                   "RELATORIOS","DESPESAS","MARKETPLACE","LOJA_ONLINE","RESERVA_ONLINE",
                   "VIDEO_ORIENTACAO","PREVIA_DOCUMENTOS"]'::jsonb
 WHERE modulos IS NULL;
