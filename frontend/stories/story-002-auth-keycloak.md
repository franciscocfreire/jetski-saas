---
story_id: STORY-002
epic: EPIC-06
title: Autenticação Keycloak com NextAuth.js (PKCE)
status: TODO
priority: CRITICAL
estimate: 5
assignee: Unassigned
started_at: null
completed_at: null
tags: [frontend, auth, keycloak, nextjs, pkce]
dependencies: [STORY-001]
---

# STORY-002: Autenticação Keycloak com NextAuth.js (PKCE)

## Como
Frontend Developer

## Quero
Login via Keycloak com PKCE e proteção de rotas

## Para que
Usuários possam autenticar e acessar apenas páginas autorizadas

## Critérios de Aceite

- [ ] **CA1:** NextAuth.js configurado com provider Keycloak
- [ ] **CA2:** Fluxo PKCE funcionando corretamente
- [ ] **CA3:** Token JWT armazenado em httpOnly cookie
- [ ] **CA4:** Refresh token automático funciona
- [ ] **CA5:** Middleware protege rotas `/dashboard/*`
- [ ] **CA6:** Logout limpa sessão e redireciona para login

## Tarefas Técnicas

- [ ] Instalar `next-auth`
- [ ] Configurar `app/api/auth/[...nextauth]/route.ts`
- [ ] Criar `lib/auth.ts` com configuração Keycloak
- [ ] Criar middleware para proteção de rotas
- [ ] Criar página `/login`
- [ ] Testar fluxo completo: login → dashboard → logout

## Notas Técnicas

```typescript
// lib/auth.ts
import KeycloakProvider from "next-auth/providers/keycloak";

export const authOptions = {
  providers: [
    KeycloakProvider({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
      issuer: process.env.KEYCLOAK_ISSUER,
      authorization: { params: { scope: "openid email profile" } },
    }),
  ],
  callbacks: {
    async jwt({ token, account }) {
      if (account) {
        token.accessToken = account.access_token;
        token.tenantId = account.tenant_id;
      }
      return token;
    },
  },
};
```

## Links

- **Epic:** [EPIC-06](../../stories/epics/epic-06-backoffice-web.md)

## Changelog

- 2025-01-15: História criada
