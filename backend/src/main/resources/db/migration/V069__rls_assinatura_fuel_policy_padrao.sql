-- =============================================================================
-- V069: RLS de assinatura e fuel_policy no padrão do projeto
-- =============================================================================
-- P0 da revisão técnica (revisao-2026-08-21, cap. 2, achado 2.2). As duas tabelas
-- eram as únicas operacionais fora do padrão tenant_isolation, com três defeitos:
--
--   1. INSERT ... WITH CHECK (true): qualquer sessão da aplicação, mesmo com o
--      tenant A fixado, inseria linha com tenant_id do tenant B — a RLS deixava de
--      ser backstop de escrita (fuel_policy = cobrança de combustível; assinatura
--      = plano/status comercial da empresa).
--   2. SELECT com COALESCE(current_setting(...)::uuid, tenant_id): em contexto
--      nulo a condição virava tenant_id = tenant_id e TODAS as linhas de todos os
--      tenants ficavam legíveis.
--   3. Cast direto de current_setting(...)::uuid sem NULLIF: com o GUC em '' (após
--      RESET numa conexão reusada do pool) a query estourava 22P02 — o gotcha do
--      ''::uuid, contornado caso a caso no código (PlanoLimiteService etc.).
--
-- Padrão novo (igual a modelo, os_manutencao, despesa_*): uma policy ALL com
-- USING e WITH CHECK explícitos pelo tenant da sessão, com NULLIF. Sem contexto =
-- nenhuma linha visível e nenhuma escrita.
--
-- Quem escreve/lê fora de request de tenant fixa o contexto antes, por transação
-- (set_config(..., true)): PlatformTenantService (aprovação/listagem),
-- PlatformFaturaService, PlatformLimiteService, PlanoLimiteService.modulosDoPlano,
-- TrialExpirationService, PlataformaMetricasService, TenantImportService e o
-- signup (TenantSignupService.createDefaultFuelPolicy). Seeds/migrations rodam
-- como superuser (dono), que não é afetado.
--
-- Espelhado (idempotente) no reset-ambiente-dev.sh, seção 7.3.
-- =============================================================================

-- ---------------------------------------------------------------- assinatura
DROP POLICY IF EXISTS assinatura_tenant_select ON public.assinatura;
DROP POLICY IF EXISTS assinatura_tenant_insert ON public.assinatura;
DROP POLICY IF EXISTS assinatura_tenant_update ON public.assinatura;
DROP POLICY IF EXISTS assinatura_tenant_delete ON public.assinatura;
DROP POLICY IF EXISTS tenant_isolation_assinatura ON public.assinatura;

CREATE POLICY tenant_isolation_assinatura ON public.assinatura
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE public.assinatura ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assinatura FORCE ROW LEVEL SECURITY;

-- --------------------------------------------------------------- fuel_policy
DROP POLICY IF EXISTS fuel_policy_tenant_select ON public.fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_insert ON public.fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_update ON public.fuel_policy;
DROP POLICY IF EXISTS fuel_policy_tenant_delete ON public.fuel_policy;
DROP POLICY IF EXISTS tenant_isolation_fuel_policy ON public.fuel_policy;

CREATE POLICY tenant_isolation_fuel_policy ON public.fuel_policy
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE public.fuel_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fuel_policy FORCE ROW LEVEL SECURITY;
