#!/usr/bin/env bash
#
# Keycloak Setup Script - Jetski SaaS (LOCAL)
#
# Configura Keycloak com:
# - Realm jetski-saas
# - Client jetski-api (Resource Server para Spring Boot)
# - Roles (OPERADOR, GERENTE, FINANCEIRO, ADMIN_TENANT, PLATFORM_ADMIN)
# - Usuários de teste com tenant_id claim
# - Protocol mappers para tenant_id, roles, groups
#
# IMPORTANTE - Sincronização PostgreSQL:
# Os UUIDs dos usuários criados pelo Keycloak são ALEATÓRIOS.
# Para desenvolvimento, os UUIDs fixos estão definidos em:
# /backend/src/main/resources/db/migration/V999__seed_data_dev.sql
#
# UUIDs documentados (extraídos do Keycloak atual):
# - admin@acme.com:      b0cd6005-a7c0-4915-a08f-abae4364ae46
# - operador@acme.com:   820cd5a2-4a6e-4f02-9193-e745b99c4f5e
# - admin@plataforma.com: 00000000-0000-0000-0000-000000000001 (fixo no V1003)
#
# Se você apagar Keycloak e recriar, os UUIDs serão DIFERENTES.
# Solução DEV: Aceitar divergência (não impacta funcionalidade)
# Solução PROD: Usar User Provisioning automático (POST /invite)
#

set -e

KEYCLOAK_URL="http://localhost:8081"
ADMIN_USER="admin"
ADMIN_PASS="admin"
REALM="jetski-saas"
CLIENT_ID="jetski-api"
CLIENT_SECRET="jetski-secret"

# Tenant ACME (padrão - seed data do banco)
TENANT_ID="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"

# Usuários do tenant ACME
ADMIN_ACME_USER="admin@acme.com"
ADMIN_ACME_PASS="admin123"
OPERADOR_ACME_USER="operador@acme.com"
OPERADOR_ACME_PASS="operador123"

# Platform admin (acesso irrestrito)
PLATFORM_ADMIN_USER="admin@plataforma.com"
PLATFORM_ADMIN_PASS="admin123"

echo "========================================"
echo "  Keycloak Setup - Jetski SaaS"
echo "========================================"
echo ""

# 1. Obter token admin
echo "[1/14] Obtendo token de administrador..."
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
echo "[2/14] Criando realm ${REALM}..."
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
echo "[3/14] Criando roles no realm..."
for ROLE in OPERADOR GERENTE FINANCEIRO ADMIN_TENANT PLATFORM_ADMIN; do
  curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/roles" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${ROLE}\"}"
  echo "  ✓ Role ${ROLE} criada"
done

# 4. Criar client jetski-api
echo ""
echo "[4/14] Criando client ${CLIENT_ID}..."
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
echo "[5/14] Obtendo UUID do client..."
CLIENT_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=${CLIENT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -z "$CLIENT_UUID" ]; then
  echo "ERRO: Client UUID não encontrado"
  exit 1
fi
echo "✓ Client UUID: ${CLIENT_UUID}"

# 6. Adicionar protocol mappers
echo ""
echo "[6/14] Configurando protocol mappers..."

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

# 7. Criar usuário admin@acme.com
echo ""
echo "[7/14] Criando usuário admin@acme.com..."
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${ADMIN_ACME_USER}\",
    \"email\": \"${ADMIN_ACME_USER}\",
    \"firstName\": \"Admin\",
    \"lastName\": \"ACME\",
    \"enabled\": true,
    \"emailVerified\": true,
    \"credentials\": [{
      \"type\": \"password\",
      \"value\": \"${ADMIN_ACME_PASS}\",
      \"temporary\": false
    }],
    \"requiredActions\": []
  }"
echo "✓ Usuário ${ADMIN_ACME_USER} criado"

# 8. Configurar roles do admin@acme.com
echo ""
echo "[8/14] Configurando roles do admin@acme.com..."
ADMIN_ACME_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${ADMIN_ACME_USER}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

# Atribuir roles ADMIN_TENANT e GERENTE
for ROLE in ADMIN_TENANT GERENTE; do
  ROLE_JSON=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/${ROLE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")

  curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${ADMIN_ACME_UUID}/role-mappings/realm" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "[${ROLE_JSON}]"
  echo "  ✓ Role ${ROLE} atribuída"
done

# 9. Criar usuário operador@acme.com
echo ""
echo "[9/14] Criando usuário operador@acme.com..."
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${OPERADOR_ACME_USER}\",
    \"email\": \"${OPERADOR_ACME_USER}\",
    \"firstName\": \"Operador\",
    \"lastName\": \"ACME\",
    \"enabled\": true,
    \"emailVerified\": true,
    \"credentials\": [{
      \"type\": \"password\",
      \"value\": \"${OPERADOR_ACME_PASS}\",
      \"temporary\": false
    }],
    \"requiredActions\": []
  }"
echo "✓ Usuário ${OPERADOR_ACME_USER} criado"

# 10. Configurar roles do operador@acme.com
echo ""
echo "[10/14] Configurando roles do operador@acme.com..."
OPERADOR_ACME_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${OPERADOR_ACME_USER}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

# Atribuir role OPERADOR
ROLE_JSON=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/OPERADOR" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")

curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${OPERADOR_ACME_UUID}/role-mappings/realm" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "[${ROLE_JSON}]"
echo "  ✓ Role OPERADOR atribuída"

# 11. Criar grupo ACME com tenant_id e adicionar usuários
echo ""
echo "[11/14] Configurando tenant ACME via grupo..."
GROUP_NAME="tenant-acme"

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
echo "  ✓ Grupo ${GROUP_NAME} criado com tenant_id=${TENANT_ID}"

# Obter GROUP UUID
GROUP_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/groups" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o "\"id\":\"[^\"]*\".*\"name\":\"${GROUP_NAME}\"" | grep -o "\"id\":\"[^\"]*\"" | head -1 | cut -d'"' -f4)

# Adicionar admin@acme.com ao grupo
curl -s -o /dev/null -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${ADMIN_ACME_UUID}/groups/${GROUP_UUID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
echo "  ✓ Usuário ${ADMIN_ACME_USER} adicionado ao grupo"

# Adicionar operador@acme.com ao grupo
curl -s -o /dev/null -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${OPERADOR_ACME_UUID}/groups/${GROUP_UUID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
echo "  ✓ Usuário ${OPERADOR_ACME_USER} adicionado ao grupo"

# 12. Criar grupo platform-admins com unrestricted_access
echo ""
echo "[12/14] Criando grupo platform-admins..."
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

# 13. Criar usuário platform admin
echo ""
echo "[13/14] Criando usuário platform admin..."
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

# 14. Adicionar mapper para global_roles (lê unrestricted_access do grupo)
echo ""
echo "[14/14] Configurando mapper para global_roles..."
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
echo "Tenant ACME (${TENANT_ID}):"
echo ""
echo "  Admin do Tenant (admin@acme.com):"
echo "    Username: ${ADMIN_ACME_USER}"
echo "    Password: ${ADMIN_ACME_PASS}"
echo "    Roles: ADMIN_TENANT, GERENTE"
echo "    Grupo: tenant-acme"
echo ""
echo "  Operador (operador@acme.com):"
echo "    Username: ${OPERADOR_ACME_USER}"
echo "    Password: ${OPERADOR_ACME_PASS}"
echo "    Roles: OPERADOR"
echo "    Grupo: tenant-acme"
echo ""
echo "Platform Admin (admin@plataforma.com):"
echo "  Username: ${PLATFORM_ADMIN_USER}"
echo "  Password: ${PLATFORM_ADMIN_PASS}"
echo "  Roles: PLATFORM_ADMIN"
echo "  Grupo: platform-admins (unrestricted_access=true)"
echo "  Acesso: IRRESTRITO - pode acessar qualquer tenant"
echo ""
echo "Para obter um token JWT de teste:"
echo ""
echo "# Admin ACME (admin@acme.com):"
echo "curl -X POST '${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token' \\"
echo "  -H 'Content-Type: application/x-www-form-urlencoded' \\"
echo "  -d 'username=${ADMIN_ACME_USER}' \\"
echo "  -d 'password=${ADMIN_ACME_PASS}' \\"
echo "  -d 'grant_type=password' \\"
echo "  -d 'client_id=${CLIENT_ID}' \\"
echo "  -d 'client_secret=${CLIENT_SECRET}'"
echo ""
echo "# Operador ACME (operador@acme.com):"
echo "curl -X POST '${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token' \\"
echo "  -H 'Content-Type: application/x-www-form-urlencoded' \\"
echo "  -d 'username=${OPERADOR_ACME_USER}' \\"
echo "  -d 'password=${OPERADOR_ACME_PASS}' \\"
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
