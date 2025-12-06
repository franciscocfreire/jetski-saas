#!/usr/bin/env bash
#
# Keycloak Setup Script - Jetski SaaS (DEV - Docker)
# Porta 8080 (Docker Compose)
#

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
ADMIN_USER="admin"
ADMIN_PASS="Mazuca@123"
REALM="jetski-saas"
CLIENT_ID="jetski-api"
CLIENT_SECRET="jetski-secret"

# Tenant ACME
TENANT_ID="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"

echo "========================================"
echo "  Keycloak Setup - DEV (Docker)"
echo "  URL: ${KEYCLOAK_URL}"
echo "========================================"
echo ""

# 1. Obter token admin
echo "[1] Obtendo token de administrador..."
ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "ERRO: Nao foi possivel obter token de admin"
  exit 1
fi
echo "OK - Token obtido"

# 2. Criar realm (se nao existir via import)
echo "[2] Verificando/criando realm ${REALM}..."
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"realm\": \"${REALM}\",
    \"enabled\": true,
    \"displayName\": \"Jetski SaaS\",
    \"accessTokenLifespan\": 600,
    \"sslRequired\": \"none\"
  }" 2>/dev/null || true
echo "OK - Realm verificado"

# 3. Criar roles
echo "[3] Criando roles..."
for ROLE in ADMIN_TENANT GERENTE OPERADOR VENDEDOR MECANICO FINANCEIRO PLATFORM_ADMIN; do
  curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/roles" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${ROLE}\"}" 2>/dev/null || true
done
echo "OK - Roles criadas"

# 4. Criar client jetski-api
echo "[4] Criando client ${CLIENT_ID}..."
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
  }" 2>/dev/null || true
echo "OK - Client criado"

# 5. Obter CLIENT UUID
CLIENT_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=${CLIENT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

# 6. Adicionar protocol mappers
echo "[5] Configurando mappers..."
# tenant_id mapper
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
  }" 2>/dev/null || true

# roles mapper
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
  }" 2>/dev/null || true
echo "OK - Mappers configurados"

# 7. Criar grupo tenant-acme
echo "[6] Criando grupo tenant-acme..."
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"tenant-acme\",
    \"attributes\": {
      \"tenant_id\": [\"${TENANT_ID}\"]
    }
  }" 2>/dev/null || true

GROUP_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*".*"name":"tenant-acme"' | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "OK - Grupo criado"

# 8. Criar usuarios
create_user() {
    local EMAIL=$1
    local PASS=$2
    local FNAME=$3
    local LNAME=$4
    local ROLE=$5
    local USER_TENANT_ID=${6:-$TENANT_ID}  # Usa TENANT_ID padrao se nao especificado

    # Criar usuario COM atributo tenant_id (necessario para o JWT claim)
    curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "{
        \"username\": \"${EMAIL}\",
        \"email\": \"${EMAIL}\",
        \"firstName\": \"${FNAME}\",
        \"lastName\": \"${LNAME}\",
        \"enabled\": true,
        \"emailVerified\": true,
        \"attributes\": {
          \"tenant_id\": [\"${USER_TENANT_ID}\"]
        },
        \"credentials\": [{
          \"type\": \"password\",
          \"value\": \"${PASS}\",
          \"temporary\": false
        }]
      }" 2>/dev/null || true

    # Obter UUID
    USER_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${EMAIL}" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

    # Atribuir role
    if [ -n "$USER_UUID" ] && [ -n "$ROLE" ]; then
        ROLE_JSON=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/${ROLE}" \
          -H "Authorization: Bearer ${ADMIN_TOKEN}")

        curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_UUID}/role-mappings/realm" \
          -H "Authorization: Bearer ${ADMIN_TOKEN}" \
          -H "Content-Type: application/json" \
          -d "[${ROLE_JSON}]" 2>/dev/null || true

        # Adicionar ao grupo tenant-acme
        if [ -n "$GROUP_UUID" ]; then
            curl -s -o /dev/null -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_UUID}/groups/${GROUP_UUID}" \
              -H "Authorization: Bearer ${ADMIN_TOKEN}" 2>/dev/null || true
        fi
    fi

    echo "   OK - ${EMAIL} (${ROLE})"
}

echo "[7] Criando usuarios..."
create_user "admin@acme.com" "admin123" "Admin" "ACME" "ADMIN_TENANT"
create_user "gerente@acme.com" "gerente123" "Gerente" "ACME" "GERENTE"
create_user "operador@acme.com" "operador123" "Operador" "ACME" "OPERADOR"
create_user "vendedor@acme.com" "vendedor123" "Vendedor" "ACME" "VENDEDOR"
create_user "mecanico@acme.com" "mecanico123" "Mecanico" "ACME" "MECANICO"

# Atribuir role GERENTE tambem ao admin
ADMIN_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=admin@acme.com" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
GERENTE_ROLE=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/GERENTE" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${ADMIN_UUID}/role-mappings/realm" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "[${GERENTE_ROLE}]" 2>/dev/null || true

echo ""
echo "========================================"
echo "  Setup Concluido!"
echo "========================================"
echo ""
echo "Client: ${CLIENT_ID} / Secret: ${CLIENT_SECRET}"
echo ""
echo "Usuarios criados no tenant ACME:"
echo "  - admin@acme.com / admin123 (ADMIN_TENANT, GERENTE)"
echo "  - gerente@acme.com / gerente123 (GERENTE)"
echo "  - operador@acme.com / operador123 (OPERADOR)"
echo "  - vendedor@acme.com / vendedor123 (VENDEDOR)"
echo "  - mecanico@acme.com / mecanico123 (MECANICO)"
echo ""
