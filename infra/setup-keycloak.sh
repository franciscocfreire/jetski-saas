#!/bin/bash

# Script para configurar Keycloak via kcadm.sh
# Assumindo que o Keycloak já está rodando

KEYCLOAK_URL="http://localhost:8080"
ADMIN_USER="admin"
ADMIN_PASS="admin"
REALM="jetski-saas"

echo "🔐 Fazendo login no Keycloak como admin..."

# Login
docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server $KEYCLOAK_URL \
  --realm master \
  --user $ADMIN_USER \
  --password $ADMIN_PASS

# Verificar se realm já existe
REALM_EXISTS=$(docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get realms/$REALM 2>/dev/null)

if [ -z "$REALM_EXISTS" ]; then
    echo "📦 Importando realm $REALM..."
    docker-compose exec -T keycloak /opt/keycloak/bin/kcadm.sh create realms \
      -f /opt/keycloak/data/import/realm.json
    echo "✅ Realm $REALM importado!"
else
    echo "ℹ️  Realm $REALM já existe"
fi

echo "✅ Keycloak configurado com sucesso!"
echo ""
echo "🔐 Credenciais de acesso:"
echo "   Admin Console: $KEYCLOAK_URL/admin (admin/admin)"
echo "   Realm: $REALM"
echo ""
echo "👥 Usuários de teste:"
echo "   admin@acme.com / admin123 (ADMIN_TENANT, GERENTE)"
echo "   operador@acme.com / operador123 (OPERADOR)"
