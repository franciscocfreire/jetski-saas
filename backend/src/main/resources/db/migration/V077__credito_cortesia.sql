-- =====================================================================
-- V077: crédito de CORTESIA no ledger de créditos de emissão.
--
-- Até aqui crédito dado de graça entrava como AJUSTE — o mesmo tipo da
-- correção de erro. Não dava para responder "quanto crédito a plataforma
-- deu" × "quanto vendeu", nem quanto um piloto custou em emissões.
--
--   AJUSTE   → só correção (positiva; a negativa já vira ESTORNO)
--   CORTESIA → concessão gratuita, sempre positiva, motivo obrigatório
--
-- condicao_id: vínculo OPCIONAL com a condição comercial (V076) que motivou
-- a cortesia (ex.: o piloto). Referência lógica, sem FK: o ledger é
-- append-only (trigger bloqueia UPDATE), então um ON DELETE SET NULL
-- quebraria; a existência e o tenant são validados no CreditoService.
--
-- O histórico não é reclassificado: ledger imutável, AJUSTE antigo fica AJUSTE.
-- =====================================================================

ALTER TABLE public.credito_lancamento DROP CONSTRAINT IF EXISTS credito_lancamento_tipo_check;
ALTER TABLE public.credito_lancamento
    ADD CONSTRAINT credito_lancamento_tipo_check
    CHECK (tipo IN ('ADESAO', 'AJUSTE', 'CORTESIA', 'CONSUMO', 'ESTORNO'));

ALTER TABLE public.credito_lancamento DROP CONSTRAINT IF EXISTS credito_lancamento_cortesia_positiva;
ALTER TABLE public.credito_lancamento
    ADD CONSTRAINT credito_lancamento_cortesia_positiva
    CHECK (tipo <> 'CORTESIA' OR quantidade > 0);

ALTER TABLE public.credito_lancamento ADD COLUMN IF NOT EXISTS condicao_id uuid;

COMMENT ON COLUMN public.credito_lancamento.condicao_id IS
    'Condição comercial (V076) que motivou a CORTESIA. Referência lógica (sem FK: ledger append-only).';

-- ---- read model: créditos concedidos × vendidos (fatos do dia) ----
ALTER TABLE public.plataforma_metrica_diaria
    ADD COLUMN IF NOT EXISTS creditos_cortesia integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS creditos_vendidos integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN public.plataforma_metrica_diaria.creditos_cortesia IS
    'Créditos concedidos de graça no dia (ADESAO + CORTESIA).';
COMMENT ON COLUMN public.plataforma_metrica_diaria.creditos_vendidos IS
    'Créditos vendidos no dia (compras APROVADAS, pela data da decisão).';
