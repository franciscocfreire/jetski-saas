-- =====================================================================
-- Emissão delegada — passos 2–5 (EMISSAO_DELEGADA_SPEC §11):
--
-- 1) vinculo_emissao: parceria operadora × EAMA emissora. SEM tenant_id
--    único — RLS DUPLA (cada lado enxerga a linha em que participa).
--    MVP: no máximo 1 vínculo não-terminal por operadora (unique parcial).
-- 2) emissao_delegada: espelho no tenant EMISSOR de cada documento
--    emitido em nome dele (trilha legal da EAMA — preservada no reset da
--    operadora; FKs com SET NULL/sem FK para sobreviver a reset/exclusão
--    da operadora).
-- 3) documento_emitido: snapshot do emissor congelado na emissão.
-- 4) emissao_uso: dimensão emissor_tenant_id (relatório por vínculo).
-- =====================================================================

CREATE TABLE public.vinculo_emissao (
    id                   uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_operador_id   uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    tenant_emissor_id    uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    status               varchar(12) NOT NULL DEFAULT 'CONVIDADO'
                         CHECK (status IN ('CONVIDADO', 'ATIVO', 'BLOQUEADO', 'REVOGADO')),
    convidado_por_tenant uuid NOT NULL,   -- lado que convidou (aceite é do outro lado)
    convidado_por        uuid,            -- usuário que convidou
    convidado_em         timestamptz NOT NULL DEFAULT now(),
    aceito_por           uuid,
    aceito_em            timestamptz,
    termo_aceite_em      timestamptz,
    termo_texto          text,            -- snapshot do termo de responsabilidade aceito
    bloqueado_em         timestamptz,     -- kill switch da EAMA (§4.3)
    revogado_por         uuid,
    revogado_em          timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vinculo_emissao_lados_distintos CHECK (tenant_operador_id <> tenant_emissor_id)
);
COMMENT ON TABLE public.vinculo_emissao IS
    'Parceria de emissão delegada (EMISSAO_DELEGADA_SPEC §3.3); RLS dupla — visível aos dois lados';

-- MVP: 1 vínculo não-terminal por operadora (multi-emissor é v2)
CREATE UNIQUE INDEX ux_vinculo_emissao_operador_vivo
    ON public.vinculo_emissao (tenant_operador_id)
    WHERE status IN ('CONVIDADO', 'ATIVO', 'BLOQUEADO');
CREATE INDEX idx_vinculo_emissao_emissor ON public.vinculo_emissao (tenant_emissor_id, status);

ALTER TABLE public.vinculo_emissao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vinculo_emissao FORCE ROW LEVEL SECURITY;
-- Uma policy só, com OR explícito dos dois lados (evita o gotcha de policies
-- permissivas somando mais largas que o par — auditoria RLS 10/jul)
CREATE POLICY vinculo_emissao_partes ON public.vinculo_emissao
    USING (tenant_operador_id = public.get_current_tenant_id()
        OR tenant_emissor_id = public.get_current_tenant_id());

CREATE TABLE public.emissao_delegada (
    id                  uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id           uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,  -- EMISSOR
    vinculo_id          uuid REFERENCES public.vinculo_emissao(id) ON DELETE SET NULL,
    documento_id        uuid REFERENCES public.documento_emitido(id) ON DELETE SET NULL,
    documento_hash      varchar(64),
    s3_key              text,             -- PDF da Marinha (para reenvio/baixa)
    operadora_tenant_id uuid NOT NULL,    -- sem FK: espelho sobrevive à exclusão da operadora
    operadora_nome      varchar(200),
    condutor_nome       varchar(200),
    condutor_cpf        varchar(20),
    instrutor_id        uuid,
    instrutor_nome      varchar(200),
    gru_numero          varchar(40),
    emitido_em          timestamptz NOT NULL,
    reenviado_em        timestamptz,
    reenviado_para      varchar(255),
    created_at          timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE public.emissao_delegada IS
    'Espelho no tenant EMISSOR de cada documento emitido em nome dele (trilha legal da EAMA)';

CREATE UNIQUE INDEX ux_emissao_delegada_documento
    ON public.emissao_delegada (documento_id) WHERE documento_id IS NOT NULL;
CREATE INDEX idx_emissao_delegada_tenant_data ON public.emissao_delegada (tenant_id, emitido_em);
CREATE INDEX idx_emissao_delegada_tenant_operadora
    ON public.emissao_delegada (tenant_id, operadora_tenant_id);

ALTER TABLE public.emissao_delegada ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emissao_delegada FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_emissao_delegada ON public.emissao_delegada
    USING (tenant_id = public.get_current_tenant_id());

-- Snapshot do emissor congelado no documento (auditoria imutável)
ALTER TABLE public.documento_emitido ADD COLUMN emissor_tenant_id uuid;
ALTER TABLE public.documento_emitido ADD COLUMN emissor_snapshot jsonb;
COMMENT ON COLUMN public.documento_emitido.emissor_tenant_id IS
    'Tenant da EAMA emissora quando a emissão foi delegada; NULL = emissão própria';

-- Metering: dimensão do emissor (relatório por vínculo sai de graça)
ALTER TABLE public.emissao_uso ADD COLUMN emissor_tenant_id uuid;
