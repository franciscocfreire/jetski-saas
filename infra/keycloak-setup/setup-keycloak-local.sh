#!/usr/bin/env bash
#
# Keycloak Setup Script - Jetski SaaS
#
# Configura Keycloak com:
# - Realm jetski-saas
# - Client jetski-api (Resource Server para Spring Boot)
# - Roles (OPERADOR, GERENTE, FINANCEIRO, ADMIN_TENANT)
# - Usuário de teste com tenant_id claim
# - Protocol mapper para tenant_id
#

set -e

KEYCLOAK_URL="http://localhost:8081"
ADMIN_USER="admin"
ADMIN_PASS="admin"
REALM="jetski-saas"
CLIENT_ID="jetski-api"
CLIENT_SECRET="jetski-secret"
TEST_USER="operador@tenant1.com"
TEST_PASS="senha123"
TENANT_ID="550e8400-e29b-41d4-a716-446655440001"
PLATFORM_ADMIN_USER="admin@plataforma.com"
PLATFORM_ADMIN_PASS="admin123"

echo "========================================"
echo "  Keycloak Setup - Jetski SaaS"
echo "========================================"
echo ""

# 1. Obter token admin
echo "[1/12] Obtendo token de administrador..."
ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "ERRO: Não foi possível obter token de admin"
  exit 1
fi
echo "✓ Token obtido com sucesso"

# 2. Criar realm jetski-saas
echo ""
echo "[2/12] Criando realm ${REALM}..."
CREATE_REALM=$(curl -s -w "%{http_code}" -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"realm\": \"${REALM}\",
    \"enabled\": true,
    \"displayName\": \"Jetski SaaS\",
    \"accessTokenLifespan\": 600,
    \"sslRequired\": \"none\"
  }")

if [ "$CREATE_REALM" = "201" ] || [ "$CREATE_REALM" = "409" ]; then
  echo "✓ Realm ${REALM} criado/já existe"
else
  echo "⚠ Status: ${CREATE_REALM}"
fi

# 3. Criar roles no realm
echo ""
echo "[3/12] Criando roles no realm..."
for ROLE in OPERADOR GERENTE FINANCEIRO ADMIN_TENANT PLATFORM_ADMIN; do
  curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/roles" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${ROLE}\"}"
  echo "  ✓ Role ${ROLE} criada"
done

# 4. Criar client jetski-api
echo ""
echo "[4/12] Criando client ${CLIENT_ID}..."
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"${CLIENT_ID}\",
    \"enabled\": true,
    \"clientAuthenticatorType\": \"client-secret\",
    \"secret\": \"${CLIENT_SECRET}\",
    \"bearerOnly\": false,
    \"standardFlowEnabled\": false,
    \"directAccessGrantsEnabled\": true,
    \"serviceAccountsEnabled\": false,
    \"publicClient\": false,
    \"protocol\": \"openid-connect\"
  }"
echo "✓ Client ${CLIENT_ID} criado"

# 5. Obter CLIENT UUID
echo ""
echo "[5/12] Obtendo UUID do client..."
CLIENT_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=${CLIENT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -z "$CLIENT_UUID" ]; then
  echo "ERRO: Client UUID não encontrado"
  exit 1
fi
echo "✓ Client UUID: ${CLIENT_UUID}"

# 6. Adicionar protocol mappers
echo ""
echo "[6/12] Configurando protocol mappers..."

# 6.1. Mapper para tenant_id
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}/protocol-mappers/models" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"tenant-id-mapper\",
    \"protocol\": \"openid-connect\",
    \"protocolMapper\": \"oidc-usermodel-attribute-mapper\",
    \"config\": {
      \"user.attribute\": \"tenant_id\",
      \"claim.name\": \"tenant_id\",
      \"jsonType.label\": \"String\",
      \"id.token.claim\": \"true\",
      \"access.token.claim\": \"true\",
      \"userinfo.token.claim\": \"true\"
    }
  }"
echo "  ✓ Mapper tenant_id configurado"

# 6.2. Mapper para roles (realm_access.roles → roles)
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}/protocol-mappers/models" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"roles-mapper\",
    \"protocol\": \"openid-connect\",
    \"protocolMapper\": \"oidc-usermodel-realm-role-mapper\",
    \"config\": {
      \"claim.name\": \"roles\",
      \"jsonType.label\": \"String\",
      \"id.token.claim\": \"true\",
      \"access.token.claim\": \"true\",
      \"userinfo.token.claim\": \"true\",
      \"multivalued\": \"true\"
    }
  }"
echo "  ✓ Mapper roles configurado"

# 6.3. Mapper para grupos (opcional - para ver grupos no token)
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}/protocol-mappers/models" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"groups-mapper\",
    \"protocol\": \"openid-connect\",
    \"protocolMapper\": \"oidc-group-membership-mapper\",
    \"config\": {
      \"claim.name\": \"groups\",
      \"full.path\": \"false\",
      \"id.token.claim\": \"true\",
      \"access.token.claim\": \"true\",
      \"userinfo.token.claim\": \"true\"
    }
  }"
echo "  ✓ Mapper groups configurado"

# 7. Criar usuário de teste
echo ""
echo "[7/12] Criando usuário de teste..."
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${TEST_USER}\",
    \"email\": \"${TEST_USER}\",
    \"firstName\": \"Operador\",
    \"lastName\": \"Tenant 1\",
    \"enabled\": true,
    \"emailVerified\": true,
    \"credentials\": [{
      \"type\": \"password\",
      \"value\": \"${TEST_PASS}\",
      \"temporary\": false
    }],
    \"requiredActions\": []
  }"
echo "✓ Usuário ${TEST_USER} criado com senha"

# 8. Obter USER UUID e configurar roles
echo ""
echo "[8/12] Configurando roles do usuário..."
USER_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${TEST_USER}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

# Atribuir roles
for ROLE in OPERADOR GERENTE; do
  ROLE_JSON=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/${ROLE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")

  curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_UUID}/role-mappings/realm" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "[${ROLE_JSON}]"
  echo "  ✓ Role ${ROLE} atribuída ao usuário"
done

# 9. Criar grupo com tenant_id e adicionar usuário
echo ""
echo "[9/12] Configurando tenant via grupo..."
GROUP_NAME="tenant-${TENANT_ID}"

# Criar grupo com attribute tenant_id
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"${GROUP_NAME}\",
    \"attributes\": {
      \"tenant_id\": [\"${TENANT_ID}\"]
    }
  }"
echo "  ✓ Grupo ${GROUP_NAME} criado com attribute tenant_id"

# Obter GROUP UUID
GROUP_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o "\"id\":\"[^\"]*\".*\"name\":\"${GROUP_NAME}\"" | grep -o "\"id\":\"[^\"]*\"" | head -1 | cut -d'"' -f4)

# Adicionar usuário ao grupo
curl -s -o /dev/null -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_UUID}/groups/${GROUP_UUID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
echo "  ✓ Usuário adicionado ao grupo (tenant_id será lido do group attribute)"

# 10. Criar grupo platform-admins com unrestricted_access
echo ""
echo "[10/12] Criando grupo platform-admins..."
PLATFORM_GROUP_NAME="platform-admins"

curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"${PLATFORM_GROUP_NAME}\",
    \"attributes\": {
      \"unrestricted_access\": [\"true\"]
    }
  }"
echo "  ✓ Grupo ${PLATFORM_GROUP_NAME} criado com unrestricted_access=true"

# 11. Criar usuário platform admin
echo ""
echo "[11/12] Criando usuário platform admin..."
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${PLATFORM_ADMIN_USER}\",
    \"email\": \"${PLATFORM_ADMIN_USER}\",
    \"firstName\": \"Platform\",
    \"lastName\": \"Admin\",
    \"enabled\": true,
    \"emailVerified\": true,
    \"credentials\": [{
      \"type\": \"password\",
      \"value\": \"${PLATFORM_ADMIN_PASS}\",
      \"temporary\": false
    }],
    \"requiredActions\": []
  }"
echo "  ✓ Usuário platform admin criado"

# Obter PLATFORM_ADMIN_UUID
PLATFORM_ADMIN_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${PLATFORM_ADMIN_USER}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

# Atribuir role PLATFORM_ADMIN
PLATFORM_ROLE_JSON=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/PLATFORM_ADMIN" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")

curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${PLATFORM_ADMIN_UUID}/role-mappings/realm" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "[${PLATFORM_ROLE_JSON}]"
echo "  ✓ Role PLATFORM_ADMIN atribuída ao usuário"

# Obter PLATFORM_GROUP_UUID e adicionar usuário ao grupo
PLATFORM_GROUP_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o "\"id\":\"[^\"]*\".*\"name\":\"${PLATFORM_GROUP_NAME}\"" | grep -o "\"id\":\"[^\"]*\"" | head -1 | cut -d'"' -f4)

curl -s -o /dev/null -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${PLATFORM_ADMIN_UUID}/groups/${PLATFORM_GROUP_UUID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
echo "  ✓ Platform admin adicionado ao grupo ${PLATFORM_GROUP_NAME}"

# 12. Adicionar mapper para global_roles (lê unrestricted_access do grupo)
echo ""
echo "[12/12] Configurando mapper para global_roles..."
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}/protocol-mappers/models" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"global-roles-mapper\",
    \"protocol\": \"openid-connect\",
    \"protocolMapper\": \"oidc-group-membership-mapper\",
    \"config\": {
      \"claim.name\": \"global_roles\",
      \"full.path\": \"false\",
      \"id.token.claim\": \"true\",
      \"access.token.claim\": \"true\",
      \"userinfo.token.claim\": \"true\"
    }
  }"
echo "  ✓ Mapper global_roles configurado"

echo ""
echo "========================================"
echo "  Setup Concluído! ✓"
echo "========================================"
echo ""
echo "Credenciais criadas:"
echo ""
echo "Realm e Client:"
echo "  Realm: ${REALM}"
echo "  Client ID: ${CLIENT_ID}"
echo "  Client Secret: ${CLIENT_SECRET}"
echo ""
echo "Usuário Tenant (operador@tenant1.com):"
echo "  Username: ${TEST_USER}"
echo "  Password: ${TEST_PASS}"
echo "  Tenant ID: ${TENANT_ID}"
echo "  Roles: OPERADOR, GERENTE"
echo "  Grupo: tenant-${TENANT_ID}"
echo ""
echo "Usuário Platform Admin (admin@plataforma.com):"
echo "  Username: ${PLATFORM_ADMIN_USER}"
echo "  Password: ${PLATFORM_ADMIN_PASS}"
echo "  Roles: PLATFORM_ADMIN"
echo "  Grupo: platform-admins (unrestricted_access=true)"
echo "  Acesso: IRRESTRITO - pode acessar qualquer tenant"
echo ""
echo "Para obter um token JWT de teste:"
echo ""
echo "# Usuário tenant (operador@tenant1.com):"
echo "curl -X POST '${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token' \\"
echo "  -H 'Content-Type: application/x-www-form-urlencoded' \\"
echo "  -d 'username=${TEST_USER}' \\"
echo "  -d 'password=${TEST_PASS}' \\"
echo "  -d 'grant_type=password' \\"
echo "  -d 'client_id=${CLIENT_ID}' \\"
echo "  -d 'client_secret=${CLIENT_SECRET}'"
echo ""
echo "# Platform admin (admin@plataforma.com):"
echo "curl -X POST '${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token' \\"
echo "  -H 'Content-Type: application/x-www-form-urlencoded' \\"
echo "  -d 'username=${PLATFORM_ADMIN_USER}' \\"
echo "  -d 'password=${PLATFORM_ADMIN_PASS}' \\"
echo "  -d 'grant_type=password' \\"
echo "  -d 'client_id=${CLIENT_ID}' \\"
echo "  -d 'client_secret=${CLIENT_SECRET}'"
echo ""
