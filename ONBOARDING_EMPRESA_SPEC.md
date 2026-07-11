# Onboarding de Empresa — Aprovação e Platform Admin (SPEC)

> Status: **IMPLEMENTADO** (v1 completo + além: god-mode do super admin, `GET /v1/platform/tenants`
> e painel `dashboard/plataforma` — itens que eram §9). Em 09/jul/2026 entraram os e-mails à
> empresa na mudança de status (`TenantStatusEmailListener`/`SignupTenantApprovedListener`) e o
> teste E2E do fluxo (`SignupApprovalFlowIntegrationTest`).
> Escopo **v1** (enxuto): ciclo de aprovação de tenants + um superadmin que aprova/bloqueia
> operando **dentro da sessão de cada tenant** (sem bypass de RLS), + correção da autogestão
> para usuários já existentes.
> **Futuro (§9):** bypass de RLS, múltiplos superadmins via painel, estado `REJEITADO`.
> Fora de escopo: billing/pagamentos, wizard de setup, grupos por tenant no Keycloak.

## 1. Contexto e objetivo

Hoje uma empresa se cadastra (`POST /v1/signup/tenant`), o tenant nasce **ATIVO**
imediatamente e o primeiro admin é provisionado no Keycloak na ativação do magic-link.
Não há curadoria.

Objetivo do **v1**: introduzir um **portão de aprovação** — empresa cadastrada fica
**pendente**; um **superadmin** (único, criado via seed/query) **libera** (→ ATIVO) ou
**bloqueia** (→ SUSPENSO/INATIVO). E corrigir a autogestão para que um **usuário já existente**
possa ser admin/membro de outro tenant.

## 2. O que JÁ existe (não reconstruir)

| Capacidade | Onde | Situação |
|---|---|---|
| Signup público + ativação magic-link | `signup/TenantSignupService`, front `/signup`, `/magic-activate` | ✅ (cria tenant ATIVO) |
| Provisionamento Keycloak (user + realm roles + atributo `tenant_id`) | `shared/internal/keycloak/KeycloakAdminService` | ✅ |
| Vínculo identidade (sem JIT cego) | `usuarios/api/IdentityProviderMappingService` | ✅ |
| Enum de status do tenant | `tenant/domain/TenantStatus` (TRIAL, ATIVO, SUSPENSO, INATIVO, CANCELADO) | ✅ (falta `PENDENTE_APROVACAO`) |
| Gestão de membros por tenant (listar/papéis/desativar/reativar/promover) | `usuarios/api/TenantMemberController` + UI `dashboard/usuarios` | ✅ |
| Convites (criar/reenviar/cancelar/listar) | `usuarios/api/{UserInvitationController,ConviteController}` | ✅ (quebra p/ usuário existente — §6.4) |
| Multi-tenant por usuário + troca de tenant | `tenant_access`, `UserTenantsController`, switcher no front | ✅ (1 usuário pode ter papéis em N tenants) |
| **Fundação de platform admin** | `usuario_global_roles.unrestricted_access`, `TenantAccessService`, OPA `rbac.rego`/`authorization.rego`/`multi_tenant.rego` | ⚠️ existe mas **desconectada** (flag não chega ao OPA) |

## 3. Grupos por tenant no Keycloak — **fora de escopo**

O realm.json não tem mapper de grupo (`"groups": []`) e o backend não lê claim `groups`.
A claim `tenant_id` vem de **atributo do usuário** e os papéis de **realm roles** — já
automatizados em Java. A delegação desta feature é **nível-app** (OPA + `usuario_global_roles`
+ `membro`), então grupos não agregam. Opcional/limpeza futura: remover a criação do grupo
`tenant-acme` dos scripts de dev.

## 4. Ciclo de vida do tenant (v1)

Novo estado **`PENDENTE_APROVACAO`** no enum + check-constraint do banco.

```
        signup (público / autenticado)
                  │
                  ▼
         PENDENTE_APROVACAO
                  │
        (superadmin aprova)
                  │
                  ▼
   ┌──────────► ATIVO ◄──────────┐
   │             │                │
(reativa)        └──(suspende)──► SUSPENSO
   │                              │
   └──────────────────────────────┘
                  │
            (encerra) ▼
               INATIVO / CANCELADO
```

- **Bloquear** = `SUSPENSO` (reversível) ou `INATIVO` (encerrado). Estado `REJEITADO`
  dedicado fica para o futuro (§9).
- **Identidade × aprovação (recomendado, decisão §8.B):** o admin **ativa a conta
  normalmente** (senha + Keycloak) mesmo com tenant `PENDENTE`. Ao logar, o acesso é
  **gated pelo status**: front mostra "empresa em análise"; API recusa operações até ATIVO.

## 5. Escopo v1 — backend

### Epic A — Ligar o superadmin (fundação já existe, falta conectar)

**A1. Propagar `unrestricted_access` ao OPA**
- `shared/authorization/dto/OPAInput.UserContext`: adicionar `unrestrictedAccess` (boolean).
- `shared/authorization/ABACAuthorizationInterceptor#buildUserContext`: popular a partir do
  `TenantAccessService` (que já resolve `usuario_global_roles`).
- Validar que `rbac.rego`/`authorization.rego` leem `input.user.unrestricted_access`.
- **Teste:** superadmin recebe `allow` para `platform:*`/`tenant:*` sem `Membro` no tenant.

**A2. Seed do superadmin único (decisão §8.D)**
- Bootstrap por env `PLATFORM_ADMIN_EMAILS` aplicado no boot de forma idempotente
  (upsert em `usuario_global_roles.unrestricted_access=true` para os emails que já tenham
  `usuario`). Sem painel, sem endpoint de grant/revoke no v1 (futuro §9).
- Refletir no `reset-ambiente-dev.sh` (seed do superadmin de dev).

**A3. Isenção do gate de status para o superadmin** *(habilitador do "tenant por tenant")*
- O gate de §B2 **não** se aplica a usuários com `unrestricted_access` → o superadmin
  consegue entrar na sessão de um tenant `PENDENTE`/`SUSPENSO` para revisar e agir.

> **Sem bypass de RLS (decisão §8.C):** o superadmin **seta `X-Tenant-Id` do tenant alvo**
> (permitido porque é irrestrito) e opera dentro daquela sessão. Aprovar/suspender é um
> `UPDATE` no próprio `tenant` com `id = current_setting('app.tenant_id')` — funciona
> escopado, sem bypass. Listagem cross-tenant fica para o futuro (§9).

### Epic B — Aprovação (signup pendente + gate + auditoria)

**B1. Signup cria tenant PENDENTE**
- `TenantStatus`: novo `PENDENTE_APROVACAO`; migração Flyway atualiza o check-constraint
  (refletir no `reset-ambiente-dev.sh`).
- `TenantSignupService.signupNewTenant` e `createTenantForExistingUser` (decisão §8.E:
  ambos pendentes): status inicial `PENDENTE_APROVACAO`.
- **Assinatura Trial** criada só na **aprovação** (não consumir os 14 dias durante a análise).

**B2. Gate de acesso por status**
- `shared/internal/TenantFilter` (ou `TenantAccessValidator`): se o tenant resolvido tem
  `status ∉ {ATIVO, TRIAL}` **e** o usuário **não** é irrestrito → 403 com código claro
  (`TENANT_PENDENTE` / `TENANT_SUSPENSO` / `TENANT_INATIVO`).
- `GET /v1/user/tenants` já inclui `status`; o front trata `PENDENTE` → tela "em análise".

**B3. Auditoria**
- Eventos `TenantApprovedEvent`, `TenantSuspendedEvent`, `TenantReactivatedEvent`
  (reusar o sistema async de audit-events). Registrar `actor` (superadmin) e `motivo`.

### §6.4 — Correção: adicionar usuário **já existente** a um tenant
`UserInvitationService` lança `ConflictException` se o email já tem `usuario` — isso bloqueia
"usuário já cadastrado vira admin de outra empresa". Correção:
- Email **já existe** como `usuario` → criar só `membro` + `tenant_access` com os papéis
  informados (incl. `ADMIN_TENANT`), **sem** nova conta/Keycloak/magic-link; o usuário acessa
  pelo switcher de tenant.
- Email **não existe** → fluxo atual (magic-link).
- Idempotência: já é membro ativo → 409 claro; membro inativo → reativar.

## 6. Endpoints novos (v1)

Protegidos por `unrestricted_access` (OPA `platform:*`), operando no tenant da sessão.

| Método | Path | Descrição |
|---|---|---|
| GET | `/v1/platform/pending-signups` | Lista pendentes lendo `tenant_signup` (sem RLS → sem bypass): id, slug, razão social, email admin, data |
| POST | `/v1/platform/tenants/{id}/approve` | `PENDENTE_APROVACAO` → `ATIVO` (+ cria assinatura Trial). `{id}` = tenant da sessão |
| POST | `/v1/platform/tenants/{id}/suspend` | `ATIVO` → `SUSPENSO` (body: `motivo`) |
| POST | `/v1/platform/tenants/{id}/reactivate` | `SUSPENSO` → `ATIVO` |

Autogestão (lacuna):

| Método | Path | Auth | Descrição |
|---|---|---|---|
| POST | `/v1/tenants/{tenantId}/members/add-existing` | ADMIN_TENANT | Adiciona **usuário já existente** como membro (§6.4) |

> Demais ações de autogestão (listar/papéis/desativar/reativar/convidar novo) **já existem**.

## 7. Modelo de acesso do superadmin (v1)

- **Descoberta:** `GET /v1/platform/pending-signups` (sem bypass) + (recomendado) **notificação
  ao superadmin no signup** (email/evento p/ `PLATFORM_ADMIN_EMAILS`) com slug/id da nova empresa.
- **Ação:** superadmin seta `X-Tenant-Id` do alvo (permitido por ser irrestrito; gate isento via
  §A3) e chama `approve/suspend/reactivate`.
- **Sem painel** no v1: o superadmin único usa os endpoints diretamente (ou form mínimo). Painel
  completo no futuro (§9).

## 8. Decisões (resolvidas)

- **A. Estado de rejeição:** **adiado** — usar `SUSPENSO`/`INATIVO` por ora; `REJEITADO` no futuro.
- **B. Identidade × aprovação:** ativar conta + **gate por status** (recomendado). ✔
- **C. Bypass de RLS:** **adiado** — superadmin opera por sessão de tenant; sem bypass no v1.
- **D. Superadmin:** **único**, via seed/query (`PLATFORM_ADMIN_EMAILS`); sem grant/revoke nem painel no v1. ✔
- **E. Tenant criado por usuário autenticado:** também **pendente** (recomendado). ✔
- **F. PLATFORM_ADMIN:** **app-level** via `usuario_global_roles` (sem realm role). ✔

## 9. [HISTÓRICO — itens abaixo já entregues: painel de plataforma, listagem completa, trial 14d com expiração automática, créditos, reset/export/exclusão de empresa]

### 9. Futuro (deferido)

- Bypass de RLS (flag de sessão `app.platform_admin` escopado **ou** datasource `BYPASSRLS`)
  para listagem/operação cross-tenant.
- `GET /v1/platform/tenants` (todas as empresas, todos os status) + filtros/busca.
- Painel de plataforma no frontend (`/(platform)`).
- Múltiplos superadmins via painel: `GET/POST/DELETE /v1/platform/admins`.
- Estado `REJEITADO` dedicado + motivo.
- Sync de papéis para o Keycloak quando `membro.papeis` muda (hoje só no provisionamento inicial).

## 10. Sequenciamento (v1)

1. **Epic A** (A1 wire OPA → A2 seed → A3 isenção do gate) — destrava o superadmin.
2. **Epic B** (B1 signup pendente → B2 gate → B3 auditoria).
3. **§6 endpoints** de plataforma + **§6.4** (usuário existente).
4. (Opcional) notificação ao superadmin no signup (§7).

Cada epic é fatiável e testável (Testcontainers + OPA). Atualizar `reset-ambiente-dev.sh`
a cada mudança de schema/RLS (regra do projeto) e `IMPLEMENTATION_STATUS.md` ao concluir.
