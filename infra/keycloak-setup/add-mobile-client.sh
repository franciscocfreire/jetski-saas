#!/usr/bin/env bash
#
# Add Mobile Client to Keycloak
# Run this after setup-keycloak-local.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEYCLOAK_URL="http://localhost:8081"
ADMIN_USER="admin"
ADMIN_PASS="admin"
REALM="jetski-saas"

echo "========================================"
echo "  Adding Mobile Client to Keycloak"
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

# 2. Create mobile client
echo ""
echo "[2/2] Creating jetski-mobile client..."
CREATE_CLIENT=$(curl -s -w "%{http_code}" -o /dev/null -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${SCRIPT_DIR}/client-mobile.json")

if [ "$CREATE_CLIENT" = "201" ]; then
  echo "✓ Mobile client created successfully!"
elif [ "$CREATE_CLIENT" = "409" ]; then
  echo "⚠ Mobile client already exists"
else
  echo "⚠ Status: ${CREATE_CLIENT}"
fi

echo ""
echo "========================================"
echo "  Mobile Client Configuration"
echo "========================================"
echo ""
echo "Client ID:        jetski-mobile"
echo "Redirect URI:     com.jetski.mobile:/oauth2redirect"
echo "PKCE:             Required (S256)"
echo "Public Client:    Yes"
echo ""
echo "To test the mobile app, use one of these users:"
echo "  - operador@acme.com / operador123"
echo "  - admin@acme.com / admin123"
echo ""
echo "Keycloak Console: ${KEYCLOAK_URL}/admin"
echo ""
