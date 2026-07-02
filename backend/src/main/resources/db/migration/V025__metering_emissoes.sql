-- ============================================================================
-- V025: Metering de emissões por tenant (base para cobrança futura por plano)
--
-- Uma linha por fato de uso:
--   DOCUMENTO — emissão consolidada de documentos (EmissaoService.emitir);
--               referencia_id = documento_emitido.id
--   GRU       — geração real de GRU na Marinha (PIX ou boleto; custo real);
--               referencia_id = reserva_habilitacao.id (regeneração legítima
--               conta de novo — ocorrido_em diferente)
--   PREVIA    — prévia gerada (não cobrável; sinal antifraude prévias × emissões);
--               referencia_id = reserva.id
--
-- Backfill futuro de DOCUMENTO (se decidido) — v1 conta do zero:
--   INSERT INTO emissao_uso (tenant_id, tipo, referencia_id, ocorrido_em)
--   SELECT tenant_id, 'DOCUMENTO', id, emitido_em FROM documento_emitido
--   ON CONFLICT DO NOTHING;
-- ============================================================================

CREATE TABLE public.emissao_uso (
    id            uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    tipo          varchar(20) NOT NULL CHECK (tipo IN ('DOCUMENTO', 'GRU', 'PREVIA')),
    referencia_id uuid NOT NULL,
    destinos      varchar(60),
    ocorrido_em   timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.emissao_uso IS 'Metering de emissões por tenant (cobrança futura por plano)';
COMMENT ON COLUMN public.emissao_uso.destinos IS 'DOCUMENTO: "marinha,cliente" | GRU: PIX/BOLETO | PREVIA: destino da prévia';

-- Idempotência do listener assíncrono: reprocesso do mesmo evento não duplica;
-- regeneração legítima (ocorrido_em novo) conta.
CREATE UNIQUE INDEX ux_emissao_uso_ref ON public.emissao_uso (tipo, referencia_id, ocorrido_em);
CREATE INDEX idx_emissao_uso_tenant_data ON public.emissao_uso (tenant_id, ocorrido_em);

ALTER TABLE public.emissao_uso ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emissao_uso FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_emissao_uso ON public.emissao_uso
    USING (tenant_id = public.get_current_tenant_id());

-- Prepara enforcement futuro nos planos (-1 = ilimitado; preço vem depois)
UPDATE public.plano SET limites = limites || '{"emissoes_mes": -1}'::jsonb;
