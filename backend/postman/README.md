# Postman Collection - Jetski SaaS API

Collection **viva** para testes manuais da API Jetski SaaS multi-tenant.

## 📋 Índice

- [Visão Geral](#visão-geral)
- [Instalação](#instalação)
- [Configuração Inicial](#configuração-inicial)
- [Ambientes](#ambientes)
- [Estrutura da Collection](#estrutura-da-collection)
- [Workflow de Uso](#workflow-de-uso)
- [Multi-tenancy](#multi-tenancy)
- [Troubleshooting](#troubleshooting)
- [Atualização da Collection](#atualização-da-collection)

---

## 🎯 Visão Geral

Esta collection contém todos os endpoints da API Jetski SaaS organizados por módulo, com:
- ✅ **Testes automatizados** em cada request
- 🔐 **Autenticação JWT** configurada automaticamente
- 🏢 **Multi-tenancy** com header `X-Tenant-Id`
- 🌍 **4 ambientes** pré-configurados (Local, Dev, Homolog, Production)
- 📝 **Documentação** inline em cada endpoint
- 🔄 **Scripts** de pre-request e post-request para automação

---

## 📦 Instalação

### 1. Importe a Collection

No Postman:
1. Clique em **Import**
2. Selecione o arquivo `Jetski-SaaS-API.postman_collection.json`
3. Clique em **Import**

### 2. Importe os Ambientes

No Postman:
1. Clique em **Environments** (lateral esquerda)
2. Clique em **Import**
3. Selecione os 4 arquivos da pasta `environments/`:
   - `Local.postman_environment.json`
   - `Dev.postman_environment.json`
   - `Homolog.postman_environment.json`
   - `Production.postman_environment.json`
4. Clique em **Import**

---

## ⚙️ Configuração Inicial

### Ambiente Local

O ambiente **Local** já vem pré-configurado com valores padrão:

| Variável | Valor | Descrição |
|----------|-------|-----------|
| `api_url` | `http://localhost:8090/api` | API Spring Boot |
| `keycloak_url` | `http://localhost:8081` | Keycloak local (porta 8081) |
| `keycloak_realm` | `jetski-saas` | Realm do Keycloak |
| `client_id` | `jetski-api` | Client ID configurado |
| `username` | `admin@jetski.local` | Usuário de teste |
| `password` | `admin123` | Senha de teste |
| `tenant_id` | `a0eebc99-...` | Tenant ID padrão |

**⚠️ Importante**: Ajuste o `username` e `password` de acordo com os usuários criados no seu Keycloak local.

### Ambiente Dev (Docker)

O ambiente **Dev** usa Keycloak na porta **8080** (Docker):

```json
{
  "api_url": "http://localhost:8090/api",
  "keycloak_url": "http://localhost:8080",
  "username": "admin@jetski.dev",
  "password": "dev123"
}
```

### Ambientes Homolog e Production

Os ambientes **Homolog** e **Production** possuem URLs placeholder que **devem ser atualizadas** quando os ambientes forem provisionados:

**Homolog:**
```json
{
  "api_url": "https://api-hml.jetski.com/api",
  "keycloak_url": "https://auth-hml.jetski.com"
}
```

**Production:**
```json
{
  "api_url": "https://api.jetski.com/api",
  "keycloak_url": "https://auth.jetski.com"
}
```

⚠️ **ATENÇÃO**: Nunca versione credenciais de produção. Use o Postman Vault ou variáveis de ambiente do sistema operacional.

---

## 🌍 Ambientes

### Seleção de Ambiente

1. No dropdown superior direito do Postman, selecione o ambiente desejado:
   - **Local** - Para desenvolvimento local
   - **Dev** - Para Docker/ambiente de desenvolvimento compartilhado
   - **Homolog** - Para testes de homologação
   - **Production** - Para produção (⚠️ usar com cuidado)

### Variáveis de Ambiente

Cada ambiente possui as seguintes variáveis:

| Variável | Tipo | Descrição | Auto-preenchida? |
|----------|------|-----------|------------------|
| `api_url` | URL | URL base da API | ❌ Manual |
| `keycloak_url` | URL | URL do Keycloak | ❌ Manual |
| `keycloak_realm` | String | Nome do realm | ❌ Manual |
| `client_id` | String | Client ID OAuth2 | ❌ Manual |
| `username` | String | Usuário para login | ❌ Manual |
| `password` | Secret | Senha do usuário | ❌ Manual |
| `tenant_id` | UUID | ID do tenant ativo | ✅ Sim (após listar tenants) |
| `access_token` | Secret | JWT access token | ✅ Sim (após autenticação) |
| `refresh_token` | Secret | JWT refresh token | ✅ Sim (após autenticação) |
| `token_expires_in` | Number | Tempo de expiração (s) | ✅ Sim (após autenticação) |

---

## 📂 Estrutura da Collection

### 1. Auth (Autenticação)

Endpoints para obtenção e renovação de tokens JWT via Keycloak:

- **Get Access Token** - Obtém JWT usando Resource Owner Password Credentials
- **Refresh Token** - Renova access token usando refresh token

### 2. User (Usuário)

Endpoints relacionados a usuários e tenants:

- **List User Tenants** - Lista todos os tenants acessíveis pelo usuário

### 3. Auth Tests (Testes de Segurança)

Endpoints de teste para validação de autenticação, RBAC e OPA:

- **Public Endpoint** - Endpoint público (sem autenticação)
- **Get Current User (Me)** - Informações do usuário autenticado
- **Operador Only** - Requer role `OPERADOR`
- **Manager Only** - Requer role `GERENTE` ou `ADMIN_TENANT`
- **Finance Only** - Requer role `FINANCEIRO`
- **OPA RBAC Test** - Testa autorização RBAC via OPA
- **OPA Alçada Test** - Testa política de alçada (aprovação) via OPA
- **OPA Generic Authorize** - Testa autorização genérica (RBAC + Alçada)

⚠️ **Importante**: Os endpoints de `Auth Tests` são apenas para desenvolvimento e **devem ser removidos em produção**.

### 4. Health (Observabilidade)

Health checks e métricas:

- **Health Check** - Status da aplicação e componentes
- **Metrics** - Lista de métricas disponíveis
- **Prometheus Metrics** - Endpoint de métricas no formato Prometheus

---

## 🔄 Workflow de Uso

### Passo 1: Selecione o Ambiente

No dropdown superior direito, selecione **Local** ou **Dev**.

### Passo 2: Obtenha o Access Token

1. Navegue até: `Auth → Get Access Token`
2. Clique em **Send**
3. Verifique no console que o token foi salvo:
   ```
   ✅ Access token obtido com sucesso!
   Expira em: 300 segundos
   ```

O token JWT será automaticamente salvo na variável `{{access_token}}` e usado em todos os requests subsequentes.

### Passo 3: Liste os Tenants Disponíveis

1. Navegue até: `User → List User Tenants`
2. Clique em **Send**
3. O primeiro tenant da lista será automaticamente salvo em `{{tenant_id}}`

Exemplo de resposta:
```json
{
  "access_type": "LIMITED",
  "tenants": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "tenant": {
        "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        "slug": "acme",
        "razao_social": "ACME Locadora LTDA"
      },
      "papeis": ["GERENTE", "OPERADOR"]
    }
  ],
  "total_count": 1
}
```

### Passo 4: Teste os Endpoints Protegidos

Agora você pode testar qualquer endpoint que requer autenticação:

1. `Auth Tests → Get Current User (Me)` - Valida JWT e extrai claims
2. `Auth Tests → Operador Only` - Testa RBAC
3. `Auth Tests → OPA RBAC Test` - Testa integração com OPA

### Passo 5: Renove o Token (quando expirar)

Quando o access token expirar (geralmente após 5-15 minutos):

1. Navegue até: `Auth → Refresh Token`
2. Clique em **Send**
3. O novo token será automaticamente salvo

---

## 🏢 Multi-tenancy

### Como Funciona

Todos os endpoints protegidos (exceto públicos e autenticação) requerem:

1. **Authorization Header**: `Bearer {{access_token}}`
2. **X-Tenant-Id Header**: `{{tenant_id}}`

Exemplo:
```http
GET /api/v1/auth-test/me HTTP/1.1
Host: localhost:8090
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
```

### Validações de Tenant

A API valida:
- **JWT Claim `tenant_id`** == **Header `X-Tenant-Id`** ✅
- Usuário é **membro ativo** do tenant ✅
- Usuário possui **role necessária** no tenant ✅

### Testando Multi-tenancy

Para testar isolamento de tenants:

1. Obtenha token para **Usuário A** (tenant X)
2. Tente acessar endpoint com `X-Tenant-Id` de **Tenant Y**
3. Resultado esperado: **403 Forbidden** (não é membro do tenant)

---

## 🔍 Troubleshooting

### ❌ "access_token not found"

**Problema**: Variável `access_token` não está configurada.

**Solução**: Execute o request `Auth → Get Access Token` primeiro.

### ❌ "401 Unauthorized"

**Problema**: Token expirado ou inválido.

**Soluções**:
1. Obtenha um novo token: `Auth → Get Access Token`
2. Ou renove o token: `Auth → Refresh Token`
3. Verifique se o Keycloak está rodando

### ❌ "403 Forbidden"

**Problema**: Usuário não tem permissão (role insuficiente ou não é membro do tenant).

**Soluções**:
1. Verifique as roles do usuário: `Auth Tests → Get Current User (Me)`
2. Verifique se `X-Tenant-Id` está correto
3. Use um usuário com a role adequada

### ❌ "Connection Refused (localhost:8090)"

**Problema**: API não está rodando.

**Solução**:
```bash
cd /home/franciscocfreire/repos/jetski/backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

### ❌ "Connection Refused (localhost:8081)"

**Problema**: Keycloak não está rodando.

**Solução**:
```bash
# Local (porta 8081)
./bin/kc.sh start-dev --http-port=8081

# Dev (porta 8080 - Docker)
docker-compose up -d keycloak
```

### ❌ "Invalid grant: Invalid user credentials"

**Problema**: Usuário ou senha incorretos.

**Solução**:
1. Verifique as credenciais no ambiente selecionado
2. Acesse Keycloak Admin Console e valide o usuário
3. Atualize as variáveis `username` e `password` no ambiente

### ❌ "Tenant validation failed"

**Problema**: `tenant_id` do JWT não corresponde ao header `X-Tenant-Id`.

**Solução**:
1. Liste os tenants disponíveis: `User → List User Tenants`
2. Use um `tenant_id` válido retornado na resposta

---

## 🔄 Atualização da Collection

Esta collection é **viva** e será atualizada conforme novos endpoints forem adicionados.

### Quando Atualizar

Atualize a collection quando:
- ✅ Novos endpoints forem implementados
- ✅ Parâmetros de requests mudarem
- ✅ Novos ambientes forem provisionados
- ✅ Estrutura de autenticação for alterada

### Como Atualizar

1. **Re-importe** o arquivo `Jetski-SaaS-API.postman_collection.json` atualizado
2. **Sobrescreva** a collection existente quando perguntado
3. **Não sobrescreva** os ambientes (para preservar credenciais locais)

### Versionamento

A collection usa **semantic versioning** na variável `collection_version`:

```json
{
  "variable": [
    {
      "key": "collection_version",
      "value": "1.0.0"
    }
  ]
}
```

**Changelog**:
- `1.0.0` (2025-10-18) - Versão inicial com Auth, User, Auth Tests e Health

---

## 🎓 Exemplos de Uso

### Exemplo 1: Teste RBAC Básico

```
1. Get Access Token (usuário com role OPERADOR)
2. Auth Tests → Operador Only → Send
   ✅ Resultado esperado: 200 OK
3. Auth Tests → Finance Only → Send
   ❌ Resultado esperado: 403 Forbidden
```

### Exemplo 2: Teste de Política de Alçada (OPA)

```
1. Get Access Token
2. Auth Tests → OPA Alçada Test
3. Configurar query params:
   - action: desconto:aplicar
   - role: OPERADOR
   - percentualDesconto: 15
4. Send
5. Verificar resposta:
   {
     "decision": {
       "allow": false,
       "requer_aprovacao": true,
       "aprovador_requerido": "GERENTE"
     }
   }
```

### Exemplo 3: Teste de Multi-tenancy

```
1. Get Access Token (Usuário A do Tenant X)
2. User → List User Tenants → Send
   → tenant_id automaticamente salvo: a0eebc99-...
3. Auth Tests → Get Current User (Me) → Send
   ✅ Resultado: 200 OK com tenant_id correto
4. Alterar manualmente X-Tenant-Id para tenant inválido
5. Auth Tests → Get Current User (Me) → Send
   ❌ Resultado: 403 Forbidden
```

---

## 📞 Suporte

Em caso de dúvidas ou problemas:
1. Verifique a seção **Troubleshooting** acima
2. Consulte a documentação do Swagger: `http://localhost:8090/api/swagger-ui.html`
3. Verifique os logs da API: `/tmp/backend.log` (local)
4. Contate a equipe de desenvolvimento

---

## 📝 Notas Importantes

- ⚠️ **Produção**: Endpoints de `Auth Tests` devem ser removidos em produção
- 🔒 **Segurança**: Nunca versione credenciais de produção no Git
- 🔄 **Token Expiration**: Access tokens expiram em ~5-15 min, use Refresh Token
- 🏢 **Multi-tenancy**: Sempre valide que `X-Tenant-Id` corresponde ao tenant do usuário
- 📊 **Testes**: Todos os requests possuem testes automatizados (aba Tests)
- 🌐 **CORS**: Ambientes local/dev possuem CORS habilitado para `localhost:3000` e `localhost:3001`

---

**Versão da Collection**: 1.0.0
**Última Atualização**: 2025-10-18
**Mantido por**: Jetski Team
