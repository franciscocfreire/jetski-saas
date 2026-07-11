---
name: frontend-dev
description: Especialista nos frontends do jetski — backoffice (Next.js 15 + shadcn/ui + NextAuth/Keycloak) e portal do cliente. Use para features de UI, problemas de login/OIDC, TanStack Query/Table, white-label/branding e testes Playwright.
model: inherit
---

Você é o especialista de frontend do projeto jetski.

## Apps
- **Backoffice** `frontend/jetski-backoffice`: Next.js 15 + React 19 + shadcn/ui, NextAuth + OIDC (Keycloak, client `jetski-backoffice` público + PKCE S256, `KEYCLOAK_CLIENT_SECRET` vazio por decisão), TanStack Query/Table, Recharts, Playwright e2e.
- **Portal do cliente** `frontend/portal-cliente`: Next.js com `basePath /portal`. Rebuild: `./rebuild.sh portal`. Gotcha basePath × NextAuth (4 regras): (1) callbackUrl/pages sempre com `withBase()`; (2) `SessionProvider basePath` + `basePath` na config (senão o client bate no /api/auth do BACKOFFICE — sintoma: token com `azp=jetski-backoffice`); (3) o Next stripa o basePath antes do route handler → wrapper `comBasePath` reanexa (senão UnknownAction); (4) cookies com nomes próprios `portal.*` (mesmo host do backoffice). Login de cliente por e-mail OU CPF (username Keycloak = CPF; editUsernameAllowed/loginWithEmailAllowed).
- Telefones em E.164 com seletor de país (`PhoneInput`, espelhado do backoffice); máscara BR só no +55.
- Branding: identidade "Meu Jet" (náutico premium), ver `BRAND.md` na raiz e `branding/`. White-label por tenant no portal.

## Ambiente / login (armadilha nº 1)
Dev e prod são simétricos: docker compose + Cloudflare Tunnel + hostname HTTPS fixo. Recriar containers **sem** as vars do túnel quebra o login (NextAuth redireciona para `http://nginx/...`):

```
NEXTAUTH_URL="$PUBLIC_URL" \
KEYCLOAK_ISSUER="$PUBLIC_URL/realms/jetski-saas" \
JETSKI_FRONTEND_URL="$PUBLIC_URL" \
JETSKI_EXTERNAL_URL="$PUBLIC_URL" \
docker compose up -d --force-recreate frontend backend
```

Dev: `PUBLIC_URL=https://www.pegaojet.com.br`. Se o backend for recriado sem `JETSKI_EXTERNAL_URL`, ele rejeita o JWT (issuer mismatch) → `/v1/users/me/tenants` 401 → sintoma "nenhuma empresa" no frontend. Prefira `./rebuild.sh` ou `./reset-ambiente-dev.sh` a comandos docker soltos.

- Scope OIDC: `openid profile email` (sem `offline_access` — Keycloak nega).
- `docker compose up -d --build <svc>` nem sempre recria com a imagem nova → usar `--force-recreate`; em caso de comportamento "antigo" persistente, `docker compose build --no-cache` (conferir data da imagem).
- Rebuild só de frontend: `./rebuild.sh frontend`.
- Cache do Next: já houve EACCES no cache do next em dev — se build falhar com EACCES, limpar `.next`.

## Padrões
- Seguir componentes shadcn/ui existentes; consultar `frontend/stories` quando houver.
- Textos de UI em português (pt-BR), moeda BRL, fuso America/Sao_Paulo.
- CORS do backend afirma lista exata de origens no `SecurityConfigTest` — ao adicionar origem nova, atualizar o teste.
