# Plano de Correção - Manutenção Retornando 403

## 🎯 Objetivo

Resolver o problema de autorização nos endpoints de Manutenção, onde:
- ✅ OPA permite quando testado manualmente (`allow: true`)
- ❌ Backend retorna 403 Forbidden
- 🎯 Meta: Alcançar 100% de sucesso nos testes Newman (145/145 assertions)

---

## 🔍 Diagnóstico Atual

### Evidências Coletadas

**1. OPA Está Funcionando Corretamente**
```bash
# Teste manual no OPA
curl -X POST http://localhost:8181/v1/data/jetski/authorization/result -d '{
  "input": {
    "action": "manutencao:list",
    "user": {
      "tenant_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "role": "GERENTE",
      "roles": ["GERENTE", "ADMIN_TENANT"]
    },
    "resource": {
      "tenant_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
    }
  }
}'

# Resultado: {"allow": true, "rbac": true} ✅
```

**2. Políticas OPA Corretas**
- `policies/authz/rbac.rego` possui `"manutencao:*"` para GERENTE
- `policies/authz/rbac.rego` possui permissões específicas para MECÂNICO

**3. Backend Retorna 403**
```bash
curl -X GET /api/v1/tenants/{id}/manutencoes \
  -H "Authorization: Bearer <GERENTE_TOKEN>" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
# Resultado: 403 Forbidden ❌
```

**4. ActionExtractor**
- Singulariza "manutencoes" → "manutencao" ✅
- Extrai resource de paths nested: `/tenants/{id}/manutencoes` → "manutencao" ✅

---

## 🔬 Hipóteses

### Hipótese #1: Estrutura do Input OPA Diferente (MAIS PROVÁVEL)

**Problema Potencial**: Backend pode estar enviando estrutura diferente de input

**Possíveis Diferenças**:
```json
// Manual (funciona)
{
  "user": {
    "role": "GERENTE",              // String singular
    "roles": ["GERENTE", "ADMIN_TENANT"]
  }
}

// Backend pode estar enviando
{
  "user": {
    "role": ["GERENTE"],             // Array (errado)
    // OU
    "roles": "GERENTE"               // String (errado)
    // OU faltando "roles" completamente
  }
}
```

**Arquivos a Investigar**:
- `ABACAuthorizationInterceptor.java` - Constrói o input OPA
- `OPAAuthorizationService.java` - Envia requisição ao OPA

---

### Hipótese #2: JWT Claims Incorretos

**Problema Potencial**: Token JWT do GERENTE pode não conter claim correto

**Verificação Necessária**:
```bash
# Decodificar token JWT
echo $GERENTE_TOKEN | jwt decode -

# Verificar se contém:
# - tenant_id: "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
# - role ou roles: ["GERENTE", "ADMIN_TENANT"]
```

---

### Hipótese #3: ActionExtractor Mapeando Incorretamente

**Problema Potencial**: ActionExtractor pode estar gerando action diferente de "manutencao:list"

**Possíveis Problemas**:
```
GET /v1/tenants/{id}/manutencoes
→ ActionExtractor gera "manutencoe:list" (plural incorreto)
→ OPA rejeita porque política espera "manutencao:list"
```

**Verificação**: Logs DEBUG do ActionExtractor

---

### Hipótese #4: Multi-Tenant Validation Falhando

**Problema Potencial**: tenant_id do user não corresponde ao tenant_id do resource

**Possíveis Causas**:
- Header `X-Tenant-Id` não sendo passado corretamente
- JWT não contém claim `tenant_id`
- Comparação case-sensitive de UUIDs

---

## 📋 Plano de Investigação (Passo a Passo)

### Fase 1: Habilitar Logs DEBUG ⏱️ 5 min

**Objetivo**: Ver exatamente o que está sendo enviado ao OPA

**Passos**:
1. Editar `application-local.yml`
2. Adicionar logs DEBUG para pacotes relevantes
3. Reiniciar backend
4. Executar request de teste
5. Analisar logs

**Arquivos**:
```yaml
# backend/src/main/resources/application-local.yml
logging:
  level:
    com.jetski.shared.authorization: DEBUG
    com.jetski.shared.opa: DEBUG
    com.jetski.shared.security: DEBUG
```

**Comandos**:
```bash
# Reiniciar backend
pkill -f "spring-boot:run"
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run > /tmp/backend-debug.log 2>&1 &

# Executar teste
curl -X GET http://localhost:8090/api/v1/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/manutencoes \
  -H "Authorization: Bearer $GERENTE_TOKEN" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"

# Analisar logs
tail -100 /tmp/backend-debug.log | grep -A5 -B5 "manutencao"
```

**O que procurar nos logs**:
- `Extracting action from: GET /v1/tenants/.../manutencoes`
- `Extracted action: manutencao:list` (ou diferente?)
- `Calling OPA with input: {...}` (payload completo)
- `OPA response: {...}` (resposta do OPA)

---

### Fase 2: Verificar JWT Token ⏱️ 3 min

**Objetivo**: Confirmar que JWT contém claims corretos

**Comandos**:
```bash
# Obter token
GERENTE_TOKEN=$(curl -s -X POST 'http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token' \
  -d 'username=gerente@acme.com' \
  -d 'password=gerente123' \
  -d 'grant_type=password' \
  -d 'client_id=jetski-api' \
  -d 'client_secret=jetski-secret' | jq -r '.access_token')

# Decodificar (usando jq ou jwt-cli)
echo $GERENTE_TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .
```

**O que procurar**:
```json
{
  "tenant_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",  // Deve existir
  "realm_access": {
    "roles": ["GERENTE", "ADMIN_TENANT"]                // Deve conter GERENTE
  },
  // OU
  "roles": ["GERENTE", "ADMIN_TENANT"],                 // Formato alternativo
  // OU
  "resource_access": {
    "jetski-api": {
      "roles": ["GERENTE"]
    }
  }
}
```

---

### Fase 3: Analisar Código do Interceptor ⏱️ 10 min

**Objetivo**: Entender como o payload OPA é construído

**Arquivos a Ler**:
1. `ABACAuthorizationInterceptor.java`
2. `OPAInput.java` / `OPARequest.java`
3. `OPAAuthorizationService.java`

**Perguntas a Responder**:
- Como `user.role` e `user.roles` são extraídos do JWT?
- `tenant_id` vem do JWT ou do header `X-Tenant-Id`?
- Qual é a estrutura exata do JSON enviado ao OPA?

**Comando**:
```bash
# Ler interceptor
cat backend/src/main/java/com/jetski/shared/authorization/ABACAuthorizationInterceptor.java

# Ler DTOs OPA
cat backend/src/main/java/com/jetski/shared/opa/dto/OPAInput.java
cat backend/src/main/java/com/jetski/shared/opa/dto/OPARequest.java
```

---

### Fase 4: Comparar Payloads ⏱️ 5 min

**Objetivo**: Identificar diferença exata entre manual (funciona) e backend (falha)

**Método**:
1. Capturar payload do backend via logs DEBUG
2. Comparar com payload manual
3. Identificar diferenças

**Exemplo de Comparação**:
```bash
# Payload manual (salvo em /tmp/manual-payload.json)
# Payload backend (extraído de logs → /tmp/backend-payload.json)

# Comparar
diff -u /tmp/manual-payload.json /tmp/backend-payload.json
```

---

### Fase 5: Aplicar Correção ⏱️ 15 min

**Cenários Possíveis**:

#### Cenário A: Role como Array ao invés de String
```java
// ERRADO (atual?)
OPAInput.User user = OPAInput.User.builder()
    .role(rolesArray)  // ["GERENTE"] - array
    .build();

// CORRETO
OPAInput.User user = OPAInput.User.builder()
    .role(principalRole)   // "GERENTE" - string
    .roles(rolesArray)     // ["GERENTE", "ADMIN_TENANT"] - array
    .build();
```

#### Cenário B: tenant_id faltando
```java
// ERRADO
OPAInput.User user = OPAInput.User.builder()
    .role("GERENTE")
    // tenant_id faltando!
    .build();

// CORRETO
String tenantId = request.getHeader("X-Tenant-Id");
OPAInput.User user = OPAInput.User.builder()
    .tenantId(tenantId)
    .role("GERENTE")
    .build();
```

#### Cenário C: ActionExtractor gerando plural
```java
// No ActionExtractor.java, linha ~109
// ERRADO
if (resource.endsWith("aes")) {
    return resource.substring(0, resource.length() - 3) + "ao";
}

// Pode estar retornando "manutencoes" ao invés de "manutencao"
// Verificar log: "Extracted action: ???"
```

---

### Fase 6: Validar Correção ⏱️ 5 min

**Comandos**:
```bash
# 1. Reiniciar backend (se mudou código)
pkill -f "spring-boot:run"
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run > /tmp/backend.log 2>&1 &
sleep 30

# 2. Teste manual
GERENTE_TOKEN=$(curl -s -X POST 'http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token' \
  -d 'username=gerente@acme.com' \
  -d 'password=gerente123' \
  -d 'grant_type=password' \
  -d 'client_id=jetski-api' \
  -d 'client_secret=jetski-secret' | jq -r '.access_token')

curl -v -X GET "http://localhost:8090/api/v1/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/manutencoes" \
  -H "Authorization: Bearer $GERENTE_TOKEN" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"

# Esperado: 200 OK (ou 404 se não houver dados)
# Não esperado: 403 Forbidden

# 3. Re-executar Newman
cd backend
newman run postman/Jetski-Sprint3-Jornadas-Testadas.postman_collection.json \
  -e postman/environments/Local.postman_environment.json \
  --folder "4️⃣ Jornada: Manutenção - OS Completa (RN06)"

# Esperado: 13/13 assertions passando ✅
```

---

## 🎯 Critérios de Sucesso

### Curto Prazo (Esta Sessão)
- [ ] Logs DEBUG habilitados e funcionando
- [ ] Payload backend identificado e comparado
- [ ] Diferença entre payloads identificada
- [ ] Correção aplicada (se possível identificar)

### Médio Prazo (Próxima Sessão)
- [ ] Request manual retorna 200 OK
- [ ] Newman: Jornada Manutenção 100% (13/13 assertions)
- [ ] Newman: Total 100% (145/145 assertions)

---

## 📊 Métricas de Progresso

| Fase | Tempo Estimado | Status |
|------|----------------|--------|
| 1. Habilitar Logs DEBUG | 5 min | ⏳ Pendente |
| 2. Verificar JWT Token | 3 min | ⏳ Pendente |
| 3. Analisar Código Interceptor | 10 min | ⏳ Pendente |
| 4. Comparar Payloads | 5 min | ⏳ Pendente |
| 5. Aplicar Correção | 15 min | ⏳ Pendente |
| 6. Validar Correção | 5 min | ⏳ Pendente |
| **TOTAL** | **~45 min** | |

---

## 🛠️ Ferramentas Necessárias

- [x] Newman instalado
- [x] jq instalado
- [x] Backend rodando
- [x] OPA rodando
- [x] Keycloak rodando
- [ ] Logs DEBUG habilitados

---

## 📝 Notas Importantes

1. **Não Modificar Políticas OPA**: Elas estão corretas e funcionando
2. **Foco no Interceptor**: Problema está na comunicação backend → OPA
3. **Comparar JSON**: Usar `jq` para formatar e `diff` para comparar
4. **Documentar Solução**: Adicionar comentários no código explicando o fix

---

## 🚀 Comandos Rápidos

```bash
# Habilitar DEBUG e reiniciar backend
echo "logging:
  level:
    com.jetski.shared.authorization: DEBUG
    com.jetski.shared.opa: DEBUG" >> backend/src/main/resources/application-local.yml

pkill -f "spring-boot:run"
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run > /tmp/backend-debug.log 2>&1 &

# Obter token e testar
GERENTE_TOKEN=$(curl -s -X POST 'http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token' -d 'username=gerente@acme.com' -d 'password=gerente123' -d 'grant_type=password' -d 'client_id=jetski-api' -d 'client_secret=jetski-secret' | jq -r '.access_token')

curl -v http://localhost:8090/api/v1/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/manutencoes -H "Authorization: Bearer $GERENTE_TOKEN" -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"

# Ver logs relevantes
tail -f /tmp/backend-debug.log | grep -E "(Extracting action|OPA|Authorization|manutencao)"
```

---

**Criado em**: 19 de Novembro de 2025
**Status**: 📋 Plano Pronto para Execução
**Próximo Passo**: Fase 1 - Habilitar Logs DEBUG

🤖 **Gerado com [Claude Code](https://claude.com/claude-code)**
