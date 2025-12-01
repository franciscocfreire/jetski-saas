#!/usr/bin/env bash
#
# Add Backoffice Client to Keycloak
# Run this after setup-keycloak-local.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEYCLOAK_URL="http://localhost:8081"
ADMIN_USER="admin"
ADMIN_PASS="admin"
REALM="jetski-saas"

echo "========================================"
echo "  Adding Backoffice Client to Keycloak"
echo "========================================"
echo ""

# 1. Get admin token
echo "[1/2] Getting admin token..."
ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "ERROR: Failed to get admin token"
  exit 1
fi
echo "✓ Token obtained"

# 2. Create backoffice client
echo ""
echo "[2/2] Creating jetski-backoffice client..."
CREATE_CLIENT=$(curl -s -w "%{http_code}" -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${SCRIPT_DIR}/client-backoffice.json")

if [ "$CREATE_CLIENT" = "201" ]; then
  echo "✓ Backoffice client created successfully!"
elif [ "$CREATE_CLIENT" = "409" ]; then
  echo "⚠ Backoffice client already exists"
else
  echo "⚠ Status: ${CREATE_CLIENT}"
fi

echo ""
echo "========================================"
echo "  Backoffice Client Configuration"
echo "========================================"
echo ""
echo "Client ID:        jetski-backoffice"
echo "Redirect URI:     http://localhost:3002/api/auth/callback/keycloak"
echo "PKCE:             Required (S256)"
echo "Public Client:    Yes"
echo ""
echo "Environment variables for .env.local:"
echo "  KEYCLOAK_CLIENT_ID=jetski-backoffice"
echo "  KEYCLOAK_ISSUER=http://localhost:8081/realms/jetski-saas"
echo "  NEXTAUTH_URL=http://localhost:3002"
echo "  NEXTAUTH_SECRET=<generate-a-secret>"
echo ""
echo "To test the backoffice, use one of these users:"
echo "  - admin@acme.com / admin123"
echo "  - gerente@acme.com / gerente123"
echo "  - operador@acme.com / operador123"
echo ""
echo "Keycloak Console: ${KEYCLOAK_URL}/admin"
echo ""
