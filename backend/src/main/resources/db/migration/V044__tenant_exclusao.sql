-- =====================================================================
-- Exclusão de empresa (super admin) — Fase 3 do plano reset/exclusão.
--
-- Fluxo padrão: excluir = suspende na hora + agenda o expurgo definitivo
-- para D+30 (prazo dos Termos de Uso), cancelável no período. O expurgo
-- apaga todos os dados/arquivos e deixa a linha do tenant como TOMBSTONE
-- (status EXCLUIDO, slug renomeado p/ liberar reuso, campos sensíveis
-- anonimizados) — DELETE do tenant é impossível por design: o ledger de
-- créditos (append-only, trigger) referencia tenant com FK RESTRICT.
-- =====================================================================

ALTER TABLE public.tenant ADD COLUMN IF NOT EXISTS exclusao_agendada_em timestamptz;
ALTER TABLE public.tenant ADD COLUMN IF NOT EXISTS excluido_em timestamptz;

COMMENT ON COLUMN public.tenant.exclusao_agendada_em IS
    'Quando o expurgo definitivo deve rodar (job diário); null = sem exclusão agendada';
COMMENT ON COLUMN public.tenant.excluido_em IS
    'Quando o expurgo foi executado (tombstone); null = não expurgado';
