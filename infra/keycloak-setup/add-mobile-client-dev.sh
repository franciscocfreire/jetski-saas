#!/usr/bin/env bash
# Adiciona client mobile no Keycloak (DEV - Docker porta 8080)

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
ADMIN_USER="admin"
ADMIN_PASS="Mazuca@123"
REALM="jetski-saas"

# Obter token
ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}&password=${ADMIN_PASS}&grant_type=password&client_id=admin-cli" \
  | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
    echo "ERRO: Nao foi possivel obter token"
    exit 1
fi

# Criar client mobile
curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "jetski-mobile",
    "name": "Jetski Mobile App",
    "enabled": true,
    "publicClient": true,
    "protocol": "openid-connect",
    "standardFlowEnabled": true,
    "directAccessGrantsEnabled": true,
    "redirectUris": [
      "com.jetski.app://oauth/callback",
      "jetski://oauth/callback",
      "http://localhost:8090/api/oauth/callback"
    ],
    "webOrigins": ["*"],
    "attributes": {
      "pkce.code.challenge.method": "S256"
    }
  }' 2>/dev/null || true

# Obter UUID do client
CLIENT_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=jetski-mobile" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -n "$CLIENT_UUID" ]; then
    # Adicionar mappers
    curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}/protocol-mappers/models" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "tenant-id-mapper",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-usermodel-attribute-mapper",
        "config": {
          "user.attribute": "tenant_id",
          "claim.name": "tenant_id",
          "jsonType.label": "String",
          "id.token.claim": "true",
          "access.token.claim": "true",
          "userinfo.token.claim": "true"
        }
      }' 2>/dev/null || true

    curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}/protocol-mappers/models" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d '{
        "name": "roles-mapper",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-usermodel-realm-role-mapper",
        "config": {
          "claim.name": "roles",
          "jsonType.label": "String",
          "id.token.claim": "true",
          "access.token.claim": "true",
          "multivalued": "true"
        }
      }' 2>/dev/null || true
fi

echo "OK - Client mobile configurado"
