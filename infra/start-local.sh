#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../backend" && pwd)"

echo "========================================="
echo "  Jetski SaaS - Ambiente LOCAL"
echo "========================================="
echo ""

# Cores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Funções de verificação
check_service() {
    local service_name=$1
    local check_command=$2

    printf "  ${service_name}... "
    if eval "$check_command" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC}"
        return 0
    else
        echo -e "${RED}✗${NC}"
        return 1
    fi
}

# 1. Verificar serviços do sistema
echo "1️⃣  Verificando serviços do sistema..."
check_service "PostgreSQL (5433)" "PGPASSWORD=dev123 psql -h localhost -p 5433 -U postgres -c '\q'"
check_service "Redis (6379)" "redis-cli ping"
echo ""

# 2. Parar containers Docker (se estiverem rodando)
echo "2️⃣  Parando containers Docker..."
cd "$SCRIPT_DIR/.."
if docker-compose ps --services 2>/dev/null | grep -q .; then
    echo "  Parando containers..."
    docker-compose stop > /dev/null 2>&1 || true
    echo -e "  ${GREEN}✓${NC} Containers parados"
else
    echo -e "  ${GREEN}✓${NC} Nenhum container rodando"
fi
echo ""

# 3. Iniciar Keycloak
echo "3️⃣  Iniciando Keycloak (porta 8081)..."
if curl -s http://localhost:8081/health/ready > /dev/null 2>&1; then
    echo -e "  ${GREEN}✓${NC} Keycloak já está rodando"
else
    echo "  Iniciando Keycloak..."
    "$SCRIPT_DIR/keycloak-setup/start-keycloak-postgres.sh" > /tmp/start-keycloak.log 2>&1 &

    # Aguardar Keycloak
    printf "  Aguardando Keycloak"
    for i in {1..40}; do
        if curl -s http://localhost:8081/health/ready > /dev/null 2>&1; then
            echo -e " ${GREEN}✓${NC}"
            break
        fi
        printf "."
        sleep 2
    done
fi
echo ""

# 4. Importar/Verificar Realm
echo "4️⃣  Verificando Realm Keycloak..."
if [ -f "$SCRIPT_DIR/keycloak-setup/setup-keycloak-local.sh" ]; then
    echo "  Verificando se realm existe..."
    # Aqui você pode adicionar lógica para verificar se o realm já foi importado
    echo -e "  ${YELLOW}ℹ${NC}  Execute manualmente se necessário: $SCRIPT_DIR/keycloak-setup/setup-keycloak-local.sh"
else
    echo -e "  ${YELLOW}⚠${NC}  Script de setup não encontrado"
fi
echo ""

# 5. Iniciar OPA
echo "5️⃣  Iniciando OPA (porta 8181)..."
if curl -s http://localhost:8181/health > /dev/null 2>&1; then
    echo -e "  ${GREEN}✓${NC} OPA já está rodando"
else
    "$SCRIPT_DIR/start-opa-local.sh" > /tmp/start-opa.log 2>&1 &
    sleep 3
    if curl -s http://localhost:8181/health > /dev/null 2>&1; then
        echo -e "  ${GREEN}✓${NC} OPA iniciado com sucesso"
    else
        echo -e "  ${RED}✗${NC} Erro ao iniciar OPA. Veja: /tmp/start-opa.log"
    fi
fi
echo ""

# 6. Iniciar Backend
echo "6️⃣  Backend Spring Boot..."
if curl -s http://localhost:8090/api/actuator/health > /dev/null 2>&1; then
    echo -e "  ${GREEN}✓${NC} Backend já está rodando"
    echo -e "  ${YELLOW}ℹ${NC}  Para reiniciar: pkill -f 'spring-boot:run' && cd $BACKEND_DIR && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
else
    echo "  Execute manualmente:"
    echo "    cd $BACKEND_DIR"
    echo "    SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
fi
echo ""

# Status Final
echo "========================================="
echo "  STATUS DO AMBIENTE"
echo "========================================="
echo ""
check_service "PostgreSQL (5433)" "PGPASSWORD=dev123 psql -h localhost -p 5433 -U postgres -c '\q'"
check_service "Redis (6379)" "redis-cli ping"
check_service "Keycloak (8081)" "curl -s http://localhost:8081/health/ready"
check_service "OPA (8181)" "curl -s http://localhost:8181/health"
check_service "Backend (8090)" "curl -s http://localhost:8090/api/actuator/health"
echo ""

echo "========================================="
echo "  URLS"
echo "========================================="
echo ""
echo "  🌐 Backend API:     http://localhost:8090/api"
echo "  📚 Swagger UI:      http://localhost:8090/api/swagger-ui/index.html"
echo "  🔐 Keycloak Admin:  http://localhost:8081/admin (admin/admin)"
echo "  📋 OPA Health:      http://localhost:8181/health"
echo ""

echo "========================================="
echo "  CREDENCIAIS"
echo "========================================="
echo ""
echo "  Keycloak Admin: admin / admin"
echo "  Usuário Teste: admin@acme.com / admin123"
echo "  Tenant ID: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
echo ""

echo "========================================="
echo "  LOGS"
echo "========================================="
echo ""
echo "  Keycloak: tail -f /tmp/keycloak-postgres.log"
echo "  OPA:      tail -f /tmp/opa-local.log"
echo "  Backend:  tail -f /tmp/backend.log"
echo ""
