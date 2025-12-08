#!/bin/bash

###############################################################################
# Rebuild Frontend com Domínio Customizado
#
# Uso:
#   ./rebuild-frontend.sh                              # Usa localhost
#   ./rebuild-frontend.sh https://xxx.ngrok-free.app   # Com ngrok
#   DOMAIN=https://xxx.ngrok-free.app ./rebuild-frontend.sh
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configurações Keycloak
KC_HOST="localhost"
KC_PORT="8080"
KC_ADMIN_USER="admin"
KC_ADMIN_PASSWORD="Mazuca@123"
KC_REALM="jetski-saas"

# URL do domínio (argumento ou variável de ambiente ou padrão)
DEFAULT_DOMAIN="http://localhost:3001"
DOMAIN="${1:-${DOMAIN:-$DEFAULT_DOMAIN}}"

echo -e "${BLUE}========================================"
echo "  REBUILD FRONTEND - JetSki"
echo -e "========================================${NC}"
echo ""
echo -e "Domínio: ${GREEN}${DOMAIN}${NC}"
echo ""

cd "$SCRIPT_DIR"

# 1. Parar apenas o frontend
echo -e "${YELLOW}1. Parando frontend...${NC}"
docker compose stop frontend 2>/dev/null || true
docker compose rm -f frontend 2>/dev/null || true
echo -e "${GREEN}   OK${NC}"

# 2. Rebuild e iniciar frontend
echo -e "${YELLOW}2. Rebuilding frontend...${NC}"
NEXTAUTH_URL="$DOMAIN" \
KEYCLOAK_ISSUER="$DOMAIN/realms/jetski-saas" \
JETSKI_FRONTEND_URL="$DOMAIN" \
JETSKI_EXTERNAL_URL="$DOMAIN" \
docker compose up -d --build frontend

echo -e "${GREEN}   OK - Frontend rebuilding...${NC}"

# 3. Atualizar Keycloak (se não for localhost)
if [[ "$DOMAIN" != "http://localhost"* ]]; then
    echo -e "${YELLOW}3. Atualizando Keycloak com novo domínio...${NC}"

    sleep 3

    # Obter token admin
    KC_TOKEN=$(curl -s -X POST "http://${KC_HOST}:${KC_PORT}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "username=${KC_ADMIN_USER}" \
      -d "password=${KC_ADMIN_PASSWORD}" \
      -d "grant_type=password" \
      -d "client_id=admin-cli" 2>/dev/null | jq -r '.access_token' 2>/dev/null)

    if [ -n "$KC_TOKEN" ] && [ "$KC_TOKEN" != "null" ]; then
        # Obter UUID do client
        CLIENT_UUID=$(curl -s "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/clients?clientId=jetski-backoffice" \
          -H "Authorization: Bearer $KC_TOKEN" 2>/dev/null | jq -r '.[0].id' 2>/dev/null)

        if [ -n "$CLIENT_UUID" ] && [ "$CLIENT_UUID" != "null" ]; then
            # Obter client atual
            CURRENT_CLIENT=$(curl -s "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/clients/$CLIENT_UUID" \
              -H "Authorization: Bearer $KC_TOKEN" 2>/dev/null)

            # Atualizar URLs
            UPDATED_CLIENT=$(echo "$CURRENT_CLIENT" | \
              jq --arg domain "$DOMAIN" \
              '.redirectUris += [$domain + "/*", $domain + "/api/auth/callback/keycloak"] | .redirectUris |= unique |
               .webOrigins += [$domain] | .webOrigins |= unique')

            # Aplicar atualização
            HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "http://${KC_HOST}:${KC_PORT}/admin/realms/${KC_REALM}/clients/$CLIENT_UUID" \
              -H "Authorization: Bearer $KC_TOKEN" \
              -H "Content-Type: application/json" \
              -d "$UPDATED_CLIENT" 2>/dev/null)

            if [ "$HTTP_CODE" = "204" ]; then
                echo -e "${GREEN}   OK - Keycloak atualizado!${NC}"
            else
                echo -e "${YELLOW}   AVISO - Falha ao atualizar Keycloak (HTTP $HTTP_CODE)${NC}"
            fi
        else
            echo -e "${YELLOW}   AVISO - Client jetski-backoffice não encontrado${NC}"
        fi
    else
        echo -e "${YELLOW}   AVISO - Não foi possível obter token admin${NC}"
    fi
else
    echo -e "${YELLOW}3. Keycloak (localhost, sem alterações)${NC}"
fi

# 4. Aguardar frontend ficar pronto
echo -e "${YELLOW}4. Aguardando frontend...${NC}"
for i in {1..30}; do
    # Frontend roda na porta 3001 internamente
    if curl -sf "http://localhost:3001/" > /dev/null 2>&1; then
        echo -e "${GREEN}   OK - Frontend pronto!${NC}"
        break
    fi
    printf "."
    sleep 2
done
echo ""

# 5. Exibir URLs
echo ""
echo -e "${BLUE}========================================"
echo "  FRONTEND REBUILD COMPLETO!"
echo -e "========================================${NC}"
echo ""
echo -e "URLs:"
echo -e "   Frontend: ${GREEN}${DOMAIN}${NC}"
if [[ "$DOMAIN" != "http://localhost"* ]]; then
    echo -e "   Local:    http://localhost:3001"
fi
echo ""
