#!/bin/bash

set -e

KC_HOME="/home/franciscocfreire/apps/keycloak-26.4.1"
POSTGRES_HOST="localhost"
POSTGRES_PORT="5433"  # PostgreSQL LOCAL (não Docker!)
POSTGRES_DB="keycloak"
POSTGRES_USER="keycloak"
POSTGRES_PASSWORD="keycloak123"

echo "========================================="
echo "Keycloak LOCAL com PostgreSQL LOCAL"
echo "========================================="

# 1. Verificar PostgreSQL LOCAL
echo -e "\n[1/5] Verificando PostgreSQL LOCAL (porta 5433)..."
if ! PGPASSWORD=postgres psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U postgres -c '\q' 2>/dev/null; then
    echo "✗ PostgreSQL LOCAL não está acessível em $POSTGRES_HOST:$POSTGRES_PORT"
    echo "  Verifique se PostgreSQL está rodando:"
    echo "    sudo systemctl status postgresql"
    echo "    sudo systemctl start postgresql"
    exit 1
fi
echo "✓ PostgreSQL LOCAL acessível"

# 2. Criar database se não existir
echo -e "\n[2/5] Configurando database..."
PGPASSWORD=postgres psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U postgres <<EOF >/dev/null 2>&1 || true
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_user WHERE usename = '$POSTGRES_USER') THEN
    CREATE USER $POSTGRES_USER WITH PASSWORD '$POSTGRES_PASSWORD';
  END IF;
END
\$\$;

SELECT 'CREATE DATABASE $POSTGRES_DB OWNER $POSTGRES_USER'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$POSTGRES_DB')\gexec

GRANT ALL PRIVILEGES ON DATABASE $POSTGRES_DB TO $POSTGRES_USER;
EOF

# Conectar ao database e dar permissões no schema
PGPASSWORD=$POSTGRES_PASSWORD psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -d $POSTGRES_DB <<EOF >/dev/null 2>&1 || true
GRANT ALL ON SCHEMA public TO $POSTGRES_USER;
EOF

echo "✓ Database configurado"

# 3. Build Keycloak se necessário
echo -e "\n[3/5] Verificando build do Keycloak..."
cd $KC_HOME
if [ ! -f "lib/lib/main/org.postgresql.postgresql-*.jar" ]; then
    echo "  Building Keycloak com PostgreSQL driver..."
    ./bin/kc.sh build --db=postgres
    echo "✓ Build concluído"
else
    echo "✓ Build já existe"
fi

# 4. Parar instância anterior
echo -e "\n[4/5] Parando instâncias anteriores..."
pkill -f "keycloak.*8081" 2>/dev/null && sleep 2 || echo "  Nenhuma instância anterior"

# 5. Iniciar Keycloak
echo -e "\n[5/5] Iniciando Keycloak LOCAL..."
KC_DB=postgres \
KC_DB_URL="jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB" \
KC_DB_USERNAME=$POSTGRES_USER \
KC_DB_PASSWORD=$POSTGRES_PASSWORD \
KEYCLOAK_ADMIN=admin \
KEYCLOAK_ADMIN_PASSWORD=admin \
./bin/kc.sh start-dev \
  --http-port=8081 \
  > /tmp/keycloak-local.log 2>&1 &

KEYCLOAK_PID=$!
echo "  PID: $KEYCLOAK_PID"

# Aguardar inicialização
echo -e "\n  Aguardando inicialização..."
for i in {1..40}; do
  if curl -s http://localhost:8081/health/ready >/dev/null 2>&1; then
    echo ""
    echo "========================================="
    echo "✓✓✓ Keycloak LOCAL INICIADO!"
    echo "========================================="
    echo ""
    echo "📊 Informações:"
    echo "  URL: http://localhost:8081"
    echo "  Admin Console: http://localhost:8081/admin"
    echo "  Usuário: admin"
    echo "  Senha: admin"
    echo "  Database: PostgreSQL LOCAL ($POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB)"
    echo "  PID: $KEYCLOAK_PID"
    echo ""
    echo "📝 Logs:"
    echo "  tail -f /tmp/keycloak-local.log"
    echo ""
    echo "🛑 Parar:"
    echo "  kill $KEYCLOAK_PID"
    echo "  # ou: pkill -f 'keycloak.*8081'"
    echo ""

    # Verificar tabelas criadas
    TABLE_COUNT=$(PGPASSWORD=$POSTGRES_PASSWORD psql -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -d $POSTGRES_DB -t -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';" 2>/dev/null | tr -d ' ')
    if [ "$TABLE_COUNT" -gt "50" ]; then
        echo "✓ Database inicializado ($TABLE_COUNT tabelas criadas)"
    fi
    echo ""

    exit 0
  fi
  printf "."
  sleep 2
done

echo ""
echo "========================================="
echo "✗ TIMEOUT - Keycloak não inicializou"
echo "========================================="
echo ""
echo "Últimas 30 linhas do log:"
tail -30 /tmp/keycloak-local.log
exit 1
