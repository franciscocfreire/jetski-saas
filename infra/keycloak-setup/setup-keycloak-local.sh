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

echo "========================================"
echo "  Keycloak Setup - Jetski SaaS"
echo "========================================"
echo ""

# 1. Obter token admin
echo "[1/8] Obtendo token de administrador..."
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
echo "[2/8] Criando realm ${REALM}..."
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
echo "[3/8] Criando roles no realm..."
for ROLE in OPERADOR GERENTE FINANCEIRO ADMIN_TENANT; do
  curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/roles" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${ROLE}\"}"
  echo "  ✓ Role ${ROLE} criada"
done

# 4. Criar client jetski-api
echo ""
echo "[4/8] Criando client ${CLIENT_ID}..."
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
echo "[5/8] Obtendo UUID do client..."
CLIENT_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=${CLIENT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -z "$CLIENT_UUID" ]; then
  echo "ERRO: Client UUID não encontrado"
  exit 1
fi
echo "✓ Client UUID: ${CLIENT_UUID}"

# 6. Adicionar protocol mapper para tenant_id
echo ""
echo "[6/8] Configurando protocol mapper para tenant_id..."
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
echo "✓ Protocol mapper configurado"

# 7. Criar usuário de teste
echo ""
echo "[7/8] Criando usuário de teste..."
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${TEST_USER}\",
    \"email\": \"${TEST_USER}\",
    \"enabled\": true,
    \"emailVerified\": true,
    \"attributes\": {
      \"tenant_id\": [\"${TENANT_ID}\"]
    }
  }"
echo "✓ Usuário ${TEST_USER} criado"

# 8. Obter USER UUID e configurar senha
echo ""
echo "[8/8] Configurando senha e roles do usuário..."
USER_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${TEST_USER}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

# Definir senha
curl -s -o /dev/null -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_UUID}/reset-password" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"type\": \"password\", \"value\": \"${TEST_PASS}\", \"temporary\": false}"

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

echo ""
echo "========================================"
echo "  Setup Concluído! ✓"
echo "========================================"
echo ""
echo "Credenciais criadas:"
echo "  Realm: ${REALM}"
echo "  Client ID: ${CLIENT_ID}"
echo "  Client Secret: ${CLIENT_SECRET}"
echo "  Usuário: ${TEST_USER}"
echo "  Senha: ${TEST_PASS}"
echo "  Tenant ID: ${TENANT_ID}"
echo "  Roles: OPERADOR, GERENTE"
echo ""
echo "Para obter um token JWT de teste, execute:"
echo ""
echo "curl -X POST '${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token' \\"
echo "  -H 'Content-Type: application/x-www-form-urlencoded' \\"
echo "  -d 'username=${TEST_USER}' \\"
echo "  -d 'password=${TEST_PASS}' \\"
echo "  -d 'grant_type=password' \\"
echo "  -d 'client_id=${CLIENT_ID}' \\"
echo "  -d 'client_secret=${CLIENT_SECRET}'"
echo ""
