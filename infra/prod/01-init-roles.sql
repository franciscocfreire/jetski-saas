-- =============================================================================
-- Bootstrap idempotente do role de aplicação (PRODUÇÃO)
-- Roda como superuser `jetski` no banco jetski_prod, ANTES e DEPOIS das migrations.
-- Cria o role jetski_app (sem SUPERUSER/BYPASSRLS) e concede privilégios.
-- Seguro de rodar em todo deploy (não destrutivo).
-- Senha via psql -v app_pwd=...  (interpolada FORA de dollar-quote → usa \gexec).
-- =============================================================================

-- Cria o role só se não existir (CREATE não aceita IF NOT EXISTS p/ ROLE).
SELECT format('CREATE ROLE jetski_app LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS', :'app_pwd')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jetski_app')
\gexec

-- Mantém a senha/atributos em sincronia (idempotente).
SELECT format('ALTER ROLE jetski_app LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS', :'app_pwd')
\gexec

GRANT USAGE ON SCHEMA public TO jetski_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jetski_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jetski_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jetski_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO jetski_app;
