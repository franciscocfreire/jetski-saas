# Identidade Única — Spec de Unificação

> Status: **proposta** (30/jul/2026). Projeto previsto desde a decisão de identidade única
> (jul/2026, CLAUDE.md regra 3) e adiado conscientemente pela F0–F6 do console
> ("o fluxo de claim é sensível demais para mudar de passagem",
> `PLATAFORMA_CONSOLE_SPEC.md` §9.4). Janela de execução ideal: **agora** —
> `cliente_identity_provider` tem **zero linhas em produção**; o custo da migração cresce
> com cada cliente que ativar conta no portal.

---

## 1. Princípios (a régua de todas as decisões abaixo)

1. **Uma pessoa = uma identidade**, que acumula papéis: staff (`membro`) de N empresas,
   cliente de N lojas, operador de plataforma (`usuario_global_roles`). Nenhum papel exclui
   outro. (Decisão de jul/2026, que revogou "duas populações que nunca se cruzam".)
2. **O cliente existe para a PLATAFORMA** (Meu Jet). A conta, a identidade civil
   (CPF/RG/nascimento) e a habilitação são da pessoa, guardadas pela plataforma.
3. **A loja só enxerga a pessoa depois de existir negócio** — reserva no portal,
   atendimento de balcão ou lead capturado pela própria loja. O que a loja vê é a
   **ficha comercial** (`cliente`, tenant-scoped, RLS), nunca o cadastro da plataforma.
   Cadastro espontâneo no portal sem reserva = invisível para todas as lojas.
4. **Vínculo nunca é JIT por coincidência de e-mail.** Todo link identidade↔papel é
   explícito e verificado: posse comprovada do e-mail (claim com senha temporária,
   verify-email do Keycloak), identidade federada verificada (Google) ou ação de
   admin — sempre auditado.

---

## 2. O problema: três raízes de pessoa e um vínculo bifurcado

Hoje a mesma pessoa pode estar espalhada em **três tabelas globais** e **uma por tenant**:

| tabela | escopo | chave | RLS | quem usa |
|---|---|---|---|---|
| `usuario` (+`usuario_identity_provider`) | global | e-mail único; sub único global | não | staff, operador de plataforma (backoffice/console) |
| `customer_profile` (V032) | global | `(provider, provider_user_id)`; `cpf` único parcial | não | identidade civil do consumidor (portal) |
| `customer_habilitacao` (V043) | global | **CPF** (pessoa pode não ter conta) | não | habilitações CHA/MTA-E |
| `cliente` (+`cliente_identity_provider`, V004) | por tenant | vínculo `(tenant, provider, sub)` | sim | ficha comercial da loja |

Consequências concretas:

- **Não existe NENHUMA ligação `cliente` → `usuario`** (a única FK é `capturado_por`, que
  aponta para o *staff* que capturou o lead). O mesmo sub do Keycloak pode ter uma linha
  em `usuario_identity_provider` (como staff) e N linhas em `cliente_identity_provider`
  (como cliente) — árvores que nunca se tocam.
- **A regra revogada sobrevive em um ponto**: `provisionOrReuseCliente`
  (`KeycloakUserProvisioningAdapter:72`) lança `IdentityConflictException` quando o e-mail
  de um claim pertence a conta sem role `CLIENTE` — **funcionário não consegue ativar
  pré-conta de cliente**, contradizendo a regra 3 do CLAUDE.md e o próprio
  `TenantFilter`, que já dá a persona CLIENTE a qualquer autenticado em
  `/v1/customers/**`.
- **Manutenção em dobro**: resolução de vínculos, self-read de RLS (V029), merge por CPF,
  propagação de identidade — tudo construído sobre `cliente_identity_provider`, paralelo
  ao mecanismo global do staff (`IdentityProviderMappingService`).

O que **já está certo** e esta spec preserva: `customer_profile`/`customer_habilitacao`
como dados da plataforma invisíveis às lojas; `cliente` tenant-scoped nascendo só com
negócio; contato/endereço/anexos **por loja**; CPF define-once; persona CLIENTE por escopo.

---

## 3. Modelo-alvo

```
                 PLATAFORMA (invisível aos tenants)
  ┌─────────────────────────────────────────────────────────┐
  │ usuario  ←── usuario_identity_provider (sub Keycloak)   │  ← raiz ÚNICA da pessoa
  │   ├── membro (papel staff, por tenant)     [já existe]  │
  │   ├── usuario_global_roles (operador)      [já existe]  │
  │   ├── customer_profile (identidade civil)  [ganha FK]   │
  │   └── customer_habilitacao (por CPF; FK opcional)       │
  └─────────────────────────────────────────────────────────┘
                          │  cliente.usuario_id (nullable)
                          ▼
                 TENANT (ficha comercial, RLS)
  ┌─────────────────────────────────────────────────────────┐
  │ cliente — nasce com o negócio; contato/anexos por loja  │
  │ (cliente_identity_provider: REMOVIDA ao final)          │
  └─────────────────────────────────────────────────────────┘
```

- **`usuario` vira a raiz única**: todo sub com conta (staff OU consumidor) tem uma linha
  em `usuario` + `usuario_identity_provider`. Consumidor sem papel de staff é apenas um
  `usuario` sem `membro` — os fluxos de staff já toleram isso (resolvem e não acham
  vínculo → 403 correto).
- **`cliente.usuario_id uuid NULL`**: preenchido quando a pessoa tem conta (ativação de
  claim, primeira reserva logada). Ficha de balcão/lead sem conta = `usuario_id NULL` —
  ficha existe, identidade ainda não. É a materialização do princípio 3: o vínculo
  comercial é a ÚNICA janela da loja para a pessoa.
- **`customer_profile.usuario_id uuid NOT NULL UNIQUE`** (após migração): o perfil civil
  passa a ser extensão do `usuario`, não uma segunda raiz keyada por sub.
- **`cliente_identity_provider` morre** ao final: o par (resolução multi-loja + vínculo)
  passa a ser `usuario_identity_provider` (sub→pessoa, global) + `cliente.usuario_id`
  (pessoa→fichas, self-read por policy).

### Resolução do escopo `/v1/customers/**` (novo caminho)

1. `sub` do JWT → `usuario_id` via `IdentityProviderMappingService.resolveUsuarioId`
   (global, cacheado — o mesmo do staff; **cai a query própria** do
   `CustomerAccountService`).
2. `set_config('app.customer_usuario', usuario_id, true)` → policy nova
   **`cliente_self_read`** (`FOR SELECT USING (usuario_id = …)`) lista as fichas da
   pessoa em todas as lojas — substitui a V029, que era na tabela de vínculo.
3. Operações por loja seguem idênticas: `fixarTenant(tenantId)` + repositórios
   **tenant-scoped explícitos** (a regra de ouro não muda: policy permissiva soma com OR,
   lookup de staff nunca confia só na RLS).

---

## 4. Decisões

**D1 — `cliente.usuario_id` em vez de re-keyar a tabela de vínculo.** Uma coluna FK dá
integridade direta, elimina uma tabela e um índice de lookup; o custo é a policy
self-read migrar para `cliente` (superfície maior — mitigada em D5).

**D2 — Consumidor ganha `usuario` + `usuario_identity_provider`.** Uma pessoa, um
mecanismo de mapping, um cache. `usuario.email` é UNIQUE — colisão de e-mail entre conta
de consumidor e convite de staff deixa de ser conflito e vira **a mesma pessoa
acumulando papéis** (o objetivo do projeto). Guarda: a criação continua vindo só de
fluxos verificados (D4), nunca de matching espontâneo.

**D3 — `IdentityConflictException` morre.** `provisionOrReuseCliente` reusa QUALQUER
conta cujo e-mail bata, staff incluído — a prova de posse é a mesma de hoje (senha
temporária entregue ao e-mail do cliente + verify-email). O aviso no log muda de
"claim recusado" para "conta existente reutilizada (staff→cliente)" e o evento de
auditoria `CONTA_ATIVADA` ganha o campo `contaReutilizada`.

**D4 — Onde nasce o `usuario` do consumidor** (uma função só, `provisionarPessoa(sub)`):
ativação de claim, primeira reserva logada e signup do portal com e-mail verificado.
Idempotente (upsert por sub), audita a criação, e NUNCA roda por matching de e-mail —
somente com o sub autenticado em mãos.

**D5 — Policy `cliente_self_read` estreita e com teste não-superuser.** `FOR SELECT`
apenas, `usuario_id IS NOT NULL AND usuario_id = NULLIF(current_setting('app.customer_usuario', true), '')::uuid`.
Exposição maior que a V029 (a ficha tem dados pessoais) é aceita porque: o setting só é
fixado nos serviços customer-scoped; staff nunca seta esse GUC; e a classe
`CustomerNonSuperuserIntegrationTest` (F1) passa a validar o isolamento com RLS valendo
de verdade — a lição dos 3 bugs de 28/jul aplicada ANTES do bug desta vez.

**D6 — `customer_habilitacao` mantém o CPF como chave humana.** Pessoa de balcão sem
conta continua existindo por CPF. `provider/provider_user_id` são substituídos por
`usuario_id` nullable; o dedupe por `gru_numero` e a união (vivo ∪ global) do
`CustomerHabilitacaoService` não mudam.

**D7 — Merge por CPF simplifica, não some.** O merge OTP (conta Google duplicada) passa a
operar sobre `usuario`: transferência da identidade federada + delete do `usuario`
duplicado (sem fichas). A restrição atual "duplicata sem vínculos" vira
"duplicata sem fichas (`cliente.usuario_id`)" — mesma semântica.

**D8 — Visibilidade formalizada (a regra do produto).** A loja vê exclusivamente as
linhas de `cliente` do seu tenant. Nenhum endpoint de staff/backoffice lê `usuario`,
`customer_profile` ou `customer_habilitacao` de consumidores; o console da plataforma
também não ganha "listagem de pessoas" neste projeto (se um dia precisar — suporte/LGPD —
é ação exclusiva de `PLATFORM_ADMIN`, auditada, em spec própria). "Negócio" que cria
ficha: reserva de portal, atendimento de balcão, lead capturado pela loja.

---

## 5. Fases

Cada fase é deployável sozinha, com a anterior no ar. Regra de schema vale em todas:
migration + bloco no `reset-ambiente-dev.sh`.

### F0 — Fundação (schema + dupla escrita)
- Migration `V0XX`: `cliente.usuario_id uuid NULL REFERENCES usuario(id)` + índice
  `(usuario_id)` parcial (`WHERE usuario_id IS NOT NULL`);
  `customer_profile.usuario_id uuid NULL REFERENCES usuario(id) UNIQUE`;
  `customer_habilitacao.usuario_id uuid NULL REFERENCES usuario(id)`.
- `provisionarPessoa(sub)` (D4) no módulo `usuarios` (NamedInterface se preciso).
- **Dupla escrita** nos 2 únicos pontos que criam vínculo hoje
  (`ClaimService:215`, `CustomerReservaService:237`): além do
  `ClienteIdentityProvider`, setar `cliente.usuario_id` + garantir
  `usuario`/`usuario_identity_provider` + `customer_profile.usuario_id`.
- **Backfill**: em produção `cliente_identity_provider` está vazia — a migration só
  ASSERTA isso (`DO $$ … RAISE EXCEPTION` se aparecer linha antes do deploy, para forçar
  tratamento manual em vez de dado órfão silencioso). Dev renasce pelo reset.

### F1 — Leitura pelo caminho novo + harness
- `CustomerAccountService.vinculos()` resolve por
  `resolveUsuarioId(sub)` + `cliente_self_read` (policy nova, migration própria);
  demais serviços customer-scoped não mudam (continuam consumindo `vinculos()`).
- `CustomerNonSuperuserIntegrationTest` no harness não-superuser: resolução multi-loja,
  isolamento da policy (um usuario não lê ficha de outro), propagação de identidade com
  o `saveAndFlush` por tenant (o gotcha flush×RLS ganha teste com RLS REAL).
- V029 e `app.customer_sub` ficam vigentes (fallback) até a F3.

### F2 — Regra nova no claim (o pedaço sensível)
- `provisionOrReuseCliente` sob D3; `IdentityConflictException` e o teste que a cobre
  são substituídos por casos de acumulação (staff ativa pré-conta; conta Google ativa
  pré-conta).
- E2E manual roteirizado em dev (claim de balcão × conta existente staff, Google e
  consumidor) ANTES do merge — o fluxo tem senha temporária, e-mail e Keycloak reais que
  a suíte não exercita.

### F3 — Re-keyar os dados globais
- `customer_profile`: preencher `usuario_id` para todo perfil cujo sub resolva; fluxos
  (`obter`, `definirCpf`, merge D7) passam a operar por `usuario_id`;
  `provider/provider_user_id` viram legado de leitura.
- `customer_habilitacao`: sync grava `usuario_id` quando houver conta (D6).

### F4 — Desligamento
- Remover leituras/escritas de `cliente_identity_provider` (lista fechada no
  levantamento: `CustomerAccountService`, `ClaimService`, `CustomerReservaService`,
  `ClaimAutoConviteListener`, `CustomerHabilitacaoSyncService`, `TenantResetService`);
  tirar a tabela da lista do reset; migration `DROP TABLE` + drop da V029;
  remover colunas legadas de `customer_profile`.
- Atualizar `CLAUDE.md` (regra 3 deixa de dizer "migração em andamento"),
  `PORTAL_CLIENTE_SPEC.md`, `docs/AUTORIZACAO.md`.

---

## 6. O que NÃO muda (âncoras de segurança)

- RLS por tenant de `cliente` e de todos os dados operacionais.
- Contato, endereço, telefone, anexos/documentos: **por loja**; propagação de identidade
  civil continua explícita e auditada (só nomes de campos, nunca valores).
- CPF define-once; username Keycloak = CPF; gate de CPF do portal.
- Persona CLIENTE por escopo (`/v1/customers/**` = qualquer autenticado); papéis de staff
  fora do contexto ABAC nesse escopo; OPA como autoridade.
- Keycloak: realm, flows (e-mail code, Google, 2FA) — intocados; a unificação é toda no
  lado da aplicação.

## 7. LGPD

- A plataforma é controladora da identidade (conta, perfil civil, habilitação); a loja é
  controladora da ficha comercial e dos documentos que coletou. A unificação NÃO amplia o
  acesso de nenhuma loja a nada (princípio 3 + D8).
- Eliminação de conta da pessoa (fase futura já prevista no PORTAL_CLIENTE_SPEC §11.1)
  fica MAIS simples no modelo novo: apagar/anonimizar `usuario` + `customer_profile`
  desliga os `cliente.usuario_id` (SET NULL) sem tocar as fichas — que têm base legal
  própria (contratos, Marinha) e retenção por loja.

## 8. Riscos

| Risco | Mitigação |
|---|---|
| Claim é o fluxo mais sensível (senha temporária, e-mail, Keycloak real) | F2 isolada, e2e manual roteirizado antes do merge; kill não é preciso — D3 só AFROUXA uma recusa |
| Policy self-read em `cliente` expõe mais que a V029 | D5: SELECT-only, GUC exclusivo do escopo customer, harness não-superuser desde a F1 |
| Vínculos aparecerem em prod antes da F0 | Assert na migration (falha alto em vez de órfão silencioso); janela curta — executar F0 primeiro |
| Lookup staff esquecendo o filtro de tenant (policy permissiva soma OR) | Regra já codificada (CLAUDE.md 1) + casos negativos no `CustomerNonSuperuserIntegrationTest` |
| `usuario.email` UNIQUE × e-mails duplicados entre populações | É o comportamento DESEJADO (mesma pessoa); colisões inesperadas aparecem na F0 (dupla escrita loga e audita) |
| Cache do mapping (Redis 5min) segurar vínculo recém-criado | `linkProvider` já faz `@CacheEvict`; F1 reusa o mesmo serviço |

## 9. Fora de escopo

- Console de "pessoas" para a plataforma (busca global de consumidores) — spec própria se
  houver demanda de suporte/LGPD.
- Renomear conceitos (`cliente` continua `cliente`); mesclar contato por loja.
- Expurgo automatizado de dados (retenção) — continua na fila do portal.
- Mobile (KMM) — consome as mesmas APIs, nada muda de contrato.
