# Fluxo de Ativação de Conta com OIDC

## 📋 Visão Geral

Este documento explica o fluxo de ativação de conta usando o **padrão OIDC nativo do Keycloak** com `UPDATE_PASSWORD` required action.

## 🔄 Fluxo Completo

### 1. Backend: Criação do Usuário

Quando um usuário é convidado e ativa sua conta:

```java
// UserInvitationService.java
keycloakAdminService.createUser(
    usuarioId,
    email,
    nome,
    tenantId,
    roles
);

// KeycloakAdminService.java
user.setRequiredActions(Collections.singletonList("UPDATE_PASSWORD"));
user.setEmailVerified(false);
```

### 2. Backend: Envio de Email

Email enviado ao usuário contém:

```
Assunto: Bem-vindo ao Jetski SaaS - Ative sua conta

Para começar a usar o sistema, faça login clicando no botão abaixo.
No primeiro acesso, você será solicitado a definir uma senha.

[Fazer Login]
↓
http://localhost:3000/login?email=user@example.com
```

### 3. Frontend: Página de Login

Quando o usuário clica no link do email, o frontend deve:

```javascript
// pages/login.tsx ou login.jsx

import { useSearchParams } from 'next/navigation'; // Next.js 13+
// ou
import { useRouter } from 'next/router'; // Next.js 12
// ou
import { useSearchParams } from 'react-router-dom'; // React Router

function LoginPage() {
  const searchParams = useSearchParams();
  const email = searchParams.get('email');

  const handleLogin = () => {
    // Redirecionar para Keycloak com Authorization Code Flow
    const keycloakUrl = process.env.NEXT_PUBLIC_KEYCLOAK_URL;
    const clientId = 'jetski-app'; // ou seu client ID
    const redirectUri = encodeURIComponent(window.location.origin + '/callback');

    const authUrl = `${keycloakUrl}/realms/jetski-saas/protocol/openid-connect/auth?` +
      `client_id=${clientId}&` +
      `redirect_uri=${redirectUri}&` +
      `response_type=code&` +
      `scope=openid profile email&` +
      (email ? `login_hint=${encodeURIComponent(email)}` : '');

    window.location.href = authUrl;
  };

  return (
    <div>
      <h1>Login</h1>
      {email && <p>Bem-vindo, {email}!</p>}
      <button onClick={handleLogin}>Fazer Login com Keycloak</button>
    </div>
  );
}
```

### 4. Keycloak: Required Action (Definir Senha)

Keycloak detecta automaticamente que o usuário tem `UPDATE_PASSWORD` required action e:

1. Exibe tela própria para definir senha
2. Valida a senha conforme políticas configuradas (mínimo 8 caracteres, etc.)
3. Usuário define a senha
4. Keycloak marca a senha como definida
5. Remove a required action `UPDATE_PASSWORD`
6. Marca `emailVerified = true` (se configurado)
7. **Completa o fluxo de autenticação** e redireciona de volta

### 5. Keycloak: Redirect de Volta

Após definir a senha, Keycloak redireciona para:

```
http://localhost:3000/callback?code=ey...
```

### 6. Frontend: Callback (Trocar Code por Tokens)

```javascript
// pages/callback.tsx ou callback.jsx

import { useEffect } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';

function CallbackPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  useEffect(() => {
    const code = searchParams.get('code');

    if (code) {
      // Trocar authorization code por tokens
      exchangeCodeForTokens(code);
    }
  }, [searchParams]);

  const exchangeCodeForTokens = async (code) => {
    const tokenUrl = `${process.env.NEXT_PUBLIC_KEYCLOAK_URL}/realms/jetski-saas/protocol/openid-connect/token`;

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: 'jetski-app',
      code: code,
      redirect_uri: window.location.origin + '/callback',
      // client_secret: 'xxx', // Apenas se for confidential client
    });

    const response = await fetch(tokenUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: body.toString(),
    });

    const tokens = await response.json();

    if (tokens.access_token) {
      // Salvar tokens (localStorage, cookies, etc.)
      localStorage.setItem('access_token', tokens.access_token);
      localStorage.setItem('refresh_token', tokens.refresh_token);
      localStorage.setItem('id_token', tokens.id_token);

      // Redirecionar para dashboard ou home
      router.push('/dashboard');
    }
  };

  return <div>Carregando...</div>;
}
```

## 🔐 Configuração do Client no Keycloak

### Opção 1: Public Client (Recomendado para SPA)

```
Client ID: jetski-app
Client Type: Public
Standard Flow Enabled: ON
Direct Access Grants Enabled: OFF
Valid Redirect URIs: http://localhost:3000/callback, http://localhost:3000/*
Web Origins: http://localhost:3000
```

### Opção 2: Confidential Client (para BFF - Backend for Frontend)

```
Client ID: jetski-app
Client Type: Confidential
Standard Flow Enabled: ON
Valid Redirect URIs: http://localhost:3000/callback
Web Origins: http://localhost:3000
Client Secret: (gerado automaticamente)
```

## 📚 Bibliotecas Recomendadas

### Next.js / React

```bash
npm install @auth0/nextjs-auth0
# ou
npm install next-auth
# ou (mais controle)
npm install oidc-client-ts
```

### Exemplo com `oidc-client-ts`:

```javascript
import { UserManager } from 'oidc-client-ts';

const userManager = new UserManager({
  authority: 'http://localhost:8081/realms/jetski-saas',
  client_id: 'jetski-app',
  redirect_uri: window.location.origin + '/callback',
  response_type: 'code',
  scope: 'openid profile email',
  post_logout_redirect_uri: window.location.origin,
});

// Login
userManager.signinRedirect();

// Callback
userManager.signinRedirectCallback().then(user => {
  console.log('User logged in:', user);
  window.location.href = '/dashboard';
});

// Logout
userManager.signoutRedirect();
```

## 🎯 Vantagens desta Abordagem

✅ **Zero código de definição de senha** no backend
✅ **Validação nativa do Keycloak** (políticas de senha, complexidade, etc.)
✅ **Padrão OIDC** amplamente suportado
✅ **Segurança** gerenciada pelo Keycloak
✅ **Suporte a MFA** out-of-the-box
✅ **Internacionalização** (telas do Keycloak em vários idiomas)
✅ **Auditoria** automática no Keycloak

## ⚠️ Limitações

❌ **UI do Keycloak** - Tela de definir senha é do Keycloak (não customizada)
❌ **Domínio do Keycloak** visível na URL durante o fluxo

## 🎨 Customização (Opcional)

Se quiser customizar a UI do Keycloak:

1. Criar tema customizado no Keycloak
2. Personalizar templates FreeMarker
3. Aplicar CSS/branding da empresa

Documentação: https://www.keycloak.org/docs/latest/server_development/#_themes

## 🧪 Testando o Fluxo

1. Backend: Criar convite e ativar conta
2. Email: Verificar link em `/tmp/emails/`
3. Abrir link no navegador
4. Clicar em "Fazer Login"
5. Keycloak exibe tela de definir senha
6. Definir senha (ex: Test123!)
7. Keycloak redireciona com code
8. Frontend troca code por tokens
9. Usuário autenticado! ✅

## 📞 Suporte

Para dúvidas sobre OIDC, consultar:
- [Keycloak OIDC Documentation](https://www.keycloak.org/docs/latest/securing_apps/#_oidc)
- [OAuth 2.0 Authorization Code Flow](https://oauth.net/2/grant-types/authorization-code/)
- [OpenID Connect Specification](https://openid.net/specs/openid-connect-core-1_0.html)
