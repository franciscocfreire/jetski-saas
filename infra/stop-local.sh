#!/bin/bash

echo "========================================="
echo "  Parando Ambiente LOCAL"
echo "========================================="
echo ""

# Cores
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# 1. Backend
printf "  Parando Backend (Spring Boot)... "
if pkill -f "spring-boot:run" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}já parado${NC}"
fi

# 2. OPA
printf "  Parando OPA... "
if pkill -f "opa run.*8181" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}já parado${NC}"
fi

# 3. Keycloak
printf "  Parando Keycloak... "
if pkill -f "keycloak.*8081" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}já parado${NC}"
fi

echo ""
echo "ℹ️  PostgreSQL e Redis continuam rodando (serviços do sistema)"
echo "   Para parar:"
echo "     sudo systemctl stop postgresql"
echo "     sudo systemctl stop redis-server"
echo ""
echo "✓ Ambiente LOCAL parado"
echo ""
