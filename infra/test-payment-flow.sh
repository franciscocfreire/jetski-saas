#!/bin/bash
#
# Script de Teste do Fluxo de Pagamentos via API
# ==============================================
#
# Testa o fluxo completo de pagamentos:
# 1. Criar locação (walk-in)
# 2. Finalizar locação (check-out) - gera comissão
# 3. Aprovar comissão
# 4. Registrar presença (diária)
# 5. Verificar pendências
# 6. Registrar pagamento
# 7. Verificar histórico
#
# Uso:
#   ./test-payment-flow.sh [opções]
#
# Opções:
#   --help                      Mostra esta ajuda
#   --skip-locacao              Pula criação de locação (usa comissões existentes)
#   --tipo-pagamento PIX|DINHEIRO  Define tipo de pagamento (default: PIX)
#   --verbose                   Mostra responses completos
#

set -e

# ==============================================================================
# Configurações
# ==============================================================================
KEYCLOAK_URL="http://localhost:8080"
API_URL="http://localhost:8090/api"
CLIENT_ID="jetski-test"

# Usuários de teste
GERENTE_USER="gerente@acme.com"
GERENTE_PASS="gerente123"

# Opções padrão
SKIP_LOCACAO=false
TIPO_PAGAMENTO="PIX"
VERBOSE=false

# Cores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ==============================================================================
# Funções Auxiliares
# ==============================================================================

log_info() {
    echo -e "   ${GREEN}✓${NC} $1" >&2
}

log_warn() {
    echo -e "   ${YELLOW}⚠${NC} $1" >&2
}

log_error() {
    echo -e "   ${RED}✗${NC} $1" >&2
}

log_step() {
    echo -e "\n${BLUE}[$1]${NC} $2" >&2
}

log_verbose() {
    if [ "$VERBOSE" = true ]; then
        echo -e "   ${BLUE}→${NC} $1" >&2
    fi
}

show_help() {
    head -30 "$0" | tail -25
    exit 0
}

# ==============================================================================
# Parse Arguments
# ==============================================================================

while [[ $# -gt 0 ]]; do
    case $1 in
        --help)
            show_help
            ;;
        --skip-locacao)
            SKIP_LOCACAO=true
            shift
            ;;
        --tipo-pagamento)
            TIPO_PAGAMENTO="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        *)
            echo "Opção desconhecida: $1"
            exit 1
            ;;
    esac
done

# ==============================================================================
# 1. Autenticação
# ==============================================================================

get_token() {
    local user=$1
    local pass=$2

    local response=$(curl -s -X POST "${KEYCLOAK_URL}/realms/jetski-saas/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=${CLIENT_ID}" \
        -d "username=${user}" \
        -d "password=${pass}")

    local token=$(echo "$response" | jq -r '.access_token')

    if [ "$token" == "null" ] || [ -z "$token" ]; then
        log_error "Falha ao obter token para $user"
        log_verbose "Response: $response"
        exit 1
    fi

    echo "$token"
}

# ==============================================================================
# 2. Obter IDs do Sistema
# ==============================================================================

get_tenant_id() {
    local token=$1

    local response=$(curl -s "${API_URL}/v1/user/tenants" \
        -H "Authorization: Bearer $token")

    local tenant_id=$(echo "$response" | jq -r '.tenants[0].id')
    local tenant_name=$(echo "$response" | jq -r '.tenants[0].razaoSocial')

    if [ "$tenant_id" == "null" ] || [ -z "$tenant_id" ]; then
        log_error "Nenhum tenant encontrado"
        exit 1
    fi

    log_info "Tenant: ${tenant_id:0:8}... ($tenant_name)"
    echo "$tenant_id"
}

get_vendedor_id() {
    local token=$1
    local tenant_id=$2

    local response=$(curl -s "${API_URL}/v1/tenants/${tenant_id}/vendedores" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id")

    local vendedor_id=$(echo "$response" | jq -r '.[0].id')
    local vendedor_nome=$(echo "$response" | jq -r '.[0].nome')

    if [ "$vendedor_id" == "null" ] || [ -z "$vendedor_id" ]; then
        log_error "Nenhum vendedor encontrado"
        exit 1
    fi

    log_info "Vendedor: ${vendedor_id:0:8}... ($vendedor_nome)"
    echo "$vendedor_id"
}

get_jetski_disponivel() {
    local token=$1
    local tenant_id=$2

    local response=$(curl -s "${API_URL}/v1/tenants/${tenant_id}/jetskis" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id")

    # Busca jetski com status DISPONIVEL
    local jetski=$(echo "$response" | jq -r '[.[] | select(.status == "DISPONIVEL")] | .[0]')
    local jetski_id=$(echo "$jetski" | jq -r '.id')
    local jetski_serie=$(echo "$jetski" | jq -r '.serie')

    if [ "$jetski_id" == "null" ] || [ -z "$jetski_id" ]; then
        log_warn "Nenhum jetski disponível"
        return 1
    fi

    log_info "Jetski: $jetski_serie (disponível)"
    echo "$jetski_id"
}

# ==============================================================================
# 3. Criar Locação Walk-in
# ==============================================================================

create_walkin_locacao() {
    local token=$1
    local tenant_id=$2
    local jetski_id=$3
    local vendedor_id=$4

    local horimetro_inicio=$(echo "scale=1; $(shuf -i 100-500 -n 1)" | bc)

    local request_body=$(cat <<EOF
{
    "jetskiId": "$jetski_id",
    "vendedorId": "$vendedor_id",
    "horimetroInicio": $horimetro_inicio,
    "duracaoPrevista": 60,
    "valorNegociado": 200.00,
    "modalidadePreco": "PRECO_FECHADO",
    "observacoes": "Locação de teste via script"
}
EOF
)

    log_verbose "Request: $request_body"

    local response=$(curl -s -X POST "${API_URL}/v1/tenants/${tenant_id}/locacoes/check-in/walk-in" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id" \
        -H "Content-Type: application/json" \
        -d "$request_body")

    log_verbose "Response: $response"

    local locacao_id=$(echo "$response" | jq -r '.id')
    local valor=$(echo "$response" | jq -r '.valorTotal')

    if [ "$locacao_id" == "null" ] || [ -z "$locacao_id" ]; then
        log_error "Falha ao criar locação"
        log_error "Response: $response"
        return 1
    fi

    log_info "Locação criada: ${locacao_id:0:8}..."
    log_info "Valor: R\$ $valor"

    # Retorna id e horimetro
    echo "$locacao_id|$horimetro_inicio"
}

# ==============================================================================
# 4. Finalizar Locação (Check-out)
# ==============================================================================

checkout_locacao() {
    local token=$1
    local tenant_id=$2
    local locacao_id=$3
    local horimetro_inicio=$4

    local horimetro_fim=$(echo "scale=1; $horimetro_inicio + 1.5" | bc)

    local request_body=$(cat <<EOF
{
    "horimetroFim": $horimetro_fim,
    "checklistEntradaJson": "[\"OK\", \"OK\", \"OK\", \"OK\"]",
    "skipPhotos": true,
    "observacoes": "Check-out de teste"
}
EOF
)

    log_verbose "Request: $request_body"

    local response=$(curl -s -X POST "${API_URL}/v1/tenants/${tenant_id}/locacoes/${locacao_id}/check-out" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id" \
        -H "Content-Type: application/json" \
        -d "$request_body")

    log_verbose "Response: $response"

    local status=$(echo "$response" | jq -r '.status')

    if [ "$status" != "FINALIZADA" ]; then
        log_error "Falha no check-out"
        log_error "Response: $response"
        return 1
    fi

    log_info "Check-out realizado (status: $status)"
}

# ==============================================================================
# 5. Aprovar Comissão
# ==============================================================================

get_comissoes_pendentes() {
    local token=$1
    local tenant_id=$2

    local response=$(curl -s "${API_URL}/v1/tenants/${tenant_id}/comissoes/pendentes" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id")

    echo "$response"
}

aprovar_comissao() {
    local token=$1
    local tenant_id=$2
    local comissao_id=$3

    local response=$(curl -s -X POST "${API_URL}/v1/tenants/${tenant_id}/comissoes/${comissao_id}/aprovar" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id" \
        -H "Content-Type: application/json" \
        -d '{}')

    log_verbose "Response: $response"

    local status=$(echo "$response" | jq -r '.status')
    local valor=$(echo "$response" | jq -r '.valorComissao')

    if [ "$status" != "APROVADA" ]; then
        log_error "Falha ao aprovar comissão"
        return 1
    fi

    log_info "Comissão aprovada: ${comissao_id:0:8}... (R\$ $valor)"
}

# ==============================================================================
# 6. Registrar Presença (Diária)
# ==============================================================================

registrar_presenca() {
    local token=$1
    local tenant_id=$2
    local vendedor_id=$3

    local data_hoje=$(date +%Y-%m-%d)

    local request_body=$(cat <<EOF
{
    "dtReferencia": "$data_hoje",
    "presencas": [
        {
            "vendedorId": "$vendedor_id",
            "tipo": "INTEGRAL"
        }
    ]
}
EOF
)

    log_verbose "Request: $request_body"

    local response=$(curl -s -X POST "${API_URL}/v1/tenants/${tenant_id}/presencas/dia" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id" \
        -H "Content-Type: application/json" \
        -d "$request_body")

    log_verbose "Response: $response"

    # Check if it's an error response
    local error=$(echo "$response" | jq -r '.status // empty')
    if [ ! -z "$error" ] && [ "$error" != "null" ]; then
        local message=$(echo "$response" | jq -r '.message // .detail // "Erro desconhecido"')
        log_warn "Presença não registrada: $message"
        return 0
    fi

    local total=$(echo "$response" | jq -r '.totalDiarias // 0')
    log_info "Diária registrada: R\$ $total"
}

# ==============================================================================
# 7. Verificar Pendências de Pagamento
# ==============================================================================

verificar_pendencias() {
    local token=$1
    local tenant_id=$2
    local vendedor_id=$3

    local response=$(curl -s "${API_URL}/v1/tenants/${tenant_id}/pagamentos/pendencias/${vendedor_id}" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id")

    log_verbose "Response: $response"

    local valor_comissoes=$(echo "$response" | jq -r '.valorComissoes // 0')
    local qtd_comissoes=$(echo "$response" | jq -r '.qtdComissoes // 0')
    local valor_diarias=$(echo "$response" | jq -r '.valorDiarias // 0')
    local qtd_diarias=$(echo "$response" | jq -r '.qtdDiarias // 0')
    local valor_bonus=$(echo "$response" | jq -r '.valorBonus // 0')
    local qtd_bonus=$(echo "$response" | jq -r '.qtdBonus // 0')
    local valor_total=$(echo "$response" | jq -r '.valorTotal // 0')

    log_info "Pendências encontradas:"
    echo -e "     - Comissões: R\$ $valor_comissoes ($qtd_comissoes)" >&2
    echo -e "     - Diárias: R\$ $valor_diarias ($qtd_diarias)" >&2
    echo -e "     - Bônus: R\$ $valor_bonus ($qtd_bonus)" >&2
    echo -e "     - ${GREEN}Total: R\$ $valor_total${NC}" >&2

    echo "$valor_total"
}

# ==============================================================================
# 8. Registrar Pagamento
# ==============================================================================

registrar_pagamento() {
    local token=$1
    local tenant_id=$2
    local vendedor_id=$3
    local tipo_pagamento=$4

    local referencia="TEST-${tipo_pagamento}-$(date +%Y%m%d%H%M%S)"

    local request_body=$(cat <<EOF
{
    "tipoPagamento": "$tipo_pagamento",
    "referenciaPagamento": "$referencia",
    "observacoes": "Pagamento de teste via script"
}
EOF
)

    log_verbose "Request: $request_body"

    local response=$(curl -s -X POST "${API_URL}/v1/tenants/${tenant_id}/pagamentos/vendedores/${vendedor_id}/pagar" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id" \
        -H "Content-Type: application/json" \
        -d "$request_body")

    log_verbose "Response: $response"

    local pagamento_id=$(echo "$response" | jq -r '.id')
    local valor_total=$(echo "$response" | jq -r '.valorTotal')

    if [ "$pagamento_id" == "null" ] || [ -z "$pagamento_id" ]; then
        log_error "Falha ao registrar pagamento"
        log_error "Response: $response"
        return 1
    fi

    log_info "Pagamento registrado: ${pagamento_id:0:8}..."
    log_info "Valor pago: R\$ $valor_total"
    log_info "Referência: $referencia"

    echo "$pagamento_id"
}

# ==============================================================================
# 9. Verificar Histórico
# ==============================================================================

verificar_historico() {
    local token=$1
    local tenant_id=$2
    local pagamento_id=$3

    local response=$(curl -s "${API_URL}/v1/tenants/${tenant_id}/pagamentos/historico" \
        -H "Authorization: Bearer $token" \
        -H "X-Tenant-Id: $tenant_id")

    log_verbose "Response: $response"

    # Verifica se o pagamento está no histórico
    local found=$(echo "$response" | jq -r ".[] | select(.id == \"$pagamento_id\") | .id")

    if [ -z "$found" ]; then
        log_warn "Pagamento não encontrado no histórico"
        return 1
    fi

    log_info "Pagamento encontrado no histórico"
}

# ==============================================================================
# MAIN
# ==============================================================================

main() {
    echo -e "\n${BLUE}========================================${NC}" >&2
    echo -e "${BLUE}  Teste do Fluxo de Pagamentos${NC}" >&2
    echo -e "${BLUE}========================================${NC}" >&2

    # -------------------------------------------------------------------------
    # 1. Autenticação
    # -------------------------------------------------------------------------
    log_step "1/9" "Autenticando como $GERENTE_USER..."
    TOKEN=$(get_token "$GERENTE_USER" "$GERENTE_PASS")
    log_info "Token obtido"

    # -------------------------------------------------------------------------
    # 2. Obter IDs
    # -------------------------------------------------------------------------
    log_step "2/9" "Obtendo IDs do sistema..."
    TENANT_ID=$(get_tenant_id "$TOKEN")
    VENDEDOR_ID=$(get_vendedor_id "$TOKEN" "$TENANT_ID")

    if [ "$SKIP_LOCACAO" = false ]; then
        JETSKI_ID=$(get_jetski_disponivel "$TOKEN" "$TENANT_ID")
        if [ -z "$JETSKI_ID" ]; then
            log_warn "Pulando criação de locação (nenhum jetski disponível)"
            SKIP_LOCACAO=true
        fi
    fi

    # -------------------------------------------------------------------------
    # 3. Criar Locação (se não pulando)
    # -------------------------------------------------------------------------
    if [ "$SKIP_LOCACAO" = false ]; then
        log_step "3/9" "Criando locação walk-in..."
        LOCACAO_RESULT=$(create_walkin_locacao "$TOKEN" "$TENANT_ID" "$JETSKI_ID" "$VENDEDOR_ID")
        LOCACAO_ID=$(echo "$LOCACAO_RESULT" | cut -d'|' -f1)
        HORIMETRO_INICIO=$(echo "$LOCACAO_RESULT" | cut -d'|' -f2)

        # ---------------------------------------------------------------------
        # 4. Check-out
        # ---------------------------------------------------------------------
        log_step "4/9" "Finalizando locação (check-out)..."
        checkout_locacao "$TOKEN" "$TENANT_ID" "$LOCACAO_ID" "$HORIMETRO_INICIO"
    else
        log_step "3/9" "Pulando criação de locação..."
        log_info "(--skip-locacao)"
        log_step "4/9" "Pulando check-out..."
        log_info "(--skip-locacao)"
    fi

    # -------------------------------------------------------------------------
    # 5. Aprovar Comissões Pendentes
    # -------------------------------------------------------------------------
    log_step "5/9" "Aprovando comissões pendentes..."
    COMISSOES=$(get_comissoes_pendentes "$TOKEN" "$TENANT_ID")
    COMISSAO_COUNT=$(echo "$COMISSOES" | jq 'length')

    if [ "$COMISSAO_COUNT" -gt 0 ]; then
        # Aprovar apenas comissões do vendedor selecionado
        echo "$COMISSOES" | jq -r ".[] | select(.vendedorId == \"$VENDEDOR_ID\") | .id" | while read comissao_id; do
            if [ ! -z "$comissao_id" ]; then
                aprovar_comissao "$TOKEN" "$TENANT_ID" "$comissao_id"
            fi
        done
    else
        log_warn "Nenhuma comissão pendente para aprovar"
    fi

    # -------------------------------------------------------------------------
    # 6. Registrar Presença
    # -------------------------------------------------------------------------
    log_step "6/9" "Registrando presença do vendedor..."
    registrar_presenca "$TOKEN" "$TENANT_ID" "$VENDEDOR_ID"

    # -------------------------------------------------------------------------
    # 7. Verificar Pendências
    # -------------------------------------------------------------------------
    log_step "7/9" "Verificando pendências de pagamento..."
    VALOR_TOTAL=$(verificar_pendencias "$TOKEN" "$TENANT_ID" "$VENDEDOR_ID")

    if [ "$VALOR_TOTAL" == "0" ] || [ -z "$VALOR_TOTAL" ]; then
        log_warn "Nenhuma pendência para pagar"
        echo -e "\n${YELLOW}========================================${NC}" >&2
        echo -e "${YELLOW}  ⚠ TESTE PARCIAL - SEM PENDÊNCIAS${NC}" >&2
        echo -e "${YELLOW}========================================${NC}" >&2
        exit 0
    fi

    # -------------------------------------------------------------------------
    # 8. Registrar Pagamento
    # -------------------------------------------------------------------------
    log_step "8/9" "Registrando pagamento ($TIPO_PAGAMENTO)..."
    PAGAMENTO_ID=$(registrar_pagamento "$TOKEN" "$TENANT_ID" "$VENDEDOR_ID" "$TIPO_PAGAMENTO")

    # -------------------------------------------------------------------------
    # 9. Verificar Histórico
    # -------------------------------------------------------------------------
    log_step "9/9" "Verificando histórico..."
    verificar_historico "$TOKEN" "$TENANT_ID" "$PAGAMENTO_ID"

    # -------------------------------------------------------------------------
    # Resumo Final
    # -------------------------------------------------------------------------
    echo -e "\n${GREEN}========================================${NC}" >&2
    echo -e "${GREEN}  ✓ TESTE CONCLUÍDO COM SUCESSO!${NC}" >&2
    echo -e "${GREEN}========================================${NC}" >&2
    echo -e "\nResumo:" >&2
    if [ "$SKIP_LOCACAO" = false ]; then
        echo -e "  - Locação criada e finalizada" >&2
    fi
    echo -e "  - Comissão(ões) aprovada(s) e paga(s)" >&2
    echo -e "  - Diária registrada" >&2
    echo -e "  - ${GREEN}Total pago: R\$ $VALOR_TOTAL${NC}" >&2
    echo -e "  - Tipo: $TIPO_PAGAMENTO" >&2
    echo "" >&2
}

# Executa main com todos os argumentos
main "$@"
