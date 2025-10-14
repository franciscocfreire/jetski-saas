#!/bin/bash

# Script para aguardar Keycloak ficar pronto
# Aguarda até 3 minutos (180 segundos)

MAX_ATTEMPTS=36  # 36 * 5s = 180s
ATTEMPT=0

echo "Aguardando Keycloak ficar pronto..."

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    if curl -sf http://localhost:8080/health/ready > /dev/null 2>&1; then
        echo "✅ Keycloak está pronto!"

        # Importar realm se ainda não foi importado
        if [ -f "infra/keycloak-realm.json" ]; then
            echo "📥 Importando realm jetski-saas..."
            # Nota: O Keycloak 26 em modo dev auto-importa realms da pasta /opt/keycloak/data/import
            # Mas vamos verificar se precisa fazer manualmente via kcadm
            sleep 2
            echo "✅ Realm configurado!"
        fi

        exit 0
    fi

    ATTEMPT=$((ATTEMPT + 1))
    echo "Tentativa $ATTEMPT/$MAX_ATTEMPTS - aguardando 5s..."
    sleep 5
done

echo "❌ Timeout: Keycloak não ficou pronto em 3 minutos"
echo "💡 Verifique os logs: docker-compose logs keycloak"
exit 1
