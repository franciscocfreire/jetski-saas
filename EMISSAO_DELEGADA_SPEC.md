# Emissão Delegada — EAMA emissora × Operadora afiliada (SPEC)

> Status: **MVP IMPLEMENTADO** (passos 1–5 do §11, 12/jul/2026).
> Passo 1 (V047): catálogo `capitania` + perfil emissor no tenant + split
> `EMISSAO_PROPRIA`/`EMISSAO_DELEGADA` + endpoints de config/plataforma.
> Passos 2–5 (V048): `vinculo_emissao` (convite/aceite bilateral + termo + kill switch +
> estorno anti-fraude do bônus via ledger), emissão delegada no `EmissaoService` (gate por
> módulo, identidade/instrutor/Capitania do emissor, snapshot no documento, espelho
> `emissao_delegada` no tenant emissor, notificação à EAMA), painel do emissor
> (lista/contagens/download/reenvio sem crédito) e metering com `emissor_tenant_id`.
> Frontend: página Emissão delegada (parceria + painel), instrutor da EAMA parceira no
> balcão, menu gateado pelo split.
>
> **13/jul/2026 — pendências fechadas** (detalhe em §12): portão duplo da emissão própria
> ligado (V050, com grandfathering), UI de superadmin (capitanias + habilitar emissora),
> audit das transições do vínculo nos dois tenants e e-mails de convite/aceite/bloqueio.
> Sem pendências de MVP abertas.
> Escopo **MVP** (enxuto): catálogo de capitanias, perfil emissor validado pelo superadmin,
> vínculo bilateral com kill switch, emissão delegada com instrutor do emissor, painel do
> emissor (visão + reenvio). Cobrança entre empresas fica **fora da plataforma**.

## 1. Contexto e objetivo

Hoje toda empresa emite CHA-MTE/EMA **em nome próprio**: o PDF sai com `razao_social`/`cnpj`
do tenant, assinado por um `instrutor` do próprio tenant, enviado por e-mail ao
`tenant.marinha_email`. Não há conceito de "empresa licenciada" — qualquer tenant com o
módulo `EMISSAO_MARINHA` emite.

Objetivo: separar dois perfis de empresa —

- **EAMA emissora**: licenciada na Capitania, com instrutores credenciados; emite em nome
  próprio (como hoje) e pode **emprestar sua licença** a parceiras.
- **Operadora afiliada**: mantém marca própria, vitrine, captação e aluguel, mas **não é
  licenciada**; emite CHA-MTE **em nome de** uma EAMA parceira (emissão delegada).

Efeito no funil: a licença deixa de ser pré-requisito de entrada e vira upgrade — empresa
nova opera desde o dia 1 e "gradua" para emissora própria depois.

## 2. O que JÁ existe (não reconstruir)

| Capacidade | Onde | Situação |
|---|---|---|
| Emissão EMA/CHA (PDF + e-mail à Capitania) | `locacoes/internal/EmissaoService` (+ `CustomerEmaService` no portal) | ✅ identidade do PDF vem do tenant da sessão |
| Identidade da emissora no tenant | `tenant.razao_social/cnpj/cidade/uf/marinha_email` (V005) | ✅ `marinha_email` é campo livre |
| Instrutor que assina o 5-B-1 | tabela `instrutor` (V011), tenant-scoped + RLS | ✅ sempre do tenant da sessão |
| Documento emitido (S3 + hash + rastro de envio) | `documento_emitido` (V039: `marinha_enviado_em`) | ✅ |
| Créditos: 1 crédito/documento à Marinha | `creditos/CreditoService` + `credito_lancamento` (V026, append-only) | ✅ débito no tenant que emite |
| Metering de emissões | `emissao_uso` (V025), módulo `metering` | ✅ sem dimensão de emissor |
| Módulo por plano `EMISSAO_MARINHA` | `ModuloPlano` (V046) | ✅ liga/desliga o recurso inteiro |
| GRU (robô com CPF **do cliente**) | `locacoes/internal/gru/*` | ✅ **neutra** — nada muda nesta spec |
| Aprovação de empresa pelo superadmin | `ONBOARDING_EMPRESA_SPEC.md` (implementado) | ✅ modelo para validar o perfil emissor |
| Audit assíncrono + carimbo RFC 3161 | módulo `audit`, `assinatura_config` (V024) | ✅ base da trilha de responsabilidade |

## 3. Modelo de dados (novo)

Toda mudança de schema = migration Flyway `V0XX` **e** bloco idempotente no
`reset-ambiente-dev.sh` (regra do projeto; usar `/nova-migration`).

### 3.1 Catálogo `capitania` (plataforma, sem tenant_id)

Mantido pelo superadmin (~40 e poucas OM entre Capitanias/Delegacias/Agências):

- `id`, `codigo` (ex.: `CPSP`), `nome`, `uf`, `email_oficial`, `ativa`.

O `email_oficial` é apenas **default/pré-preenchimento** — ver §3.2.

### 3.2 `tenant` — perfil de emissão

- `capitania_id` (FK → `capitania`): **toda** empresa declara a sua — a emissora pela
  licença, a operadora pela **área de operação** (vínculo geográfico, não de registro).
- `emissora_habilitada` (boolean, default false) + dados de registro:
  `eama_registro` (nº de inscrição na Capitania), `eama_registro_validade`.
  Superadmin valida documentação e habilita (análogo à aprovação de empresa).
- `marinha_email`: **continua editável pelo tenant emissor** (decisão §8.E). O catálogo só
  pré-preenche; a EAMA pode apontar para a própria caixa e reenviar manualmente à Capitania.
  A validação de vínculo usa `capitania_id`, **nunca** o e-mail.

### 3.3 `vinculo_emissao`

| Campo | Nota |
|---|---|
| `tenant_operador_id`, `tenant_emissor_id` | ambos FK → tenant |
| `status` | `CONVIDADO → ATIVO ⇄ BLOQUEADO → REVOGADO` |
| `convidado_em/por`, `aceito_em/por`, `revogado_em/por` | trilha do handshake |
| `termo_aceite_em` + snapshot do termo | responsabilidade explícita da EAMA (§5.1) |

Restrições:
- **MVP: no máximo 1 vínculo ATIVO/BLOQUEADO por operadora** (unique parcial). Multi-emissor é v2.
- `capitania_id` do operador **=** do emissor (validado no convite e no aceite — deny de
  **negócio**, 400 com mensagem clara; nunca 403).
- Emissor precisa de `emissora_habilitada = true`.

RLS: a tabela é visível **pelos dois lados** — policy `tenant_id IN (operador, emissor)`
via coluna dupla ou duas policies permissivas (atenção ao gotcha: permissivas somam com OR —
não deixar nenhuma mais larga que o par).

### 3.4 `documento_emitido` — snapshot do emissor

- `emissor_tenant_id` (nullable; null = emissão própria, comportamento atual).
- `emissor_snapshot` (JSONB): razão social, CNPJ, cidade/UF, capitania e **instrutor**
  (nome/CPF/CHA) **congelados no momento da emissão** — auditoria imutável mesmo se a EAMA
  mudar dados ou desligar o instrutor depois.

### 3.5 `emissao_delegada` — espelho no tenant emissor

Registro no tenant **do emissor** por documento delegado: `tenant_id` (= emissor),
`vinculo_id`, `documento_hash`, `s3_key`, `operadora` (nome/slug), condutor (nome/CPF),
`instrutor_id`, `emitido_em`, rastro de reenvios. Cada lado enxerga só a sua metade — RLS
intacta dos dois lados, sem leitura cross-tenant em runtime na consulta.

### 3.6 `emissao_uso` (metering)

Nova dimensão `emissor_tenant_id` — relatório por vínculo/período sai de graça e prepara
repasse intermediado no futuro (§9), sem retrabalho.

## 4. Fluxos

### 4.1 Convite e aceite (bilateral)

1. Operadora convida a EAMA (por slug/e-mail) **ou** a EAMA convida a operadora.
2. Validações: mesma capitania, emissor habilitado, sem vínculo ativo prévio (MVP).
3. O lado convidado **aceita** assinando o termo de responsabilidade (§5.1) → `ATIVO`.
4. **Na ativação, os créditos de bônus da operadora são zerados** (anti-fraude, decisão §8.H):
   - Motivo: sem isso, farm de bônus — abrir N operadoras, ganhar +20 em cada e queimar
     tudo emitindo via a mesma EAMA.
   - Mecânica: ledger é **append-only** → "zerar" = lançamento de **estorno**
     `ESTORNO_BONUS` de valor `min(saldo disponível, soma dos bônus de adesão)` — nunca
     deixa saldo negativo e não toca crédito **comprado**. Idempotente por vínculo
     (`referencia_id = vinculo_id` + unique parcial): revogar e recriar vínculo não
     estorna de novo. Bônus já consumido antes do aceite não é recuperado.
   - UX: o aceite avisa antes ("você tem X créditos de bônus; ao ativar a parceria eles
     serão zerados") — sem surpresa.
5. Qualquer lado revoga a qualquer momento → `REVOGADO` (terminal). Revogação **não**
   devolve o bônus estornado.

### 4.2 Emissão delegada (mudança no `EmissaoService`)

Igual à emissão própria, exceto:

- Gate: operadora sem módulo `EMISSAO_PROPRIA` exige vínculo `ATIVO`; sem vínculo ou
  `BLOQUEADO` → 400 de negócio ("Emissão via {EAMA} indisponível/suspensa pelo parceiro").
- **Identidade do PDF** = snapshot do emissor (razão social/CNPJ/cidade/UF da EAMA).
- **Instrutor**: dropdown lista os instrutores **do emissor** (serviço de plataforma com
  query explicitamente tenant-scoped `findByTenantIdAndAtivoTrue(emissorId)` — regra 1 do
  projeto; exposição mínima à operadora: **id + nome apenas**, CPF/RG/CHA entram no PDF
  pelo lado do serviço). Operadora **não tem** CRUD de instrutores.
- **E-mail à Capitania** = `marinha_email` **do emissor** (operadora não vê nem edita).
- **Crédito**: debitado **da operadora** — `CreditoService` intocado (débito no tenant que
  dispara, mesma transação, mesmo advisory lock).
- Pós-emissão: grava `emissor_tenant_id` + `emissor_snapshot` no `documento_emitido`,
  cria o espelho `emissao_delegada` no tenant emissor, notifica a EAMA por e-mail.

### 4.3 Kill switch (liberar/bloquear)

- Toggle no painel do emissor: `ATIVO ⇄ BLOQUEADO`, efeito **imediato para novas emissões**.
- Emissão em andamento no momento do bloqueio **conclui** (cortar no meio cria crédito
  debitado sem documento).
- Cada transição vai ao `audit` com autor/timestamp — a EAMA prova quando bloqueou.
- Caso de uso: parceira atrasou o acerto do mês → bloqueia até receber (alavanca do
  "se acertam por fora").

### 4.4 Painel do emissor + reenvio

- **"Emissões em meu nome"**: lista (espelho §3.5) com filtros por operadora/período,
  contagem mensal por operadora (base do faturamento por fora), PDF/hash por documento.
- **"Reenviar à Capitania"**: reenvia o **mesmo PDF do S3** — não re-emite, **não debita
  crédito**; destinatário editável no reenvio pontual; cada reenvio no `audit` + rastro de
  envio. (Vale também para emissão própria — válvula de escape do envio best-effort.)

## 5. Responsabilidade e segurança

1. **Termo de responsabilidade** no aceite: a EAMA declara que seus instrutores
   realizarão/atestarão as demonstrações práticas (Anexo 5-B-1). O instrutor ser sempre do
   emissor **força o modelo honesto** — a parceria precisa ser operacional, não assinatura
   de fachada. A plataforma é cartório (registra e prova), não cúmplice.
2. **RLS**: nenhuma leitura cross-tenant "ao vivo" nas consultas — snapshot na emissão +
   espelho por lado. A única ponte é o serviço de plataforma que lista instrutores do
   emissor sob vínculo ATIVO (tenant-scoped explícito).
3. **OPA**: ações novas (`vinculo:convidar/aceitar/bloquear/revogar`, `emissao:delegada`,
   `emissao:reenviar`) exigem `default <regra> := false` (regra undefined colapsa o result)
   + `docker compose restart opa`.
4. **LGPD**: dados pessoais do instrutor não trafegam à UI da operadora; condutor aparece
   no espelho do emissor (necessário — a EAMA responde pelo documento).

## 6. Endpoints novos (MVP)

| Método | Path | Quem | Descrição |
|---|---|---|---|
| GET | `/v1/capitanias` | autenticado | catálogo (para cadastro/vínculo) |
| POST | `/v1/emissao/vinculos` | ADMIN_TENANT | convidar (operadora→emissora ou vice-versa) |
| POST | `/v1/emissao/vinculos/{id}/aceitar` | ADMIN_TENANT (lado convidado) | aceite + termo |
| POST | `/v1/emissao/vinculos/{id}/bloquear` · `/liberar` | ADMIN_TENANT (emissor) | kill switch |
| POST | `/v1/emissao/vinculos/{id}/revogar` | ADMIN_TENANT (qualquer lado) | terminal |
| GET | `/v1/emissao/vinculos` | ADMIN_TENANT | vínculos do tenant (papel: operador ou emissor) |
| GET | `/v1/emissao/instrutores-emissor` | operadora c/ vínculo ATIVO | id+nome dos instrutores do parceiro |
| GET | `/v1/emissao/delegadas` | emissor | espelho + contagens por operadora/período |
| POST | `/v1/emissao/delegadas/{id}/reenviar` | emissor | reenvio do PDF (sem crédito) |
| POST | `/v1/platform/tenants/{id}/habilitar-emissora` | superadmin | valida registro EAMA |

Emissão delegada em si **não ganha endpoint novo** — é o `emitir(reservaId)` atual com o
gate/identidade trocados pelo vínculo.

## 7. Frontend (backoffice)

- **Emissor**: menu Instrutores como hoje; novo painel "Emissões delegadas" (lista, contadores,
  toggle liberar/bloquear, reenviar); cadastro de registro EAMA em Configurações.
- **Operadora**: menu Instrutores **oculto** (gate por módulo); tela "Parceria de emissão"
  (convidar/status/termo); wizard de emissão com dropdown de instrutor do parceiro + badge
  "via {razão social da EAMA}"; sem vínculo → CTA "convide uma EAMA parceira" (mesmo espírito
  do bloqueio por falta de crédito).
- **Superadmin**: catálogo de capitanias + fila de habilitação de emissoras.

## 8. Decisões (resolvidas no brainstorm de 12/jul/2026)

- **A. Instrutor**: sempre do tenant emissor; operadora não cadastra instrutor. ✔
- **B. Capitania**: vira catálogo de plataforma + `tenant.capitania_id`; vínculo só entre
  empresas da **mesma capitania**. ✔
- **C. Cobrança**: crédito debitado da **operadora** (quem usa paga); acerto financeiro
  EAMA×operadora **por fora** da plataforma; EAMA tem visão/contagem por operadora. ✔
- **D. Kill switch**: EAMA libera/bloqueia a emissão em seu nome, efeito imediato para novas
  emissões; em andamento conclui. ✔
- **E. `marinha_email`**: continua **editável pela EAMA** (catálogo só pré-preenche) — ela
  pode receber na própria caixa e reenviar manualmente; reenvio vira feature (§4.4). ✔
- **F. Aprovação por emissão**: MVP = automática + notificação por e-mail à EAMA; fila de
  aprovação é v2. ✔
- **G. Multi-emissor**: MVP = 1 emissor ativo por operadora; N:N é v2. ✔
- **L. Instrutores designados por parceria** (V049, 12/jul/2026): a EAMA escolhe quais dos
  seus instrutores atendem cada operadora — a operadora só vê (e só emite com) os
  designados. Opt-in: sem designação, todos os instrutores ativos da EAMA ficam
  disponíveis (compatível com V048). Validado na listagem E na emissão. ✔
- **H. Anti-fraude do bônus**: ao ativar o vínculo de delegação, os créditos ganhos como
  **bônus** (adesão) da **operadora** são **zerados** via lançamento de estorno (§4.1.4);
  créditos comprados são preservados; a **EAMA mantém** o bônus dela (emissão delegada não
  debita nada do emissor). Fecha o farm de bônus via múltiplas operadoras. ✔
- **I. Regulatório**: premissa validada — a Marinha **não se importa com quem captou** o
  cliente; o que importa é quem emite e quem atesta a demonstração (ambos = EAMA no nosso
  desenho). O modelo formaliza prática já existente no mercado. ✔
- **J. Operadora invisível no PDF**: o documento à Capitania sai 100% em nome da EAMA, sem
  menção à operadora. A relação entre as empresas fica registrada **dentro** da plataforma
  (termo, snapshot, espelho, audit) — em caso de disputa, a trilha prova quem fez o quê. ✔
- **K. Módulos (V046)**: separar em `EMISSAO_PROPRIA` × `EMISSAO_DELEGADA` (módulo =
  portão **comercial** do plano; `emissora_habilitada` = portão **cadastral** validado pelo
  superadmin). Emissão própria exige **os dois** portões. Viabiliza planos por perfil e o
  upgrade "graduação" operadora → emissora. ✔

## 9. Futuro (deferido)

- **v2**: fila de aprovação por emissão (`PENDENTE_EMISSOR` antes de debitar/enviar);
  limite de emissões/dia por vínculo; multi-emissor (N:N); relatório de faturamento do
  vínculo (export); agenda do instrutor cruzando operadoras (a seleção na emissão já gera
  o histórico de graça).
- **v3**: marketplace de emissoras (operadora sem parceiro encontra EAMA na plataforma);
  repasse financeiro intermediado com taxa da plataforma.
- Estado `REJEITADO`/expiração de convite; renovação/vencimento do registro EAMA com alerta.

## 10. Questões em aberto

Nenhuma — todas as questões do brainstorm foram resolvidas (§8.A–K). Nota: a premissa
regulatória (§8.I) foi validada pelo produto; se a orientação da Capitania mudar no futuro,
revisitar §8.J (visibilidade da operadora no PDF), que foi decidida em função dela.

## 11. Sequenciamento (MVP)

1. **Fundação**: catálogo `capitania` + campos no `tenant` + habilitação pelo superadmin
   + split dos módulos `EMISSAO_PROPRIA`/`EMISSAO_DELEGADA` (§8.K).
2. **Vínculo**: `vinculo_emissao` + convite/aceite/termo + kill switch + OPA + UI de parceria.
3. **Emissão delegada**: gate + snapshot + instrutor do emissor + espelho + notificação
   (mudança concentrada no `EmissaoService`; `CustomerEmaService` na sequência).
4. **Painel do emissor**: lista/contagens + reenviar à Capitania (entregável isolado — o
   reenvio serve até para emissão própria e pode ir antes).
5. Metering com `emissor_tenant_id` (junto com o passo 3, barato).

Cada passo é fatiável e testável (Testcontainers + OPA). Atualizar `reset-ambiente-dev.sh` a
cada mudança de schema/RLS e `IMPLEMENTATION_STATUS.md` ao concluir.

## 12. Notas de implementação (12/jul/2026)

- **Migrations**: V047 (capitania + perfil emissor + split de módulos) e V048 (vínculo +
  espelho + snapshot + metering), ambas espelhadas no `reset-ambiente-dev.sh` e aplicadas
  no dev.
- **Gate da emissão própria**: o portão cadastral (`emissora_habilitada`) é exigido no
  MVP apenas no lado **delegado** (vínculo só ativa com EAMA habilitada e re-checa na
  emissão). A emissão própria segue como sempre — inclusive planos NULL (todos os
  módulos) — para não quebrar as lojas que já emitem em produção. Ligar o portão duplo
  da própria (§8.K estrito) exige antes habilitar as emissoras existentes (grandfathering).
- **RLS cross-tenant**: leituras/escritas do outro lado rodam em janelas
  `set_config('app.tenant_id', ..., true)` restauradas em finally; lookup por slug usa
  janela `app.unrestricted` com SELECT de colunas limitadas (nunca segredos). Escritas em
  janela usam SQL nativo + flush explícito antes de trocar o contexto (gotcha flush×RLS).
- **Estorno do bônus**: `CreditoService.estornarBonusDelegacao` — tipo ESTORNO,
  `referencia_id = vinculo_id` (idempotente), quantidade = min(saldo, bônus restante);
  bônus restante = ADESAO + estornos de bônus anteriores (prefixo do motivo).
- **Interceptor de módulos**: path coberto por MAIS de um módulo libera se QUALQUER um
  estiver no plano (documentos/GRUs pertencem à própria E à delegada; instrutores só à
  própria).
- **Designação de instrutores (V049, §8.L)**: `vinculo_emissao_instrutor` sem tenant_id —
  RLS herda do vínculo via subquery (visível aos dois lados); PUT substitui o conjunto
  (`instrutores-designados`); condição de designação aplicada na listagem
  (`instrutores-parceiro`) e no `resolverParaEmissao`.
- **OPA**: ações novas via listas de papéis no `rbac.rego` (sem regra nova → sem gotcha de
  default); sub-actions novas no `ActionExtractor` (aceitar/bloquear/liberar/revogar/termo/
  instrutores-parceiro/contagens). Gestão do vínculo = ADMIN_TENANT.
- **Pendências fechadas em 13/jul/2026**: (a) portão duplo da emissão PRÓPRIA ligado
  (V050) com grandfathering — quem já emitia (documento_emitido) ou tinha marinha_email
  entrou habilitado; empresas novas passam pela validação do superadmin; (b) UI do
  superadmin no painel plataforma (badge EAMA + habilitar/desabilitar emissora por
  empresa + catálogo de capitanias com e-mails oficiais e criação de
  delegacias/agências); (c) transições do vínculo auditadas NOS DOIS tenants
  (VinculoEmissaoTransicaoEvent → dois handlers no AuditEventListener, um por lado, cada
  um em transação própria com o contexto RLS certo); (d) e-mail best-effort ao outro lado
  em convite/aceite/bloqueio/liberação/revogação (email_remetente). Sobre o portal do
  cliente: verificado que a emissão de documentos SÓ acontece via EmissaoService.emitir
  (balcão) — o portal faz autoatendimento de dados/GRU (GRU é neutra, CPF do cliente),
  então a delegada já cobre todos os caminhos de emissão.
- **Testes**: `EmissaoDelegadaIntegrationTest` (vínculo/estorno/emissão fim-a-fim/kill
  switch/painel), `CapitaniaEmissoraIntegrationTest`, split no `ModuloPlanoIntegrationTest`,
  + suítes afetadas (metering/audit/metrics/créditos/balcão E2E/ModuleStructure) e OPA
  `opa test` 183/183.
