#!/usr/bin/env bash
# Cria usuario multi-tenant no Keycloak (DEV - Docker porta 8080)

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
ADMIN_USER="admin"
ADMIN_PASS="Mazuca@123"
REALM="jetski-saas"

# Tenants
TENANT_ACME="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
TENANT_WAVE="b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22"

# Obter token
ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}&password=${ADMIN_PASS}&grant_type=password&client_id=admin-cli" \
  | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
    echo "ERRO: Nao foi possivel obter token"
    exit 1
fi

# Criar grupo tenant-wave se nao existir
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"tenant-wave\",
    \"attributes\": {
      \"tenant_id\": [\"${TENANT_WAVE}\"]
    }
  }" 2>/dev/null || true

# Obter UUIDs dos grupos
ACME_GROUP=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*".*"name":"tenant-acme"' | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

WAVE_GROUP=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*".*"name":"tenant-wave"' | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Criar usuario multi-tenant
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "gerente.multi@example.com",
    "email": "gerente.multi@example.com",
    "firstName": "Gerente",
    "lastName": "Multi-Tenant",
    "enabled": true,
    "emailVerified": true,
    "credentials": [{
      "type": "password",
      "value": "multi123",
      "temporary": false
    }]
  }' 2>/dev/null || true

# Obter UUID do usuario
USER_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=gerente.multi@example.com" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -n "$USER_UUID" ]; then
    # Atribuir role GERENTE
    ROLE_JSON=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/GERENTE" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}")

    curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_UUID}/role-mappings/realm" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "[${ROLE_JSON}]" 2>/dev/null || true

    # Adicionar aos dois grupos (multi-tenant)
    if [ -n "$ACME_GROUP" ]; then
        curl -s -o /dev/null -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_UUID}/groups/${ACME_GROUP}" \
          -H "Authorization: Bearer ${ADMIN_TOKEN}" 2>/dev/null || true
    fi

    if [ -n "$WAVE_GROUP" ]; then
        curl -s -o /dev/null -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_UUID}/groups/${WAVE_GROUP}" \
          -H "Authorization: Bearer ${ADMIN_TOKEN}" 2>/dev/null || true
    fi

    echo "OK - Usuario multi-tenant criado: gerente.multi@example.com / multi123"
else
    echo "AVISO - Usuario ja existe ou erro ao criar"
fi
