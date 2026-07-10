-- =====================================================================
-- Segurança (pré-lançamento): RLS na tabela tenant.
--
-- A tabela-raiz guarda segredos por loja (smtp_password, cnpj, contatos)
-- e nunca teve RLS — qualquer lookup sem filtro explícito enxergava
-- todos os tenants. Backstop de banco:
--
--   - contexto NULO (signup, login/lista de empresas, marketplace
--     público, jobs globais): acesso liberado — esses fluxos rodam
--     ANTES de existir tenant selecionado, por design (mesma isenção
--     documentada para membro/tenant_access no 02-verify-rls.sql);
--   - contexto FIXADO (request tenant-scoped): só a própria linha;
--   - superadmin de plataforma: GUC app.unrestricted = 'true', setada
--     pelo TenantAwareDataSource a partir do TenantFilter (opera com
--     X-Tenant-Id mas lista/gerencia todos os tenants).
--
-- A marketplace_public_read da V001 (inerte até aqui — policy sem RLS
-- habilitada não faz nada) é REMOVIDA: a vitrine pública roda sem
-- contexto (ramo nulo) e mantê-la permitiria a um request tenant-scoped
-- ler a linha inteira (incl. smtp_password) de outra loja visível no
-- marketplace.
-- =====================================================================

DROP POLICY IF EXISTS marketplace_public_read ON public.tenant;

ALTER TABLE public.tenant ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_tenant ON public.tenant;
CREATE POLICY tenant_isolation_tenant ON public.tenant
    USING (
        public.get_current_tenant_id() IS NULL
        OR id = public.get_current_tenant_id()
        OR current_setting('app.unrestricted', true) = 'true'
    );
