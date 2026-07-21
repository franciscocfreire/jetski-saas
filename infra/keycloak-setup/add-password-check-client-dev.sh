#!/usr/bin/env bash
# Adiciona/converge o client jetski-password-check no Keycloak (DEV).
# Client confidencial dedicado ao direct grant que valida a senha ATUAL na
# troca de senha do perfil staff — sem standard flow, sem service account.
# Idempotente: cria se não existir, converge secret/flags se existir.

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
ADMIN_USER="admin"
ADMIN_PASS="Mazuca@123"
REALM="jetski-saas"
SECRET="${KEYCLOAK_PASSWORD_CHECK_CLIENT_SECRET:-dev-password-check-secret}"

ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}&password=${ADMIN_PASS}&grant_type=password&client_id=admin-cli" \
  | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
    echo "ERRO: Nao foi possivel obter token"
    exit 1
fi

BODY='{
    "clientId": "jetski-password-check",
    "name": "Validação de senha (perfil self-service)",
    "enabled": true,
    "publicClient": false,
    "secret": "'"${SECRET}"'",
    "protocol": "openid-connect",
    "standardFlowEnabled": false,
    "implicitFlowEnabled": false,
    "directAccessGrantsEnabled": true,
    "serviceAccountsEnabled": false,
    "redirectUris": [],
    "webOrigins": []
  }'

CLIENT_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=jetski-password-check" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -z "$CLIENT_UUID" ]; then
    curl -s -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "$BODY"
    echo "OK - Client jetski-password-check criado"
else
    curl -s -o /dev/null -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "$BODY"
    echo "OK - Client jetski-password-check convergido"
fi
