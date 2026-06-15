# Jetski SaaS - Implementation Status Report

**Date:** 2026-06-14
**Project Version:** 0.8.0
**Architecture:** Modular monolith (Spring Modulith 1.2.7) — Java 21 / Spring Boot 3.3

> **Estado da suíte:** `mvn test` = **749 testes, 0 falhas** (BUILD SUCCESS), incluindo o
> `ModuleStructureTest` (sem ciclos e sem acesso a `internal` entre módulos).

---

## EXECUTIVE SUMMARY

A plataforma está em **desenvolvimento ativo** e já cobre todo o ciclo operacional e financeiro
do MVP. O backend é um monólito modular com isolamento multi-tenant (RLS), Keycloak (OIDC) e
OPA (ABAC/RBAC). O backoffice web (Next.js 15) está funcional com ~27 telas. O app mobile (KMM)
ainda é só documentação.

**Estado por camada:**

- **Backend** — Funcional. 385+ classes Java, **749 testes verdes**, **2 migrations Flyway** (baseline + seed, rodam limpas do zero). Spring Modulith com **todos os módulos `CLOSED`** (fronteiras enforçadas).
- **Backoffice web** (`frontend/jetski-backoffice`) — Funcional. Next.js 15 + React 19 + shadcn/ui, NextAuth + OIDC, TanStack Query/Table, Recharts, Playwright e2e.
- **Mobile** (KMM) — Apenas documentação (`mobile/*.md`); código em working dir separado (`/mnt/c/repos/jetski-mobile`).

---

## 1. MÓDULOS DO BACKEND

Estrutura de pacotes por módulo: `api/` (controllers, DTOs) · `domain/` (entidades, enums, eventos) · `internal/` (services, repositories).

### ✅ shared (infraestrutura transversal)
- **security** — Spring Security 6 (OAuth2 Resource Server), `JwtAuthenticationConverter` (extrai `tenant_id` do JWT), `TenantContext` (ThreadLocal), `TenantFilter`, `TenantAccessService`.
- **authorization (OPA)** — `OPAAuthorizationService` + interceptor, 6 políticas Rego (RBAC, contexto, alçadas, regras de negócio, multi-tenant).
- **exception** — `GlobalExceptionHandler`, exceções customizadas, `ErrorResponse`.
- **email** — `EmailService` (`SmtpEmailService` / `DevEmailService`).
- **storage** — `LocalStorageController` (storage local de mídia; abstração para evoluir a S3).
- **audit** — `Auditoria` + `AuditoriaService` + `AuditEventListener` (**eventos assíncronos**), `AuditoriaController` (consulta).

### ✅ tenant / tenants / signup
- `Tenant`, `TenantStatus`, `ComissaoConfig` (config de comissão por tenant), `TenantConfigController`.
- `signup` — onboarding self-service (`TenantSignup`, `SignupStatus`, `TenantSignupController`).

### ✅ usuarios
- Entidades: `Usuario`, `Membro` (relação usuário-tenant + papéis), `Convite`, `UsuarioIdentityProvider`.
- Controllers: convites (`UserInvitationController`, `ConviteController`), ativação de conta (`AccountActivationController` — magic-link), membros (`TenantMemberController`), tenants do usuário (`UserTenantsController`).
- Provisionamento Keycloak + PostgreSQL, magic-link JWT, e-mails de convite.
- Eventos: `MemberInvited/Activated/Deactivated/RolesChanged`.

### ✅ locacoes (núcleo operacional — maior módulo)
- **Frota/catálogo**: `Modelo`, `ModeloMidia`, `Jetski` (`JetskiStatus`), `Cliente`, `ItemOpcional`.
- **Reservas**: `Reserva` (baseada em modelo), `ReservaConfig`, prioridade ALTA/BAIXA, confirmação de sinal, alocação de jetski, expiração agendada, overbooking configurável.
- **Locação**: `Locacao` (`LocacaoStatus`), check-in (reserva e walk-in), check-out com **billing RN01** (tolerância, arredondamento 15 min, horímetro), itens opcionais (`LocacaoItemOpcional`).
- **Fotos**: `Foto`/`FotoTipo`, `PhotoController` (upload de fotos via storage).
- **Vendedores**: `Vendedor` (`VendedorTipo`, PIX `TipoChavePix`, diária base), `PresencaVendedor` (`TipoPresenca`) + `PresencaVendedorController`.
- Eventos: `CheckInEvent`, `CheckOutEvent`, `RentalCompletedEvent`, `LocacaoEditadaEvent`, `DataCheckInAlteradaEvent`.

### ✅ frota
- `FrotaDashboardController` (visão de frota).

### ✅ manutencao (RN06)
- `OSManutencao` (status ABERTA/EM_ANDAMENTO/AGUARDANDO_PECAS/CONCLUIDA/CANCELADA, tipo, prioridade).
- `DespesaManutencao` + `DespesaManutencaoController`.
- Workflow: criar (bloqueia jetski) → start → finish (peças + mão de obra) → libera jetski; endpoint de disponibilidade.

### ✅ combustivel (RN03)
- `Abastecimento` (`TipoAbastecimento`), `FuelPolicy` (`FuelPolicyType`, `FuelChargeMode` — modos **Incluso / Medido / Taxa fixa**), `FuelPriceDay` (preço diário).
- Controllers: `AbastecimentoController`, `FuelPolicyController`, `FuelPriceDayController`.

### ✅ comissoes (RN04)
- `Comissao` (`StatusComissao`, `TipoComissao`), `PoliticaComissao` (`NivelPolitica` — hierarquia campanha → modelo → duração → vendedor).
- Controllers: `ComissaoController`, `PoliticaComissaoController`.

### ✅ fechamento
- `FechamentoDiario`, `FechamentoMensal` (consolidação financeira com lock retroativo, hash de valores, diárias e despesas).
- `FechamentoController`.

### ✅ despesas
- `DespesaOperacional` (`CategoriaDespesa`, `StatusDespesa`), `DespesaOperacionalController`.

### ✅ pagamentos
- `PagamentoVendedor` (`TipoPagamento`) — pagamentos a vendedores (inclui PIX), `PagamentoVendedorController`.

### ✅ bonus
- `BonusVendedor` (`StatusBonus`).

### ✅ dashboard
- `DashboardFinanceiroController` (KPIs financeiros).

### ✅ marketplace
- `PublicMarketplaceController` — vitrine pública de embarcações (endpoint público).

---

## 2. BACKOFFICE WEB (`frontend/jetski-backoffice`)

Next.js 15 + React 19 + TypeScript + shadcn/ui. Auth via NextAuth + OIDC (Keycloak).
Estado: TanStack Query; tabelas: TanStack Table; gráficos: Recharts; testes e2e: Playwright.

**Telas (~27):**
- Auth: login, logout, signup, magic-activate.
- Operacional: agenda, locações, clientes, jetskis (+ detalhe), modelos (+ detalhe), manutenção.
- Vendas/comissões: vendedores (+ detalhe), comissões.
- Financeiro: financeiro, pagamentos, despesas-operacionais, fechamentos (diário/mensal).
- Gestão: usuários, configurações, relatórios, auditoria, dashboard.
- Público: home, `embarcacao/[id]` (marketplace).

---

## 3. INFRAESTRUTURA LOCAL

`docker-compose.yml` — serviços: **nginx, postgres (16), redis (7), keycloak (26), opa, backend, frontend**.

- **PostgreSQL 16** com RLS habilitado em todas as tabelas operacionais; `set_config('app.tenant_id', …)` por sessão.
- **Keycloak 26** — realm único + claim `tenant_id`; setup automatizado (`setup-keycloak-local.sh`).
  O `infra/keycloak-realm.json` é **self-contained**: traz os 5 usuários ACME com IDs
  fixos que casam com o seed (`usuario_identity_provider`) e o client `jetski-test`
  (público, *direct access grants*). Após `docker compose up` os usuários já logam e
  resolvem o usuário interno, sem depender da sincronização de UUID do `reset-ambiente-dev.sh`.
- **OPA** — políticas `.rego` montadas via volume.
- Scripts de ambiente: `reset-ambiente-dev.sh` (toda alteração/inclusão de tabela deve entrar aqui), `rebuild.sh`, `rebuild-frontend.sh`.

---

## 4. BANCO DE DADOS — MIGRATIONS (consolidadas em 2)

As 36 migrations incrementais antigas (V001→V036) não rodavam limpas do zero (as
`align_*_with_entity` assumiam um schema antigo) e foram **consolidadas**:

- **`V001__schema.sql`** — baseline completo (36 tabelas, índices, constraints, RLS policies,
  functions, triggers). Gerado a partir do schema real e alinhado às entidades JPA.
- **`V002__seed_data.sql`** — seed de dev/test (planos, tenant ACME, usuários, modelos, jetskis,
  fixtures de teste).

> Toda alteração de tabela deve entrar no `reset-ambiente-dev.sh` e, daqui pra frente, como uma
> nova migration `V003+` (não editar o baseline).

---

## 5. REGRAS DE NEGÓCIO IMPLEMENTADAS

- **RN01** — Billing de locação (tolerância, arredondamento de 15 min, horímetro, valor base).
- **RN03** — Combustível em 3 modos (Incluso / Medido / Taxa fixa) via `FuelPolicy`.
- **RN04** — Comissão com hierarquia de políticas (campanha → modelo → duração → vendedor).
- **RN06** — Manutenção bloqueia jetski; libera ao concluir/cancelar todas as OS ativas.
- Fechamento diário/mensal com **lock retroativo** e hash de valores.
- Multi-tenancy por RLS + RBAC (Keycloak) + ABAC/alçadas (OPA).

> Mapa **RN ↔ cenário BDD ↔ teste** (cobertura e lacunas) em [`docs/RN-COVERAGE.md`](docs/RN-COVERAGE.md).
> Lacunas conhecidas: RN02 (cancelamento/no-show) não implementado, RN05 caução/danos
> faltando, RN07 sem teste dedicado.

---

## 5.1 ARQUITETURA MODULAR (Spring Modulith)

`ModuleStructureTest` valida as fronteiras em todo build e está **verde**:

- **Sem ciclos.** Os 3 ciclos que existiam foram quebrados:
  - `shared ↔ tenant` → `TenantTimeService` movido para o módulo `tenant` (shared é fundação pura).
  - `comissoes ↔ bonus` → evento `ComissaoCalculadaEvent` (comissoes publica, bonus escuta).
  - `locacoes ↔ fechamento` → port `FechamentoLockChecker` (DI invertida) + eventos.
- **Todos os módulos `CLOSED`.** Internals não são acessados entre módulos; a comunicação
  cross-module passa por named interfaces (`api`, `domain`, `events`) e serviços públicos
  (`*QueryService`, `*Service` em `api`).
- Módulos consumidores que antes "furavam" fronteiras (`dashboard`, `pagamentos`, `signup`)
  agora consomem apenas APIs públicas.

---

## 6. PENDÊNCIAS / PRÓXIMOS PASSOS

### Storage de mídia
- Hoje via storage **local** (`LocalStorageController`). Falta integração **S3** (presigned URLs, SSE), validação de fotos obrigatórias, EXIF e hash SHA-256.

### Segurança / compliance
- LGPD (consentimento, retenção por tenant), AWS KMS (envelope encryption), rate-limit por tenant, DPA.

### CI/CD & deploy
- **CI implementado (GitHub Actions):**
  - `.github/workflows/ci.yml` — `mvn clean test` (unit + integração Testcontainers +
    `ModuleStructureTest`) em todo push/PR na main.
  - `.github/workflows/e2e.yml` — sobe o stack via docker compose e roda a collection
    Postman com **Newman** contra a API real (Keycloak real). Usa `infra/ci-bootstrap-db.sh`
    (cria `jetski_app` + migrations Flyway + grants) e `docker-compose.ci.yml` (alinha o
    issuer do JWT). Roda em push na main + `workflow_dispatch`; **ainda não em PR** até a
    1ª execução verde no runner (validado localmente: 37 requests / 70 assertions / 0 falhas).
- **Falta:** build/push de imagem Docker no pipeline, EKS/Helm/ArgoCD, RDS — nenhum dos
  workflows faz **deploy** (apenas validação).

### Mobile (KMM)
- Apenas documentação; código no working dir `/mnt/c/repos/jetski-mobile`.

### Observabilidade
- Configuração base (Actuator, infra de monitoring em `backend/infra/monitoring` com Grafana/Loki/Prometheus); validar dashboards e correlação por `traceId`.

---

## 7. DOCUMENTAÇÃO RELACIONADA

- `inicial.md` — especificação original (PT).
- `CLAUDE.md` — diretrizes para assistentes de IA + visão da arquitetura.
- `README.md` — setup e arquitetura.
- `AMBIENTE-LOCAL.md`, `DESENVOLVIMENTO-LOCAL.md`, `SETUP.md` — ambiente e workflow.
- `BACKOFFICE-API-*.md` — referência da API consumida pelo backoffice.
- `policies/README.md` — políticas OPA.

---

**Como atualizar este relatório:** rode `mvn test` para números atuais de testes/cobertura e
reflita aqui novos módulos/migrations. Mantenha sincronizado com `CLAUDE.md` (seção *Current Status*).
