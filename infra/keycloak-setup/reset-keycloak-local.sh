#!/bin/bash

set -e

echo "========================================="
echo "  RESET KEYCLOAK LOCAL (Porta 5433)"
echo "========================================="
echo ""
echo "⚠️  ATENÇÃO: Isso vai APAGAR todos os dados do Keycloak LOCAL!"
echo ""
read -p "Tem certeza que deseja continuar? (digite 'SIM' em maiúsculas): " confirm

if [ "$confirm" != "SIM" ]; then
    echo "❌ Cancelado pelo usuário"
    exit 1
fi

POSTGRES_HOST="localhost"
POSTGRES_PORT="5433"  # PostgreSQL LOCAL

echo ""
echo "=== [1/6] Parando Keycloak ==="
pkill -f "keycloak.*8081" 2>/dev/null && echo "✓ Keycloak parado" || echo "✓ Nenhuma instância rodando"
sleep 2

echo ""
echo "=== [2/6] Dropando database Keycloak LOCAL ==="
PGPASSWORD=postgres psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U postgres <<EOF
-- Desconectar todas as conexões
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'keycloak' AND pid <> pg_backend_pid();

-- Dropar database
DROP DATABASE IF EXISTS keycloak;

-- Dropar usuário se existir
DROP USER IF EXISTS keycloak;
EOF
echo "✓ Database 'keycloak' removido do PostgreSQL LOCAL (porta $POSTGRES_PORT)"

echo ""
echo "=== [3/6] Recriando database e usuário ==="
PGPASSWORD=postgres psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U postgres <<EOF
-- Criar usuário
CREATE USER keycloak WITH PASSWORD 'keycloak123';

-- Criar database
CREATE DATABASE keycloak OWNER keycloak;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
EOF

# Conectar ao database e dar permissões no schema
PGPASSWORD=keycloak123 psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U keycloak -d keycloak <<EOF
GRANT ALL ON SCHEMA public TO keycloak;
EOF

echo "✓ Database 'keycloak' criado no PostgreSQL LOCAL (porta $POSTGRES_PORT)"
echo "✓ Usuário 'keycloak' criado com senha 'keycloak123'"

echo ""
echo "=== [4/6] Limpando diretório de dados do Keycloak ==="
KC_DATA_DIR="/home/franciscocfreire/apps/keycloak-26.4.1/data"
if [ -d "$KC_DATA_DIR/h2" ]; then
    rm -rf "$KC_DATA_DIR/h2"
    echo "✓ Removido H2 data"
fi
if [ -d "$KC_DATA_DIR/tmp" ]; then
    rm -rf "$KC_DATA_DIR/tmp"
    echo "✓ Removido temp data"
fi
echo "✓ Diretório de dados limpo"

echo ""
echo "=== [5/6] Iniciando Keycloak LOCAL ==="
/home/franciscocfreire/repos/jetski/infra/keycloak-setup/start-keycloak-local.sh

echo ""
echo "=== [6/6] Aguardando Keycloak ficar pronto ==="
echo "Aguardando 10 segundos para Keycloak inicializar completamente..."
sleep 10

if curl -sf http://localhost:8081/health/ready >/dev/null 2>&1; then
    echo "✓ Keycloak está pronto!"
else
    echo "⚠️  Keycloak ainda não está pronto. Aguarde mais alguns segundos."
    echo "   Monitore: tail -f /tmp/keycloak-local.log"
    exit 1
fi

echo ""
echo "========================================="
echo "  ✓✓✓ RESET LOCAL CONCLUÍDO!"
echo "========================================="
echo ""
echo "📊 Status:"
echo "  PostgreSQL: LOCAL (porta 5433)"
echo "  Database: keycloak (limpo)"
echo "  Keycloak: rodando na porta 8081"
echo "  Admin: admin / admin"
echo ""
echo "🔜 Próximo passo:"
echo "  Importar realm com usuários de teste:"
echo "  ./infra/keycloak-setup/setup-keycloak-local.sh"
echo ""
