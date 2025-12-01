# Diagnóstico: Falhas na Collection Postman

**Data**: 2025-11-08
**Status**: OPA funcionando ✅ | Endpoints fechamento ausentes ❌ | Credenciais incorretas ❌

---

## Resumo Executivo

Dos **84 testes** na collection:
- ✅ **43 passaram** (auth, alguns endpoints)
- ❌ **41 falharam** (fechamento e comissões)

**Causas identificadas**:
1. **FechamentoController NÃO está sendo registrado** pelo Spring Boot (retorna 404)
2. **Credenciais na collection Postman estão INCORRETAS**

**OPA está funcionando perfeitamente** - todos os testes mostram autorização aprovada.

---

## Problema 1: Credenciais Incorretas na Collection

### Credenciais CORRETAS (setup-keycloak-local.sh)

```bash
CLIENT_SECRET="jetski-secret"  # ← Collection usa: 9p8KHZqGX4mN2wL5vR7tY3sJ6cB1aF0e

# Usuários do tenant ACME:
gerente@acme.com / gerente123     # ← Collection usa: gerente.teste@example.com / Test@123
operador@acme.com / operador123   # ← Collection usa: operador.teste@example.com / Test@123
financeiro@acme.com / financeiro123  # (não existe na collection)
```

### Como testar manualmente

```bash
# 1. Obter token com credenciais CORRETAS
TOKEN=$(curl -s -X POST "http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=jetski-api" \
  -d "client_secret=jetski-secret" \
  -d "username=gerente@acme.com" \
  -d "password=gerente123" \
  -d "scope=openid profile email" | jq -r '.access_token')

# 2. Testar endpoint (exemplo: locações)
curl -X GET "http://localhost:8090/api/v1/locacoes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
```

---

## Problema 2: FechamentoController Não Registrado

### Endpoints ausentes (todos retornam 404)

```
POST /api/v1/fechamentos/dia/consolidar
GET  /api/v1/fechamentos/dia/{id}
POST /api/v1/fechamentos/dia/{id}/fechar
POST /api/v1/fechamentos/dia/{id}/aprovar
POST /api/v1/fechamentos/dia/{id}/reabrir
POST /api/v1/fechamentos/mes/consolidar
... (todos os endpoints de fechamento)
```

### Evidências

1. **Controller compilado**: `backend/target/classes/com/jetski/fechamento/api/FechamentoController.class` existe
2. **Anotações corretas**:
   - `@RestController`
   - `@RequestMapping("/api/v1/fechamentos")`
   - `@RequiredArgsConstructor`
3. **Dependências OK**:
   - `FechamentoService` → `@Service` ✅
   - `LocacaoQueryService` → `@Service` ✅
   - `ComissaoQueryService` → `@Service` ✅
   - `UsuarioService` → `@Service` ✅
4. **Aplicação inicia sem erros** de bean creation
5. **Nenhum log de registro** do controller (nem "Mapped", nem "Bean creation error")

### Testes realizados

```bash
# Backend está rodando
curl http://localhost:8090/api/actuator/health
# → {"status":"UP",...}

# Token de autenticação funciona
curl -X POST http://localhost:8090/api/v1/fechamentos/dia/consolidar \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11" \
  -H "Content-Type: application/json" \
  -d '{"dtReferencia": "2025-11-08"}'
# → 404 "No static resource v1/fechamentos/dia/consolidar"

# Logs mostram autorização APROVADA
# 2025-11-08 08:20:34 [fa41d5...] INFO ABAC ALLOW: action=fechamento:consolidar, tenant_valid=true
# 2025-11-08 08:20:34 [fa41d5...] WARN NoResourceFoundException: No static resource
```

### Possíveis causas

1. **Component Scan não inclui `com.jetski.fechamento.api`** (improvável - `@SpringBootApplication` escaneia tudo)
2. **Erro silencioso na criação do bean** (mas sem logs!)
3. **Dependência circular não detectada** (mas grep não encontrou)
4. **Controller em package errado** (mas path está correto: `com.jetski.fechamento.api.FechamentoController`)

### Próximos passos investigativos

1. Adicionar log level `TRACE` para `org.springframework.context` para ver bean creation
2. Verificar se há `@Conditional` annotations que possam estar excluindo o bean
3. Tentar criar um controller de teste mínimo em `com.jetski.fechamento.api.TestController` para verificar se o package é escaneado
4. Verificar se há dependência com erro de compilação que impeça o bean (mas Maven não reportou)

---

## Problema 3: OPA Está Funcionando Corretamente ✅

**Não há problema com OPA!** Logs confirmam autorização aprovada:

```
2025-11-08 08:20:34 DEBUG ActionExtractor - Extracted action: fechamento:consolidar
2025-11-08 08:20:34 DEBUG OPAAuthorizationService - Autorizando ABAC: action=fechamento:consolidar, user.role=offline_access, tenant=a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
2025-11-08 08:20:34 INFO  OPAAuthorizationService - ABAC Decision: action=fechamento:consolidar, allow=true, tenant_valid=true, requer_aprovacao=false
2025-11-08 08:20:34 INFO  ABACAuthorizationInterceptor - ABAC ALLOW: action=fechamento:consolidar, tenant_valid=true
```

**OPA responde corretamente:**

```bash
curl -s http://localhost:8181/v1/data/jetski/authorization/result \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "action": "fechamento:consolidar",
      "user": {
        "user_id": "gerente@acme.com",
        "tenant_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        "role": "GERENTE",
        "roles": ["GERENTE"]
      },
      "resource": {"tenant_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"},
      "context": {
        "timestamp": "2025-11-08T11:00:00Z",
        "ip": "127.0.0.1",
        "device_type": "web"
      }
    }
  }' | jq .

# Resposta:
{
  "result": {
    "allow": true,
    "tenant_is_valid": true,
    "requer_aprovacao": false,
    "aprovador_requerido": null,
    "deny_reasons": [],
    "warnings": [],
    "evaluated_policies": {
      "rbac": true,
      "alcada": true,
      "business": true,
      "context": true,
      "multi_tenant": true
    }
  }
}
```

---

## Ações Recomendadas

### Imediato

1. ✅ **Corrigir credenciais na collection Postman**:
   - `client_secret`: `jetski-secret`
   - Usuários: `gerente@acme.com`, `operador@acme.com`, `financeiro@acme.com`
   - Senhas: `gerente123`, `operador123`, `financeiro123`

2. 🔍 **Investigar por que FechamentoController não é registrado**:
   - Verificar logs com `logging.level.org.springframework.context=TRACE`
   - Tentar criar TestController simples em `com.jetski.fechamento.api`
   - Verificar se há @Profile ou @Conditional que exclua o bean

### Médio Prazo

3. **Adicionar health check** que valide se todos os controllers esperados estão registrados
4. **Documentar credenciais** em arquivo `.env.example` ou AMBIENTE-LOCAL.md
5. **Sincronizar collection Postman** com setup scripts

---

## Arquivos Relevantes

- **Setup Keycloak**: `infra/keycloak-setup/setup-keycloak-local.sh`
- **Collection Postman**: `backend/postman/Jetski-Sprint3-Jornadas-Testadas.postman_collection.json`
- **Controller ausente**: `backend/src/main/java/com/jetski/fechamento/api/FechamentoController.java`
- **Logs backend**: `/tmp/spring-boot-local.log`
- **Políticas OPA**: `policies/authz/*.rego`
