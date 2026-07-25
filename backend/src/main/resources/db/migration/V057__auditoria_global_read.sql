-- =====================================================================
-- Leitura da auditoria GLOBAL pelo console (F5).
--
-- A V051 criou a policy de INSERT para linhas sem tenant (eventos de
-- plataforma: merge de CPF, concessão de acesso, sessão de suporte), mas
-- deliberadamente NÃO expôs a leitura: "leitura de linhas globais fica
-- restrita a acesso administrativo direto ao banco".
--
-- Com o console, isso deixou de servir — a trilha era gravada e ninguém
-- conseguia lê-la pela aplicação. Sem esta policy, /v1/platform/auditoria
-- devolveria SEMPRE vazio: a única policy de SELECT é
-- tenant_id = get_current_tenant_id(), e NULL nunca casa com nada.
--
-- ESCOPO ESTREITO de propósito. Policies permissivas somam com OR (a
-- auditoria RLS de 10/jul mordeu exatamente nisso), então esta precisa ser
-- inofensiva quando combinada:
--   - só linhas SEM tenant (tenant_id IS NULL) — nunca dado de empresa;
--   - só com app.unrestricted = 'true', GUC que o TenantAwareDataSource
--     seta a partir do TenantContext apenas para operador de plataforma;
--   - só SELECT.
-- =====================================================================

DROP POLICY IF EXISTS auditoria_global_read ON public.auditoria;

CREATE POLICY auditoria_global_read ON public.auditoria
    FOR SELECT
    USING (
        tenant_id IS NULL
        AND current_setting('app.unrestricted', true) = 'true'
    );

COMMENT ON POLICY auditoria_global_read ON public.auditoria IS
    'Console da plataforma lê a trilha global (tenant_id NULL). Escopo estreito: nunca expõe linha de empresa, mesmo somando com as demais policies.';
