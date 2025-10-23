#!/bin/bash

set -e

OPA_BIN="/home/franciscocfreire/apps/opa"
POLICIES_DIR="/home/franciscocfreire/repos/jetski/policies"
OPA_PORT="8181"
LOG_FILE="/tmp/opa-local.log"

echo "========================================="
echo "  OPA Local - Jetski SaaS"
echo "========================================="

# 1. Verificar binário OPA
echo -e "\n[1/4] Verificando OPA..."
if [ ! -f "$OPA_BIN" ]; then
    echo "✗ OPA não encontrado em $OPA_BIN"
    echo "  Execute: cd /home/franciscocfreire/apps && curl -L -o opa https://openpolicyagent.org/downloads/v1.9.0/opa_linux_amd64_static && chmod +x opa"
    exit 1
fi
echo "✓ OPA encontrado: $($OPA_BIN version | head -1)"

# 2. Verificar políticas
echo -e "\n[2/4] Verificando políticas..."
if [ ! -d "$POLICIES_DIR" ]; then
    echo "✗ Diretório de políticas não encontrado: $POLICIES_DIR"
    exit 1
fi
POLICY_COUNT=$(find "$POLICIES_DIR" -name "*.rego" | wc -l)
echo "✓ $POLICY_COUNT políticas encontradas"

# 3. Parar instância anterior
echo -e "\n[3/4] Parando instâncias anteriores..."
pkill -f "opa run.*8181" 2>/dev/null && sleep 1 || echo "  Nenhuma instância anterior"

# 4. Iniciar OPA
echo -e "\n[4/4] Iniciando OPA..."
$OPA_BIN run \
    --server \
    --addr="localhost:$OPA_PORT" \
    --log-level=debug \
    --log-format=json-pretty \
    "$POLICIES_DIR" \
    > "$LOG_FILE" 2>&1 &

OPA_PID=$!
echo "  PID: $OPA_PID"

# Aguardar inicialização
echo -e "\n  Aguardando inicialização..."
for i in {1..10}; do
    if curl -s http://localhost:$OPA_PORT/health > /dev/null 2>&1; then
        echo ""
        echo "========================================="
        echo "✓✓✓ OPA INICIADO COM SUCESSO!"
        echo "========================================="
        echo ""
        echo "📊 Informações:"
        echo "  URL: http://localhost:$OPA_PORT"
        echo "  Health: http://localhost:$OPA_PORT/health"
        echo "  Metrics: http://localhost:$OPA_PORT/metrics"
        echo "  Políticas: $POLICIES_DIR"
        echo "  PID: $OPA_PID"
        echo ""
        echo "📝 Logs:"
        echo "  tail -f $LOG_FILE"
        echo ""
        echo "🛑 Parar:"
        echo "  kill $OPA_PID"
        echo "  # ou: pkill -f 'opa run.*8181'"
        echo ""
        echo "🧪 Testar:"
        echo "  curl http://localhost:8181/v1/data/jetski/rbac/allow \\"
        echo "    -d '{\"input\": {\"roles\": [\"GERENTE\"], \"action\": \"modelo:list\"}}'"
        echo ""
        exit 0
    fi
    printf "."
    sleep 1
done

echo ""
echo "========================================="
echo "✗ TIMEOUT - OPA não inicializou"
echo "========================================="
echo ""
echo "Últimas 30 linhas do log:"
tail -30 "$LOG_FILE"
exit 1
