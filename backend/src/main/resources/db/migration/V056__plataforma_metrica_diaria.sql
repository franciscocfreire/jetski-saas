-- =====================================================================
-- Read model da plataforma (F4): um snapshot por empresa por dia.
--
-- O problema que resolve: não existe visão consolidada. O padrão atual
-- (PlatformFaturaService.pendentesConferencia) itera TODOS os tenants
-- re-setando app.tenant_id a cada volta — funciona com dezenas de empresas,
-- não sustenta um dashboard com vários indicadores.
--
-- Tabela de PLATAFORMA: sem RLS. tenant_id aqui é a DIMENSÃO do agregado, não
-- o dono da linha — o console lê tudo de uma vez, sem varrer empresa a empresa
-- e sem abrir bypass de RLS em tabela operacional.
--
-- O job recalcula uma JANELA MÓVEL (últimos dias) em vez de só ontem: locação
-- editada, fechamento tardio e estorno mudam o passado recente.
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.plataforma_metrica_diaria (
    tenant_id             uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    dia                   date NOT NULL,

    -- ---- operacional: fatos DO dia (data_check_in / created_at) ----
    locacoes              integer NOT NULL DEFAULT 0,
    reservas              integer NOT NULL DEFAULT 0,
    no_shows              integer NOT NULL DEFAULT 0,
    receita_bruta         numeric(12,2) NOT NULL DEFAULT 0,
    -- RN04: base de comissão exclui combustível
    receita_comissionavel numeric(12,2) NOT NULL DEFAULT 0,

    -- ---- emissão (metering) ----
    emissoes_documento    integer NOT NULL DEFAULT 0,
    emissoes_gru          integer NOT NULL DEFAULT 0,
    emissoes_previa       integer NOT NULL DEFAULT 0,

    -- ---- créditos ----
    creditos_consumidos   integer NOT NULL DEFAULT 0,
    saldo_creditos_fim    integer NOT NULL DEFAULT 0,

    -- ---- assinatura ----
    -- MRR = preço do plano VIGENTE naquele dia (derivável do histórico de assinatura).
    mrr                   numeric(12,2) NOT NULL DEFAULT 0,
    plano_nome            varchar(100),

    -- ---- estado no momento do cálculo (NÃO é histórico fiel) ----
    -- Fatura em aberto é estado, não evento: reconstruir "quantas estavam abertas
    -- em 12/jul" exigiria log de transição que não existe. Só a linha MAIS RECENTE
    -- é confiável para estes dois campos — é a que o dashboard mostra.
    faturas_abertas       integer NOT NULL DEFAULT 0,
    valor_em_aberto       numeric(12,2) NOT NULL DEFAULT 0,

    atualizado_em         timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, dia)
);

CREATE INDEX IF NOT EXISTS idx_metrica_diaria_dia
    ON public.plataforma_metrica_diaria (dia DESC);

COMMENT ON TABLE public.plataforma_metrica_diaria IS
    'Read model da plataforma: agregado diário por empresa. Sem RLS — tenant_id é dimensão, não dono. Populado por PlataformaMetricasJob (janela móvel).';
COMMENT ON COLUMN public.plataforma_metrica_diaria.faturas_abertas IS
    'Estado no momento do cálculo, não histórico: só a linha mais recente é confiável.';
