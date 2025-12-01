#!/usr/bin/env bash
#
# Cria usuário gerente.multi@example.com no Keycloak
# Este usuário tem acesso a 11 tenants no banco de dados
#

set -e

KEYCLOAK_URL="http://localhost:8081"
ADMIN_USER="admin"
ADMIN_PASS="admin"
REALM="jetski-saas"

# Dados do usuário multi-tenant
USER_EMAIL="gerente.multi@example.com"
USER_PASS="senha123"
USER_FIRST="Gerente"
USER_LAST="Multi-Tenant"

echo "========================================"
echo "  Criar Usuário Multi-Tenant"
echo "========================================"
echo ""

# 1. Obter token admin
echo "[1/4] Obtendo token de administrador..."
ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "❌ ERRO: Não foi possível obter token de admin"
  exit 1
fi
echo "✓ Token obtido"

# 2. Verificar se usuário já existe
echo ""
echo "[2/4] Verificando se usuário já existe..."
USER_EXISTS=$(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/users?email=${USER_EMAIL}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id"' | wc -l)

if [ "$USER_EXISTS" -gt 0 ]; then
  echo "⚠️  Usuário ${USER_EMAIL} já existe no Keycloak"
  exit 0
fi

# 3. Criar usuário
echo ""
echo "[3/4] Criando usuário ${USER_EMAIL}..."
CREATE_USER=$(curl -s -w "%{http_code}" -o /tmp/create_user_response.json -X POST \
  "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${USER_EMAIL}\",
    \"email\": \"${USER_EMAIL}\",
    \"emailVerified\": true,
    \"enabled\": true,
    \"firstName\": \"${USER_FIRST}\",
    \"lastName\": \"${USER_LAST}\",
    \"credentials\": [{
      \"type\": \"password\",
      \"value\": \"${USER_PASS}\",
      \"temporary\": false
    }]
  }")

if [ "$CREATE_USER" = "201" ]; then
  echo "✓ Usuário criado com sucesso"
else
  echo "❌ Erro ao criar usuário. Status: ${CREATE_USER}"
  cat /tmp/create_user_response.json
  exit 1
fi

# 4. Obter ID do usuário criado
echo ""
echo "[4/4] Associando role GERENTE..."
USER_ID=$(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/users?email=${USER_EMAIL}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -z "$USER_ID" ]; then
  echo "❌ ERRO: Não foi possível obter ID do usuário"
  exit 1
fi

# 5. Obter role GERENTE
ROLE_DATA=$(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/GERENTE" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")

ROLE_ID=$(echo "$ROLE_DATA" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
ROLE_NAME=$(echo "$ROLE_DATA" | grep -o '"name":"[^"]*' | cut -d'"' -f4)

# 6. Associar role ao usuário
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_ID}/role-mappings/realm" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "[{
    \"id\": \"${ROLE_ID}\",
    \"name\": \"${ROLE_NAME}\"
  }]"

echo "✓ Role GERENTE associada"
echo ""
echo "========================================"
echo "✅ Usuário criado com sucesso!"
echo ""
echo "Email:    ${USER_EMAIL}"
echo "Senha:    ${USER_PASS}"
echo "Tenants:  11 tenants (angra-paradise, balneario-waves, etc)"
echo ""
echo "Agora você pode fazer login no mobile app com este usuário!"
echo "========================================"
