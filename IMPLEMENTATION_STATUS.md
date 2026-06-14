# Jetski SaaS - Implementation Status Report

**Date:** 2026-06-14
**Project Version:** 0.8.0
**Architecture:** Modular monolith (Spring Modulith) — Java 21 / Spring Boot 3.3+

> Nota: contagens de testes e cobertura abaixo precisam ser re-medidas com `mvn test`
> (o `target/` estava limpo na geração deste relatório). Fato verificável: **51 classes de teste**.

---

## EXECUTIVE SUMMARY

A plataforma está em **desenvolvimento ativo** e já cobre todo o ciclo operacional e financeiro
do MVP. O backend é um monólito modular com isolamento multi-tenant (RLS), Keycloak (OIDC) e
OPA (ABAC/RBAC). O backoffice web (Next.js 15) está funcional com ~27 telas. O app mobile (KMM)
ainda é só documentação.

**Estado por camada:**

- **Backend** — Funcional. 385+ classes Java, 51 classes de teste, 36 migrations Flyway (V001→V036).
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
- **OPA** — políticas `.rego` montadas via volume.
- Scripts de ambiente: `reset-ambiente-dev.sh` (toda alteração/inclusão de tabela deve entrar aqui), `rebuild.sh`, `rebuild-frontend.sh`.

---

## 4. BANCO DE DADOS — MIGRATIONS (36, V001→V036)

- **V001–V010** — schema base, seed, multi-tenant, signup, marketplace, modelo_midia, ajustes de RLS.
- **V011–V020** — alinhamento de entidades (fechamento diário, comissão, foto, fechamento mensal), despesa operacional, hash de fechamento, e-mail/telefone de vendedor, política de comissão.
- **V021–V033** — domínio de vendedores: config de comissão por tenant, bônus, diária base, presença, PIX, pagamento a vendedor, tipo de pagamento.
- **V034–V036** — alinhamento de OS de manutenção (status check) e despesa de manutenção.

---

## 5. REGRAS DE NEGÓCIO IMPLEMENTADAS

- **RN01** — Billing de locação (tolerância, arredondamento de 15 min, horímetro, valor base).
- **RN03** — Combustível em 3 modos (Incluso / Medido / Taxa fixa) via `FuelPolicy`.
- **RN04** — Comissão com hierarquia de políticas (campanha → modelo → duração → vendedor).
- **RN06** — Manutenção bloqueia jetski; libera ao concluir/cancelar todas as OS ativas.
- Fechamento diário/mensal com **lock retroativo** e hash de valores.
- Multi-tenancy por RLS + RBAC (Keycloak) + ABAC/alçadas (OPA).

---

## 6. PENDÊNCIAS / PRÓXIMOS PASSOS

### Storage de mídia
- Hoje via storage **local** (`LocalStorageController`). Falta integração **S3** (presigned URLs, SSE), validação de fotos obrigatórias, EXIF e hash SHA-256.

### Segurança / compliance
- LGPD (consentimento, retenção por tenant), AWS KMS (envelope encryption), rate-limit por tenant, DPA.

### CI/CD & deploy
- Não há pipeline (GitHub Actions), build de imagem Docker, EKS/Helm/ArgoCD nem RDS configurados.

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
