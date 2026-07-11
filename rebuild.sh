#!/bin/bash

###############################################################################
# Rebuild Backend e Frontend (Docker)
#
# Uso:
#   ./rebuild.sh              # Rebuild backend e frontend
#   ./rebuild.sh backend      # Rebuild apenas backend
#   ./rebuild.sh frontend     # Rebuild apenas frontend
#   ./rebuild.sh --no-cache   # Rebuild sem cache (mais lento, mas garante fresh build)
#   ./rebuild.sh --local      # Usa localhost em vez de ngrok
#   ./rebuild.sh --migrate    # Executa migrations pendentes do Flyway
#   ./rebuild.sh --clear-cache # Limpa cache Redis
#   PUBLIC_URL=https://xxx.ngrok-free.app ./rebuild.sh  # Com ngrok customizado
#
# Exemplos:
#   ./rebuild.sh                           # Rebuild tudo com ngrok padrao
#   ./rebuild.sh frontend                  # Rebuild frontend com ngrok padrao
#   ./rebuild.sh --local                   # Rebuild tudo para localhost
#   ./rebuild.sh backend --no-cache        # Rebuild backend sem cache
#   ./rebuild.sh backend --migrate         # Rebuild backend e executa migrations
#   ./rebuild.sh --clear-cache             # Rebuild tudo e limpa cache Redis
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
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
REBUILD_PORTAL=true
NO_CACHE=""
USE_LOCAL=false
RUN_MIGRATE=false
CLEAR_CACHE=false

# URL do ngrok padrao
DEFAULT_PUBLIC_URL="https://www.pegaojet.com.br"

# Configuracoes do ambiente DEV (Docker)
PG_USER="jetski"
PG_DB="jetski_dev"

# Parse arguments
for arg in "$@"; do
    case $arg in
        backend)
            REBUILD_FRONTEND=false
            REBUILD_PORTAL=false
            ;;
        frontend)
            REBUILD_BACKEND=false
            REBUILD_PORTAL=false
            ;;
        portal)
            REBUILD_BACKEND=false
            REBUILD_FRONTEND=false
            ;;
        --no-cache)
            NO_CACHE="--no-cache"
            ;;
        --local)
            USE_LOCAL=true
            ;;
        --migrate)
            RUN_MIGRATE=true
            ;;
        --clear-cache)
            CLEAR_CACHE=true
            ;;
        -h|--help)
            echo "Uso: $0 [backend|frontend|portal] [--no-cache] [--local] [--migrate] [--clear-cache]"
            echo ""
            echo "Opções:"
            echo "  backend       Rebuild apenas o backend"
            echo "  frontend      Rebuild apenas o frontend"
            echo "  portal        Rebuild apenas o portal do cliente"
            echo "  --no-cache    Rebuild sem cache Docker (mais lento)"
            echo "  --local       Usa localhost em vez de ngrok (para desenvolvimento local)"
            echo "  --migrate     Executa migrations pendentes do Flyway"
            echo "  --clear-cache Limpa cache Redis (recomendado após mudanças no backend)"
            echo ""
            echo "Variáveis de ambiente:"
            echo "  PUBLIC_URL    URL pública do túnel (default: $DEFAULT_PUBLIC_URL)"
            echo ""
            echo "Exemplos:"
            echo "  $0                              # Rebuild tudo com ngrok padrao"
            echo "  $0 backend                      # Rebuild apenas backend"
            echo "  $0 frontend                     # Rebuild apenas frontend"
            echo "  $0 --no-cache                   # Rebuild tudo sem cache"
            echo "  $0 --local                      # Rebuild para localhost"
            echo "  $0 backend --migrate            # Rebuild backend com migrations"
            echo "  $0 --clear-cache                # Rebuild tudo e limpa cache"
            echo "  PUBLIC_URL=https://pegaojet.com.br $0  # Com ngrok customizado"
            exit 0
            ;;
    esac
done

# Determinar URL base
if [ "$USE_LOCAL" = true ]; then
    BASE_URL="http://localhost:3001"
    KEYCLOAK_ISSUER="http://localhost:8080/realms/jetski-saas"
else
    BASE_URL="${PUBLIC_URL:-$DEFAULT_PUBLIC_URL}"
    KEYCLOAK_ISSUER="${BASE_URL}/realms/jetski-saas"
    JETSKI_FRONTEND_URL="${BASE_URL}"
    JETSKI_EXTERNAL_URL="${BASE_URL}"
fi
# Portal no subdomínio próprio (fase 1): deriva cliente.* do host www se não setada
PORTAL_PUBLIC_URL="${PORTAL_PUBLIC_URL:-$(echo "$BASE_URL" | sed 's#//www\.#//cliente.#')}"
# Backoffice no subdomínio app.* (deriva do www; ex.: app.pegaojet.com.br).
# Login/convites/troca de senha apontam para cá; www continua servindo o site.
APP_PUBLIC_URL="${APP_PUBLIC_URL:-$(echo "$BASE_URL" | sed 's#//www\.#//app.#')}"

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
if [ "$REBUILD_PORTAL" = true ]; then
    SERVICES="$SERVICES portal"
fi

if [ -z "$SERVICES" ]; then
    echo -e "${RED}Erro: Nenhum serviço selecionado${NC}"
    exit 1
fi

echo -e "${YELLOW}Serviços a rebuildar:${NC}$SERVICES"
if [ -n "$NO_CACHE" ]; then
    echo -e "${YELLOW}Modo: --no-cache (build completo)${NC}"
fi
if [ "$RUN_MIGRATE" = true ]; then
    echo -e "${YELLOW}Migrations: habilitadas${NC}"
fi
if [ "$CLEAR_CACHE" = true ]; then
    echo -e "${YELLOW}Cache: será limpo${NC}"
fi
echo -e "${GREEN}NEXTAUTH_URL:    ${APP_PUBLIC_URL}${NC}"
echo -e "${GREEN}KEYCLOAK_ISSUER: ${KEYCLOAK_ISSUER}${NC}"
echo ""

# Step 1: Stop services
echo -e "${BLUE}[1/6] Parando serviços...${NC}"
docker compose stop $SERVICES 2>/dev/null || true

# Step 2: Remove containers
echo -e "${BLUE}[2/6] Removendo containers antigos...${NC}"
docker compose rm -f $SERVICES 2>/dev/null || true

# Step 3: Clear Redis cache (if requested)
if [ "$CLEAR_CACHE" = true ]; then
    echo -e "${BLUE}[3/6] Limpando cache Redis...${NC}"
    docker compose exec -T redis redis-cli FLUSHDB > /dev/null 2>&1 || true
    echo -e "${GREEN}   OK - Cache limpo!${NC}"
else
    echo -e "${BLUE}[3/6] Cache Redis mantido (use --clear-cache para limpar)${NC}"
fi

# Step 4: Run migrations (if requested and rebuilding backend)
if [ "$RUN_MIGRATE" = true ] && [ "$REBUILD_BACKEND" = true ]; then
    echo -e "${BLUE}[4/6] Executando migrations do Flyway...${NC}"

    # Verificar se PostgreSQL está rodando
    if docker compose exec -T postgres pg_isready -U $PG_USER > /dev/null 2>&1; then
        # Rodar Flyway via container temporario
        docker run --rm \
            --network jetski_jetski-network \
            -v "$BACKEND_DIR/src/main/resources/db/migration:/flyway/sql:ro" \
            flyway/flyway:10-alpine \
            -url=jdbc:postgresql://postgres:5432/${PG_DB} \
            -user=${PG_USER} \
            -password=dev123 \
            -baselineOnMigrate=true \
            -baselineVersion=0 \
            -outOfOrder=true \
            migrate 2>&1 | tail -5
        echo -e "${GREEN}   OK - Migrations executadas!${NC}"
    else
        echo -e "${YELLOW}   AVISO: PostgreSQL não está rodando, migrations ignoradas${NC}"
    fi
else
    echo -e "${BLUE}[4/6] Migrations ignoradas (use --migrate para executar)${NC}"
fi

# Step 5: Build images
echo -e "${BLUE}[5/6] Buildando imagens...${NC}"
if [ "$REBUILD_BACKEND" = true ]; then
    echo -e "${YELLOW}  -> Backend (Maven + Java 21)...${NC}"
    docker compose build $NO_CACHE backend
fi
if [ "$REBUILD_FRONTEND" = true ]; then
    echo -e "${YELLOW}  -> Frontend (Next.js)...${NC}"
    docker compose build $NO_CACHE frontend
fi
if [ "$REBUILD_PORTAL" = true ]; then
    echo -e "${YELLOW}  -> Portal do cliente (Next.js, basePath /portal)...${NC}"
    docker compose build $NO_CACHE portal
fi

# Step 6: Start services with environment variables
# --force-recreate ensures containers are recreated with new env vars
echo -e "${BLUE}[6/6] Iniciando serviços...${NC}"
NEXTAUTH_URL="$APP_PUBLIC_URL" \
APP_PUBLIC_URL="$APP_PUBLIC_URL" \
PORTAL_NEXTAUTH_URL="$PORTAL_PUBLIC_URL" \
PORTAL_PUBLIC_URL="$PORTAL_PUBLIC_URL" \
STORAGE_MINIO_PUBLIC_URL="$BASE_URL" \
KEYCLOAK_ISSUER="$KEYCLOAK_ISSUER" \
JETSKI_FRONTEND_URL="$APP_PUBLIC_URL" \
JETSKI_EXTERNAL_URL="$BASE_URL" \
docker compose up -d --force-recreate $SERVICES

# Wait for services to be healthy
echo ""
echo -e "${YELLOW}Aguardando serviços iniciarem...${NC}"

# Wait for backend health check (if rebuilding backend)
if [ "$REBUILD_BACKEND" = true ]; then
    echo -n "   Backend: "
    for i in {1..60}; do
        if curl -sf "http://localhost:8090/api/actuator/health" > /dev/null 2>&1; then
            echo -e "${GREEN}UP${NC}"
            break
        fi
        echo -n "."
        sleep 2
    done
    # Check if we timed out
    if ! curl -sf "http://localhost:8090/api/actuator/health" > /dev/null 2>&1; then
        echo -e "${YELLOW}STARTING (pode demorar mais)${NC}"
    fi
fi

# Wait for frontend health check (if rebuilding frontend)
if [ "$REBUILD_FRONTEND" = true ]; then
    echo -n "   Frontend: "
    for i in {1..30}; do
        HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/ 2>/dev/null || echo "000")
        if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "307" ] || [ "$HTTP_CODE" = "302" ]; then
            echo -e "${GREEN}UP${NC}"
            break
        fi
        echo -n "."
        sleep 2
    done
    # Final check
    HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/ 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "307" ] && [ "$HTTP_CODE" != "302" ]; then
        echo -e "${YELLOW}STARTING${NC}"
    fi
fi

# Show status
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
echo -e "${YELLOW}Dicas:${NC}"
echo "   - Se o login não funcionar, tente limpar cookies do navegador"
echo "   - Para limpar cache: ./rebuild.sh --clear-cache"
echo "   - Para executar migrations: ./rebuild.sh --migrate"
echo "   - Para ver logs: docker compose logs -f $SERVICES"
echo ""
