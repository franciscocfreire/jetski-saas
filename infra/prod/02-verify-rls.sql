-- =============================================================================
-- Guarda de segurança (PRODUÇÃO): falha o deploy se alguma tabela com coluna
-- `tenant_id` NÃO tiver Row Level Security habilitado. Evita vazamento entre
-- tenants por uma migration NOVA que esqueceu de ligar RLS.
-- Roda como superuser após as migrations. Erro → deploy.sh aborta.
--
-- ALLOWLIST (exceções pré-existentes, isenção consciente):
--   - membro, tenant_access, tenant_signup: cross-tenant POR DESIGN (auth/
--     onboarding são consultadas ANTES do contexto de tenant ser fixado).
--   - reserva_config: PK = tenant_id, acesso sempre por findById(tenant) →
--     risco prático baixo. Candidata a endurecer com RLS no futuro.
-- Qualquer tabela com tenant_id FORA desta lista e sem RLS aborta o deploy.
-- =============================================================================
DO $$
DECLARE
    faltando text;
    allow text[] := ARRAY['membro', 'tenant_access', 'tenant_signup', 'reserva_config'];
BEGIN
    SELECT string_agg(c.relname, ', ' ORDER BY c.relname)
      INTO faltando
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'public'
       AND c.relkind = 'r'
       AND c.relrowsecurity = false
       AND NOT (c.relname = ANY(allow))
       AND EXISTS (
            SELECT 1 FROM information_schema.columns col
             WHERE col.table_schema = 'public'
               AND col.table_name = c.relname
               AND col.column_name = 'tenant_id'
       );

    IF faltando IS NOT NULL THEN
        RAISE EXCEPTION 'RLS DESABILITADO em tabela(s) multi-tenant nova(s): % (adicione RLS na migration ou justifique na allowlist do 02-verify-rls.sql)', faltando;
    END IF;

    RAISE NOTICE 'RLS OK: todas as tabelas com tenant_id têm RLS (exceto allowlist consciente).';
END
$$;
