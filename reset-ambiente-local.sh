#!/bin/bash

###############################################################################
# Reset Completo do Ambiente LOCAL - Jetski SaaS
# (PostgreSQL porta 5433 + Keycloak porta 8081)
###############################################################################

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"

# Configurações do ambiente LOCAL
PG_HOST="localhost"
PG_PORT="5433"
PG_USER="jetski"
PG_PASSWORD="dev123"
PG_DB_LOCAL="jetski_local"

KC_HOST="localhost"
KC_PORT="8081"
KC_ADMIN_USER="admin"
KC_ADMIN_PASSWORD="admin"
KC_REALM="jetski-saas"
KEYCLOAK_SETUP_DONE=false

echo "🔄 INICIANDO RESET DO AMBIENTE LOCAL..."
echo ""
echo "📋 Configurações:"
echo "   PostgreSQL: ${PG_HOST}:${PG_PORT}"
echo "   Keycloak:   ${KC_HOST}:${KC_PORT}"
echo "   Database:   ${PG_DB_LOCAL}"
echo ""

# 1. Parar backend se estiver rodando
echo "1️⃣  Parando backend..."
lsof -ti:8090 | xargs kill -9 2>/dev/null || true
sleep 2

# 1.5. Limpar cache do Redis
echo "1️⃣.5 Limpando cache do Redis..."
if command -v redis-cli &> /dev/null; then
    redis-cli FLUSHDB > /dev/null 2>&1 && echo "   ✅ Cache do Redis limpo!" || echo "   ⚠️  Não foi possível limpar o Redis (pode não estar rodando)"
else
    echo "   ⚠️  redis-cli não encontrado, pulando limpeza do Redis"
fi

# 2. Verificar se PostgreSQL está acessível
echo "2️⃣  Verificando conexão com PostgreSQL..."
if ! PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d postgres -c "SELECT 1;" > /dev/null 2>&1; then
    echo "   ❌ ERRO: PostgreSQL na porta $PG_PORT não está acessível!"
    echo "   💡 Certifique-se de que o PostgreSQL está rodando em $PG_HOST:$PG_PORT"
    exit 1
fi
echo "   ✅ PostgreSQL está acessível!"

# 3. Dropar e recriar banco jetski_local
echo "3️⃣  Recriando banco de dados ${PG_DB_LOCAL}..."
PGPASSWORD=postgres psql -h $PG_HOST -p $PG_PORT -U postgres -d postgres << EOF
DROP DATABASE IF EXISTS ${PG_DB_LOCAL};
CREATE DATABASE ${PG_DB_LOCAL} OWNER ${PG_USER};
EOF
echo "   ✅ Banco ${PG_DB_LOCAL} recriado!"

# 4. Verificar se Keycloak está acessível
echo "4️⃣  Verificando conexão com Keycloak..."
if ! curl -sf http://${KC_HOST}:${KC_PORT}/realms/master > /dev/null 2>&1; then
    echo "   ⚠️  AVISO: Keycloak na porta $KC_PORT não está acessível!"
    echo "   💡 Para resetar o Keycloak, você precisa iniciá-lo manualmente"
    echo "   💡 Ou use o script: ./bin/kc.sh start"
    KC_AVAILABLE=false
else
    echo "   ✅ Keycloak está acessível!"
    KC_AVAILABLE=true
fi

# 5. Limpar Keycloak (se disponível)
if [ "$KC_AVAILABLE" = true ]; then
    echo "5️⃣  Resetando Keycloak realm ${KC_REALM}..."

    # Obter token de admin (usando grep/sed em vez de jq)
    TOKEN=$(curl -s -X POST "http://${KC_HOST}:${KC_PORT}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "username=${KC_ADMIN_USER}" \
      -d "password=${KC_ADMIN_PASSWORD}" \
      -d "grant_type=password" \
      -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | sed 's/"access_token":"//')

    if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
        # Deletar realm se existir
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
          "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}" \
          -H "Authorization: Bearer ${TOKEN}")

        if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "404" ]; then
            echo "   ✅ Realm ${KC_REALM} removido!"
        else
            echo "   ⚠️  Realm pode não ter sido removido (HTTP $HTTP_CODE)"
        fi
        echo "   💡 Execute o setup do Keycloak após o reset"
    else
        echo "   ⚠️  Não foi possível obter token de admin do Keycloak"
        echo "   💡 Verifique as credenciais: ${KC_ADMIN_USER}/${KC_ADMIN_PASSWORD}"
    fi
else
    echo "5️⃣  ⏭️  Pulando reset do Keycloak (não disponível)"
fi

# 6. Rodar migrations do Flyway
echo "6️⃣  Executando migrations do banco..."
cd "$BACKEND_DIR"
mvn flyway:migrate -q

# 7. Verificar migrations aplicadas
echo "7️⃣  Verificando migrations aplicadas..."
MIGRATION_COUNT=$(mvn flyway:info -q 2>/dev/null | grep -c "Success" || echo "0")
echo "   ✅ ${MIGRATION_COUNT} migrations aplicadas com sucesso!"

# 8. Configurar Keycloak (opcional)
if [ "$KC_AVAILABLE" = true ]; then
    echo "8️⃣  Configurando Keycloak..."
    echo ""
    echo "   Deseja executar o setup do Keycloak agora? (y/n)"
    read -r SETUP_KC

    if [ "$SETUP_KC" = "y" ] || [ "$SETUP_KC" = "Y" ]; then
        echo "   Executando setup do Keycloak..."
        bash "$SCRIPT_DIR/infra/keycloak-setup/setup-keycloak-local.sh"
        KEYCLOAK_SETUP_DONE=true
    else
        echo "   ⏭️  Pulando setup do Keycloak (você pode rodar manualmente depois)"
        KEYCLOAK_SETUP_DONE=false
    fi
else
    echo "8️⃣  ⏭️  Pulando configuração do Keycloak (não disponível)"
    KEYCLOAK_SETUP_DONE=false
fi

# 9. Criar mapeamentos de identity provider para todos os 5 usuários (se Keycloak foi configurado)
if [ "$KEYCLOAK_SETUP_DONE" = true ]; then
    echo ""
    echo "9️⃣  Criando mapeamentos de identity provider para todos os usuários..."

    # Aguardar um pouco para Keycloak processar
    sleep 2

    # Obter token de admin uma vez
    ADMIN_TOKEN=$(curl -s -X POST "http://${KC_HOST}:${KC_PORT}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "username=${KC_ADMIN_USER}" \
      -d "password=${KC_ADMIN_PASSWORD}" \
      -d "grant_type=password" \
      -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

    if [ -z "$ADMIN_TOKEN" ]; then
        echo "   ⚠️  Não foi possível obter token de admin do Keycloak"
        echo "   💡 Execute manualmente: bash infra/keycloak-setup/update-keycloak-uuids.sh"
    else
        # Array com os 5 usuários principais
        USERS=("admin@acme.com" "gerente@acme.com" "operador@acme.com" "vendedor@acme.com" "mecanico@acme.com")

        # Primeiro, deletar todos os mapeamentos antigos dos 5 usuários principais
        for EMAIL in "${USERS[@]}"; do
            PG_USER_ID=$(PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB_LOCAL -t -c "SELECT id FROM usuario WHERE email = '${EMAIL}' LIMIT 1;" | xargs)
            if [ -n "$PG_USER_ID" ]; then
                PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB_LOCAL -c "DELETE FROM usuario_identity_provider WHERE usuario_id = '${PG_USER_ID}' AND provider = 'keycloak';" > /dev/null
            fi
        done

        # Agora criar os novos mapeamentos
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
                    echo "   ✅ ${EMAIL} -> KC: ${KC_USER_ID:0:8}..."
                else
                    echo "   ⚠️  ${EMAIL} não encontrado no Keycloak"
                fi
            else
                echo "   ⚠️  ${EMAIL} não encontrado no PostgreSQL"
            fi
        done

        echo "   ✅ Mapeamentos de identity provider criados com sucesso!"

        # Limpar cache do Redis novamente para garantir que os novos UUIDs sejam usados
        if command -v redis-cli &> /dev/null; then
            redis-cli FLUSHDB > /dev/null 2>&1 && echo "   ✅ Cache do Redis atualizado!" || echo "   ⚠️  Não foi possível limpar o cache"
        fi
    fi
else
    echo "9️⃣  ⏭️  Pulando criação de mapeamentos (Keycloak não foi configurado neste reset)"
fi

# 10. Exibir dados seed
echo "🔟 Verificando dados seed..."
PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB_LOCAL << EOF
\echo '   📊 Tenants cadastrados:'
SELECT '      ' || slug || ' - ' || razao_social FROM tenant;
\echo ''
\echo '   👥 Usuários cadastrados:'
SELECT '      ' || email || ' (' || nome || ')' FROM usuario;
\echo ''
\echo '   🔗 Mapeamentos identity provider:'
SELECT '      ' || u.email || ' -> ' || uip.provider || ':' || SUBSTRING(uip.provider_user_id, 1, 8) || '...'
FROM usuario_identity_provider uip
JOIN usuario u ON u.id = uip.usuario_id;
EOF

echo ""
echo "✅ RESET DO AMBIENTE LOCAL FINALIZADO COM SUCESSO!"
echo ""
echo "📋 Próximos passos:"
if [ "$KEYCLOAK_SETUP_DONE" = true ]; then
    echo "   1. cd backend"
    echo "   2. SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
elif [ "$KC_AVAILABLE" = true ]; then
    echo "   1. bash infra/keycloak-setup/setup-keycloak-local.sh  (configurar Keycloak LOCAL)"
    echo "   2. Criar mapeamento: ver script ou criar manualmente no banco"
    echo "   3. cd backend"
    echo "   4. SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
else
    echo "   1. Iniciar Keycloak na porta 8081"
    echo "   2. bash infra/keycloak-setup/setup-keycloak-local.sh"
    echo "   3. Criar mapeamento identity provider (ver README)"
    echo "   4. cd backend"
    echo "   5. SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
fi
echo ""
echo "🌐 URLs do ambiente LOCAL:"
echo "   - Backend:   http://localhost:8090/api"
echo "   - Keycloak:  http://localhost:8081 (admin/admin)"
echo "   - PostgreSQL: localhost:5433 (jetski/dev123)"
echo "   - Redis:     localhost:6379"
echo ""
