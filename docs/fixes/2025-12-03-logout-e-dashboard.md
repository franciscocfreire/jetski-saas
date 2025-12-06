# Correções: Logout e Dashboard - 03/12/2025

## Resumo

Esta documentação descreve as correções aplicadas para resolver problemas de logout com ngrok e melhorias no dashboard.

---

## 1. Problema: Logout redirecionando para localhost ao invés de ngrok

### Sintomas
- Ao clicar em "Sair", o usuário era redirecionado para `localhost:3001` ao invés da URL do ngrok
- Erro 400 do backend: "Tenant ID not found in request"
- Chamada duplicada para `/api/login?logout`

### Causa Raiz
1. **Nginx roteando incorretamente**: A rota `/api/logout` estava sendo enviada para o backend (Spring Boot) ao invés do frontend (Next.js)
2. **Variáveis de ambiente**: O `NEXTAUTH_URL` não estava sendo passado corretamente durante o rebuild
3. **Método de logout inadequado**: Uso de `fetch()` ao invés de navegação direta

### Correções Aplicadas

#### 1.1 Nginx - Adicionada rota específica para logout

**Arquivo:** `infra/nginx/nginx.conf`

```nginx
# Custom logout route (must come before /api/)
location /api/logout {
    set $frontend_upstream http://frontend:3000;
    proxy_pass $frontend_upstream;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $real_scheme;
    proxy_set_header X-Forwarded-Host $host;
}
```

**Motivo:** O nginx estava roteando todos os requests `/api/*` para o backend. A rota `/api/logout` precisa ir para o frontend (Next.js) onde está o handler de logout.

#### 1.2 Rota de Logout - URLs absolutas com NEXTAUTH_URL

**Arquivo:** `frontend/jetski-backoffice/app/api/logout/route.ts`

```typescript
export async function GET() {
  const baseUrl = process.env.NEXTAUTH_URL || 'http://localhost:3001'
  const loginUrl = `${baseUrl}/login`

  try {
    const session = await auth()
    let keycloakLogoutUrl: string | null = null

    if (session?.accessToken) {
      const sessionWithIdToken = session as SessionWithIdToken
      const idToken = sessionWithIdToken.idToken

      if (idToken && process.env.KEYCLOAK_ISSUER) {
        keycloakLogoutUrl = `${process.env.KEYCLOAK_ISSUER}/protocol/openid-connect/logout?id_token_hint=${idToken}&post_logout_redirect_uri=${encodeURIComponent(loginUrl)}`
      }
    }

    // Clear all auth cookies
    const cookieStore = await cookies()
    const allCookies = cookieStore.getAll()
    for (const cookie of allCookies) {
      if (cookie.name.includes('authjs') || cookie.name.includes('next-auth') || cookie.name.includes('session')) {
        cookieStore.delete(cookie.name)
      }
    }

    return NextResponse.redirect(keycloakLogoutUrl || loginUrl)
  } catch (error) {
    return NextResponse.redirect(loginUrl)
  }
}
```

**Motivo:** URLs relativas causavam redirect para localhost. Usando `NEXTAUTH_URL` garantimos que o redirect vá para a URL correta (ngrok ou localhost).

#### 1.3 Sidebar - Navegação direta ao invés de fetch

**Arquivo:** `frontend/jetski-backoffice/components/layout/app-sidebar.tsx`

```typescript
const handleSignOut = () => {
  // Navegar diretamente para a rota de logout
  window.location.href = '/api/logout'
}
```

**Motivo:** `fetch()` não segue redirects automaticamente para URLs externas (Keycloak). Usando `window.location.href`, o browser faz a navegação completa.

#### 1.4 Rebuild Script - Passar variáveis de ambiente

**Arquivo:** `rebuild.sh`

```bash
# URL do ngrok padrao
DEFAULT_NGROK_URL="https://539d02e90662.ngrok-free.app"

# Determinar URL base
if [ "$USE_LOCAL" = true ]; then
    BASE_URL="http://localhost:3001"
    KEYCLOAK_ISSUER="http://localhost:8080/realms/jetski-saas"
else
    BASE_URL="${NGROK_URL:-$DEFAULT_NGROK_URL}"
    KEYCLOAK_ISSUER="${BASE_URL}/realms/jetski-saas"
fi

# Start services with environment variables
NEXTAUTH_URL="$BASE_URL" \
KEYCLOAK_ISSUER="$KEYCLOAK_ISSUER" \
docker compose up -d $SERVICES
```

**Motivo:** As variáveis `NEXTAUTH_URL` e `KEYCLOAK_ISSUER` precisam ser definidas em runtime para o container usar as URLs corretas.

### Fluxo de Logout Corrigido

```
1. Usuário clica em "Sair"
   ↓
2. Browser navega para /api/logout (via nginx → frontend)
   ↓
3. Frontend limpa cookies de sessão
   ↓
4. Frontend redireciona para Keycloak logout:
   https://ngrok.../realms/jetski-saas/protocol/openid-connect/logout
   ?id_token_hint=<token>
   &post_logout_redirect_uri=https://ngrok.../login
   ↓
5. Keycloak invalida sessão SSO
   ↓
6. Keycloak redireciona para https://ngrok.../login
   ↓
7. Usuário vê tela de login
```

---

## 2. Melhoria: Dashboard "Locações em Curso" com indicadores de tempo

### Descrição
Adicionados indicadores visuais de tempo nas locações ativas no dashboard.

### Funcionalidades Adicionadas

**Arquivo:** `frontend/jetski-backoffice/app/(dashboard)/dashboard/page.tsx`

#### 2.1 Funções auxiliares de tempo

```typescript
// Formatar hora de check-in
function formatTime(dateString: string): string {
  const date = new Date(dateString)
  return date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

// Calcular hora de término prevista
function calculateEndTime(checkInDate: string, durationMinutes?: number): string {
  if (!durationMinutes) return '--:--'
  const date = new Date(checkInDate)
  date.setMinutes(date.getMinutes() + durationMinutes)
  return date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

// Determinar status do tempo
function getTimeStatus(checkInDate: string, durationMinutes?: number): {
  status: 'ok' | 'warning' | 'exceeded'
  minutesRemaining: number
}

// Formatar tempo restante
function formatTimeRemaining(minutes: number): string
```

#### 2.2 Indicadores visuais

| Status | Cor | Condição |
|--------|-----|----------|
| Em curso (ok) | Verde | > 15 minutos restantes |
| Atenção (warning) | Amarelo | <= 15 minutos restantes |
| Excedido (exceeded) | Vermelho | Tempo esgotado |

#### 2.3 Informações exibidas

- **Hora Inicial**: Horário do check-in
- **Hora Término**: Horário previsto para término
- **Tempo Restante/Excedido**: Indicador dinâmico

---

## Arquivos Modificados

| Arquivo | Tipo de Alteração |
|---------|-------------------|
| `infra/nginx/nginx.conf` | Adicionada rota `/api/logout` |
| `frontend/.../app/api/logout/route.ts` | Reescrito com URLs absolutas |
| `frontend/.../components/layout/app-sidebar.tsx` | Alterado método de logout |
| `frontend/.../app/(dashboard)/dashboard/page.tsx` | Adicionados indicadores de tempo |
| `rebuild.sh` | Adicionadas variáveis de ambiente ngrok |

---

## Como Testar

### Logout
1. Acesse `https://539d02e90662.ngrok-free.app`
2. Faça login com usuário de teste
3. Clique no menu do usuário (canto inferior esquerdo)
4. Clique em "Sair"
5. Verifique: deve redirecionar para a tela de login do ngrok (não localhost)

### Dashboard com tempos
1. Acesse o dashboard após login
2. Crie uma locação com check-in
3. Observe os indicadores de tempo na seção "Locações em Curso"

---

## Referências

- [NextAuth.js v5 Configuration](https://authjs.dev/getting-started/introduction)
- [Keycloak Logout Endpoint](https://www.keycloak.org/docs/latest/securing_apps/#logout)
- [Nginx Location Directive](https://nginx.org/en/docs/http/ngx_http_core_module.html#location)
