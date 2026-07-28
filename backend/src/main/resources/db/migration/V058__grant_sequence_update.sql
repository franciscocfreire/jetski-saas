-- V058: UPDATE nas sequences para jetski_app.
--
-- O import de arquivamento (TenantImportService) restaura linhas com os IDs
-- originais do zip e precisa de setval() para realinhar as sequences
-- (abastecimento, fuel_policy, fuel_price_day, membro) — setval exige
-- privilégio UPDATE, que o grant original (USAGE, SELECT) não cobre.
--
-- Condicional: o role só existe em dev/CI/prod (criado fora do Flyway);
-- no Testcontainers a suíte roda como superuser e o role não está lá.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'jetski_app') THEN
        GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO jetski_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public
            GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO jetski_app;
    END IF;
END
$$;
