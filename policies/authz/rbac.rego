# =============================================================================
# JETSKI SaaS - RBAC Policy (Role-Based Access Control)
# =============================================================================
#
# Esta política implementa controle de acesso baseado em roles.
#
# Conceitos importantes de Rego:
# 1. package: Define o namespace da política (similar a Java package)
# 2. import: Importa bibliotecas (future.keywords para sintaxe moderna)
# 3. default: Define valor padrão para uma regra
# 4. if: Condição para ativar uma regra
# 5. input: Dados enviados pelo Spring Boot (JSON)
# 6. data: Dados estáticos carregados do disco (configurações)
#
# =============================================================================

package jetski.authz.rbac

# Import keywords modernos do Rego
import future.keywords.if
import future.keywords.in
import future.keywords.every

# =============================================================================
# SEÇÃO 1: DEFAULTS
# =============================================================================
#
# No Rego, se uma regra não é satisfeita, ela retorna undefined.
# Usamos 'default' para garantir um valor padrão (negado por padrão).
#

default allow := false
default deny := true

# =============================================================================
# SEÇÃO 2: VALIDAÇÃO MULTI-TENANT
# =============================================================================
#
# CRÍTICO: Sempre validar que user.tenant_id == resource.tenant_id
# Previne acesso cross-tenant (vazamento de dados)
#

# Regra auxiliar: verifica se tenant é válido
tenant_is_valid if {
    input.user.tenant_id
    input.resource.tenant_id
    input.user.tenant_id == input.resource.tenant_id
}

# =============================================================================
# SEÇÃO 3: RBAC BÁSICO - ENDPOINTS PÚBLICOS
# =============================================================================

# Endpoint público: não requer autenticação
allow if {
    input.action == "public:access"
}

# =============================================================================
# SEÇÃO 4: RBAC - LISTAR RECURSOS
# =============================================================================

# OPERADOR pode listar modelos do seu tenant
allow if {
    input.action == "modelo:list"
    input.user.role == "OPERADOR"
    tenant_is_valid
}

# GERENTE pode listar modelos do seu tenant
allow if {
    input.action == "modelo:list"
    input.user.role == "GERENTE"
    tenant_is_valid
}

# ADMIN_TENANT pode listar modelos do seu tenant
allow if {
    input.action == "modelo:list"
    input.user.role == "ADMIN_TENANT"
    tenant_is_valid
}

# =============================================================================
# SEÇÃO 5: RBAC - CRIAR RECURSOS
# =============================================================================

# Apenas GERENTE e ADMIN_TENANT podem criar modelos
allow if {
    input.action == "modelo:create"
    input.user.role in ["GERENTE", "ADMIN_TENANT"]
    tenant_is_valid
}

# =============================================================================
# SEÇÃO 6: RBAC - LOCAÇÕES
# =============================================================================

# OPERADOR, GERENTE e ADMIN podem fazer check-in
allow if {
    input.action == "locacao:checkin"
    input.user.role in ["OPERADOR", "GERENTE", "ADMIN_TENANT"]
    tenant_is_valid
}

# OPERADOR, GERENTE e ADMIN podem fazer check-out
allow if {
    input.action == "locacao:checkout"
    input.user.role in ["OPERADOR", "GERENTE", "ADMIN_TENANT"]
    tenant_is_valid
}

# =============================================================================
# SEÇÃO 7: RBAC - FECHAMENTOS
# =============================================================================

# Apenas GERENTE e FINANCEIRO podem fechar caixa
allow if {
    input.action == "fechamento:diario"
    input.user.role in ["GERENTE", "FINANCEIRO", "ADMIN_TENANT"]
    tenant_is_valid
}

# Apenas FINANCEIRO pode fechar mensal
allow if {
    input.action == "fechamento:mensal"
    input.user.role in ["FINANCEIRO", "ADMIN_TENANT"]
    tenant_is_valid
}

# =============================================================================
# SEÇÃO 8: DECISÃO FINAL
# =============================================================================

# Negar se não passou por nenhuma regra allow
deny if {
    not allow
}

# =============================================================================
# SEÇÃO 9: METADATA (opcional - para auditoria)
# =============================================================================

# Retorna metadata sobre a decisão (útil para logs)
decision := {
    "allow": allow,
    "deny": deny,
    "tenant_valid": tenant_is_valid,
    "user": input.user.id,
    "action": input.action,
    "timestamp": time.now_ns()
}
