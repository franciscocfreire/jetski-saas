# Quick Start - Postman Collection

Guia rápido de 5 minutos para começar a usar a collection.

## 🚀 Setup Rápido (5 minutos)

### 1. Importe no Postman

```bash
# No Postman:
File → Import → Selecione:
  - Jetski-SaaS-API.postman_collection.json
  - environments/Local.postman_environment.json
  - environments/Dev.postman_environment.json
  - environments/Homolog.postman_environment.json
  - environments/Production.postman_environment.json
```

### 2. Selecione o Ambiente

```
Dropdown superior direito → "Local"
```

### 3. Configure Credenciais

Edite o ambiente **Local** e ajuste se necessário:

| Variável | Valor Padrão | Ajuste Necessário? |
|----------|--------------|-------------------|
| `username` | `admin@jetski.local` | ✅ Sim - use seu usuário Keycloak |
| `password` | `admin123` | ✅ Sim - use sua senha |
| `api_url` | `http://localhost:8090/api` | ❌ Não (se porta for 8090) |
| `keycloak_url` | `http://localhost:8081` | ❌ Não (se porta for 8081) |

### 4. Inicie a API e Keycloak

```bash
# Terminal 1: Keycloak
cd /home/franciscocfreire/repos/jetski/backend
./bin/kc.sh start-dev --http-port=8081

# Terminal 2: API
cd /home/franciscocfreire/repos/jetski/backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

### 5. Execute Primeiro Request

```
Postman → Collection "Jetski SaaS API"
  → Auth
    → Get Access Token
      → [Send]
```

✅ **Sucesso se ver no console:**
```
✅ Access token obtido com sucesso!
Expira em: 300 segundos
```

---

## 📋 Checklist de Validação

Execute nesta ordem para validar setup completo:

### ✅ Teste 1: Endpoint Público (sem autenticação)
```
Auth Tests → Public Endpoint → [Send]
Resultado esperado: 200 OK
```

### ✅ Teste 2: Autenticação
```
Auth → Get Access Token → [Send]
Resultado esperado: 200 OK + token salvo
```

### ✅ Teste 3: Listar Tenants
```
User → List User Tenants → [Send]
Resultado esperado: 200 OK + lista de tenants
```

### ✅ Teste 4: Informações do Usuário
```
Auth Tests → Get Current User (Me) → [Send]
Resultado esperado: 200 OK + JWT claims
```

### ✅ Teste 5: RBAC
```
Auth Tests → Operador Only → [Send]
Resultado esperado: 200 OK (se tiver role) ou 403 (se não tiver)
```

### ✅ Teste 6: OPA RBAC
```
Auth Tests → OPA RBAC Test → [Send]
Resultado esperado: 200 OK + decision.allow=true/false
```

---

## 🔍 Troubleshooting Rápido

### ❌ "Connection refused (localhost:8090)"
**Solução**: Inicie a API
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

### ❌ "Connection refused (localhost:8081)"
**Solução**: Inicie o Keycloak
```bash
./bin/kc.sh start-dev --http-port=8081
```

### ❌ "401 Unauthorized"
**Solução**: Obtenha novo token
```
Auth → Get Access Token → [Send]
```

### ❌ "Invalid user credentials"
**Solução**: Ajuste credenciais no ambiente
```
Environments → Local → Edit
  → username: seu_usuario@jetski.local
  → password: sua_senha
```

### ❌ "access_token not found"
**Solução**: Execute primeiro
```
Auth → Get Access Token → [Send]
```

---

## 🎯 Workflows Comuns

### Workflow 1: Teste Completo de Autenticação
```
1. Auth → Get Access Token
2. User → List User Tenants
3. Auth Tests → Get Current User (Me)
```

### Workflow 2: Teste RBAC
```
1. Auth → Get Access Token
2. Auth Tests → Operador Only (200 se tiver role, 403 se não)
3. Auth Tests → Manager Only (200 se GERENTE/ADMIN, 403 se não)
4. Auth Tests → Finance Only (200 se FINANCEIRO, 403 se não)
```

### Workflow 3: Teste OPA
```
1. Auth → Get Access Token
2. Auth Tests → OPA RBAC Test
   → Query params: action=locacao:checkin, role=OPERADOR
3. Auth Tests → OPA Alçada Test
   → Query params: action=desconto:aplicar, role=OPERADOR, percentualDesconto=15
```

### Workflow 4: Teste Multi-tenancy
```
1. Auth → Get Access Token (Usuário do Tenant A)
2. User → List User Tenants (salva tenant_id automaticamente)
3. Auth Tests → Get Current User (200 OK)
4. Editar request manualmente: X-Tenant-Id → tenant inválido
5. Auth Tests → Get Current User (403 Forbidden esperado)
```

---

## 📊 Estrutura de Pastas

```
backend/postman/
├── Jetski-SaaS-API.postman_collection.json  # Collection principal
├── environments/
│   ├── Local.postman_environment.json       # Ambiente local
│   ├── Dev.postman_environment.json         # Ambiente dev (Docker)
│   ├── Homolog.postman_environment.json     # Ambiente HML (placeholder)
│   └── Production.postman_environment.json  # Ambiente PRD (placeholder)
├── README.md                                # Documentação completa
└── QUICK_START.md                           # Este guia rápido
```

---

## 🔐 Segurança

### ⚠️ IMPORTANTE: Nunca versione credenciais sensíveis

```bash
# ✅ SEGURO - versionado no Git:
- Estrutura da collection
- Endpoints e testes
- URLs de ambiente
- Credenciais de DESENVOLVIMENTO (admin123, dev123)

# ❌ NUNCA VERSIONAR:
- Credenciais de HOMOLOGAÇÃO
- Credenciais de PRODUÇÃO
- Tokens JWT obtidos
```

### Boas Práticas

1. **Local/Dev**: Pode usar credenciais simples (admin123, dev123)
2. **Homolog**: Usar credenciais específicas de HML (não versionar)
3. **Production**: Usar Postman Vault ou variáveis de sistema (NUNCA versionar)

```javascript
// Exemplo: Usar variável de ambiente do sistema
pm.environment.get("PRD_PASSWORD") || "fallback-dev-password"
```

---

## 📖 Próximos Passos

Após completar o Quick Start:

1. 📚 Leia o [README.md](README.md) completo para documentação detalhada
2. 🔧 Explore os **Tests** de cada request (aba "Tests" no Postman)
3. 📝 Customize os **Pre-request Scripts** conforme necessário
4. 🚀 Adicione novos endpoints à collection conforme forem implementados
5. 📊 Use **Postman Monitors** para testes automatizados periódicos

---

## 💡 Dicas Úteis

### Console do Postman
Abra o console para ver logs detalhados:
```
View → Show Postman Console (Ctrl+Alt+C)
```

### Salvar Tokens Automaticamente
Os scripts já fazem isso! Mas você pode customizar:
```javascript
// Post-request script (aba Tests)
const jsonData = pm.response.json();
pm.environment.set("custom_var", jsonData.some_field);
```

### Copiar como cURL
Útil para debug:
```
Request → Code (botão </>)  → cURL
```

### Runner
Execute múltiplos requests em sequência:
```
Collection → Run
  → Selecione requests
  → Selecione ambiente
  → Run Jetski SaaS API
```

---

**Versão**: 1.0.0
**Tempo de setup**: ~5 minutos
**Última atualização**: 2025-10-18
