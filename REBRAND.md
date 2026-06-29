# Rebrand — Pega o Jet → MeuJet

Plataforma renomeada de **Pega o Jet** para **MeuJet**.

- **Marca (display):** `Pega o Jet` → `MeuJet` (UI, e-mails, Keycloak, docs).
- **Domínio PROD:** `jetsave.com.br` → `meujet.com.br`.
- **Domínio DEV/staging:** `pegaojet.com.br` — **mantido** (sem mudança).
- **Tenant cliente "Jet Save Turismo Náutico LTDA":** **mantido** — é uma empresa
  cliente real (aparece no termo NORMAM, mocks e dados de teste), distinta da marca
  da plataforma. Não confundir com o domínio `jetsave.com.br`.

## O que já foi alterado no código (esta leva)

- Marca `Pega o Jet` → `MeuJet`: títulos/UI (layout, login, signup, navbar, footer,
  sidebar, form público de reserva), e-mails (`EmailTemplates`, `DevEmailService`,
  `SmtpEmailService` from-name; `application-{dev,prod}.yml` from-name), Keycloak
  (`keycloak-realm.json` fromDisplayName, `configure-keycloak-smtp.sh`), docs
  (`DEPLOY.md`, `SUPERADMIN.md`, banner do `reset-ambiente-dev.sh`) e teste.
- Domínio prod `jetsave.com.br` → `meujet.com.br`: defaults em `application-prod.yml`
  (CORS/email/frontend-url), `docker-compose.prod.yml` (SMTP_FROM), origens CORS
  hardcoded em `SecurityConfig.java` (+ `SecurityConfigTest`), `DEPLOY.md`.

## Passos de INFRA (executar para o domínio prod entrar no ar)

1. **DNS:** registrar/apontar `meujet.com.br` (e `www`) — no Cloudflare do tunnel.
2. **Cloudflare Tunnel (Zero Trust):** adicionar o public hostname
   `meujet.com.br` (e/ou `www`) → service `http://nginx:80` no mesmo tunnel.
   (O roteamento é no painel, não no compose.)
3. **`.env` de produção (Oracle):** `PUBLIC_URL=https://www.meujet.com.br`
   (ou sem `www`, conforme o hostname do tunnel). Isso já dirige
   NEXTAUTH_URL, KEYCLOAK_ISSUER, BACKOFFICE_URL/REDIRECT, CORS, etc.
4. **Keycloak client `jetski-backoffice`:** garantir redirect/origins do novo
   domínio (o `configure-keycloak-client.sh`/deploy usa `PUBLIC_URL`).
5. **E-mail prod:** `noreply@meujet.com.br` exige SPF/DKIM/sender configurados no
   provedor (Gmail/DNS) do `meujet.com.br` antes de valer; senão, manter o remetente
   atual via `GMAIL_USER` no `.env` até a entrega estar validada.
6. **Deploy:** `deploy.sh` (não destrutivo) com o `.env` atualizado.

### Transição (se for servir os dois domínios por um tempo)
As origens CORS hardcoded em `SecurityConfig.java` foram trocadas para `meujet`.
Se ainda precisar atender `jetsave.com.br` durante o cutover, reabilitar
temporariamente essa origem (ou usar `JETSKI_ALLOWED_ORIGINS` no `.env` com os dois).

## Pendências de marca a decidir (não alteradas)
- `app/(auth)/login/page.tsx`: e-mail de suporte `suporte@pegaojet.com.br`.
- `app/(auth)/signup/page.tsx`: exemplo de subdomínio `{slug}.pegaojet.com.br`.
  Ambos apontam para o domínio **dev**; em produção (meujet) o ideal é torná-los
  dinâmicos (via env) ou trocar para `meujet.com.br`.
- `spring.application.name = pegaojet-api` (`application-{dev,prod}.yml`): rótulo de
  observabilidade. Mantido para não fragmentar métricas/dashboards; renomear é opcional.
