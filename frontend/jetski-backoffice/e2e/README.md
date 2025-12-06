# Testes E2E com Playwright

## Visão Geral

Este diretório contém testes end-to-end (E2E) usando Playwright para o Jetski Backoffice.

## Início Rápido - 100% Automático

Os testes E2E são **totalmente automatizados** e não requerem configuração manual!

### 1. Instalar dependências (uma vez)

```bash
cd frontend/jetski-backoffice
npm install
npx playwright install chromium
```

### 2. Iniciar o backend local

```bash
# No diretório raiz do projeto
docker compose up -d
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

### 3. Executar os testes

```bash
# Contra ambiente local
PLAYWRIGHT_BASE_URL=http://localhost:3000 \
PLAYWRIGHT_API_URL=http://localhost:8090/api \
npm run test:e2e
```

**O que acontece automaticamente:**
1. Sistema cria um novo tenant de teste via API
2. Obtém token de ativação via endpoint de teste
3. Ativa a conta automaticamente
4. Faz login no Keycloak
5. Executa todos os testes
6. Ao final, mostra informações do tenant para cleanup

## Fluxo Automático de Testes

```
┌─────────────────────────────────────────────────────────────────┐
│                      GLOBAL SETUP                                │
├─────────────────────────────────────────────────────────────────┤
│ 1. POST /v1/signup/tenant    → Cria tenant + admin              │
│ 2. GET /v1/test/last-email   → Obtém magic token                │
│ 3. POST /v1/signup/magic-activate → Ativa conta                 │
│ 4. Login via Keycloak        → Salva estado de auth             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    EXECUÇÃO DOS TESTES                          │
│  • Signup, Autenticação, Locações, Reservas, Cadastros          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                     GLOBAL TEARDOWN                             │
│  • Loga informações do tenant para cleanup manual               │
│  • Limpa dados de email no backend                              │
└─────────────────────────────────────────────────────────────────┘
```

## Configuração Avançada (Opcional)

Se você preferir usar um tenant existente em vez de criar um novo a cada execução:

```bash
cp .env.e2e.example .env.e2e.local
```

Edite `.env.e2e.local`:

```env
PLAYWRIGHT_BASE_URL=https://pegaojet.com.br
PLAYWRIGHT_API_URL=https://pegaojet.com.br/api
TEST_USER_EMAIL=seu-email@example.com
TEST_USER_PASSWORD=sua-senha
```

## Estrutura

```
e2e/
├── fixtures/           # Fixtures e helpers
│   ├── auth.fixture.ts     # Helpers de autenticação
│   ├── auth-check.ts       # Verificação de credenciais
│   ├── api.fixture.ts      # Cliente API para setup/teardown
│   └── test-data.ts        # Factories para dados de teste
├── pages/              # Page Objects
│   ├── login.page.ts       # Login
│   ├── signup.page.ts      # Signup e Magic Activate
│   ├── dashboard.page.ts   # Dashboard
│   └── locacoes.page.ts    # Locações
├── tests/              # Arquivos de teste
│   ├── auth.spec.ts        # Testes de autenticação
│   ├── signup.spec.ts      # Testes de signup/onboarding
│   ├── locacoes.spec.ts    # Testes de locações
│   ├── reservas.spec.ts    # Testes de reservas
│   └── cadastros.spec.ts   # Testes de cadastros
├── utils/              # Utilitários
│   └── onboarding.ts       # Helpers para fluxo de signup
├── .auth/              # Estado de autenticação (gitignored)
├── global-setup.ts     # Setup automático antes dos testes
└── global-teardown.ts  # Cleanup após testes
```

## Executando os Testes

### Todos os testes (headless)

```bash
npm run test:e2e
```

### Com interface visual do Playwright

```bash
npm run test:e2e:ui
```

### Com browser visível

```bash
npm run test:e2e:headed
```

### Modo debug

```bash
npm run test:e2e:debug
```

### Apenas um arquivo de teste

```bash
npx playwright test signup.spec.ts
```

### Apenas um teste específico

```bash
npx playwright test -g "deve exibir página de signup"
```

## Ver Relatório

Após executar os testes:

```bash
npm run test:e2e:report
```

## Fluxos de Teste

### Signup/Onboarding (`signup.spec.ts`)
- Página de signup
- Geração automática de slug
- Validação de slug em tempo real
- Criação de tenant
- Ativação via magic link

### Autenticação (`auth.spec.ts`)
- Exibição da página de login
- Redirecionamento para Keycloak
- Erro com credenciais inválidas
- Proteção de rotas (redirect sem auth)
- Login com sucesso
- Logout
- Seleção de tenant

### Locações (`locacoes.spec.ts`)
- Listagem e filtros
- Check-in walk-in
- Checklist de saída
- Check-out com billing

### Reservas (`reservas.spec.ts`)
- Visualização de agenda
- Criação de reserva

### Cadastros (`cadastros.spec.ts`)
- CRUD de Jetskis
- CRUD de Modelos
- CRUD de Clientes
- CRUD de Vendedores
- Manutenção

## Backend: Endpoint de Teste

Para que o fluxo automático funcione, o backend precisa ter o endpoint:

```
GET /v1/test/last-email
```

Este endpoint está disponível **apenas** nos profiles `local` e `test` e retorna:

```json
{
  "success": true,
  "to": "email@example.com",
  "magicToken": "eyJhbGciOiJIUzI1NiIs...",
  "temporaryPassword": "ABC123xyz!",
  "sentAt": "2024-01-15T10:30:00Z"
}
```

**Implementação:** `/backend/src/main/java/com/jetski/shared/email/TestEmailController.java`

## Cleanup de Tenants de Teste

Após a execução dos testes, o sistema mostra as informações do tenant criado:

```
📋 Tenant de teste criado durante esta execução:
   ID: a1b2c3d4-...
   Slug: e2e-test-xyz-1705318200000
   Email: e2e.test.1705318200000@example.com
```

Para limpar manualmente:

```sql
-- Identifique tenants de teste (slug começa com "e2e-test-")
SELECT id, slug, razao_social, created_at FROM tenant
WHERE slug LIKE 'e2e-test-%'
ORDER BY created_at DESC;

-- Delete específico
DELETE FROM tenant WHERE id = 'UUID_DO_TENANT';

-- Ou delete todos os tenants de teste antigos (> 24h)
DELETE FROM tenant
WHERE slug LIKE 'e2e-test-%'
AND created_at < NOW() - INTERVAL '24 hours';
```

## Troubleshooting

### Erro "Test endpoint not available"

O backend precisa estar rodando com profile `local` ou `test`:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

### Erro durante ativação de conta

Verifique se o Keycloak está rodando e acessível:

```bash
curl http://localhost:8081/realms/jetski-saas
```

### Timeout no login

Aumente o timeout no `playwright.config.ts` ou verifique a conectividade com o Keycloak.

### Screenshot de erro

Se o login falhar, um screenshot é salvo em:
```
e2e/.auth/login-error.png
```
