# Identidade do Agente — Spec

> Status: **PROPOSTA APROVADA, NÃO IMPLEMENTADA** (14/set/2026). Decisões de produto e de
> desenho fechadas com o usuário; falta executar. Primeiro consumidor: o **assistente de
> cadastro por IA** (modelos, jetskis, instrutores por linguagem natural), piloto com IA
> **local**. Máquina de inferência especificada em `IA_LOCAL_GPU_SPEC.md`.
>
> Versões visuais (mesmo conteúdo, com diagramas): estudo de viabilidade e custo
> (https://claude.ai/code/artifact/79d726d9-f4e3-4496-b4a3-631467ef7353) e esta spec
> (https://claude.ai/code/artifact/b1c77fdf-7f05-4880-9dcd-c78040afab5b).

---

## 0. Contexto e decisões já tomadas

| Tema | Decisão (14/set/2026) |
|---|---|
| Onde a IA roda no piloto | **Local**, GPU própria (RTX 5080) exposta só ao backend — ver `IA_LOCAL_GPU_SPEC.md` |
| Cobrança | **Incluso no plano** durante o piloto; decidir cobrança com dados de uso (tabela de uso registra desde o início) |
| Instrutor pelo chat | **Sim**; assinatura (PNG) feita na tela, por link devolvido pelo assistente |
| MCP externo ("traga sua IA") | **Depois do piloto**, sobre o mesmo modelo de identidade |
| Escrita por agente | **Sempre por proposta confirmada** por humano, inclusive no MCP externo; pré-autorização (§6) é a única exceção controlada |
| Prazo da delegação no chat | **Renovável a cada mensagem, teto de 2 h** |
| Consentimento | **Tela única na primeira vez** por usuário, registrada |
| SUPORTE e SISTEMA | **Entram na fundação** (mesma coluna/código; corrige a lacuna da sessão de suporte) |
| Quais processos exigem step-up | **Adiado** — definido mais à frente. No piloto `data.processos` fica vazio (cadastros não exigem step-up) |
| Validade máxima de pré-autorização | **30 dias**; renovar exige verificação forte |
| Controle da empresa | **ADMIN_TENANT limita/bloqueia** a confiança da equipe; padrão = supervisionado |
| Troca de modelo do agente | **Nova versão principal invalida pré-autorizações**; reconfirmação com step-up |

---

## 1. Tese

1. **Delegação, não personificação.** O agente nunca se passa pela pessoa e nunca vira
   `usuario`/`membro`. É um **ator próprio** que age sob uma **delegação explícita**, com
   prazo e escopo, em nome de um **titular** humano (distinção da RFC 8693: na delegação o
   ator mantém identidade separada; na personificação fica indistinguível).
2. **Step-up é do processo, não do ator.** Se um processo exige verificação forte, ele
   continua exigindo para quem quer que execute. Diante de um agente, o desafio é
   **devolvido ao titular** — ou satisfeito por uma **pré-autorização** que o titular deu
   àquele agente, naquele processo, com limites. Quem confia abre caminho; quem quer
   supervisionar continua aprovando; o processo não muda.

---

## 2. Lacunas atuais (levantamento no código, branch base `main` @ ff28c52)

| Lacuna | Onde |
|---|---|
| Não existe "quem executou": contexto, OPA e auditoria só conhecem `usuario_id` | `shared/security/TenantContext.java:34-38`, `audit/domain/Auditoria.java` |
| `azp` validado e descartado; "device" do OPA deduzido do User-Agent (falsificável) | `JwtAuthorizedPartyValidator.java:49-60`, `policies/authz/context.rego:115-129` |
| Sessão de suporte **não carimba ações** (só abertura/fim), apesar do comentário dizer que sim | `TenantContext.java:161-163`, `AuditEventListener.java:1234-1310` |
| Step-up só dentro do Keycloak (AIA `max_age=0` + `StepUp.java`); backend não checa `acr`/`auth_time`; nenhum processo de negócio declara exigência | `infra/keycloak-extensions/email-code/.../StepUp.java` |
| Sem rate limit por ator (só nginx por IP) e sem kill switch global | `infra/nginx/nginx.conf:50-59` |
| Service account não serve: `TenantFilter` exige membership do `usuario` | `TenantFilter.java:222`, `TenantAccessService.java:103-157` |
| Delegação `act`/`may_act` do Keycloak é experimental (prevista 26.8) e tem bypass aberto (#52691); prod está em 26.7.3 sem `KC_FEATURES` | `infra/keycloak-theme/Dockerfile:29` |

---

## 3. Modelo: titular, ator, delegação

| `ator_tipo` | Titular (responde) | Ator (executa) | Autorização vem de |
|---|---|---|---|
| `PESSOA` | o usuário | o mesmo usuário | papéis do `membro` |
| `SUPORTE` | operador de plataforma | operador em sessão de suporte | `plataforma_sessao_suporte` |
| `AGENTE` | usuário que delegou | agente do catálogo (`assistente-cadastro@1`) | `agente_delegacao` (§5) |
| `SISTEMA` | a plataforma | job agendado | configuração do job |

---

## 4. Princípios (A1–A11)

- **A1 — Agente não é pessoa.** Nunca vira `usuario`, `membro` ou `usuario_global_roles`;
  nunca recebe papel. Identidade própria em catálogo global (nome, versão, provedor, modelo).
- **A2 — Autoridade derivada, nunca armazenada.** Efetivo = papéis *atuais* do titular ∩
  escopos da delegação ∩ capacidades do agente − raiz de identidade. Recalculado a cada
  chamada; titular perdeu papel ⇒ agente perdeu na hora (atenção ao cache `tenant-access`).
- **A3 — Vínculo explícito e auditado.** Delegação só nasce de ação humana registrada.
- **A4 — Escopo de tarefa.** Uma empresa, um canal, prazo curto, revogável. A empresa vem
  da delegação, nunca de argumento escrito pelo modelo.
- **A5 — Escrita consequente exige humano presente.** Agente propõe; grava-se com ator
  `PESSOA`, sobre a proposta exata (hash), uso único. Exceção: pré-autorização (A11), só em
  processos marcados como pré-autorizáveis.
- **A6 — Titular e ator viajam juntos.** Contexto, OPA, `auditoria`, logs, métricas levam
  `ator_tipo`, `ator_id`, `delegacao_id`, `canal` e como o step-up foi satisfeito. Ação sem
  ator identificado é bug.
- **A7 — Step-up é do processo.** Requisito (nível `acr` + janela `max_age`) declarado pelo
  processo, igual para pessoa e agente. **Sem bypass por tipo de ator.** Para agente: desafio
  ao titular ou pré-autorização válida. **Raiz de identidade** (senha, fatores, dispositivos
  confiáveis, membros/papéis, delegações e pré-autorizações, rotas de plataforma): agente
  **não inicia**.
- **A8 — Credencial nunca chega ao modelo.** Nem token, cookie, id de delegação ou código de
  aprovação no prompt. O servidor de IA (GPU local) não recebe credencial de usuário.
- **A9 — Freios.** Kill switch global / por agente-versão / por empresa (módulo);
  rate limit por delegação e titular; teto de passos; teto de desafios pendentes;
  revogação checada no banco a cada chamada.
- **A10 — Formato padrão, verdade no banco.** Ator espelha `sub`/`act` (RFC 8693); desafio
  segue RFC 9470. MCP ou delegação nativa do Keycloak só mudam o mapeamento token→`Ator`.
  Revogação e pré-autorizações ficam no banco (token trocado sobrevive à revogação no IdP).
- **A11 — Confiança concedida, limitada, revogável.** Titular pode permitir que *um* agente
  responda o step-up por ele em *um* processo, com limites e validade ≤ 30 dias. Conceder ou
  ampliar é raiz de identidade (verificação forte na hora). Efetivo = menor entre tetos da
  plataforma, da empresa e do titular.

---

## 5. Fluxo do piloto (assistente de cadastro)

1. Pessoa abre o assistente → `POST /v1/tenants/{id}/assistente/conversas` (JWT da pessoa)
   → cria `agente_delegacao` (titular, empresa, escopos, canal `ASSISTENTE`, expira em 30 min,
   renovável por mensagem até 2 h). Primeira vez: tela de consentimento registrada.
2. Mensagem → backend chama a IA **sem credenciais**, com as ferramentas
   `buscar_modelos`, `buscar_jetskis`, `buscar_instrutores`, `propor_cadastro` (nenhuma grava).
3. A cada pedido de ferramenta o **executor de ação delegada** monta `Ator(AGENTE)` a partir
   da delegação (valida expiração, revogação, suspensão, kill switch), chama o OPA e executa
   com a RLS da empresa da delegação.
4. `propor_cadastro` valida sem gravar (Bean Validation, unicidade, `frota_max`) e salva
   `assistente_proposta` com hash → cartão de confirmação.
5. Pessoa confirma → `POST .../assistente/propostas/{id}/confirmar` com ator **PESSOA** →
   services existentes gravam numa transação; auditoria com
   `(titular, PESSOA, canal ASSISTENTE, delegacao_id, proposta)`.
6. Instrutor: criado sem assinatura; resposta traz link para a tela de assinatura.

Services não têm `@PreAuthorize`: **o executor é a única porta** para agentes — não pode
existir caminho alternativo.

---

## 6. Step-up delegado (fases I6–I8, quando o 1º processo protegido for aberto a agentes)

**Três classes de ação:** livre por escopo (sem step-up) · processo protegido (step-up) ·
raiz de identidade (agente não inicia). A lista de processos protegidos será definida depois.

**Como o agente satisfaz um step-up (ordem):**

| # | Evidência | Vale quando | Auditoria |
|---|---|---|---|
| 1 | Aprovação do titular para *esta* ação | desafio aprovado com fator forte, mesmo hash, na janela, uso único | `verificacao=TITULAR_APROVOU` + desafio, método, `auth_time` |
| 2 | Pré-autorização do titular | mesmo agente (versão principal), mesmo processo, dentro de limites/validade/tetos | `verificacao=PREAUTORIZACAO` + id |
| 3 | nenhuma → cria `stepup_desafio` | sempre que 1 e 2 não se aplicam | agente recebe "aguardando aprovação do titular" |

**Aprovação:** cartão com detalhes vindos da ação validada (não do texto da IA) → "Verificar e
aprovar" dispara o step-up existente (AIA `max_age=0`, 2FA forçado mesmo em device
confiável; código por e-mail sem 2FA) → backend valida `acr`, `auth_time`, hash, titular,
expiração. Checkbox opcional "permitir que este agente aprove isso por mim" cria a
pré-autorização com limites na mesma verificação. Sem a pessoa na tela: notificação com
link que exige login + step-up (nunca aprova sozinho); CIBA do Keycloak é evolução possível
(exige `AuthenticationChannelProvider` próprio).

**Modos de supervisão (titular × agente × processo):** Supervisionado (padrão) ·
Pré-autorizado com limites (aviso por uso opcional) · Bloqueado.
**Tetos:** plataforma (define processo e teto; versionado no OPA) ≥ empresa (ADMIN_TENANT) ≥
titular. Recalculado a cada uso.

**Anti-fadiga:** hash da ação; expira em 5 min; uso único; ≤ 3 desafios pendentes por
delegação e ≤ 10/h por titular; 2 negações seguidas suspendem a delegação; pedidos repetidos
agrupados; link de notificação nunca aprova sem login.

**Para PESSOA** o mesmo requisito vira `401` no formato RFC 9470
(`WWW-Authenticate: Bearer error="insufficient_user_authentication", acr_values=..., max_age=...`)
e o frontend dispara o step-up antes de repetir.

---

## 7. OPA

**Entrada ganha** `actor` (tipo, id, delegação com escopos/expiração), `authn` (`acr`,
`auth_time` do titular, quando houver) e `context.channel`/`context.client` (do `azp`
assinado + delegação — não do User-Agent).

**Saída passa a ser** `{allow, step_up?}`:

```rego
default decisao := {"allow": false}
decisao := {"allow": true} if { permitido; not requisito }
decisao := {"allow": true, "step_up": requisito} if { permitido; requisito }

requisito := data.processos[input.action].step_up   # vazio no piloto

permitido if { input.actor.type == "PESSOA"; rbac_allow }
permitido if { input.actor.type == "AGENTE"; allow_agente }

default allow_agente := false
allow_agente if {
  rbac_allow
  input.action in input.actor.delegation.scopes
  input.action in data.agentes[input.actor.id].capacidades
  not raiz_identidade
  not bloqueado_para_agente
}

default raiz_identidade := false
raiz_identidade if { some p in data.raiz_identidade; glob.match(p, [":"], input.action) }
```

Lembrar o gotcha do projeto: toda regra referenciada precisa de `default … := false`
(regra undefined colapsa o `result`); OPA sem hot-reload (`docker compose restart opa`).
Pré-autorizações/desafios **não** vão para o OPA (dados vivos no banco); limites de valor e
quantidade são checados no executor.

---

## 8. Modelo de dados

Toda alteração = migration Flyway **e** bloco idempotente no `reset-ambiente-dev.sh`
(usar `/nova-migration`). Tabelas por empresa: `tenant_id` + RLS + índice composto.

**Fundação (piloto):**

- `agente` (global, catálogo): `id` (`slug@versao`), `nome`, `descricao`, `provedor`,
  `modelo`, `capacidades text[]`, `ativo` (kill switch por agente).
- `agente_delegacao` (tenant): `titular_usuario_id`, `agente_id`, `escopos text[]`, `canal`
  (`ASSISTENTE|MCP|AGENDADO`), `criada_em`, `expira_em`, `expira_teto_em`, `encerrada_em`,
  `encerrada_por`, `motivo`, `suspensa_em`, `token_hash`, `ip`, `user_agent`.
- `agente_consentimento` (tenant): `usuario_id`, `agente_id`, `escopos_apresentados`,
  `aceito_em`.
- `assistente_proposta` (tenant): `delegacao_id`, `payload jsonb`, `payload_hash`, `status`
  (`PENDENTE|CONFIRMADA|DESCARTADA|EXPIRADA`), `confirmada_por`, `confirmada_em`, `expira_em`.
- `auditoria` + colunas: `ator_tipo` (default `PESSOA`), `ator_id`, `delegacao_id`, `canal`,
  `verificacao` (default `NENHUMA`), `verificacao_ref`. Mesmas colunas na trilha global.

**Step-up delegado (I6–I8):**

- `stepup_desafio` (tenant): `delegacao_id`, `titular_usuario_id`, `acao`, `detalhes jsonb`
  (formato `authorization_details`, RFC 9396), `acao_hash`, `requisito`, `status`
  (`PENDENTE|APROVADO|NEGADO|EXPIRADO|USADO`), `metodo`, `acr`, `auth_time`, `expira_em`.
- `agente_preautorizacao` (tenant): `titular_usuario_id`, `agente_id`, `agente_versao_principal`,
  `acao`, `limites jsonb`, `avisar_a_cada_uso`, `valida_ate` (≤ 30 dias), `concedida_com`
  (desafio, método, `auth_time`), `revogada_em`, `revogada_por`.
- `empresa_politica_agente` (tenant): `acao`, `modo_maximo`
  (`BLOQUEADO|SUPERVISIONADO|PREAUTORIZAVEL`), `papeis_que_podem text[]`, `teto jsonb`,
  `alterada_por`, `alterada_em`.

---

## 9. Evolução prevista no mesmo molde

| Caso | Delegação nasce de | Escrita | Step-up |
|---|---|---|---|
| Assistente de cadastro (piloto) | abrir conversa | proposta confirmada | não há |
| Assistente com processos protegidos | idem | proposta ou pré-autorização | cartão no chat |
| MCP externo | OAuth com consentimento (client novo + `JETSKI_JWT_ALLOWED_CLIENTS`; token com audiência do MCP, sem repasse) | proposta com link | link para o backoffice |
| Rotina agendada | pessoa configura | propostas pendentes | notificação + link; pré-autorização |
| IA de suporte da plataforma | sessão de suporte | herda `somente_leitura` | desafio ao operador |
| Delegação nativa Keycloak / CIBA | token com `act` | igual | CIBA no celular do titular |

---

## 10. Ameaças e barreiras

| Ameaça | Barreira |
|---|---|
| Agente contorna step-up | A7: sem bypass por tipo de ator |
| Agente amplia a própria confiança | pré-autorização é raiz de identidade; agente não inicia |
| Fadiga de aprovação | detalhes validados, hash, tetos de pendência, suspensão após negações |
| Aprovação reaproveitada (R$ 50 → R$ 5.000) | hash da ação, uso único, 5 min |
| Pré-autorização esquecida | validade ≤ 30 dias, aviso por uso, versão principal invalida |
| Empresa aperta a regra | tetos recalculados a cada uso |
| Troca de empresa pelo prompt | empresa vem da delegação; RLS + `existsByIdAndTenantId` |
| Titular perde acesso no meio | papéis relidos por chamada; invalidar cache `tenant-access` |
| Máquina de IA comprometida | sem credencial (A8); tudo validado; escrita e step-up dependem do titular |
| Canal falsificado | canal do `azp` assinado + delegação |
| Laço/volume | teto de passos, rate limit por delegação, kill switch |

---

## 11. Fases

**Fundação — necessária para o piloto (12–16 dias):**

- **I1 (3–4 d)** `Ator` no `TenantContext`; `azp` → canal; colunas de ator/verificação na
  `auditoria` e trilha global; sessão de suporte carimba ações; jobs como `SISTEMA`.
- **I2 (2–3 d)** `agente`, `agente_delegacao`, `agente_consentimento`, `assistente_proposta`;
  serviço de delegação (criar, renovar, validar, suspender, encerrar).
- **I3 (3–4 d)** OPA com `input.actor`/`input.authn` e decisão `{allow, step_up}`;
  `agent.rego`, `data.raiz_identidade`, `data.processos` vazio; testes: raiz de identidade
  negada para AGENTE mesmo com titular ADMIN_TENANT.
- **I4 (2–3 d)** executor de ação delegada (única porta), rate limit por delegação, teto de passos.
- **I5 (2 d)** perfil "Agentes com acesso" (listar/revogar); console: kill switch global e por
  agente, filtro por ator na trilha.

Com o assistente (F0 pré-requisitos de cadastro, F1 módulo/ferramentas, F2 chat, F3 plano e
uso, F4 avaliação) e o preparo da GPU local: **MVP ≈ 27–36 dias úteis (6–7 semanas)**.

**Step-up delegado — quando a lista de processos protegidos for definida (10–13 dias):**

- **I6 (3–4 d)** `data.processos` preenchido; backend valida `acr`/`auth_time`; 401 RFC 9470
  para PESSOA; frontend trata e chama o step-up existente. Beneficia humanos também.
- **I7 (3–4 d)** `stepup_desafio`, cartão de aprovação, notificação com link, anti-fadiga.
- **I8 (4–5 d)** `agente_preautorizacao`, `empresa_politica_agente`, telas de confiança
  (titular) e tetos (ADMIN_TENANT), auditoria da evidência.

---

## 12. Referências

- RFC 8693 (Token Exchange — `act`), RFC 9470 (Step Up Authentication Challenge),
  RFC 9396 (Rich Authorization Requests), RFC 8707 (Resource Indicators).
- Keycloak: token exchange (V2 só internal-internal; delegação experimental),
  keycloak#38279 (delegação, 26.8), keycloak#52691 (bypass de ator), ACR→LoA, CIBA
  (`AuthenticationChannelProvider`).
- MCP Authorization, especificação 2026-07-28 (audiência obrigatória, sem repasse de token).
- `IDENTIDADE_UNICA_SPEC.md` (vínculo explícito), memória do projeto sobre step-up
  (`StepUp.java`, AIA `max_age=0`).
