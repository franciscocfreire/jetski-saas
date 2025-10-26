# Postman Collections - Jetski SaaS API

Collections **vivas** para testes manuais da API Jetski SaaS multi-tenant.

## 📦 Collections Disponíveis

### 1. **Jetski-SaaS-API** (Collection Principal)
Todos os endpoints organizados por recurso - ideal para testes individuais e exploração da API.

### 2. **Jetski-Jornadas** 🆕 (User Journeys)
Fluxos completos organizados por caso de uso - ideal para testes E2E e demonstrações.

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
   - `Staging.postman_environment.json`
   - `Production.postman_environment.json`
4. Clique em **Import**

---

## ⚙️ Configuração Inicial

### 👥 Credenciais Multi-Persona 🆕 v2.3

Todos os ambientes agora incluem credenciais para **5 personas** com papéis distintos:

| Persona | Variáveis | Papéis/Permissões |
|---------|-----------|-------------------|
| 👑 **ADMIN_TENANT** | `admin_username`, `admin_password` | Administrador do tenant, gerencia usuários e configurações |
| 👔 **GERENTE** | `gerente_username`, `gerente_password` | Gerente de operações, autoriza descontos, fecha caixa |
| 🎯 **OPERADOR** | `operador_username`, `operador_password` | Operador de pier, faz check-in/check-out, registra abastecimentos |
| 💼 **VENDEDOR** | `vendedor_username`, `vendedor_password` | Vendedor/parceiro, cria reservas, ganha comissões |
| 🔧 **MECANICO** | `mecanico_username`, `mecanico_password` | Mecânico, executa manutenções preventivas e corretivas |

**📌 Uso na Collection Jornadas:**
- Cada persona possui seu próprio token JWT (`admin_token`, `gerente_token`, etc.)
- Fluxos multi-persona validam RBAC e business rules
- Testes negativos verificam bloqueio de operações não autorizadas

### Ambiente Local

O ambiente **Local** já vem pré-configurado com valores padrão:

| Variável | Valor | Descrição |
|----------|-------|-----------|
| `api_url` | `http://localhost:8090/api` | API Spring Boot |
| `base_url` | `http://localhost:8090/api` | Alias para Jornadas collection |
| `keycloak_url` | `http://localhost:8081` | Keycloak local (porta 8081) |
| `keycloak_realm` | `jetski-saas` | Realm do Keycloak |
| `client_id` | `jetski-api` | Client ID configurado |
| `client_secret` | `jetski-secret` | Client secret |
| `admin_username` | `admin@acme.com` | ADMIN_TENANT |
| `gerente_username` | `gerente@acme.com` | GERENTE |
| `operador_username` | `operador@acme.com` | OPERADOR |
| `vendedor_username` | `vendedor@acme.com` | VENDEDOR |
| `mecanico_username` | `mecanico@acme.com` | MECANICO |
| `tenant_id` | `a0eebc99-...` | Tenant ID padrão |

**⚠️ Senhas padrão**: Todas as personas usam senha `{persona}123` (ex: `admin123`, `gerente123`)

### Ambiente Dev (Docker)

O ambiente **Dev** usa Keycloak na porta **8080** (Docker):

```json
{
  "api_url": "http://localhost:8090/api",
  "keycloak_url": "http://localhost:8080",
  "admin_username": "admin@acme.com",
  "gerente_username": "gerente@acme.com",
  "operador_username": "operador@acme.com",
  "vendedor_username": "vendedor@acme.com",
  "mecanico_username": "mecanico@acme.com"
}
```

### Ambientes Staging e Production

Os ambientes **Staging** e **Production** possuem URLs placeholder que **devem ser atualizadas** quando os ambientes forem provisionados:

**Staging (AWS Homologação):**
```json
{
  "api_url": "https://api-staging.jetski.com/api",
  "keycloak_url": "https://auth-staging.jetski.com",
  "admin_username": "admin@jetski.staging",
  "gerente_username": "gerente@jetski.staging"
}
```

**Production:**
```json
{
  "api_url": "https://api.jetski.com/api",
  "keycloak_url": "https://auth.jetski.com"
}
```

⚠️ **ATENÇÃO**:
- Nunca versione credenciais de produção. Use o Postman Vault ou variáveis de ambiente do sistema operacional.
- Ambientes Staging e Production requerem configuração de senhas via variáveis secretas (indicadas com ⚠️ nos arquivos)

---

## 🌍 Ambientes

### Seleção de Ambiente

1. No dropdown superior direito do Postman, selecione o ambiente desejado:
   - **Local** - Para desenvolvimento local (Keycloak porta 8081)
   - **Dev** - Para Docker/ambiente de desenvolvimento compartilhado (Keycloak porta 8080)
   - **Staging** - Para ambiente AWS de homologação (⚠️ requer configuração de URLs e credenciais)
   - **Production** - Para produção (⚠️ usar com cuidado, requer credenciais seguras)

### Variáveis de Ambiente

Cada ambiente possui as seguintes variáveis:

| Variável | Tipo | Descrição | Auto-preenchida? |
|----------|------|-----------|------------------|
| `api_url` | URL | URL base da API | ❌ Manual |
| `base_url` | URL | Alias para api_url (Jornadas) 🆕 | ❌ Manual |
| `keycloak_url` | URL | URL do Keycloak | ❌ Manual |
| `keycloak_realm` | String | Nome do realm | ❌ Manual |
| `client_id` | String | Client ID OAuth2 | ❌ Manual |
| `client_secret` | Secret | Client secret OAuth2 | ❌ Manual |
| `admin_username` 🆕 | String | Username ADMIN_TENANT | ❌ Manual |
| `admin_password` 🆕 | Secret | Password ADMIN_TENANT | ❌ Manual |
| `gerente_username` 🆕 | String | Username GERENTE | ❌ Manual |
| `gerente_password` 🆕 | Secret | Password GERENTE | ❌ Manual |
| `operador_username` 🆕 | String | Username OPERADOR | ❌ Manual |
| `operador_password` 🆕 | Secret | Password OPERADOR | ❌ Manual |
| `vendedor_username` 🆕 | String | Username VENDEDOR | ❌ Manual |
| `vendedor_password` 🆕 | Secret | Password VENDEDOR | ❌ Manual |
| `mecanico_username` 🆕 | String | Username MECANICO | ❌ Manual |
| `mecanico_password` 🆕 | Secret | Password MECANICO | ❌ Manual |
| `username` | String | Legacy (usar admin_username) | ❌ Manual |
| `password` | Secret | Legacy (usar admin_password) | ❌ Manual |
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

### 4. Locações - Operação (Check-in/Check-out) 🆕 Sprint 2

Operações de locação com check-in, check-out e RN01:

- **Check-in from Reservation** - Converte reserva confirmada em locação
- **Walk-in Check-in** - Cliente sem reserva → locação direta
- **Check-out** - Finaliza locação com RN01 (tolerância + arredondamento 15min)
- **List Locações** - Lista locações com filtros
- **Get Locação by ID** - Detalhes completos da locação

**RN01 - Cálculo de cobrança:**
- Tolerância configurável (ex: 5 min)
- Arredondamento para blocos de 15 minutos
- Fórmula: `billableMinutes = ceil((usedMinutes - tolerance) / 15) * 15`

**Exemplo:** Usado 70 min, Tolerância 5 min → Faturável 75 min (5 blocos de 15min)

### 5. Health (Observabilidade)

Health checks e métricas:

- **Health Check** - Status da aplicação e componentes
- **Metrics** - Lista de métricas disponíveis
- **Prometheus Metrics** - Endpoint de métricas no formato Prometheus

---

## 🎯 Jetski - Jornadas (User Journeys)

A collection **Jetski-Jornadas** organiza os endpoints em fluxos completos passo-a-passo com **multi-persona** e **testes automatizados**.

### ✨ Novidades v2.3 (2025-10-25) - FASE 3 🆕

#### 🌍 Ambientes Multi-Persona
**3 ambientes pré-configurados** com credenciais para **5 personas**:
- **Local** (Keycloak porta 8081) - Desenvolvimento local
- **Dev** (Keycloak porta 8080) - Docker/ambiente compartilhado
- **Staging** (AWS) - Homologação (requer configuração de URLs e senhas)

**Credenciais por Persona:**
- 👑 `admin_username` / `admin_password` → ADMIN_TENANT
- 👔 `gerente_username` / `gerente_password` → GERENTE
- 🎯 `operador_username` / `operador_password` → OPERADOR
- 💼 `vendedor_username` / `vendedor_password` → VENDEDOR
- 🔧 `mecanico_username` / `mecanico_password` → MECANICO

**Compatibilidade:**
- Variável `base_url` adicionada como alias de `api_url` (compatibilidade com Jornadas collection)
- Variáveis legadas `username`/`password` mantidas para backward compatibility

#### 📊 Testes Data-Driven (CSV)
**Arquivo de dados**: `data/rn01-tolerance-rounding-tests.csv`

**10 cenários de teste RN01**:
1. Exato no tempo previsto
2-3. Dentro da tolerância (não cobra extra)
4-10. Acima da tolerância (arredondamento 15min)

**Como usar:**
1. Abra Collection Runner no Postman
2. Selecione a jornada "3. Check-in → Check-out (RN01)"
3. Importe o CSV em **Data**
4. Execute 10 iterações com validação automática

**Resultado esperado**: ✅ 10/10 testes passando (100% coverage de RN01)

#### 📂 Nova Estrutura de Pastas
```
postman/
├── Jetski-SaaS-API.postman_collection.json
├── Jetski-Jornadas.postman_collection.json
├── environments/
│   ├── Local.postman_environment.json       (5 personas)
│   ├── Dev.postman_environment.json         (5 personas)
│   ├── Staging.postman_environment.json     (5 personas - configuração manual)
│   └── Production.postman_environment.json  (não versionar senhas)
└── data/
    └── rn01-tolerance-rounding-tests.csv    (10 cenários RN01)
```

### ✨ Novidades v2.2 (2025-10-25) - FASE 2

#### 🔧 5ª Persona: MECANICO 🆕
Nova persona com autenticação separada:
- 🔧 **MECANICO**: Executa manutenções preventivas e corretivas
- Token: `mecanico_token`
- Operações: Iniciar/concluir manutenções, registrar peças

#### 🛠️ Jornada de Manutenção Preventiva 🆕
**"🔧 4. Manutenção Preventiva - Multi-Persona"**

Valida **RN06**: Jetski em manutenção não pode ser reservado.

**Fluxo:**
1. 🎯 Operador: Altera status do jetski para `MANUTENCAO`
2. 💼 Vendedor: Tenta criar reserva → **DEVE FALHAR** (400 Bad Request)
3. 🎯 Operador: Finaliza manutenção → status `DISPONIVEL`
4. 💼 Vendedor: Cria reserva → **AGORA FUNCIONA** (201 Created)

**Teste de bloqueio:** Garante que sistema bloqueia corretamente agendamentos durante manutenção.

#### 📝 Renumeração de Seções
Para acomodar a nova jornada:
- **4. Manutenção Preventiva** (nova)
- **6. Jornadas Negativas** (antigo 5)
- **7. Consultas por Persona** (antigo 6)

### ✨ Novidades v2.1 (2025-10-25) - FASE 1

#### ✅ Testes Automatizados (100% Coverage)
Todos os requests possuem validações automáticas:
- **Status codes** esperados (200, 201, 400, 401, 403, 404)
- **Estrutura de response** (IDs, campos obrigatórios)
- **Transições de estado** (AGUARDANDO_SINAL → CONFIRMADA → EM_CURSO → FINALIZADA)
- **Regras de negócio** (RN01: minutos cobráveis são múltiplos de 15)

#### 📊 Logs Visuais de Billing (RN01)
Check-outs exibem cálculo detalhado no **Console do Postman**:
```
╔════════════════════════════════════════════════╗
║         📊 CÁLCULO RN01 - LOCAÇÃO              ║
╠════════════════════════════════════════════════╣
║ ⏱️  Tempo Usado:        45 minutos            ║
║ 🎁 Tolerância:          5 minutos             ║
║ 💰 Tempo Cobrável:      45 min                ║
║ 💵 Valor Total:         R$ 262.50             ║
╠════════════════════════════════════════════════╣
║ 📐 Fórmula RN01:                               ║
║ billable = ceil((used - tolerance) / 15) * 15  ║
║ valor = (billable / 60) × preço_hora           ║
╚════════════════════════════════════════════════╝
```

#### 🚫 Jornadas Negativas - Testes de Segurança 🆕
**10 cenários de teste de autorização** que devem **FALHAR** com códigos esperados:

**Grupo A: Violações de Papel (5 testes)**
- 5.1. ❌ Vendedor tenta fazer check-in → 403 (apenas Operador pode)
- 5.2. ❌ Vendedor tenta fazer check-out → 403
- 5.3. ❌ Operador tenta confirmar reserva → 403 (apenas Gerente pode)
- 5.4. ❌ Operador tenta criar modelo → 403 (apenas Admin pode)
- 5.5. ❌ Vendedor tenta criar jetski → 403 (apenas Admin pode)

**Grupo B: Violações de Autenticação (3 testes)**
- 5.6. ❌ Request sem token → 401 Unauthorized
- 5.7. ❌ Request com token inválido → 401 Unauthorized
- 5.8. ❌ Request com token expirado → 401 Unauthorized (implementar manualmente)

**Grupo C: Violações de Lógica de Negócio (2 testes)**
- 5.8. ❌ Check-out sem check-in (locação inexistente) → 400 ou 404
- 5.9. ❌ Check-in em reserva não confirmada → 400 Bad Request

**Como usar:**
1. Execute as jornadas positivas (1, 2, 3) primeiro para criar dados
2. Execute a "🚫 5. Jornadas Negativas" completa
3. **Todos os testes devem PASSAR** (validando que a operação foi corretamente BLOQUEADA)

### 📅 Jornadas Disponíveis:

#### 🔐 0. Setup - Autenticar Personas
Obtém tokens JWT de **5 personas diferentes**:
- 👑 **ADMIN_TENANT** (cria dados mestres)
- 👔 **GERENTE** (confirma reservas, autoriza)
- 🎯 **OPERADOR** (check-in/out, atende pier)
- 💼 **VENDEDOR** (cria reservas, ganha comissão)
- 🔧 **MECANICO** (executa manutenções) 🆕 v2.2

#### 🏗️ 1. Configuração do Sistema (Admin)
👑 Admin cria: Modelo → Jetski → Vendedor → Cliente

#### 📅 2. Cliente com Reserva - Multi-Persona ⭐
Fluxo completo com **personas corretas**:
1. 🎯 Operador: Cliente aceita termos
2. 💼 Vendedor: Cria reserva (status: AGUARDANDO_SINAL)
3. 💼 Vendedor: Confirma sinal pago
4. 👔 Gerente: Confirma/autoriza reserva
5. 🎯 Operador: Aloca jetski específico
6. 🎯 Operador: Check-in (status: EM_CURSO)
7. 🎯 Operador: Check-out com **log RN01** (status: FINALIZADA)
8. 👔 Gerente: Consulta resultado final

#### 🚶 3. Cliente Walk-in (Operador)
🎯 Operador:
1. Verifica disponibilidade
2. Check-in direto (sem reserva)
3. Check-out com **log RN01**

#### 🔧 4. Manutenção Preventiva - Multi-Persona 🆕 v2.2
Fluxo completo de manutenção com **bloqueio de agendamento (RN06)**:
1. 🎯 Operador: Altera jetski para MANUTENCAO
2. 💼 Vendedor: Tenta criar reserva (**DEVE FALHAR** - 400)
3. 🎯 Operador: Finaliza manutenção → DISPONIVEL
4. 💼 Vendedor: Cria reserva (**AGORA FUNCIONA** - 201)

**Valida:** Sistema bloqueia agendamentos durante manutenção

#### 🚫 6. Jornadas Negativas - Testes RBAC
Valida segurança e autorização (10 cenários de teste)

#### 📊 7. Consultas por Persona
Cada persona consulta o que tem permissão:
- 👔 Gerente: Locações em curso
- 🎯 Operador: Reservas do dia
- 💼 Vendedor: Minhas reservas (comissões)

### 🎭 Multi-Persona Authentication

**v2.1** usa **tokens separados por persona**:
```javascript
// Cada request usa o token correto automaticamente
"Authorization": "Bearer {{operador_token}}"  // Check-in/out
"Authorization": "Bearer {{vendedor_token}}"  // Criar reserva
"Authorization": "Bearer {{gerente_token}}"   // Confirmar reserva
"Authorization": "Bearer {{admin_token}}"     // Criar modelo
```

**Diferencial:**
- ✅ IDs propagados automaticamente entre requests
- ✅ Persona correta executa cada operação (RBAC realista)
- ✅ 100% dos requests com validações automáticas
- ✅ Logs visuais de cálculos RN01
- ✅ Testes negativos validam segurança

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

## 📊 Testes Data-Driven com CSV 🆕 v2.3

### Arquivo de Dados RN01

O arquivo `data/rn01-tolerance-rounding-tests.csv` contém **10 cenários de teste** para validação completa da **RN01** (Tolerância e Arredondamento de 15min).

**Localização**: `postman/data/rn01-tolerance-rounding-tests.csv`

**Estrutura do CSV**:
```csv
scenario,predicted_minutes,used_minutes,tolerance_minutes,base_price_per_hour,billable_minutes,expected_value,description
RN01.1,60,60,5,100.00,60,100.00,Exato no tempo previsto - cobra valor base
RN01.2,60,64,5,100.00,60,100.00,4min extra dentro da tolerância - não cobra adicional
RN01.3,60,65,5,100.00,60,100.00,5min extra (limite exato) - não cobra adicional
RN01.4,60,66,5,100.00,75,125.00,6min extra (1min acima tolerância) - arredonda p/ 75min
...
```

### Como Usar no Postman Collection Runner

1. **Abra Collection Runner**:
   - Clique nos 3 pontos `...` ao lado de "Jetski - Jornadas"
   - Selecione **Run collection**

2. **Configure Data-Driven Testing**:
   - Em **Data**, clique em **Select File**
   - Escolha `postman/data/rn01-tolerance-rounding-tests.csv`
   - Selecione a jornada **"3. Check-in → Check-out (RN01)"**

3. **Execute**:
   - O Runner executará **10 iterações** (uma para cada linha do CSV)
   - Cada iteração usará valores diferentes de `used_minutes`, `tolerance_minutes`, etc.
   - Valide que `billable_minutes` e `expected_value` correspondem ao esperado

### Cenários de Teste Cobertos

| Cenário | Situação | Expected Outcome |
|---------|----------|------------------|
| **RN01.1** | Exato no tempo previsto (60 min) | 60 min faturáveis = R$ 100.00 |
| **RN01.2** | 4 min extra (dentro da tolerância de 5 min) | 60 min faturáveis = R$ 100.00 |
| **RN01.3** | 5 min extra (limite exato da tolerância) | 60 min faturáveis = R$ 100.00 |
| **RN01.4** | 6 min extra (1 min acima da tolerância) | 75 min faturáveis = R$ 125.00 |
| **RN01.5** | 10 min extra | Arredonda para 75 min = R$ 125.00 |
| **RN01.6** | 20 min extra | Arredonda para 90 min (2 blocos) = R$ 150.00 |
| **RN01.7** | 15 min extra sobre 120 min | Arredonda para 150 min = R$ 200.00 |
| **RN01.8** | 10 min extra sobre 90 min | Arredonda para 105 min = R$ 210.00 |
| **RN01.9** | 30 min extra | Arredonda para 105 min = R$ 175.00 |
| **RN01.10** | 20 min extra sobre 180 min | Arredonda para 210 min = R$ 210.00 |

### Validação Automatizada

Os testes de check-out já incluem validação automática:
```javascript
pm.test('Billable minutes calculados corretamente (RN01)', function () {
    const locacao = pm.response.json();
    const expectedBillable = pm.iterationData.get('billable_minutes');
    pm.expect(locacao.billableMinutes).to.eql(expectedBillable);
});

pm.test('Valor total corresponde ao esperado', function () {
    const locacao = pm.response.json();
    const expectedValue = pm.iterationData.get('expected_value');
    pm.expect(locacao.valorTotal).to.eql(expectedValue);
});
```

**Resultado esperado**: ✅ 10/10 testes passando (100% coverage de RN01)

---

## 🤖 Automação CLI com Newman 🆕 v2.4

### O que é Newman?

**Newman** é o CLI runner oficial do Postman que permite executar collections via linha de comando, ideal para:
- ✅ Integração CI/CD (GitHub Actions, GitLab CI, Jenkins)
- ✅ Testes automatizados em pipelines
- ✅ Execução em ambientes headless (servidores, containers)
- ✅ Geração de relatórios HTML/JSON/JUnit

### Instalação

```bash
cd backend/postman
npm install
```

**Dependências instaladas:**
- `newman` (v6.1.1) - CLI runner
- `newman-reporter-htmlextra` (v1.23.1) - Gerador de relatórios HTML
- `nodemon` (v3.0.2) - Watch mode para desenvolvimento

### Scripts Disponíveis

#### 1. Teste Completo (Local)
Executa a collection completa no ambiente Local com relatório HTML:
```bash
npm run test:local
```

**Saída:**
- Console: Progresso e resultados
- `results/report-local.html` - Relatório visual completo

#### 2. Teste em Ambiente Específico
```bash
npm run test:dev      # Ambiente Dev (Docker)
npm run test:staging  # Ambiente Staging (AWS)
```

#### 3. Testes Data-Driven (RN01)
Executa 10 iterações com o arquivo CSV:
```bash
npm run test:rn01
```

**Saída:** `results/rn01-report.html` com resultados de todas as 10 iterações

#### 4. Testes Negativos (RBAC)
Executa apenas os testes de autorização:
```bash
npm run test:negative
```

**Valida:**
- 401 Unauthorized (sem token)
- 403 Forbidden (role insuficiente)
- 400 Bad Request (lógica de negócio)

#### 5. Modo CI/CD
Executa testes com formato JUnit (para integração CI/CD):
```bash
npm run test:ci
```

**Características:**
- `--bail` - Para na primeira falha
- `--reporters cli,junit` - Saída para CI
- `results/junit-report.xml` - Formato compatível com Jenkins/GitLab/GitHub Actions

#### 6. Watch Mode (Desenvolvimento)
Re-executa testes automaticamente quando a collection muda:
```bash
npm run test:watch
```

**Uso:** Útil durante desenvolvimento de novos endpoints/testes

#### 7. Abrir Relatório HTML
```bash
npm run report:open
```

Abre o último relatório gerado no navegador padrão.

#### 8. Limpar Resultados
```bash
npm run clean
```

Remove todos os arquivos de resultado (`results/*.html`, `*.json`, `*.xml`).

### Estrutura de Resultados

```
postman/
├── results/  (gerado pelo Newman)
│   ├── report-local.html       (relatório visual completo)
│   ├── report-dev.html
│   ├── rn01-report.html        (10 iterações RN01)
│   ├── test-results.json       (JSON estruturado)
│   └── junit-report.xml        (formato CI/CD)
```

**⚠️ Nota:** A pasta `results/` está no `.gitignore` (não é versionada).

### Integração CI/CD 🚀

#### GitHub Actions

Exemplo de workflow completo em `.github/workflows/api-tests.yml`:

```yaml
name: API Tests - Postman Collections

on:
  push:
    branches: [ main, develop ]
  pull_request:
  schedule:
    - cron: '0 6 * * *'  # Daily at 6 AM UTC

jobs:
  api-tests:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: jetski_test
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres

      keycloak:
        image: quay.io/keycloak/keycloak:26.0
        env:
          KEYCLOAK_ADMIN: admin
          KEYCLOAK_ADMIN_PASSWORD: admin123

    steps:
      - uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install Newman
        run: npm ci
        working-directory: backend/postman

      - name: Run API Tests
        run: npm run test:ci
        working-directory: backend/postman

      - name: Upload test results
        uses: actions/upload-artifact@v4
        with:
          name: postman-test-results
          path: backend/postman/results/

      - name: Publish JUnit Report
        uses: dorny/test-reporter@v1
        with:
          path: backend/postman/results/junit-report.xml
          reporter: java-junit
```

**Funcionalidades do pipeline:**
- ✅ Execução automática em push/PR
- ✅ Testes diários agendados (smoke tests)
- ✅ Upload de artefatos (relatórios HTML/JSON)
- ✅ Publicação de resultados no PR
- ✅ Integração com GitHub Checks

#### GitLab CI

```yaml
api-tests:
  stage: test
  image: node:20
  services:
    - postgres:16
    - redis:7
  script:
    - cd backend/postman
    - npm ci
    - npm run test:ci
  artifacts:
    when: always
    reports:
      junit: backend/postman/results/junit-report.xml
    paths:
      - backend/postman/results/
```

#### Jenkins

```groovy
pipeline {
  agent any
  stages {
    stage('API Tests') {
      steps {
        dir('backend/postman') {
          sh 'npm ci'
          sh 'npm run test:ci'
        }
      }
    }
  }
  post {
    always {
      junit 'backend/postman/results/junit-report.xml'
      publishHTML([
        reportDir: 'backend/postman/results',
        reportFiles: 'report-local.html',
        reportName: 'Postman Test Report'
      ])
    }
  }
}
```

### Exemplo de Saída Newman

```
newman

Jetski - Jornadas por Persona

→ 0. Setup - Autenticar Personas
  POST http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token [200 OK, 2.1KB, 245ms]
  ✓ Status code é 200 OK
  ✓ Response contém access_token

→ 1. Configuração do Sistema (Admin)
  POST http://localhost:8090/api/v1/tenants/.../modelos [201 Created, 456B, 89ms]
  ✓ Status code é 201 Created
  ✓ Modelo criado com sucesso

[... mais requests ...]

┌─────────────────────────┬───────────────────┬──────────────────┐
│                         │          executed │           failed │
├─────────────────────────┼───────────────────┼──────────────────┤
│              iterations │                 1 │                0 │
├─────────────────────────┼───────────────────┼──────────────────┤
│                requests │                45 │                0 │
├─────────────────────────┼───────────────────┼──────────────────┤
│            test-scripts │                90 │                0 │
├─────────────────────────┼───────────────────┼──────────────────┤
│      prerequest-scripts │                45 │                0 │
├─────────────────────────┼───────────────────┼──────────────────┤
│              assertions │               127 │                0 │
├─────────────────────────┴───────────────────┴──────────────────┤
│ total run duration: 12.4s                                      │
├────────────────────────────────────────────────────────────────┤
│ total data received: 28.5KB (approx)                           │
├────────────────────────────────────────────────────────────────┤
│ average response time: 134ms [min: 45ms, max: 567ms]          │
└────────────────────────────────────────────────────────────────┘
```

### Relatório HTML Newman

O relatório HTML (`newman-reporter-htmlextra`) inclui:
- 📊 Gráficos de sucesso/falha por request
- ⏱️ Timeline de execução
- 📝 Request/Response bodies
- 🔍 Headers completos
- ✅ Assertions detalhadas
- 📈 Métricas de performance (response times)

**Exemplo:** Abra `results/report-local.html` no navegador para visualização interativa.

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
- `2.4.0` (2025-10-25) - 🤖 **Jetski-Jornadas v2.4**: Automação Newman CLI + CI/CD (GitHub Actions/GitLab/Jenkins) + package.json com 8 scripts
- `2.3.0` (2025-10-25) - 🌍 **Jetski-Jornadas v2.3**: Environments multi-persona (Local/Dev/Staging) + Testes data-driven (CSV 10 cenários RN01)
- `2.2.0` (2025-10-25) - 🔧 **Jetski-Jornadas v2.2**: 5ª persona (MECANICO) + Jornada de Manutenção Preventiva (RN06)
- `2.1.0` (2025-10-25) - 🚀 **Jetski-Jornadas v2.1**: Testes automatizados + Jornadas Negativas (RBAC) + Logs RN01
- `1.6.0` (2025-10-25) - 🆕 **Sprint 2**: Check-in/Check-out com RN01 + Collection "Jetski - Jornadas"
- `1.2.1` (2025-10-20) - Atualização de infraestrutura: OPA v1.9.0 com políticas Rego v1
- `1.2.0` (2025-10-18) - Fluxo OIDC de ativação de conta (sem senha no activate)
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
- 🔐 **OPA**: Authorization policies via Open Policy Agent v1.9.0 (Rego v1 syntax)

---

## 📋 Infraestrutura

### OPA (Open Policy Agent)

**Versão**: 1.9.0
**Policies**: Rego v1 (modernizadas em 2025-10-20)

Os endpoints de `Auth Tests → OPA *` testam a integração com OPA para:
- **RBAC**: Role-based access control
- **Alçada**: Approval authority (descontos, OS)
- **Multi-tenancy**: Isolamento lógico de tenants
- **Business rules**: Regras de negócio específicas

**Como iniciar OPA local:**
```bash
cd /home/franciscocfreire/repos/jetski
./infra/start-opa-local.sh
```

OPA estará disponível em `http://localhost:8181`

---

---

## 🚀 Sprint 2: Check-in/Check-out

### Novos Endpoints

#### POST /v1/tenants/{tenantId}/locacoes/check-in/reserva
Converte reserva confirmada em locação ativa.

**Payload:**
```json
{
  "reservaId": "uuid",
  "horimetroInicio": 100.5,
  "observacoes": "Check-in realizado"
}
```

#### POST /v1/tenants/{tenantId}/locacoes/check-in/walk-in
Cliente sem reserva → locação direta.

**Payload:**
```json
{
  "jetskiId": "uuid",
  "clienteId": "uuid",
  "horimetroInicio": 100.5,
  "duracaoPrevista": 120
}
```

#### POST /v1/tenants/{tenantId}/locacoes/{id}/check-out
Finaliza locação com cálculo RN01.

**Payload:**
```json
{
  "horimetroFim": 102.75,
  "observacoes": "Retornou OK"
}
```

**Resposta (RN01 aplicada):**
```json
{
  "minutosUsados": 135,
  "minutosFaturaveis": 135,
  "valorBase": 337.50,
  "valorTotal": 337.50
}
```

---

**Versão Principal**: Jetski-SaaS-API v1.6.0
**Versão Jornadas**: Jetski-Jornadas v2.2.0
**Última Atualização**: 2025-10-25
**Sprint**: 2 (Check-in/Check-out)
**Mantido por**: Jetski Team

## 📊 Estatísticas v2.4 🆕

**Jetski-Jornadas Collection:**
- ✅ **100%** dos requests com testes automatizados
- 🎯 **7 jornadas** completas (Setup + 6 fluxos)
- 🚫 **10 testes negativos** (RBAC + Auth + Business Logic)
- 👥 **5 personas** autenticadas separadamente (Admin, Gerente, Operador, Vendedor, Mecânico)
- 📊 **2 logs visuais** de cálculo RN01 (check-outs)
- 🔧 **1 teste RN06** (bloqueio durante manutenção)
- 🌍 **3 ambientes** pré-configurados (Local, Dev, Staging)
- 📁 **10 cenários CSV** para data-driven testing (RN01)
- 🤖 **8 scripts Newman** CLI para automação
- 🚀 **3 exemplos CI/CD** (GitHub Actions, GitLab CI, Jenkins)
