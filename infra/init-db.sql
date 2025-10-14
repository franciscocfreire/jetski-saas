-- Inicialização do banco de dados PostgreSQL para Jetski SaaS

-- Criar schema do Keycloak (separado do schema da aplicação)
CREATE SCHEMA IF NOT EXISTS keycloak;

-- Criar extensões necessárias
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Configurar timezone padrão
SET timezone = 'America/Sao_Paulo';

-- Log de inicialização
DO $$
BEGIN
    RAISE NOTICE 'Database jetski_dev initialized successfully';
    RAISE NOTICE 'Extensions created: uuid-ossp, pgcrypto';
    RAISE NOTICE 'Schema created: keycloak';
    RAISE NOTICE 'Default timezone: America/Sao_Paulo';
END $$;
