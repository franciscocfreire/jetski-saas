# Console da Plataforma — Spec

Separação do super admin: tirar a operação da plataforma de dentro do backoffice das
empresas e colocá-la num app próprio, em `admin.meujet.com.br`.

> Status: **F0–F3 entregues** (25/jul/2026); F4–F6 planejadas.
> Relacionado: `SUPERADMIN.md` (operação atual), `ONBOARDING_EMPRESA_SPEC.md`,
> `EMISSAO_DELEGADA_SPEC.md`, `PORTAL_CLIENTE_SPEC.md` (referência de app separado).

---

## 1. Por que

O super admin nasceu como "visualização operacional de todos os tenants" e foi crescendo
dentro do backoffice. Hoje:

- **Frontend**: uma única página de 775 linhas
  (`frontend/jetski-backoffice/app/(dashboard)/dashboard/plataforma/page.tsx`) com 9 cards
  empilhados + 8 componentes em `components/plataforma/`. Zero cobertura E2E.
- **Backend**: 45 endpoints `/v1/platform/**` espalhados por 4 módulos (`tenant`, `creditos`,
  `metering`, `usuarios`).
- **Shell compartilhado**: a página de plataforma roda dentro do `(dashboard)/layout.tsx` do
  tenant, com o mesmo sidebar, o mesmo switcher de empresa e os mesmos gates.
- **Acesso**: flag binária `usuario_global_roles.unrestricted_access`, concedida por env
  (`PLATFORM_ADMIN_EMAILS`) ou `INSERT` manual. Não há tela para administrar administradores.
- **God mode invisível**: o super admin troca de empresa no switcher e opera como se fosse
  membro dela. Sem TTL, sem banner, sem trilha de "entrei na empresa X".
- **Sem visão consolidada**: não existe um número da plataforma. `PlatformFaturaService
  .pendentesConferencia()` (`:105`) itera **todos os tenants** re-setando `app.tenant_id` a
  cada volta — funciona com dezenas de empresas, não sustenta um dashboard.

O produto amadureceu: a plataforma e as empresas são dois negócios com públicos, rotinas e
riscos diferentes. Precisam de dois apps.

---

## 2. Decisões

| # | Decisão | Escolha |
|---|---------|---------|
| D1 | Topologia | App Next.js novo em `frontend/plataforma-console`, servido em `admin.meujet.com.br` (dev: `admin.pegaojet.com.br`), client Keycloak próprio |
| D2 | God mode | **Impersonação explícita e auditada** — sessão de suporte com motivo, TTL, banner e trilha. O switcher "todas as empresas" sai do backoffice |
| D3 | Leitura cross-tenant | **Read model agregado** (`plataforma_metrica_diaria`, tabela de plataforma sem RLS) populado por job noturno. Sem `BYPASSRLS`, sem furo novo em tabela operacional |
| D4 | Modelo de acesso | Papéis de plataforma granulares em `usuario_global_roles.roles[]`, geridos por tela. `unrestricted_access` continua existindo como "acessa qualquer empresa" — mas deixa de ser sinônimo de "pode tudo" |
| D5 | Escopo v1 | Paridade + operadores + dashboard + saúde/auditoria (fases F0–F5) |

### 2.1 Topologia resultante

```
www.meujet.com.br      → site público + marketplace
app.meujet.com.br      → backoffice (empresas operam)
cliente.meujet.com.br  → portal do cliente final
{slug}.meujet.com.br   → vitrine da empresa
sso.meujet.com.br      → Keycloak
admin.meujet.com.br    → console da plataforma            ← NOVO

frontend/
  jetski-backoffice/
  portal-cliente/
  plataforma-console/                                     ← NOVO
```

`admin` já está em `HOSTS_RESERVADOS` (`frontend/jetski-backoffice/middleware.ts:15`) e nos
`SLUGS_RESERVADOS` do backend — nenhuma empresa pode registrar esse slug. Não há colisão com
a vitrine.

---

## 3. Pré-requisitos de segurança (F0 — não negociáveis)

Três correções precisam entrar **antes** de qualquer tela nova. Todas são dívida existente
que a separação expõe.

### 3.1 Defesa em profundidade no Java

Hoje nenhum controller `/v1/platform/**` tem `@PreAuthorize` nem checa
`TenantContext.isUnrestricted()`. O único gate é o OPA, via `ABACAuthorizationInterceptor`.
Há mitigação parcial (o interceptor falha fechado quando o OPA devolve null,
`ABACAuthorizationInterceptor.java:84-90`), mas uma regra `.rego` editada errado abre 45
endpoints de uma vez.

**Ação**: `PlatformScopeInterceptor` (novo, em `shared/authorization`) registrado para o
padrão `/v1/platform/**`, executando **antes** do ABAC: se o principal não tem nenhum papel
de plataforma, 403 imediato. Cobre 100% dos paths presentes e futuros, sem depender de
anotação por método.

### 3.2 Ações de plataforma com nome completo

`ActionExtractor.java:63-70` monta a ação com o **último segmento** do path:

| Endpoint | Ação hoje | Ação proposta |
|---|---|---|
| `POST /v1/platform/tenants/{id}/approve` | `platform:approve` | `platform:tenants:approve` |
| `POST /v1/platform/tenants/{id}/plano` | `platform:plano` | `platform:tenants:plano` |
| `PUT /v1/platform/creditos/config` | `platform:config` | `platform:creditos:config` |
| `PUT /v1/platform/documentos/imagem-config` | `platform:imagem-config` | `platform:documentos:imagem-config` |

Duas ações distintas colapsando no mesmo nome hoje é inócuo (a regra OPA é
`startswith("platform:")`), mas impede papéis granulares e polui a trilha de decisões.

**Ação**: para `/v1/platform/**`, montar a ação com o path completo normalizado (segmentos
que são UUID/`{id}` são descartados), e adaptar `authorization_platform_test.rego` e
`ActionExtractorTest`.

### 3.3 `/v1/platform/**` sem `X-Tenant-Id` obrigatório

`TenantFilter` exige `X-Tenant-Id` em tudo que não está na lista de paths públicos
(`TenantFilter.java:242-264`). Por isso o frontend atual manda o tenant corrente até em
listagens globais (`lib/api/services/platform.ts:13,119,137,149,180`). Um console sem
"empresa corrente" não tem o que mandar.

**Ação**: `/v1/platform/**` passa a dispensar `X-Tenant-Id`; o alvo, quando existe, vem
sempre no path (`/tenants/{id}/...`). O `PlatformScopeInterceptor` (3.1) assume o papel de
gate. Endpoints que hoje dependem do header implícito passam a receber o tenant explícito.

---

## 4. Modelo de acesso (D4)

### 4.1 O problema de hoje

`unrestricted_access = true` colapsa dois conceitos:

1. **Alcance**: "posso acessar qualquer empresa, sem ser membro".
2. **Poder**: "posso executar qualquer ação".

Enquanto só existe um tipo de operador, tudo bem. Com suporte, financeiro e leitura, não.

### 4.2 Papéis propostos

Vivem em `usuario_global_roles.roles[]` (coluna `text[]` já existente — **sem migration de
schema**):

| Papel | Pode | Não pode |
|---|---|---|
| `PLATFORM_ADMIN` | tudo | — |
| `PLATFORM_SUPORTE` | ler tudo, abrir sessão de suporte, aprovar/suspender empresa | destrutivo (reset/excluir/expurgo), lançar créditos, mexer em preço/planos, gerir operadores |
| `PLATFORM_FINANCEIRO` | faturas, créditos, compras, preço, planos e módulos | destrutivo, sessão de suporte, gerir operadores |
| `PLATFORM_LEITURA` | dashboard, listas, auditoria (só GET) | qualquer escrita |

Regras derivadas:

- **Alcance**: `unrestricted_access = true` passa a significar apenas "acessa qualquer
  empresa" e é setado para **qualquer** papel de plataforma. `TenantAccessService:105-116`,
  `TenantFilter:204/213/223` e `TenantAwareDataSourceConfig:79` continuam funcionando sem
  mudança.
- **Poder**: nova policy `policies/authz/platform.rego` mapeia papel → conjunto de ações
  `platform:*`. `authorization.rego:116-121` (`allow if is_platform_admin`) deixa de ser
  incondicional: vira `allow if platform.allow` para ações `platform:*`, e o god mode em
  ações de tenant (ex. `modelo:list`) passa a exigir **sessão de suporte ativa** (§5).
- `PLATFORM_ADMIN` mantém `allow` incondicional — retrocompatível com o comportamento atual.

### 4.3 Ajustes necessários

- `UserPermissionsController:57-70` devolve `["*"]` para qualquer `unrestricted`. Passa a
  devolver o conjunto real de permissões do papel (o console monta o menu com isso — mesmo
  padrão do backoffice, memória "menu por permissão OPA").
- `PlatformAdminSeeder` continua promovendo `PLATFORM_ADMIN` a partir de
  `PLATFORM_ADMIN_EMAILS` — é o bootstrap galinha-e-ovo do primeiro operador
  (`SUPERADMIN.md:37-60`). Documentar que os demais são criados **pela tela**.
- Migration `V0XX__platform_roles_backfill.sql`: linhas existentes com
  `unrestricted_access = true` ganham `PLATFORM_ADMIN` em `roles[]` se ainda não tiverem.
  (+ bloco idempotente no `reset-ambiente-dev.sh` — regra #2 do `CLAUDE.md`.)

### 4.4 Autenticação do console

- Client Keycloak novo: **`jetski-platform-console`** (público + PKCE, como os demais),
  redirect URIs restritas a `https://admin.{meujet,pegaojet}.com.br/*`.
- Browser flow próprio com **2FA obrigatório** (não opt-in) — reusa o que já existe do
  trabalho de 2FA/TOTP/WebAuthn.
- **Step-up (sudo mode)** obrigatório em: reset, exclusão, expurgo, lançamento manual de
  créditos, alteração de preço, concessão/revogação de papel de plataforma, abertura de
  sessão de suporte com escrita. Reusa `kc_action` + `max_age=0` já implementado.
- Realm import (`infra/keycloak-realm.json`) + script de convergência em prod
  (espelhar `infra/prod/configure-keycloak-client.sh`).

---

## 5. Sessão de suporte (D2)

Substitui o god mode implícito. É a feature que justifica a separação: hoje ninguém consegue
responder "quem entrou na empresa X, quando e por quê".

### 5.1 Fluxo

```
Console /empresas/{id}
  └─ [Entrar na empresa]  → motivo obrigatório + step-up 2FA + escolha leitura/escrita
       POST /v1/platform/tenants/{id}/support-session
       ← { code, expiresIn: 60 }                      código de uso único, TTL 60s
  └─ redirect https://app.meujet.com.br/suporte?code=...
       POST /v1/platform/support-session/redeem       backoffice troca code → sessão
       ← Set-Cookie: mj_support=<opaco>               TTL 30min
```

A partir daí, todo request do backoffice manda o cookie; o `TenantFilter` valida a sessão
(existe, não expirada, não encerrada), fixa o `X-Tenant-Id` a partir dela (o operador **não
escolhe** a empresa depois de entrar) e marca `TenantContext.setSupportSession(...)`.

```
Backoffice app.meujet.com.br
┌────────────────────────────────────────────────────┐
│ ⚠ MODO SUPORTE — Náutica Sol · leitura · 28min · ✕ │
└────────────────────────────────────────────────────┘
```

### 5.2 Regras

- **Código de uso único**, nunca o token de sessão, trafega na URL (URL vaza em log de
  proxy e histórico de navegador). TTL 60s, consumido no redeem.
- Sessão opaca em **Redis** (TTL nativo, revogação imediata) + linha durável em Postgres
  para a trilha.
- **Modo leitura** (padrão): o ABAC nega qualquer método ≠ GET. Escrita exige justificativa
  adicional e só para `PLATFORM_ADMIN`/`PLATFORM_SUPORTE`.
- **Toda** escrita feita sob sessão de suporte carimba a auditoria com o `support_session_id`
  e o operador real — a empresa consegue ver o que foi feito em nome dela.
- Encerramento: pelo botão, por expiração, ou por revogação de outro operador.
- Renovação exige nova sessão (sem sliding window).

### 5.3 Tabela

`V0XX__plataforma_sessao_suporte.sql` — tabela de plataforma, **sem RLS** (`tenant_id` é
referência ao alvo, não escopo do dono):

```sql
CREATE TABLE public.plataforma_sessao_suporte (
    id             uuid PRIMARY KEY,
    operador_id    uuid NOT NULL REFERENCES public.usuario(id),
    tenant_id      uuid NOT NULL REFERENCES public.tenant(id),
    motivo         text NOT NULL,
    somente_leitura boolean NOT NULL DEFAULT true,
    iniciada_em    timestamptz NOT NULL DEFAULT now(),
    expira_em      timestamptz NOT NULL,
    encerrada_em   timestamptz,
    encerrada_por  uuid REFERENCES public.usuario(id),
    ip             inet,
    user_agent     text
);
CREATE INDEX idx_sessao_suporte_tenant ON public.plataforma_sessao_suporte (tenant_id, iniciada_em DESC);
CREATE INDEX idx_sessao_suporte_operador ON public.plataforma_sessao_suporte (operador_id, iniciada_em DESC);
```

### 5.4 O que sai do backoffice

- `app/(dashboard)/layout.tsx:90-100` — substituição da lista do switcher por
  `platformService.listAllTenants()`.
- `app/(dashboard)/layout.tsx:135-137,142-145` — isenção de `NoTenantGate` e
  `TenantStatusGate` para `UNRESTRICTED`.
- `components/layout/app-sidebar.tsx:209-216,287` — grupo "Plataforma" e a flag
  `superAdminOnly`.
- `components/layout/app-sidebar.tsx:257-263` — bypass do gate de módulos por plano.

Entra no lugar: banner de modo suporte + o mesmo shell de sempre. Um operador sem sessão de
suporte ativa **não tem empresa nenhuma** no backoffice.

> **Ordem importa**: essa remoção é o último passo da F3. Fazer antes tira a capacidade de
> suporte antes de existir substituto.

---

## 6. Leitura cross-tenant (D3)

### 6.1 A regra

| Categoria | Como ler | Exemplos |
|---|---|---|
| Tabelas de plataforma (sem `tenant_id` ou sem RLS) | `SELECT` direto | `tenant`*, `plano`, `capitania`, `plataforma_config`, `usuario_global_roles`, `plataforma_sessao_suporte`, `plataforma_metrica_diaria` |
| Agregados operacionais | **Read model** `plataforma_metrica_diaria` | receita, locações, emissões, créditos, MRR |
| Detalhe de UMA empresa | `set_config('app.tenant_id', ?, true)` na transação (padrão atual) | detalhe de fatura, comprovante de compra, preview de reset |
| Detalhe cross-tenant ao vivo | **não existe** — não vamos abrir RLS de tabela operacional | — |

\* `tenant` é o único ponto do schema com escape por GUC: a policy da V042 aceita
`current_setting('app.unrestricted') = 'true'`. Esse escape **continua restrito à tabela
`tenant`** — nenhuma tabela operacional ganha cláusula equivalente. O padrão de janela
transaction-local com colunas explicitamente listadas
(`VinculoEmissaoService.java:488-509`) vira um utilitário compartilhado e testado
(`shared/persistence/PlatformReadWindow`), em vez de ficar copiado.

### 6.2 O read model

`V0XX__plataforma_metrica_diaria.sql` — tabela de plataforma, sem RLS, um snapshot por
empresa por dia:

```sql
CREATE TABLE public.plataforma_metrica_diaria (
    tenant_id              uuid NOT NULL REFERENCES public.tenant(id) ON DELETE CASCADE,
    dia                    date NOT NULL,
    -- operacional
    locacoes               integer NOT NULL DEFAULT 0,
    reservas               integer NOT NULL DEFAULT 0,
    no_shows               integer NOT NULL DEFAULT 0,
    receita_bruta          numeric(12,2) NOT NULL DEFAULT 0,
    receita_comissionavel  numeric(12,2) NOT NULL DEFAULT 0,
    jetskis_ativos         integer NOT NULL DEFAULT 0,
    usuarios_ativos        integer NOT NULL DEFAULT 0,
    -- emissão / créditos
    emissoes_documento     integer NOT NULL DEFAULT 0,
    emissoes_gru           integer NOT NULL DEFAULT 0,
    emissoes_previa        integer NOT NULL DEFAULT 0,
    creditos_consumidos    integer NOT NULL DEFAULT 0,
    saldo_creditos_fim     integer NOT NULL DEFAULT 0,
    -- assinatura
    plano_id               uuid,
    mrr                    numeric(12,2) NOT NULL DEFAULT 0,
    faturas_abertas        integer NOT NULL DEFAULT 0,
    valor_em_aberto        numeric(12,2) NOT NULL DEFAULT 0,
    atualizado_em          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, dia)
);
CREATE INDEX idx_metrica_dia ON public.plataforma_metrica_diaria (dia);
```

`PlataformaMetricasJob` — `@Scheduled(cron = "0 15 4 * * *")` (antes do `TenantExclusaoJob`
das 05:45 e do backup). Recalcula uma **janela móvel de 7 dias** (upsert), para absorver
lançamento retroativo e fechamento tardio. Itera tenants com `set_config('app.tenant_id', ?,
true)`, o mesmo padrão dos serviços de plataforma atuais.

> **Gotcha obrigatório**: o job roda na thread `scheduling-*`. `TenantContext` é `ThreadLocal`
> e um vazamento contamina o `TenantAwareDataSource` de todos os jobs seguintes (já aconteceu
> uma vez). `try/finally` com `TenantContext.clear()` a cada iteração, e teste que verifica o
> contexto limpo ao fim.

Endpoint manual `POST /v1/platform/metricas/recalcular?de=&ate=` para backfill e para
depurar divergência sem esperar a madrugada.

---

## 7. O app `plataforma-console`

Next.js 15 + React 19 + shadcn/ui, mesmo stack do backoffice.

```
/                        Dashboard da plataforma
/empresas                Lista (todos os status, filtros, busca)
/empresas/[id]           Detalhe, em abas:
    · Visão geral        status, plano, contato, métricas, [Entrar na empresa]
    · Plano & módulos    alterar plano, módulos habilitados, EAMA (habilitar/desabilitar)
    · Créditos           saldo, extrato, lançamento manual
    · Faturas            histórico, conferência, inadimplência
    · Emissões           metering por competência
    · Dados & LGPD       exportar, resetar (3 níveis), excluir (carência/imediata)
    · Suporte            sessões de suporte na empresa (quem, quando, por quê)
/creditos                Compras pendentes, preço unitário, saldos consolidados
/faturamento             Faturas em conferência, gerar mês, inadimplentes
/catalogo                Planos, módulos por plano, capitanias, compressão de imagem
/operadores              Operadores da plataforma: conceder/revogar papel, 2FA, atividade
/auditoria               Trilha global de ações de plataforma + sessões de suporte
/saude                   Status das dependências + atalhos Grafana
/configuracoes           Rotação de chave de criptografia, config global
```

Cada card da página monolítica atual vira uma rota ou uma aba. Nada some.

### 7.1 Compartilhamento com o backoffice

**Não** vamos criar monorepo / package compartilhado agora — o custo no build Docker e no
CI não se paga para dois apps. O console copia o mínimo:

- `lib/api/client.ts` (axios + interceptors + refresh) — adaptado: sem `X-Tenant-Id` global.
- UI kit shadcn (`components/ui/*`) — já é código gerado, divergir é aceitável.
- `lib/auth.ts` (NextAuth) — client Keycloak diferente.

Fica registrado como dívida consciente. Se um terceiro app aparecer, aí sim vale extrair.

### 7.2 Infra

- `docker-compose.yml`: serviço `console` (porta host `3005`), `depends_on: backend, nginx`.
- `infra/nginx/nginx.conf`: server block `admin.pegaojet.com.br admin.meujet.com.br`,
  espelhando o do portal (`/api/auth/` → console, `/api/` → backend, `/` → console).
- Cloudflare Tunnel: hostname `admin.*` (DNS de subdomínio é **manual**, o wildcard não
  cobre automaticamente — mesma pegadinha da vitrine).
- `rebuild.sh`: aceitar `console` como serviço (lembrando: **um** serviço por vez).
- `deploy.sh`: incluir `console` no build/recreate.
- Vars: `CONSOLE_PUBLIC_URL`, `CONSOLE_NEXTAUTH_URL` — recriar container sem elas quebra o
  login (mesma armadilha já documentada no `CLAUDE.md`).

---

## 8. Backend: organização

O API path `/v1/platform/**` já é o contrato — não muda. A questão é onde o código mora.

**v1**: os `Platform*Controller`/`Platform*Service` **ficam onde estão** (`tenant`,
`creditos`, `metering`). Mover 45 endpoints entre módulos do Modulith exigiria promover
internals a `@NamedInterface` e mexeria em ~15 classes de teste, com risco desproporcional
ao ganho. O que entra é um módulo **novo** `com.jetski.plataforma` para o que não existe
hoje:

```
plataforma/
  api/        PlataformaDashboardController, OperadorController,
              SessaoSuporteController, PlataformaAuditoriaController,
              PlataformaSaudeController
  internal/   PlataformaMetricasJob, PlataformaMetricasService,
              SessaoSuporteService, OperadorService, SaudeService
```

**Fase posterior (opcional, F6)**: consolidar os `Platform*` existentes em `plataforma`,
promovendo o que for necessário a `@NamedInterface`. Vale quando a superfície estabilizar.

### 8.1 Auditoria dual

Ação de plataforma sobre uma empresa grava **duas** entradas: uma no tenant alvo (a empresa
enxerga o que foi feito com ela) e uma global com `tenant_id NULL` (o console lê sem
cross-tenant — a policy insert-only da V051 já permite). É o mesmo padrão de audit dual da
emissão delegada. A tela `/auditoria` lê **só** `tenant_id IS NULL`.

---

## 9. Fases

| Fase | Entrega | Depende de |
|---|---|---|
| **F0** ✅ | Fundação e segurança: `PlatformScopeInterceptor`, `ActionExtractor` com path completo, `/v1/platform/**` sem `X-Tenant-Id`, app Next + compose + nginx + scripts + client Keycloak `jetski-platform-console` | — |
| **F1** ✅ | 2FA obrigatório + paridade: todas as telas atuais migradas para rotas do console. Backoffice ainda mantém a página antiga (coexistência até a F3) | F0 |
| **F2** ✅ | Papéis granulares + tela `/operadores` + migration de backfill + `platform.rego` + `UserPermissionsController` real | F0 |
| **F3** ✅ | Sessão de suporte (backend + banner no backoffice) **e então** remoção da página `/dashboard/plataforma`, do grupo "Plataforma" no sidebar e do switcher de todas-as-empresas | F1, F2 |
| **F4** | `plataforma_metrica_diaria` + job + dashboard da plataforma | F0 |
| **F5** | `/auditoria` (audit dual) + `/saude` (dependências + atalhos Grafana) | F0 |
| **F6** | *(opcional)* consolidação dos `Platform*` no módulo `plataforma` | F1–F5 |

F1, F2, F4 e F5 são paralelizáveis depois da F0. F3 é o corte definitivo.

### 9.1 O que a F0 entregou (25/jul/2026)

**Backend**
- `PlatformScopeInterceptor` (`shared/authorization`) — 403 em `/v1/platform/**` para quem
  não tem `unrestricted_access`, registrado com `order(-10)` (antes do ABAC). É a barreira
  em Java que faltava (§3.1).
- `ActionExtractor` — ações de plataforma pelo path completo, sem identificadores
  (§3.2). `platform:tenants:plano` ≠ `platform:planos:modulos` ≠ `platform:creditos:config`.
- `TenantFilter` — branch de plataforma: resolve identidade **global** pelo JWT sem exigir
  `X-Tenant-Id`; o header continua aceito quando enviado (o backoffice atual manda),
  preservando o comportamento de hoje.
- `PlatformAccessInfo` + `TenantAccessValidator.resolvePlatformAccess(...)`, implementado
  em `TenantAccessService`. Identidade não mapeada devolve `none()` → 403, não 500.

**Frontend** — `frontend/plataforma-console` (Next.js 15, sem basePath, porta 3005):
login pelo client próprio, shell com o mapa das rotas F1–F5 e a visão geral chamando
`/v1/platform/tenants` sem tenant.

**Infra** — serviço `console` no compose (dev e prod), server block `admin.*` no nginx,
`rebuild.sh console`, `deploy.sh`, `CONSOLE_PUBLIC_URL` no `.env.prod.example`, client
`jetski-platform-console` no realm import e no `configure-keycloak-client.sh`.

**Testes** — `PlatformScopeInterceptorTest` (5 casos), `ActionExtractorTest` (+5 casos,
incluindo a não-colisão de ações), e nos testes de integração de plataforma um **operador
de plataforma dedicado por classe** (antes, nenhum deles semeava `usuario_global_roles` —
passavam só porque o OPA estava mockado). Dois casos novos em `MeteringIntegrationTest`:
`ADMIN_TENANT` barrado mesmo com OPA liberando, e endpoint de plataforma sem
`X-Tenant-Id`. OPA: 189/189.

**Não entregue na F0 — 2FA obrigatório no console.** Entregue na F1 (ver §9.2).

### 9.2 O que a F1 entregou

**2FA obrigatório** (`infra/prod/configure-keycloak-console-2fa.sh`, flow `console-browser`):

```
console-browser (top-level)
├─ auth-username-password-form  REQUIRED
└─ auth-otp-form                REQUIRED   → força CONFIGURE_TOTP no 1º login
```

Três decisões deliberadas, cada uma fechando um furo:

- **Sem `auth-cookie`.** No flow padrão o cookie de SSO é ALTERNATIVE e satisfaz o login
  sozinho: quem entrasse no backoffice com 1 fator abriria o console **sem 2FA nenhum**.
  Todo acesso ao console re-autentica.
- ~~**Sem `identity-provider-redirector`.** Operador de plataforma não entra por login social.~~
  **Revogado em 25/jul** junto com a separação staff×cliente: sob identidade única, a pessoa
  entra por onde já autentica. Ver §9.4.
- **`auth-otp-form` REQUIRED, não ALTERNATIVE em subflow.** O arranjo "subflow REQUIRED com
  webauthn/otp ALTERNATIVE" não serve: a selection-list filtra ALTERNATIVE sem credencial e
  o subflow falha (mesma mordida do trusted device). WebAuthn como segunda opção fica para
  depois, com validação de login ao vivo.

Validado no Keycloak de dev sem reset destrutivo (converge via admin API, como em prod):
senha correta → `required-action?execution=CONFIGURE_TOTP`; nenhum código é emitido sem o
segundo fator. Backoffice e portal seguem no `email-code-id` — inalterados. `ROLLBACK=1`
desbinda o flow (kill switch para não perder acesso à plataforma).

**Telas** (`frontend/plataforma-console`) — a página de 775 linhas do backoffice virou:

| Rota | O que traz do backoffice |
|---|---|
| `/empresas` | tabela de empresas + filtro por status e busca (estado na URL) |
| `/empresas/[id]` | aprovar/suspender/reativar, plano e módulos, EAMA, saldo e lançamento de créditos, faturas da empresa, emissões do mês, **zona de perigo** (export, reset 3 níveis, exclusão carência/imediata, cancelar) |
| `/creditos` | fila de compras PIX (aprovar/rejeitar + comprovante), preço unitário, saldos por empresa |
| `/faturamento` | fila global de faturas em conferência (confirmar/cancelar), gerar lote do mês |
| `/emissoes` | metering por empresa com seletor de competência |
| `/catalogo` | módulos por plano, capitanias (CRUD), compressão de imagem |
| `/configuracoes` | rotação de chave de criptografia |

Arquitetura: **server components** para leitura + **server actions** para mutação. O access
token nunca chega ao browser; downloads binários (export .zip, comprovante PIX) passam pelo
proxy autenticado `/api/download`. Sem axios e sem react-query — a superfície é de leitura
com invalidação por `revalidatePath`, e um cliente `fetch` tipado dá conta.

**Detalhe da empresa é uma página com seções, não sub-rotas por aba.** A API de plataforma
não tem endpoint por empresa: tudo vem de listas globais que a página filtra. Uma sub-rota
por aba refaria as mesmas listas a cada troca.

### 9.5 O que a F3 entregou — o corte

**God mode acabou.** Até aqui, operador de plataforma escolhia a empresa no switcher do
backoffice e operava como se fosse membro dela: sem motivo, sem prazo, sem trilha. O banco
não sabia responder "quem entrou na empresa X, quando e por quê".

**Sessão de suporte** (`plataforma_sessao_suporte`, V055):

- **motivo obrigatório** (mín. 5 caracteres — CHECK no banco também);
- **30 minutos**, sem renovação por uso: acabou, abre outra;
- **somente leitura por padrão** — e isso é negação de verdade no OPA, não aviso de UI;
- **revogação imediata** (sem cache: uma consulta por PK por request; janela em que um
  acesso revogado ainda funciona não vale a micro-otimização).

**Handoff por código de uso único.** O console vive em `admin.*` e o backoffice em `app.*`:
cookie não atravessa. O que trafega na URL é um código com 2 minutos de vida, trocado **uma
vez** pelo cookie `mj_support` (HttpOnly). O token nunca vai na URL — URL vaza em log de
proxy, Referer e histórico. Uso único garantido pelo `UPDATE ... WHERE codigo_usado_em IS
NULL`: dois resgates simultâneos, só um afeta linha. Código e token ficam no banco como
SHA-256 — dump não entrega sessão viva.

**OPA.** A regra de god mode (`is_platform_admin` liberando qualquer ação de tenant) foi
substituída por `platform.allow_suporte`: exige sessão ativa **e** papel de plataforma, e a
sessão somente-leitura só passa em GET. Três testes que afirmavam god mode foram reescritos
para afirmar o contrário — é a mudança de comportamento, não regressão.

**Auditoria dual**: cada abertura/encerramento grava no tenant alvo (a empresa tem direito
de ver quem entrou) e globalmente (o console lê sem cross-tenant).

**Removido do backoffice**: página `/dashboard/plataforma`, `components/plataforma/`,
`lib/api/services/platform.ts`, o grupo "Plataforma" do menu, o switcher de todas-as-empresas
e as isenções de `NoTenantGate`/`TenantStatusGate`/gate de módulos para `UNRESTRICTED`. No
lugar entrou a faixa permanente de modo suporte.

**Achado do teste-guarda:** `TenantResetClassificationTest` reprovou a tabela nova — o repo
exige que toda tabela com `tenant_id` seja classificada no reset. `plataforma_sessao_suporte`
entrou como **preservada**: é trilha, não dado operacional; apagá-la num reset deixaria o
acesso sem prova.

### 9.4 Identidade única e login Google no console (25/jul)

Duas decisões do usuário, tomadas juntas:

1. **A regra "duas populações que nunca se cruzam" caiu.** Uma pessoa = uma identidade, que
   acumula papéis (staff de N empresas, cliente de N lojas, operador de plataforma). O
   `CLAUDE.md` #3 foi reescrito. Continua proibido **vínculo automático por coincidência de
   e-mail** — o link é explícito e auditado, senão volta o account-takeover que motivou a
   regra original.
2. **A regra "operador não usa login social" caiu** — era corolário da primeira.

**Flow do console** passou a oferecer os dois caminhos:

```
console-browser
├─ identity-provider-redirector  ALTERNATIVE   → Google
└─ console-browser-forms         ALTERNATIVE   → senha
     ├─ auth-username-password-form REQUIRED
     └─ auth-otp-form               REQUIRED   → força CONFIGURE_TOTP
```

Segue **sem `auth-cookie`**: sessão SSO do backoffice não pode satisfazer o login do console.

**O furo que isso abriria, e como foi fechado.** O segundo fator do caminho Google vem do
`post-broker-2fa`, que é **condicional** (`conditional-user-configured`) e vive no **IdP** —
compartilhado com backoffice e portal, sem como exigi-lo só no console. Quem entrasse pelo
Google sem nenhum fator passaria com **um** fator, enfraquecendo o console. Fechado no
backend: ao conceder papel de plataforma, `PlatformOperadorService` marca `CONFIGURE_TOTP`
para quem não tem fator algum — com o fator cadastrado, a condição do post-broker passa a
valer em qualquer caminho de login.

**Armadilha paga em dev:** o script tentava `DELETE` do flow inteiro para migrar o shape.
O `DELETE` falha com **500** enquanto o flow está vinculado ao client, o `POST` seguinte bate
**409** e as execuções novas são **acrescentadas** às antigas — sobrando REQUIRED e
ALTERNATIVE no mesmo nível, exatamente o arranjo que o Keycloak descarta. A migração passou
a ser cirúrgica: remove só as execuções órfãs do nível 0, sem tocar no flow.

**O que o login real revelou (e a correção).** O primeiro teste do usuário entrou por Google
**sem 2FA**. Os eventos do Keycloak deram o veredito — `IDENTITY_PROVIDER_POST_LOGIN`
seguido de `LOGIN`, ou seja o post-broker **rodou** e não desafiou. Causa: o
`TrustedDeviceCheckAuthenticator` é a *condição* do subflow de 2FA e, com cookie de
dispositivo confiável válido, devolve `false` — o bloco inteiro é pulado. O cookie tinha
sido cadastrado no **backoffice**. Resultado: dispositivo confiável de outro app zerava o
2FA do console, a mesma brecha que motivou remover o `auth-cookie`, por outra porta.

A saída não estava no flow (que é do IdP e compartilhado) e sim um degrau abaixo, **na
condição, que enxerga o client de origem** — o próprio SPI já tinha o precedente: ação
sensível de step-up também não honra dispositivo confiável. O `mj-trusted-device-check`
passou a ter uma lista de clients que nunca honram o cookie; o *enroll* segue a mesma lista
(quem não honra também não oferece "confiar neste navegador"). Confirmado por login real.

**Liga/desliga.** A lista é config da execution
(`clientsSemTrustedDevice`), não constante no código — muda sem reiniciar o Keycloak:

```bash
bash infra/prod/configure-keycloak-console-2fa.sh                      # console SEM trusted device (default)
TRUSTED_DEVICE_CONSOLE=1 bash infra/prod/configure-keycloak-console-2fa.sh   # console honra como backoffice
```

Duas armadilhas pagas ao construir o toggle: (1) `GET /authentication/executions/{id}` **não**
devolve `authenticationConfig` — ler dali fazia o script criar config nova a cada execução
(POST em vez de PUT), deixando órfãs; o id vem do listing do flow. (2) O Keycloak **descarta
valor vazio** ao gravar, então "desligado" chega ao authenticator como chave **ausente** —
cair no default nesse caso tornaria o kill switch inócuo. A regra passou a ser: *existir
config* manda; sem config alguma (realm recém-importado) vale o default, que protege o console.

**Não validável sem navegador:** o login Google de ponta a ponta exige credencial real do
Google — as duas rodadas de verificação vieram do usuário.

**Inconsistência conhecida, ainda não corrigida:** `UserProvisioningService
.provisionOrReuseCliente` lança `IdentityConflictException` quando um e-mail de staff tenta
virar cliente, com a justificativa "populações nunca se cruzam". Sob a regra nova isso está
invertido. Fica para o projeto de unificação (o fluxo de claim é sensível demais para mudar
de passagem).

### 9.3 O que a F2 entregou

**Alcance e poder deixaram de ser a mesma coisa.** `unrestricted_access` segue significando
*acesso a qualquer empresa* (é o que o `TenantFilter` e a RLS usam) e passa a valer para
qualquer papel de plataforma; o que cada um PODE fazer vem do papel `PLATFORM_*` em
`usuario_global_roles.roles[]`, decidido em `policies/authz/platform.rego`.

**O método HTTP entrou na decisão de autorização** (`OPAInput.context.method`). Sem ele,
"somente leitura" era impossível: `GET /v1/platform/creditos` (saldos) e
`POST /v1/platform/creditos/{id}` (lançar) produzem a **mesma** ação `platform:creditos`,
porque o identificador é descartado por design. Idem `creditos/config` e
`documentos/imagem-config`. A matriz agora casa ação **e** método.

| Papel | Pode | Não pode |
|---|---|---|
| `PLATFORM_ADMIN` | tudo | — |
| `PLATFORM_SUPORTE` | leitura; aprovar/suspender/reativar empresa; EAMA; capitanias | destrutivo, financeiro, operadores, segredos |
| `PLATFORM_FINANCEIRO` | leitura; créditos, compras, faturas, plano, oferta de módulos | destrutivo, operadores, ciclo de vida da empresa |
| `PLATFORM_LEITURA` | só GET | qualquer escrita |

Fora do "qualquer GET" para todos menos admin: **export completo da empresa** e
**comprovante PIX** (o financeiro vê o comprovante — precisa dele para conferir).

**God mode em ações de tenant** (`modelo:list`, `locacao:checkin`) ficou restrito a
`PLATFORM_ADMIN`. Suporte, financeiro e leitura não herdam o backoffice. Na F3 isso vira
sessão de suporte explícita, com TTL e trilha.

**Tela `/operadores`** substitui o caminho anterior — editar `PLATFORM_ADMIN_EMAILS` no
`.env` e reiniciar o backend, ou `INSERT` manual, ambos sem trilha. Duas travas contra
auto-bloqueio, ambas com teste: não é possível **revogar o próprio acesso de admin** nem
**remover o último `PLATFORM_ADMIN`** — o conserto seria SQL manual em produção, que é
justamente o que a tela veio eliminar. Conceder exige conta **já cadastrada e ativada**: a
API não cria usuário a partir de um e-mail digitado, para não conceder acesso a um typo.

Toda concessão e revogação grava na auditoria **global** (`tenant_id NULL`, policy
insert-only da V051) com o antes e o depois, de forma **síncrona** — perder essa linha por
falha assíncrona é pior que atrasar a resposta.

`UserPermissionsController` parou de devolver `["*"]` para qualquer irrestrito: só
`PLATFORM_ADMIN` recebe acesso total; os demais recebem a matriz real do `platform.rego`.
Sem isso o menu do console mostraria ações que o OPA vai negar.

**Migration V054** é backfill puro (a coluna `roles[]` existe desde a V001): quem tinha
acesso irrestrito sem papel explícito vira `PLATFORM_ADMIN` — é o poder que já exercia.
Sem `CHECK` de nomes no banco de propósito: `CHECK` do PostgreSQL não aceita subquery e a
alternativa (função `IMMUTABLE`) viraria DDL obrigatório a cada papel novo. A validação
vive no enum `PapelPlataforma`, fonte única usada pela API.

---

## 10. Testes

- **Backend**: `PlatformScopeInterceptorTest` (403 sem papel), `ActionExtractorTest`
  (novos nomes de ação), `SessaoSuporteIntegrationTest` (código de uso único, expiração,
  leitura nega escrita, auditoria carimbada), `PlataformaMetricasJobTest` (agregado bate
  com a soma por tenant **e** `TenantContext` limpo ao fim), `OperadorServiceTest`
  (concessão/revogação auditada, não é possível revogar o próprio último `PLATFORM_ADMIN`).
- **OPA**: estender `authorization_platform_test.rego` com uma matriz papel × ação — cada
  papel novo precisa de `default <regra> := false` (regra undefined colapsa o `result`).
- **E2E**: o console nasce com Playwright desde a F1 — o backoffice hoje não tem **nenhum**
  teste E2E de plataforma, e essa lacuna não deve ser herdada.

---

## 11. Riscos

| Risco | Mitigação |
|---|---|
| Papel granular quebrar acesso de quem hoje é `unrestricted` | Migration de backfill dá `PLATFORM_ADMIN` a todos os existentes; `PLATFORM_ADMIN` mantém `allow` incondicional |
| Mexer em `TenantFilter`/`ActionExtractor` quebrar parte dos ~1060 testes | F0 isolada, roda a suíte inteira antes de seguir; CI verde na main dispara CD |
| Duplicação de código entre os apps divergir | Dívida assumida e registrada; reavaliar se surgir um 4º frontend |
| Read model divergir do dado real | Janela móvel de 7 dias + endpoint de recálculo manual + teste comparando agregado × soma por tenant |
| DNS de `admin.*` esquecido no Cloudflare | Item explícito no checklist da F0 (o wildcard não resolve sozinho) |
| Bootstrap do primeiro operador | Continua por `PLATFORM_ADMIN_EMAILS` + SQL; documentado em `SUPERADMIN.md` |
| Perder capacidade de suporte no corte da F3 | A remoção é o **último** passo da F3, depois da sessão de suporte validada em dev |
