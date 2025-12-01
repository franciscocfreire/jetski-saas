# ✅ SOLUÇÃO: Controllers Não Mapeados

**Data**: 2025-11-08
**Status**: RESOLVIDO ✅

---

## Problema Identificado

Os controllers com `@RequestMapping("/api/v1/...")` estavam **duplicando o prefixo `/api`**, resultando em paths `/api/api/v1/...` que não existiam.

### Causa Raiz

**Context path configurado** em `application-local.yml`:
```yaml
server:
  servlet:
    context-path: /api  # ← Já adiciona /api automaticamente
```

**Controllers com prefixo `/api`**:
```java
@RestController
@RequestMapping("/api/v1/fechamentos")  // ← ERRADO: duplica /api
public class FechamentoController { ... }
```

**Resultado**: Path final seria `/api/api/v1/fechamentos` ❌

---

## Solução Aplicada

Removido o prefixo `/api` dos `@RequestMapping` nos controllers:

### Controllers Corrigidos

1. **FechamentoController**
   - ❌ Antes: `@RequestMapping("/api/v1/fechamentos")`
   - ✅ Depois: `@RequestMapping("/v1/fechamentos")`

2. **ComissaoController**
   - ❌ Antes: `@RequestMapping("/api/v1/comissoes")`
   - ✅ Depois: `@RequestMapping("/v1/comissoes")`

3. **PoliticaComissaoController**
   - ❌ Antes: `@RequestMapping("/api/v1/politicas-comissao")`
   - ✅ Depois: `@RequestMapping("/v1/politicas-comissao")`

4. **PhotoController**
   - ❌ Antes: `@RequestMapping("/api/v1/tenants/{tenantId}/fotos")`
   - ✅ Depois: `@RequestMapping("/v1/tenants/{tenantId}/fotos")`

---

## Resultado

### Antes da Correção
```bash
POST http://localhost:8090/api/v1/fechamentos/dia/consolidar
→ 404 "No static resource v1/fechamentos/dia/consolidar."
```

### Depois da Correção
```bash
POST http://localhost:8090/api/v1/fechamentos/dia/consolidar
→ 404 "Usuário não encontrado: ..."  # ← Erro de negócio, não de routing!
```

✅ O **controller está funcionando**! O erro 404 agora é da lógica de negócio (usuário não encontrado), não mais de routing.

---

## Também Corrigido: WebMvcConfig

O interceptor ABAC também foi ajustado para aplicar a **todos os paths**:

```java
@Override
public void addInterceptors(@NonNull InterceptorRegistry registry) {
    registry.addInterceptor(abacAuthorizationInterceptor)
        .addPathPatterns("/**")  // ← Antes era "/v1/**"
        .excludePathPatterns(
            "/v1/auth-test/public",
            "/actuator/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
        );
}
```

---

## Verificação

Para confirmar que tudo funciona:

```bash
# 1. Obter token
TOKEN=$(curl -s -X POST "http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=jetski-api" \
  -d "client_secret=jetski-secret" \
  -d "username=gerente@acme.com" \
  -d "password=gerente123" \
  -d "scope=openid profile email" | jq -r '.access_token')

# 2. Testar endpoints
curl -X GET "http://localhost:8090/api/v1/locacoes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"

curl -X POST "http://localhost:8090/api/v1/fechamentos/dia/consolidar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11" \
  -H "Content-Type: application/json" \
  -d '{"dtReferencia": "2025-11-08"}'
```

---

## Arquivos Modificados

```
backend/src/main/java/com/jetski/fechamento/api/FechamentoController.java
backend/src/main/java/com/jetski/comissoes/api/ComissaoController.java
backend/src/main/java/com/jetski/comissoes/api/PoliticaComissaoController.java
backend/src/main/java/com/jetski/locacoes/api/PhotoController.java
backend/src/main/java/com/jetski/shared/config/WebMvcConfig.java
```

---

## Lição Aprendida

Quando usando `server.servlet.context-path`, os `@RequestMapping` dos controllers **NÃO devem incluir o context-path** no path, pois ele já é adicionado automaticamente pelo Spring.

**Regra:**
- ✅ CORRETO: `@RequestMapping("/v1/resource")` com `context-path=/api` → `/api/v1/resource`
- ❌ ERRADO: `@RequestMapping("/api/v1/resource")` com `context-path=/api` → `/api/api/v1/resource`
