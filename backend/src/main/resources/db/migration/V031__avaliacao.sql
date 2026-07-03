-- =====================================================================
-- P4 (Portal do Cliente) — avaliações de locação
--
-- 1 avaliação por locação (nota 1-5 + comentário), feita pelo cliente no
-- portal após a locação FINALIZADA. A média por modelo aparece na vitrine
-- pública do marketplace — por isso a policy adicional de SELECT público
-- (nota/comentário são conteúdo público por natureza; nenhum dado pessoal
-- do cliente é exposto pelos endpoints).
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.avaliacao (
    id          uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    locacao_id  uuid NOT NULL REFERENCES public.locacao(id) ON DELETE CASCADE,
    cliente_id  uuid NOT NULL,
    modelo_id   uuid NOT NULL,
    nota        integer NOT NULL,
    comentario  text,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT avaliacao_locacao_uq UNIQUE (locacao_id),
    CONSTRAINT avaliacao_nota_check CHECK (nota BETWEEN 1 AND 5)
);

CREATE INDEX IF NOT EXISTS idx_avaliacao_tenant_modelo
    ON public.avaliacao (tenant_id, modelo_id);
CREATE INDEX IF NOT EXISTS idx_avaliacao_modelo
    ON public.avaliacao (modelo_id);

ALTER TABLE public.avaliacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.avaliacao FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_avaliacao ON public.avaliacao;
CREATE POLICY tenant_isolation_avaliacao ON public.avaliacao
    USING (tenant_id = public.get_current_tenant_id());

-- Vitrine pública (média/lista por modelo) — somente leitura
DROP POLICY IF EXISTS avaliacao_public_read ON public.avaliacao;
CREATE POLICY avaliacao_public_read ON public.avaliacao
    FOR SELECT USING (true);
