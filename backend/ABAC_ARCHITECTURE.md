# ABAC Architecture - Jetski SaaS Multi-Tenant

## Visão Geral

O sistema Jetski migrou de **RBAC (Role-Based Access Control)** para **ABAC (Attribute-Based Access Control)** usando OPA (Open Policy Agent) para autorização.

### Por que ABAC?

**RBAC** funciona bem para sistemas simples, mas em um **SaaS multi-tenant B2B** com regras de negócio complexas, o ABAC oferece:

✅ **Sem explosão de roles**: Evita criação de roles como `GERENTE_TENANT_A`, `GERENTE_TENANT_B`, etc.
✅ **Alçada automática**: Desconto de 15% requer aprovação de GERENTE automaticamente
✅ **Multi-tenancy nativo**: Isolamento por `tenant_id` na política, não na aplicação
✅ **Regras de negócio centralizadas**: RN06 (jetski em manutenção) vira política OPA
✅ **Contexto dinâmico**: Horário comercial, IP, device, ambiente
✅ **Auditoria e compliance**: Logs de decisões OPA rastreáveis

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                         HTTP Request                            │
│  GET /v1/locacoes/123 + X-Tenant-Id + Authorization: Bearer JWT │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│               Spring Security Filter Chain                       │
│  1. CORS → 2. OAuth2 (JWT validation) → 3. TenantFilter         │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│          ABACAuthorizationInterceptor (Spring MVC)               │
│  • ActionExtractor: GET /v1/locacoes/123 → "locacao:view"       │
│  • Builds OPAInput: user + resource + context                   │
│  • Calls OPA: POST http://opa:8181/v1/data/jetski/authorization │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                  OPA (Open Policy Agent)                         │
│  Evaluates 6 policies in parallel:                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  1. multi_tenant.rego  → tenant_is_valid?                │  │
│  │  2. rbac.rego          → role has permission?            │  │
│  │  3. alcada.rego        → within authority limit?         │  │
│  │  4. business_rules.rego → jetski in maintenance?         │  │
│  │  5. context.rego       → business hours? IP allowed?     │  │
│  │  6. authorization.rego → combine all + return decision   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  Returns:                                                        │
│  {                                                               │
│    "allow": true,                                                │
│    "tenant_is_valid": true,                                      │
│    "requer_aprovacao": false,                                    │
│    "aprovador_requerido": null,                                  │
│    "deny_reasons": [],                                           │
│    "warnings": []                                                │
│  }                                                               │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
       ┌───────────────┴────────────────┐
       │ allow=true?                    │
       └───┬────────────────────┬───────┘
           │ YES                │ NO
           ▼                    ▼
    ┌──────────────┐    ┌──────────────────────┐
    │  Controller  │    │ 403 Forbidden        │
    │  Executes    │    │ (AccessDeniedException)
    └──────────────┘    └──────────────────────┘
```

---

## Componentes

### 1. Java Components

#### `ABACAuthorizationInterceptor`
**Localização**: `com.jetski.shared.authorization.ABACAuthorizationInterceptor`

Intercepta **todos os requests** em `/v1/**` (exceto públicos) antes de chegar ao controller.

**Responsabilidades**:
- Extrai `action` do request usando `ActionExtractor`
- Constrói `OPAInput` com:
  - **User**: `id`, `tenant_id`, `role` (do JWT)
  - **Resource**: `id`, `tenant_id` (do path e TenantContext)
  - **Context**: `timestamp`, `ip`, `device`, `user_agent`, `environment`
- Chama OPA via `OPAAuthorizationService.authorize()`
- Lança `AccessDeniedException` se `allow=false`

**Exemplo**:
```java
// Request: POST /v1/locacoes/123/desconto
OPAInput input = OPAInput.builder()
    .action("locacao:desconto")
    .user(UserContext.builder()
        .id("operador@test.com")
        .tenant_id("abc-def-ghi")
        .role("OPERADOR")
        .build())
    .resource(ResourceContext.builder()
        .id("123")
        .tenant_id("abc-def-ghi")
        .build())
    .context(ContextAttributes.builder()
        .timestamp("2025-01-20T14:30:00Z")
        .ip("192.168.1.100")
        .device("mobile")
        .build())
    .operation(OperationContext.builder()
        .percentual_desconto(BigDecimal.valueOf(12))
        .build())
    .build();
```

#### `ActionExtractor`
**Localização**: `com.jetski.shared.authorization.ActionExtractor`

Mapeia HTTP requests para actions OPA usando padrões RESTful.

**Regras**:
- `GET /v1/locacoes` → `locacao:list`
- `GET /v1/locacoes/123` → `locacao:view`
- `POST /v1/locacoes` → `locacao:create`
- `PUT /v1/locacoes/123` → `locacao:update`
- `DELETE /v1/locacoes/123` → `locacao:delete`
- `POST /v1/locacoes/123/checkin` → `locacao:checkin` (custom sub-action)
- `POST /v1/os/456/aprovar` → `o:aprovar`

**Singularização**: `locacoes` → `locacao`, `jetskis` → `jetski`

#### `OPAAuthorizationService`
**Localização**: `com.jetski.shared.authorization.OPAAuthorizationService`

Cliente HTTP para OPA usando Spring WebClient.

**Endpoints OPA**:
- `/v1/data/jetski/authorization/result` - **Autorização completa** (usa este!)
- `/v1/data/jetski/rbac/allow` - RBAC isolado
- `/v1/data/jetski/alcada/allow` - Alçada isolada

**Métodos**:
```java
public OPADecision authorize(OPAInput input)            // ✅ Main method
public OPADecision authorizeRBAC(OPAInput input)       // Teste RBAC
public OPADecision authorizeAlcada(OPAInput input)     // Teste Alçada
```

---

### 2. OPA Policies (Rego)

Localizadas em: `src/main/resources/opa/policies/`

#### `multi_tenant.rego`
**Propósito**: Isola recursos por tenant.

```rego
tenant_is_valid if {
    input.user.tenant_id == input.resource.tenant_id
}

tenant_is_valid if {
    input.user.unrestricted_access == true  # Platform Admin
}

deny contains "Multi-tenant: usuário e recurso pertencem a tenants diferentes" if {
    not tenant_is_valid
}
```

**Testes**: `multi_tenant_test.rego` (39 testes)

---

#### `rbac.rego`
**Propósito**: Mapeia roles para permissões.

**7 Roles**:
1. **OPERADOR**: pier operations (checkin, checkout, abastecimento)
2. **GERENTE**: wildcard `locacao:*`, desconto, OS approval, daily closure
3. **FINANCEIRO**: closures, commissions, reports
4. **MECANICO**: OS management, jetski maintenance
5. **VENDEDOR**: reservations, clients, view own commissions
6. **ADMIN_TENANT**: wildcard `*` (all tenant actions)
7. **PLATFORM_ADMIN**: unrestricted (cross-tenant)

**Permissões** (exemplo OPERADOR):
```rego
role_permissions := {
    "OPERADOR": [
        "locacao:list",
        "locacao:view",
        "locacao:checkin",
        "locacao:checkout",
        "abastecimento:registrar",
        "jetski:list",
        "jetski:view",
        "modelo:list",
        "cliente:list",
        "cliente:view"
    ]
}
```

**Wildcard matching**:
- `locacao:*` matches `locacao:list`, `locacao:checkin`, etc.
- `*` matches any action (ADMIN_TENANT)

**Testes**: `rbac_test.rego` (45+ testes)

---

#### `alcada.rego`
**Propósito**: Approval authority (alçada) com escalonamento automático.

**Descontos**:
| Role          | Limite | Aprovador requerido |
|---------------|--------|---------------------|
| OPERADOR      | 10%    | GERENTE             |
| GERENTE       | 25%    | ADMIN_TENANT        |
| ADMIN_TENANT  | 50%    | PLATFORM_ADMIN      |

**Aprovação de OS**:
| Role          | Limite    | Aprovador requerido |
|---------------|-----------|---------------------|
| OPERADOR      | R$ 2.000  | GERENTE             |
| GERENTE       | R$ 10.000 | ADMIN_TENANT        |
| ADMIN_TENANT  | ilimitado | -                   |

**Exemplo**:
```rego
# OPERADOR aplica desconto de 12%
allow_desconto_operador if {
    input.user.role == "OPERADOR"
    input.action == "desconto:aplicar"
    input.operation.percentual_desconto <= 10  # ❌ 12% > 10%
}

requer_aprovacao if {
    input.user.role == "OPERADOR"
    input.operation.percentual_desconto > 10
    input.operation.percentual_desconto <= 25
}

aprovador_requerido := "GERENTE" if {
    input.user.role == "OPERADOR"
    input.operation.percentual_desconto > 10
}
```

**Resultado OPA**:
```json
{
  "allow": false,
  "requer_aprovacao": true,
  "aprovador_requerido": "GERENTE"
}
```

**Testes**: `alcada_test.rego` (50+ testes)

---

#### `business_rules.rego`
**Propósito**: Regras de negócio específicas do domínio.

**RN06**: Jetski em manutenção não pode ser reservado
```rego
deny_manutencao contains msg if {
    input.action == "reserva:criar"
    jetski := data.jetskis[input.resource.jetski_id]
    jetski.status == "manutencao"
    msg := "Jetski em manutenção não disponível para reserva (RN06)"
}
```

**Lifecycle**: Checkout requer check-in
```rego
deny_lifecycle contains msg if {
    input.action == "locacao:checkout"
    locacao := data.locacoes[input.resource.id]
    locacao.status != "em_andamento"
    msg := "Checkout só é permitido após check-in (status: em_andamento)"
}
```

**Fotos obrigatórias**: 4 fotos no checkout
```rego
deny_fotos contains msg if {
    input.action == "locacao:checkout"
    input.operation.fotos_count < 4
    msg := "Checkout requer mínimo 4 fotos"
}
```

**Fechamento travado**: Edições bloqueadas após fechamento diário
```rego
deny_fechamento contains msg if {
    input.action in ["locacao:update", "locacao:delete"]
    data_checkin := extract_date(input.resource.dt_checkin)
    fechamento := data.fechamentos_diarios[data_checkin]
    fechamento.locked == true
    msg := sprintf("Dia %s já foi fechado. Edições bloqueadas.", [data_checkin])
}
```

**Testes**: `business_rules_test.rego` (40+ testes)

---

#### `context.rego`
**Propósito**: Políticas contextuais (tempo, IP, device).

**Horário comercial**: 8h-20h
```rego
is_horario_comercial if {
    timestamp_ns := time.parse_rfc3339_ns(input.context.timestamp)
    [hora, _, _] := time.clock(timestamp_ns)
    hora >= 8
    hora < 20
}

deny_horario contains msg if {
    input.action in ["locacao:checkin", "locacao:checkout"]
    not is_horario_comercial
    msg := "Operações de pier permitidas apenas em horário comercial (8h-20h)"
}
```

**Dia útil**: Segunda a sexta
```rego
is_dia_util if {
    timestamp_ns := time.parse_rfc3339_ns(input.context.timestamp)
    [_, _, dia_semana] := time.weekday(timestamp_ns)
    dia_semana in ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
}

deny_horario contains msg if {
    input.action == "fechamento:diario"
    not is_dia_util
    msg := "Fechamento diário só permitido em dias úteis"
}
```

**IP whitelist/blacklist** (exemplo):
```rego
is_ip_allowed if {
    # Se whitelist existe, IP deve estar nela
    count(data.ip_whitelist) > 0
    input.context.ip in data.ip_whitelist
}

is_ip_allowed if {
    # Se não há whitelist, verifica blacklist
    count(data.ip_whitelist) == 0
    not input.context.ip in data.ip_blacklist
}
```

**Device detection**:
```rego
is_mobile if {
    contains(lower(input.context.user_agent), "iphone")
}

warnings contains msg if {
    input.action in ["fechamento:mensal", "os:aprovar"]
    is_mobile
    msg := "Ação de alto valor sendo executada via mobile. Considere usar desktop."
}
```

**Testes**: `context_test.rego` (45+ testes)

---

#### `authorization.rego`
**Propósito**: Política principal que combina todas as outras.

```rego
import data.jetski.rbac
import data.jetski.alcada
import data.jetski.multi_tenant
import data.jetski.business_rules
import data.jetski.context

# Main decision
allow if {
    tenant_is_valid         # Multi-tenant
    rbac_allow              # RBAC
    alcada_allow            # Alçada
    business_allow          # Business rules
    context_allow           # Context
}

# Result structure
result := {
    "allow": allow,
    "tenant_is_valid": multi_tenant.tenant_is_valid,
    "requer_aprovacao": alcada.requer_aprovacao,
    "aprovador_requerido": object.get(alcada.aprovador_requerido, null, null),
    "deny_reasons": deny,
    "warnings": warnings
}
```

**Agregação de denies**:
```rego
deny contains msg if {
    not multi_tenant.tenant_is_valid
    msg := "Multi-tenant: usuário e recurso pertencem a tenants diferentes"
}

deny contains msg if {
    not rbac.allow
    msg := sprintf("RBAC: role '%s' não tem permissão para '%s'",
        [input.user.role, input.action])
}

deny[msg] {
    msg := business_rules.deny_manutencao[_]
}
```

**Testes**: `authorization_test.rego` (30+ testes de integração)

---

## Fluxo Completo

### Exemplo 1: OPERADOR faz check-in (✅ PERMITIDO)

**Request**:
```http
POST /v1/locacoes/123/checkin
X-Tenant-Id: abc-def-ghi
Authorization: Bearer eyJ... (JWT com role=OPERADOR)
```

**OPAInput**:
```json
{
  "action": "locacao:checkin",
  "user": {
    "id": "operador@test.com",
    "tenant_id": "abc-def-ghi",
    "role": "OPERADOR"
  },
  "resource": {
    "id": "123",
    "tenant_id": "abc-def-ghi",
    "jetski_id": "jetski-456"
  },
  "context": {
    "timestamp": "2025-01-20T14:30:00Z",
    "ip": "192.168.1.100"
  }
}
```

**OPA Evaluation**:
1. ✅ `multi_tenant.tenant_is_valid`: `abc-def-ghi` == `abc-def-ghi`
2. ✅ `rbac.allow`: `OPERADOR` tem `locacao:checkin` nas permissões
3. ✅ `alcada.allow`: não há regra de alçada para checkin
4. ✅ `business_rules.allow`: jetski não está em manutenção
5. ✅ `context.allow`: 14:30 está em horário comercial (8h-20h)

**OPADecision**:
```json
{
  "allow": true,
  "tenant_is_valid": true,
  "requer_aprovacao": false,
  "aprovador_requerido": null,
  "deny_reasons": [],
  "warnings": []
}
```

**Resultado**: ✅ Request processado pelo controller

---

### Exemplo 2: OPERADOR aplica desconto de 15% (❌ NEGADO - requer aprovação)

**Request**:
```http
POST /v1/locacoes/123/desconto
X-Tenant-Id: abc-def-ghi
Content-Type: application/json

{
  "percentual": 15
}
```

**OPAInput**:
```json
{
  "action": "locacao:desconto",
  "user": {
    "role": "OPERADOR",
    "tenant_id": "abc-def-ghi"
  },
  "resource": {
    "id": "123",
    "tenant_id": "abc-def-ghi"
  },
  "operation": {
    "percentual_desconto": 15
  }
}
```

**OPA Evaluation**:
1. ✅ `multi_tenant.tenant_is_valid`
2. ❌ `alcada.allow`: OPERADOR limite = 10%, requisitado = 15%

**OPADecision**:
```json
{
  "allow": false,
  "tenant_is_valid": true,
  "requer_aprovacao": true,
  "aprovador_requerido": "GERENTE",
  "deny_reasons": [
    "Alçada: desconto de 15% requer aprovação de GERENTE (limite OPERADOR: 10%)"
  ],
  "warnings": []
}
```

**Resultado**: ❌ `403 Forbidden` com mensagem: "Ação 'locacao:desconto' requer aprovação de: GERENTE"

---

### Exemplo 3: OPERADOR tenta acessar locação de outro tenant (❌ NEGADO - multi-tenant violation)

**Request**:
```http
GET /v1/locacoes/999
X-Tenant-Id: tenant-xyz
```

**OPAInput**:
```json
{
  "action": "locacao:view",
  "user": {
    "tenant_id": "abc-def-ghi",  // Do JWT
    "role": "OPERADOR"
  },
  "resource": {
    "id": "999",
    "tenant_id": "tenant-xyz"     // Do banco de dados
  }
}
```

**OPA Evaluation**:
1. ❌ `multi_tenant.tenant_is_valid`: `abc-def-ghi` != `tenant-xyz`

**OPADecision**:
```json
{
  "allow": false,
  "tenant_is_valid": false,
  "deny_reasons": [
    "Multi-tenant: usuário e recurso pertencem a tenants diferentes"
  ]
}
```

**Resultado**: ❌ `403 Forbidden`

---

## Testes

### Executar Testes OPA

```bash
# Instalar OPA
brew install opa  # macOS
# ou
wget https://openpolicyagent.org/downloads/latest/opa_linux_amd64 -O opa
chmod +x opa

# Executar testes
cd backend
opa test -v src/main/resources/opa/policies/

# Output:
# rbac_test.rego:
#   ✓ test_operador_can_list_locacoes
#   ✓ test_operador_cannot_apply_desconto
#   ... (45 testes RBAC)
# alcada_test.rego:
#   ✓ test_operador_can_apply_10_percent_desconto
#   ✓ test_operador_11_percent_requires_gerente
#   ... (50 testes Alçada)
# Total: 200+ testes PASSED
```

### Executar Testes Java

```bash
# Unit tests
mvn test -Dtest=ActionExtractorTest

# Integration tests
mvn test -Dtest=ABACAuthorizationInterceptorTest

# Todos os testes
mvn clean test
```

### Coverage
- **OPA Policies**: 200+ Rego tests
- **ActionExtractor**: 30+ unit tests
- **ABACAuthorizationInterceptor**: 25+ integration tests

---

## Como Adicionar Novas Políticas

### 1. Novo Action (ex: `jetski:transferir`)

**Passo 1**: Mapear no controller
```java
@PostMapping("/jetskis/{id}/transferir")
public ResponseEntity<?> transferirJetski(@PathVariable String id) {
    // Action extraído automaticamente: "jetski:transferir"
}
```

**Passo 2**: Adicionar permissão em `rbac.rego`
```rego
role_permissions := {
    "GERENTE": [
        "jetski:*",  // Wildcard já cobre
        // ou explicitamente:
        "jetski:transferir"
    ]
}
```

**Passo 3**: Adicionar regra de negócio em `business_rules.rego` (opcional)
```rego
deny_transferencia contains msg if {
    input.action == "jetski:transferir"
    jetski := data.jetskis[input.resource.jetski_id]
    jetski.status == "em_uso"
    msg := "Jetski em uso não pode ser transferido"
}
```

**Passo 4**: Adicionar testes
```rego
# rbac_test.rego
test_gerente_can_transfer_jetski if {
    allow with input as {
        "user": {"role": "GERENTE"},
        "action": "jetski:transferir"
    }
}
```

---

### 2. Novo Limite de Alçada (ex: valor máximo de locação)

**Passo 1**: Adicionar em `alcada.rego`
```rego
# Limites de valor de locação
allow_locacao_operador if {
    input.user.role == "OPERADOR"
    input.action == "locacao:create"
    input.operation.valor_total <= 5000
}

allow_locacao_gerente if {
    input.user.role == "GERENTE"
    input.action == "locacao:create"
    input.operation.valor_total <= 20000
}

requer_aprovacao if {
    input.user.role == "OPERADOR"
    input.operation.valor_total > 5000
}

aprovador_requerido := "GERENTE" if {
    input.user.role == "OPERADOR"
    input.operation.valor_total > 5000
    input.operation.valor_total <= 20000
}
```

**Passo 2**: Enviar `valor_total` no `OPAInput.operation`
```java
OPAInput input = OPAInput.builder()
    .action("locacao:create")
    .operation(OperationContext.builder()
        .valor_total(new BigDecimal("8000"))
        .build())
    .build();
```

---

### 3. Nova Regra Contextual (ex: bloquear ações fora do Brasil)

**Passo 1**: Adicionar em `context.rego`
```rego
is_brazil if {
    input.context.location.country == "BR"
}

deny_localizacao contains msg if {
    input.action in ["locacao:checkin", "abastecimento:registrar"]
    not is_brazil
    msg := "Operações de pier só permitidas no Brasil"
}
```

**Passo 2**: Enviar `location` no context
```java
// ABACAuthorizationInterceptor
private ContextAttributes buildContextAttributes(HttpServletRequest request) {
    return ContextAttributes.builder()
        .timestamp(Instant.now().toString())
        .ip(extractIp(request))
        .location(Map.of("country", "BR", "city", "Rio de Janeiro"))
        .build();
}
```

---

## Troubleshooting

### OPA retorna 404

**Problema**: `OpaAuthorizationService` não consegue conectar ao OPA.

**Solução**:
```bash
# Verificar se OPA está rodando
docker-compose ps

# Logs OPA
docker-compose logs opa

# Testar endpoint manualmente
curl -X POST http://localhost:8181/v1/data/jetski/authorization/result \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "action": "locacao:list",
      "user": {"role": "OPERADOR", "tenant_id": "test"}
    }
  }'
```

---

### Decision sempre retorna `allow: false`

**Problema**: Política OPA bloqueando tudo.

**Debug**:
```bash
# Testar política diretamente
opa test -v src/main/resources/opa/policies/

# Debugar input/output
# Adicionar print em authorization.rego
result := {
    "allow": allow,
    "debug_rbac": rbac.allow,
    "debug_tenant": multi_tenant.tenant_is_valid,
    ...
}
```

**Verificar logs**:
```java
// ABACAuthorizationInterceptor
log.debug("OPAInput: {}", input);
log.debug("OPADecision: {}", decision);
```

---

### Action não está sendo extraído corretamente

**Problema**: `ActionExtractor` retorna `unknown:unknown`.

**Debug**:
```java
// Adicionar log em ActionExtractor
log.debug("Extracting action from: method={}, uri={}", method, uri);
log.debug("Extracted resource: {}, subAction: {}", resource, subAction);
```

**Testar unitariamente**:
```java
MockHttpServletRequest request = new MockHttpServletRequest();
request.setMethod("POST");
request.setRequestURI("/v1/locacoes/123/checkin");

String action = actionExtractor.extractAction(request);
assertThat(action).isEqualTo("locacao:checkin");
```

---

### Alçada não está retornando `aprovador_requerido`

**Problema**: `OPADecision.aprovador_requerido` é null mesmo quando `requer_aprovacao=true`.

**Verificar política**:
```rego
# alcada.rego DEVE ter:
aprovador_requerido := "GERENTE" if {
    # condições
}

# authorization.rego DEVE incluir:
result := {
    "aprovador_requerido": object.get(alcada.aprovador_requerido, null, null)
}
```

---

## Migração de @PreAuthorize para ABAC

### Antes (RBAC com Spring Security)

```java
@PreAuthorize("hasRole('OPERADOR')")
@GetMapping("/locacoes")
public ResponseEntity<?> listLocacoes() { ... }

@PreAuthorize("hasAnyRole('GERENTE', 'ADMIN_TENANT')")
@PostMapping("/fechamentos/diario")
public ResponseEntity<?> fecharDia() { ... }
```

**Problemas**:
- ❌ Role explosion em multi-tenant
- ❌ Sem alçada automática
- ❌ Sem regras de negócio (RN06)
- ❌ Sem contexto (horário, IP)

---

### Depois (ABAC com OPA)

```java
// Sem anotações! Authorization via interceptor
@GetMapping("/locacoes")
public ResponseEntity<?> listLocacoes() { ... }

@PostMapping("/fechamentos/diario")
public ResponseEntity<?> fecharDia() { ... }
```

**Vantagens**:
- ✅ RBAC centralizado em `rbac.rego`
- ✅ Alçada automática em `alcada.rego`
- ✅ Regras de negócio em `business_rules.rego`
- ✅ Contexto dinâmico em `context.rego`
- ✅ Multi-tenant nativo em `multi_tenant.rego`

---

## Endpoints de Teste

Use `/v1/auth-test` para validar ABAC:

### `/v1/auth-test/public` (público)
```bash
curl http://localhost:8090/api/v1/auth-test/public
# ✅ 200 OK (sem autenticação)
```

### `/v1/auth-test/me` (qualquer autenticado)
```bash
curl -H "Authorization: Bearer $TOKEN" \
     -H "X-Tenant-Id: abc-def" \
     http://localhost:8090/api/v1/auth-test/me
# ✅ 200 OK com claims do JWT
```

### `/v1/auth-test/operador-only` (apenas OPERADOR)
```bash
# OPERADOR
curl -H "Authorization: Bearer $TOKEN_OPERADOR" \
     -H "X-Tenant-Id: abc-def" \
     http://localhost:8090/api/v1/auth-test/operador-only
# ✅ 200 OK

# FINANCEIRO
curl -H "Authorization: Bearer $TOKEN_FINANCEIRO" \
     -H "X-Tenant-Id: abc-def" \
     http://localhost:8090/api/v1/auth-test/operador-only
# ❌ 403 Forbidden
```

### `/v1/auth-test/opa/rbac` (testar RBAC direto)
```bash
curl "http://localhost:8090/api/v1/auth-test/opa/rbac?action=locacao:checkin&role=OPERADOR" \
     -H "Authorization: Bearer $TOKEN" \
     -H "X-Tenant-Id: abc-def"

# Response:
{
  "input": {
    "action": "locacao:checkin",
    "user": {"tenant_id": "abc-def", "role": "OPERADOR"}
  },
  "decision": {
    "allow": true,
    "tenant_is_valid": true
  }
}
```

### `/v1/auth-test/opa/alcada` (testar Alçada direto)
```bash
curl "http://localhost:8090/api/v1/auth-test/opa/alcada?action=desconto:aplicar&role=OPERADOR&percentualDesconto=15" \
     -H "Authorization: Bearer $TOKEN" \
     -H "X-Tenant-Id: abc-def"

# Response:
{
  "decision": {
    "allow": false,
    "requer_aprovacao": true,
    "aprovador_requerido": "GERENTE"
  }
}
```

---

## Roadmap

### ✅ Implementado
- RBAC com 7 roles
- Alçada (desconto + OS approval)
- Multi-tenant isolation
- Business rules (RN06, lifecycle, fotos, fechamento)
- Context (horário comercial, dia útil, device)
- 200+ testes OPA
- Integration tests Java
- Documentation

### 🚧 Futuro
- [ ] IP whitelist/blacklist por tenant (data.ip_whitelist)
- [ ] Rate limiting por tenant em OPA
- [ ] Audit log de decisões OPA
- [ ] Dashboard de métricas OPA (Prometheus)
- [ ] Dynamic policy reload (OPA Bundle API)
- [ ] Rego playground embarcado para admins

---

## Referências

- [OPA Documentation](https://www.openpolicyagent.org/docs/latest/)
- [Rego Language Guide](https://www.openpolicyagent.org/docs/latest/policy-language/)
- [ABAC vs RBAC](https://www.okta.com/identity-101/role-based-access-control-vs-attribute-based-access-control/)
- [Spring Security Architecture](https://spring.io/guides/topicals/spring-security-architecture)

---

## Contato

Para dúvidas sobre ABAC/OPA:
- Consultar testes: `*_test.rego`
- Logs: `docker-compose logs opa`
- Debugar: `opa test -v` + logs Java
