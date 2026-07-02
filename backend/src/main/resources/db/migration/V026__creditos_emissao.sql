-- ============================================================================
-- V026: Créditos de emissão pré-pagos — ledger append-only por tenant
--
-- Modelo de negócio: 1 crédito por documento emitido com destino à Marinha
-- (via EMA). Sem saldo, a emissão é bloqueada. Adesão (aprovação do tenant)
-- credita o valor configurado (jetski.creditos.adesao). Super admin lança
-- ajustes com auditoria.
--
-- Anti-fraude:
--   - ledger append-only (trigger proíbe UPDATE/DELETE — histórico imutável)
--   - saldo_apos corrente em cada linha (adulteração quebra a cadeia)
--   - 1 grant de ADESAO por tenant e 1 CONSUMO por documento (uniques parciais)
-- ============================================================================

CREATE TABLE public.credito_lancamento (
    id            uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES public.tenant(id) ON DELETE RESTRICT,
    tipo          varchar(20) NOT NULL CHECK (tipo IN ('ADESAO', 'AJUSTE', 'CONSUMO', 'ESTORNO')),
    quantidade    integer NOT NULL CHECK (quantidade <> 0),
    saldo_apos    integer NOT NULL,
    referencia_id uuid,                 -- documento_emitido.id no CONSUMO
    motivo        varchar(200),         -- obrigatório em AJUSTE (validado no service)
    criado_por    uuid,                 -- actor (admin no AJUSTE, operador no CONSUMO)
    created_at    timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.credito_lancamento IS 'Ledger append-only de créditos de emissão por tenant';

CREATE UNIQUE INDEX ux_credito_adesao_unica ON public.credito_lancamento (tenant_id) WHERE tipo = 'ADESAO';
CREATE UNIQUE INDEX ux_credito_consumo_por_doc ON public.credito_lancamento (referencia_id) WHERE tipo = 'CONSUMO';
CREATE INDEX idx_credito_lancamento_tenant ON public.credito_lancamento (tenant_id, created_at);

-- Append-only: nenhum UPDATE/DELETE, nem por engano, nem por fraude aplicativa
CREATE OR REPLACE FUNCTION public.forbid_credito_lancamento_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'credito_lancamento é append-only: % não é permitido', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_credito_lancamento_append_only
    BEFORE UPDATE OR DELETE ON public.credito_lancamento
    FOR EACH ROW EXECUTE FUNCTION public.forbid_credito_lancamento_mutation();

ALTER TABLE public.credito_lancamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credito_lancamento FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_credito_lancamento ON public.credito_lancamento
    USING (tenant_id = public.get_current_tenant_id());
