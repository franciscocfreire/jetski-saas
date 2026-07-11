# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Multi-tenant SaaS B2B jetski rental management system** ("Meu Jet") — fleet control, reservations, check-in/check-out with mandatory photos, fuel, maintenance, seller commissions, daily/monthly closures, credit-based document emission to the Brazilian Navy (EMA/CHA + GRU with PIX/boleto), customer self-service portal, marketplace, and platform admin (aprovação de empresas, créditos, reset/exclusão de empresa com export de arquivamento).

In **production** (Oracle Cloud ARM, docker compose + Cloudflare Tunnel): site público + marketplace em `www.meujet.com.br`, backoffice em `app.meujet.com.br`, portal do cliente em `cliente.meujet.com.br` (dev espelha em `*.pegaojet.com.br`). Domain language, UI texts and docs are **Portuguese** (pt-BR, BRL, America/Sao_Paulo).

## Codebase Map

- **Backend** `backend/`: Spring Boot 3.3 / Java 21 **modular monolith** (Spring Modulith). Modules: `tenant`, `tenants`, `usuarios`, `signup`, `frota`, `reservas`, `locacoes` (inclui GRU/EMA/assinatura), `manutencao`, `comissoes`, `fechamento`, `combustivel`, `despesas`, `pagamentos`, `bonus`, `dashboard`, `marketplace`, `creditos`, `metering`, `audit`, `metrics` + `shared` (subpacotes expostos exigem `@NamedInterface`). Migrations Flyway V001–V044 (próximo número: `ls backend/src/main/resources/db/migration | sort | tail`), ~1060 testes (Testcontainers Postgres+Redis).
- **Backoffice** `frontend/jetski-backoffice/`: Next.js 15 + React 19 + shadcn/ui, NextAuth + Keycloak (público + PKCE), TanStack Query/Table, Playwright.
- **Portal do cliente** `frontend/portal-cliente/`: Next.js no subdomínio próprio (`cliente.*`), login por e-mail ou CPF, reserva online com sinal PIX.
- **Infra**: `docker-compose.yml` (+ `.prod.yml`/`.ci.yml`), `infra/` (nginx, keycloak realm, OPA policies em `policies/`, observability, `infra/prod/backup.sh` — backup diário com off-site), scripts na raiz (`rebuild.sh`, `reset-ambiente-dev.sh`, `deploy.sh`).
- **Mobile** (KMM): apenas docs (`mobile/*.md`); código em working dir separado (`/mnt/c/repos/jetski-mobile`).

Referências: `IMPLEMENTATION_STATUS.md` (status por feature), `PORTAL_CLIENTE_SPEC.md`, `DEPLOY.md`, `BRAND.md`, `inicial.md` (spec original, histórica — inclui cenários BDD e schema SQL).

**Conhecimento especializado** vive em `.claude/agents/` (`backend-dev`, `frontend-dev`, `devops`) e `.claude/skills/` (`/nova-migration`, `/rebuild-dev`, `/rodar-testes`, `/deploy-prod`) — consulte-os antes de mexer nas respectivas áreas; eles carregam os gotchas do projeto.

## Non-negotiable Rules

1. **Multi-tenant**: TODA entidade operacional tem `tenant_id` + RLS (`tenant_id = current_setting('app.tenant_id')::uuid`) + índice composto `(tenant_id, fk)`. Nunca confie SÓ na RLS: testes rodam como superuser (bypass) e o escopo de cliente (`/v1/customers/**`) tem policy de self-read cross-tenant — lookups sensíveis devem ser tenant-scoped explícitos (`findByTenantIdAnd...`).
2. **Toda alteração de schema = 2 artefatos**: migration Flyway `V0XX` **e** bloco idempotente no `reset-ambiente-dev.sh` (o backend dev NÃO roda Flyway no boot; o reset é o mecanismo real de schema em dev). Usar o skill `/nova-migration`.
3. **Duas populações de identidade que nunca se cruzam**: staff (`Usuario`+`Membro`, papéis, backoffice) vs clientes (role `CLIENTE`, vínculo explícito via `cliente_identity_provider`, portal). Nunca JIT-link por e-mail; nunca dar `Membro` a cliente.
4. **Docker Compose v2** apenas (`docker compose`, nunca `docker-compose`).
5. **CI verde na main dispara o CD** → deploy automático em produção (com migrations). Não faça merge "para ver o CI passar".

## Architecture Essentials

- **Auth/autz**: Keycloak 26 (realm único `jetski-saas`, claim `tenant_id` no JWT) + OPA (`policies/authz/*.rego`) para ABAC/RBAC. Regras novas de OPA precisam de `default <regra> := false` (regra undefined colapsa o `result` inteiro); OPA sem hot-reload → `docker compose restart opa`.
- **Papéis** (em `membro.papeis[]`): ADMIN_TENANT, GERENTE, OPERADOR, VENDEDOR, MECANICO, FINANCEIRO; superadmin de plataforma via `usuario_global_roles.unrestricted_access` (opera um tenant por vez via `X-Tenant-Id`, sem bypass de RLS). 403 = deny de autorização; 400 = deny de negócio — não confundir.
- **Storage**: MinIO/S3 com prefixo por tenant (`{tenant_id}/...`), URLs presignadas (host público via `STORAGE_MINIO_PUBLIC_URL`).
- **E-mail**: dev → Mailpit (UI :8025); prod → Gmail SMTP best-effort (nunca bloqueia emissão).
- **Fuso**: backend e testes rodam em `America/Sao_Paulo` (TZ no compose; `-Duser.timezone` no surefire).

## Key Business Rules

- **Cobrança** (RN01): tempo faturável = round(minutos_usados − tolerância, bloco 15min); valor = horas × preço_hora + extras − descontos.
- **Combustível** (RN03), 3 modos por modelo/jetski/global: Incluso (sem cobrança), Medido (litros × preço do dia), Taxa fixa (por hora faturável). Combustível não é comissionável.
- **Comissão** (RN04): hierarquia primeiro-que-casar: campanha ativa → modelo → faixa de duração → default do vendedor; base = receita comissionável (sem combustível/taxas/multas); calculada no fechamento mensal.
- **Disponibilidade** (RN06): jetski em manutenção não pode ser reservado; OS aberta bloqueia agenda até fechar.
- **Fechamentos**: diário/mensal consolidam e travam edição retroativa.
- **Créditos de emissão**: 1 crédito por documento emitido à Marinha (via EMA); ledger append-only `credito_lancamento`; sem saldo, a emissão bloqueia.
- **Reserva de balcão × portal**: reserva do portal nasce PENDENTE com sinal PIX (30%) e expira em 24h aguardando comprovante; alocação do jetski só no check-in.

## Dev Environment

- Subir código novo: `./rebuild.sh [backend|frontend|portal] [--no-cache]` — **sempre** conferir se a imagem mudou (sha/idade); build 100% CACHED = deploy falso. Ver `/rebuild-dev`.
- Reset completo: `./reset-ambiente-dev.sh` (banco + realm + seeds + migrations). Destrutivo em dev.
- Dev e prod são simétricos (compose + Cloudflare Tunnel); recriar containers sem as vars `NEXTAUTH_URL`/`APP_PUBLIC_URL`/`KEYCLOAK_ISSUER`/`JETSKI_FRONTEND_URL`/`JETSKI_EXTERNAL_URL` quebra o login — prefira os scripts. `rebuild.sh` aceita **UM** serviço por vez.
- Testes: `cd backend && mvn test` (precisa de Docker). Ver `/rodar-testes` para os gotchas (nome exato `ModuleStructureTest`, pool, TZ, CORS).
- Deploy produção: ver `/deploy-prod` e `DEPLOY.md`.
