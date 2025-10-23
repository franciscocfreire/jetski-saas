#!/bin/bash

###############################################################################
# Reset Completo do Ambiente Local - Jetski SaaS
###############################################################################

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"

echo "🔄 INICIANDO RESET COMPLETO DO AMBIENTE..."
echo ""

# 1. Parar backend se estiver rodando
echo "1️⃣  Parando backend..."
lsof -ti:8090 | xargs kill -9 2>/dev/null || true
sleep 2

# 1.5. Limpar cache do Redis (antes de derrubar os containers)
echo "1️⃣.5 Limpando cache do Redis..."
if docker ps --format '{{.Names}}' | grep -q jetski-redis 2>/dev/null; then
    docker exec jetski-redis redis-cli FLUSHDB > /dev/null 2>&1 && echo "   ✅ Cache do Redis limpo!" || echo "   ⚠️  Redis pode não estar respondendo"
else
    echo "   ⚠️  Container Redis não encontrado (será recriado limpo)"
fi

# 2. Parar e remover containers + volumes
echo "2️⃣  Parando e removendo containers Docker..."
cd "$SCRIPT_DIR"
docker-compose down -v

# 3. Limpar volumes órfãos
echo "3️⃣  Limpando volumes órfãos..."
docker volume prune -f

# 4. Subir containers novamente
echo "4️⃣  Subindo containers (PostgreSQL, Redis, Keycloak, OPA)..."
docker-compose up -d

# 5. Aguardar PostgreSQL ficar pronto
echo "5️⃣  Aguardando PostgreSQL ficar pronto..."
for i in {1..30}; do
  if docker exec jetski-postgres pg_isready -U jetski > /dev/null 2>&1; then
    echo "   ✅ PostgreSQL está pronto!"
    break
  fi
  echo "   ⏳ Tentativa $i/30..."
  sleep 2
done

# 6. Criar banco jetski_local
echo "6️⃣  Criando banco jetski_local..."
docker exec jetski-postgres psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS jetski_local;" 2>/dev/null || true
docker exec jetski-postgres psql -U postgres -d postgres -c "CREATE DATABASE jetski_local OWNER jetski;"

# 7. Aguardar Keycloak ficar pronto
echo "7️⃣  Aguardando Keycloak ficar pronto (pode levar até 90s)..."
for i in {1..45}; do
  if curl -sf http://localhost:8080/health/ready > /dev/null 2>&1; then
    echo "   ✅ Keycloak está pronto!"
    break
  fi
  echo "   ⏳ Tentativa $i/45..."
  sleep 2
done

# 8. Executar setup do Keycloak
echo "8️⃣  Configurando Keycloak (realm, roles, users)..."
if [ -f "$SCRIPT_DIR/infra/setup-keycloak.sh" ]; then
  bash "$SCRIPT_DIR/infra/setup-keycloak.sh"
else
  echo "   ⚠️  Script setup-keycloak.sh não encontrado"
fi

# 9. Rodar migrations do Flyway
echo "9️⃣  Executando migrations do banco..."
cd "$BACKEND_DIR"
mvn flyway:migrate -q

# 10. Verificar migrations aplicadas
echo "🔍 Verificando migrations aplicadas..."
mvn flyway:info -q | tail -20

echo ""
echo "✅ RESET COMPLETO FINALIZADO COM SUCESSO!"
echo ""
echo "📋 Próximos passos:"
echo "   1. cd backend"
echo "   2. SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
echo ""
echo "🌐 URLs importantes:"
echo "   - Backend:   http://localhost:8090/api"
echo "   - Keycloak:  http://localhost:8080 (admin/admin)"
echo "   - PostgreSQL: localhost:5432 (jetski/dev123)"
echo "   - Redis:     localhost:6379"
echo "   - OPA:       http://localhost:8181"
echo ""
