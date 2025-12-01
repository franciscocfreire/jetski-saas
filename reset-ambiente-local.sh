#!/bin/bash

###############################################################################
# Reset Completo do Ambiente LOCAL - Jetski SaaS
# (PostgreSQL porta 5433 + Keycloak porta 8081)
###############################################################################

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"

# Configuracoes do ambiente LOCAL
PG_HOST="localhost"
PG_PORT="5433"
PG_USER="jetski"           # Usuario da aplicacao (SEM BYPASSRLS - RLS ativo)
PG_ADMIN_USER="jetski_admin" # Usuario admin para migrations (COM BYPASSRLS)
PG_PASSWORD="dev123"
PG_DB_LOCAL="jetski_local"

KC_HOME="/home/franciscocfreire/apps/keycloak-26.4.1"
KC_HOST="localhost"
KC_PORT="8081"
KC_ADMIN_USER="admin"
KC_ADMIN_PASSWORD="admin"
KC_REALM="jetski-saas"
KEYCLOAK_SETUP_DONE=false
KC_AVAILABLE=false

echo "========================================"
echo "  RESET AMBIENTE LOCAL - Jetski SaaS"
echo "========================================"
echo ""
echo "Configuracoes:"
echo "   PostgreSQL: ${PG_HOST}:${PG_PORT}"
echo "   Keycloak:   ${KC_HOST}:${KC_PORT}"
echo "   Database:   ${PG_DB_LOCAL}"
echo ""

# 1. Parar backend se estiver rodando
echo "1. Parando backend..."
lsof -ti:8090 | xargs kill -9 2>/dev/null || true
sleep 1
echo "   OK - Backend parado!"

# 2. Parar Keycloak se estiver rodando
echo "2. Parando Keycloak..."
pkill -f "keycloak" 2>/dev/null && sleep 3 || true
echo "   OK - Keycloak parado!"

# 3. Limpar cache do Redis
echo "3. Limpando cache do Redis..."
if command -v redis-cli &> /dev/null; then
    redis-cli FLUSHDB > /dev/null 2>&1 && echo "   OK - Cache do Redis limpo!" || echo "   AVISO - Nao foi possivel limpar o Redis"
else
    echo "   AVISO - redis-cli nao encontrado, pulando limpeza do Redis"
fi

# 4. Verificar se PostgreSQL esta acessivel
echo "4. Verificando conexao com PostgreSQL..."
if ! PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d postgres -c "SELECT 1;" > /dev/null 2>&1; then
    echo "   ERRO: PostgreSQL na porta $PG_PORT nao esta acessivel!"
    echo "   Certifique-se de que o PostgreSQL esta rodando em $PG_HOST:$PG_PORT"
    exit 1
fi
echo "   OK - PostgreSQL esta acessivel!"

# 5. Dropar e recriar banco jetski_local
echo "5. Recriando banco de dados ${PG_DB_LOCAL}..."
sudo -u postgres psql -d postgres << EOF
-- Terminar todas as conexoes ativas ao banco
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '${PG_DB_LOCAL}' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS ${PG_DB_LOCAL};
CREATE DATABASE ${PG_DB_LOCAL} OWNER ${PG_USER};

-- Criar usuario admin para migrations (se nao existir)
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${PG_ADMIN_USER}') THEN
        CREATE ROLE ${PG_ADMIN_USER} WITH LOGIN PASSWORD '${PG_PASSWORD}' BYPASSRLS;
    ELSE
        -- Garantir que tem BYPASSRLS
        ALTER ROLE ${PG_ADMIN_USER} BYPASSRLS;
    END IF;
END
\$\$;

-- Garantir permissoes para ambos usuarios
GRANT ALL PRIVILEGES ON DATABASE ${PG_DB_LOCAL} TO ${PG_ADMIN_USER};
GRANT ALL PRIVILEGES ON DATABASE ${PG_DB_LOCAL} TO ${PG_USER};

-- Usuario da aplicacao NAO pode bypassar RLS (seguranca multi-tenant)
ALTER ROLE ${PG_USER} NOBYPASSRLS;
EOF

# Conectar ao banco criado para dar permissoes no schema public
sudo -u postgres psql -d ${PG_DB_LOCAL} << EOF
-- Dar permissao no schema public para ambos usuarios
GRANT ALL ON SCHEMA public TO ${PG_ADMIN_USER};
GRANT ALL ON SCHEMA public TO ${PG_USER};
GRANT CREATE ON SCHEMA public TO ${PG_ADMIN_USER};
GRANT CREATE ON SCHEMA public TO ${PG_USER};
EOF
echo "   OK - Banco ${PG_DB_LOCAL} recriado!"
echo "   OK - Usuario ${PG_ADMIN_USER} configurado com BYPASSRLS!"
echo "   OK - Usuario ${PG_USER} configurado com NOBYPASSRLS!"

# 6. Iniciar Keycloak com PostgreSQL
echo "6. Iniciando Keycloak com PostgreSQL..."
cd "$KC_HOME"

# Build se necessario
if [ ! -d "lib/lib/main" ] || [ -z "$(ls -A lib/lib/main/org.postgresql.postgresql-*.jar 2>/dev/null)" ]; then
    echo "   Building Keycloak com PostgreSQL driver..."
    ./bin/kc.sh build --db=postgres > /dev/null 2>&1
    echo "   OK - Build concluido"
fi

# Iniciar Keycloak em background
KC_DB=postgres \
KC_DB_URL="jdbc:postgresql://${PG_HOST}:${PG_PORT}/${PG_DB_LOCAL}" \
KC_DB_USERNAME=$PG_USER \
KC_DB_PASSWORD=$PG_PASSWORD \
KEYCLOAK_ADMIN=$KC_ADMIN_USER \
KEYCLOAK_ADMIN_PASSWORD=$KC_ADMIN_PASSWORD \
./bin/kc.sh start-dev --http-port=$KC_PORT > /tmp/keycloak-postgres.log 2>&1 &

KEYCLOAK_PID=$!
echo "   PID: $KEYCLOAK_PID"

# Aguardar Keycloak ficar pronto (ate 90 segundos)
echo "   Aguardando Keycloak inicializar..."
for i in {1..45}; do
    if curl -sf http://${KC_HOST}:${KC_PORT}/realms/master > /dev/null 2>&1; then
        echo ""
        echo "   OK - Keycloak iniciado com sucesso!"
        KC_AVAILABLE=true
        break
    fi
    printf "."
    sleep 2
done

if [ "$KC_AVAILABLE" != true ]; then
    echo ""
    echo "   ERRO: Keycloak nao inicializou em tempo!"
    echo "   Verifique: tail -50 /tmp/keycloak-postgres.log"
    exit 1
fi

cd "$SCRIPT_DIR"

# 7. Rodar migrations do Flyway (com usuario admin que tem BYPASSRLS)
echo "7. Executando migrations do banco..."
cd "$BACKEND_DIR"
# Usar usuario admin para migrations (tem BYPASSRLS para inserir seed data)
# baselineOnMigrate com baselineVersion=0 para executar todas as migrations
# (necessario porque Keycloak ja criou tabelas no schema public)
mvn flyway:migrate \
    -Dflyway.user=${PG_ADMIN_USER} \
    -Dflyway.password=${PG_PASSWORD} \
    -Dflyway.baselineOnMigrate=true \
    -Dflyway.baselineVersion=0 \
    -q
echo "   OK - Migrations executadas com usuario ${PG_ADMIN_USER}!"

# 8. Garantir permissoes nas tabelas criadas pelas migrations
echo "8. Configurando permissoes nas tabelas..."
sudo -u postgres psql -d ${PG_DB_LOCAL} << EOF > /dev/null
-- Garantir permissoes em todas as tabelas existentes para ambos usuarios
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ${PG_ADMIN_USER};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ${PG_USER};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ${PG_ADMIN_USER};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ${PG_USER};
-- Default para futuras tabelas
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ${PG_ADMIN_USER};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ${PG_USER};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO ${PG_ADMIN_USER};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO ${PG_USER};
EOF
echo "   OK - Permissoes configuradas!"

# 8.5. Verificar migrations aplicadas
echo "8.5. Verificando migrations aplicadas..."
MIGRATION_COUNT=$(PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB_LOCAL -t -c "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true;" | xargs)
echo "   OK - ${MIGRATION_COUNT} migrations aplicadas com sucesso!"

cd "$SCRIPT_DIR"

# 9. Configurar Keycloak (realm, usuarios)
if [ "$KC_AVAILABLE" = true ]; then
    echo "9. Configurando Keycloak (realm, usuarios)..."
    bash "$SCRIPT_DIR/infra/keycloak-setup/setup-keycloak-local.sh"
    KEYCLOAK_SETUP_DONE=true
else
    echo "9. PULANDO configuracao do Keycloak (nao disponivel)"
    KEYCLOAK_SETUP_DONE=false
fi

# 10. Adicionar client mobile no Keycloak
if [ "$KEYCLOAK_SETUP_DONE" = true ]; then
    echo ""
    echo "10. Adicionando client mobile no Keycloak..."
    bash "$SCRIPT_DIR/infra/keycloak-setup/add-mobile-client.sh"
    echo "   OK - Client mobile configurado!"
else
    echo "10. PULANDO configuracao do client mobile"
fi

# 11. Adicionar client backoffice no Keycloak
if [ "$KEYCLOAK_SETUP_DONE" = true ]; then
    echo ""
    echo "11. Adicionando client backoffice no Keycloak..."
    bash "$SCRIPT_DIR/infra/keycloak-setup/add-backoffice-client.sh"
    echo "   OK - Client backoffice configurado!"
else
    echo "11. PULANDO configuracao do client backoffice"
fi

# 11.5. Criar usuário multi-tenant no Keycloak
if [ "$KEYCLOAK_SETUP_DONE" = true ]; then
    echo ""
    echo "11.5. Criando usuario multi-tenant no Keycloak..."
    bash "$SCRIPT_DIR/infra/keycloak-setup/create-multi-tenant-user.sh"
    echo "   OK - Usuario multi-tenant criado!"
else
    echo "11.5. PULANDO criacao do usuario multi-tenant"
fi

# 12. Criar mapeamentos de identity provider
if [ "$KEYCLOAK_SETUP_DONE" = true ]; then
    echo ""
    echo "12. Criando mapeamentos de identity provider..."

    # Aguardar um pouco para Keycloak processar
    sleep 2

    # Obter token de admin
    ADMIN_TOKEN=$(curl -s -X POST "http://${KC_HOST}:${KC_PORT}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "username=${KC_ADMIN_USER}" \
      -d "password=${KC_ADMIN_PASSWORD}" \
      -d "grant_type=password" \
      -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

    if [ -z "$ADMIN_TOKEN" ]; then
        echo "   AVISO - Nao foi possivel obter token de admin do Keycloak"
        echo "   Execute manualmente: bash infra/keycloak-setup/update-keycloak-uuids.sh"
    else
        # Array com os usuarios principais (incluindo multi-tenant)
        USERS=("admin@acme.com" "gerente@acme.com" "operador@acme.com" "vendedor@acme.com" "mecanico@acme.com" "gerente.multi@example.com")

        # Primeiro, deletar todos os mapeamentos antigos
        for EMAIL in "${USERS[@]}"; do
            PG_USER_ID=$(PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB_LOCAL -t -c "SELECT id FROM usuario WHERE email = '${EMAIL}' LIMIT 1;" | xargs)
            if [ -n "$PG_USER_ID" ]; then
                PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB_LOCAL -c "DELETE FROM usuario_identity_provider WHERE usuario_id = '${PG_USER_ID}' AND provider = 'keycloak';" > /dev/null
            fi
        done

        # Criar os novos mapeamentos
        for EMAIL in "${USERS[@]}"; do
            # Buscar UUID no PostgreSQL
            PG_USER_ID=$(PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB_LOCAL -t -c "SELECT id FROM usuario WHERE email = '${EMAIL}' LIMIT 1;" | xargs)

            if [ -n "$PG_USER_ID" ]; then
                # Buscar UUID no Keycloak
                KC_USER_ID=$(curl -s -X GET "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/users?username=${EMAIL}" \
                  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

                if [ -n "$KC_USER_ID" ]; then
                    # Inserir mapeamento
                    PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB_LOCAL << EOF > /dev/null
INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at, created_at, updated_at)
VALUES ('${PG_USER_ID}', 'keycloak', '${KC_USER_ID}', NOW(), NOW(), NOW());
EOF
                    echo "   OK - ${EMAIL} -> KC: ${KC_USER_ID:0:8}..."
                else
                    echo "   AVISO - ${EMAIL} nao encontrado no Keycloak"
                fi
            else
                echo "   AVISO - ${EMAIL} nao encontrado no PostgreSQL"
            fi
        done

        echo "   OK - Mapeamentos de identity provider criados!"

        # Limpar cache do Redis novamente
        if command -v redis-cli &> /dev/null; then
            redis-cli FLUSHDB > /dev/null 2>&1 && echo "   OK - Cache do Redis atualizado!" || true
        fi
    fi
else
    echo "12. PULANDO criacao de mapeamentos (Keycloak nao configurado)"
fi

# 13. Exibir dados seed
echo ""
echo "13. Verificando dados seed..."
PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB_LOCAL << EOF
\echo '   Tenants cadastrados:'
SELECT '      ' || slug || ' - ' || razao_social FROM tenant;
\echo ''
\echo '   Usuarios cadastrados:'
SELECT '      ' || email || ' (' || nome || ')' FROM usuario LIMIT 10;
\echo ''
\echo '   Jetskis cadastrados:'
SELECT '      ' || j.serie || ' - ' || j.status FROM jetski j LIMIT 10;
\echo ''
\echo '   Mapeamentos identity provider:'
SELECT '      ' || u.email || ' -> ' || uip.provider || ':' || SUBSTRING(uip.provider_user_id, 1, 8) || '...'
FROM usuario_identity_provider uip
JOIN usuario u ON u.id = uip.usuario_id;
EOF

echo ""
echo "========================================"
echo "  RESET FINALIZADO COM SUCESSO!"
echo "========================================"
echo ""
echo "Proximos passos:"
echo "   1. cd backend"
echo "   2. SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
echo ""
echo "URLs do ambiente LOCAL:"
echo "   - Backend:   http://localhost:8090/api"
echo "   - Keycloak:  http://localhost:8081 (admin/admin)"
echo "   - PostgreSQL: localhost:5433 (jetski/dev123)"
echo "   - Redis:     localhost:6379"
echo ""
echo "Usuarios de teste (senha: test123):"
echo "   - admin@acme.com (ADMIN_TENANT)"
echo "   - gerente@acme.com (GERENTE)"
echo "   - operador@acme.com (OPERADOR)"
echo "   - vendedor@acme.com (VENDEDOR)"
echo "   - mecanico@acme.com (MECANICO)"
echo ""
