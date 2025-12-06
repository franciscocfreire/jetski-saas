#!/bin/bash

###############################################################################
# Rebuild Backend e Frontend (Docker)
#
# Uso:
#   ./rebuild.sh              # Rebuild backend e frontend (usa ngrok padrao)
#   ./rebuild.sh backend      # Rebuild apenas backend
#   ./rebuild.sh frontend     # Rebuild apenas frontend
#   ./rebuild.sh --no-cache   # Rebuild sem cache (mais lento, mas garante fresh build)
#   ./rebuild.sh --local      # Usa localhost em vez de ngrok
#   NGROK_URL=https://xxx.ngrok-free.app ./rebuild.sh  # Com ngrok customizado
#
# Exemplos:
#   ./rebuild.sh                           # Rebuild tudo com ngrok padrao
#   ./rebuild.sh frontend                  # Rebuild frontend com ngrok padrao
#   ./rebuild.sh --local                   # Rebuild tudo para localhost
#   ./rebuild.sh frontend --no-cache       # Rebuild frontend sem cache
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Defaults
REBUILD_BACKEND=true
REBUILD_FRONTEND=true
NO_CACHE=""
USE_LOCAL=false

# URL do ngrok padrao
DEFAULT_NGROK_URL="https://539d02e90662.ngrok-free.app"

# Parse arguments
for arg in "$@"; do
    case $arg in
        backend)
            REBUILD_FRONTEND=false
            ;;
        frontend)
            REBUILD_BACKEND=false
            ;;
        --no-cache)
            NO_CACHE="--no-cache"
            ;;
        --local)
            USE_LOCAL=true
            ;;
        -h|--help)
            echo "Uso: $0 [backend|frontend] [--no-cache] [--local]"
            echo ""
            echo "Opções:"
            echo "  backend      Rebuild apenas o backend"
            echo "  frontend     Rebuild apenas o frontend"
            echo "  --no-cache   Rebuild sem cache Docker (mais lento)"
            echo "  --local      Usa localhost em vez de ngrok (para desenvolvimento local)"
            echo ""
            echo "Variáveis de ambiente:"
            echo "  NGROK_URL    URL do ngrok (default: $DEFAULT_NGROK_URL)"
            echo ""
            echo "Exemplos:"
            echo "  $0                              # Rebuild tudo com ngrok padrao"
            echo "  $0 backend                      # Rebuild apenas backend"
            echo "  $0 frontend                     # Rebuild apenas frontend"
            echo "  $0 --no-cache                   # Rebuild tudo sem cache"
            echo "  $0 --local                      # Rebuild para localhost"
            echo "  $0 backend --no-cache           # Rebuild backend sem cache"
            echo "  NGROK_URL=https://xxx.ngrok-free.app $0  # Com ngrok customizado"
            exit 0
            ;;
    esac
done

# Determinar URL base
if [ "$USE_LOCAL" = true ]; then
    BASE_URL="http://localhost:3001"
    KEYCLOAK_ISSUER="http://localhost:8080/realms/jetski-saas"
else
    BASE_URL="${NGROK_URL:-$DEFAULT_NGROK_URL}"
    KEYCLOAK_ISSUER="${BASE_URL}/realms/jetski-saas"
fi

echo -e "${BLUE}========================================"
echo "  REBUILD - Jetski SaaS"
echo -e "========================================${NC}"
echo ""

# Build list of services
SERVICES=""
if [ "$REBUILD_BACKEND" = true ]; then
    SERVICES="$SERVICES backend"
fi
if [ "$REBUILD_FRONTEND" = true ]; then
    SERVICES="$SERVICES frontend"
fi

if [ -z "$SERVICES" ]; then
    echo -e "${RED}Erro: Nenhum serviço selecionado${NC}"
    exit 1
fi

echo -e "${YELLOW}Serviços a rebuildar:${NC}$SERVICES"
if [ -n "$NO_CACHE" ]; then
    echo -e "${YELLOW}Modo: --no-cache (build completo)${NC}"
fi
echo -e "${GREEN}NEXTAUTH_URL:    ${BASE_URL}${NC}"
echo -e "${GREEN}KEYCLOAK_ISSUER: ${KEYCLOAK_ISSUER}${NC}"
echo ""

# Step 1: Stop services
echo -e "${BLUE}[1/4] Parando serviços...${NC}"
docker compose stop $SERVICES 2>/dev/null || true

# Step 2: Remove containers
echo -e "${BLUE}[2/4] Removendo containers antigos...${NC}"
docker compose rm -f $SERVICES 2>/dev/null || true

# Step 3: Build images
echo -e "${BLUE}[3/4] Buildando imagens...${NC}"
if [ "$REBUILD_BACKEND" = true ]; then
    echo -e "${YELLOW}  -> Backend (Maven + Java 21)...${NC}"
    docker compose build $NO_CACHE backend
fi
if [ "$REBUILD_FRONTEND" = true ]; then
    echo -e "${YELLOW}  -> Frontend (Next.js)...${NC}"
    docker compose build $NO_CACHE frontend
fi

# Step 4: Start services with environment variables
echo -e "${BLUE}[4/4] Iniciando serviços...${NC}"
NEXTAUTH_URL="$BASE_URL" \
KEYCLOAK_ISSUER="$KEYCLOAK_ISSUER" \
docker compose up -d $SERVICES

# Wait and show status
echo ""
echo -e "${YELLOW}Aguardando serviços iniciarem...${NC}"
sleep 5

# Health check
echo ""
echo -e "${BLUE}Status dos serviços:${NC}"
docker compose ps $SERVICES

# Final info
echo ""
echo -e "${GREEN}========================================"
echo "  REBUILD CONCLUÍDO!"
echo -e "========================================${NC}"
echo ""
if [ "$USE_LOCAL" = true ]; then
    echo "URLs (localhost):"
    echo "   - Frontend:   http://localhost:3001"
    echo "   - Backend:    http://localhost:8090/api"
else
    echo "URLs (ngrok):"
    echo "   - Frontend:   $BASE_URL"
    echo "   - Backend:    $BASE_URL/api"
fi
echo ""
echo -e "${YELLOW}Para ver logs: docker compose logs -f $SERVICES${NC}"
