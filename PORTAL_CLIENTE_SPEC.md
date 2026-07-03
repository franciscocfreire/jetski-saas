# Especificação — Portal do Cliente (Frontend)

> **Status:** Proposta v1 (MVP) · **Data:** 2026-06-15 · **Versão do produto:** 0.8.x
> **Escopo:** App web voltado ao **cliente final** (locatário de moto aquática), separado do backoffice.
> Documento de especificação de **frontend**, com os contratos de API e dependências de backend que o portal exige.

---

## 0. Estado em 2026-07-03 — reconciliação com o código (fases P0–P4)

O trabalho do **balcão (F2/F3)**, posterior a esta spec, construiu no backend boa parte do
que o §13 listava como dependência. Inventário:

**Já existe (reusar, não recriar):**
- Identidade do cliente: role **CLIENTE** no realm, `cliente_identity_provider`,
  `cliente_claim_token`, `POST /v1/public/clientes/claim/validar` (ativação com
  provisioning Keycloak, sem Membro), `cliente.origem`/`status_conta`, eventos + auditoria.
- Aceite com evidências (hash/IP/UA/assinatura) + **OTP** por e-mail + página de auditoria
  + carimbo de tempo + **PAdES** — hoje staff-only (`AceiteController`).
- **GRU real** (`GruService` PIX/boleto via RPA) + e-mail da GRU ao cliente.
- Modelo de habilitação (`reserva_habilitacao`: via EMA/CHA, anexos, GRU, resolvida).
- Pagamento com `tipo` (SINAL/TOTAL) e `status` (AGUARDANDO/EM_ANALISE/CONFIRMADO/RECUSADO)
  + `confirmar-sinal`/`recusar-pagamento` (staff). Tabela `reserva_comprovante` criada
  (V003) porém **sem entity/endpoint — falta plugar**.
- `BrCodePix` (QR PIX com valor exato) e `tenant.pix_chave`.
- Créditos de emissão (cobrança da plataforma) — a emissão final continua no staff.

**Continua faltando:** client OIDC `jetski-customer-portal`; signup público; escopo
`/v1/customers/**` (agregação multi-tenant por vínculos com `set_config`); catálogo
público por loja e disponibilidade realmente pública; comprovante anexável pelo cliente;
aceite/habilitação iniciados pelo cliente; estados de visão do cliente + job de expiração
24h; avaliações; notificações in-app; i18n EN.

**Decisões (03/07):** portal real ponta a ponta · Keycloak com senha (client próprio,
PKCE) · sinal PIX manual com QR de valor exato + comprovante (staff confirma).

**Fases executáveis:**
- **P0 — Fundação de identidade** ✅ *(em implementação)*: client OIDC novo, signup
  público (identidade global + Verify Email), escopo CLIENTE `/v1/customers/**`,
  `GET/PUT /v1/customers/self`, portal com NextAuth real (cadastro/login/perfil).
- **P1 — Catálogo + reserva online + sinal PIX**: vitrine por loja, disponibilidade
  pública, `POST /v1/customers/reservas` (cria/vincula Cliente; dedupe CPF ⇒ claim/OTP),
  QR de sinal com valor, wire do `reserva_comprovante` + fila no backoffice, checklist,
  job de expiração, telas reais do wizard/minhas-reservas.
- **P2 — Termos + habilitação caminho A**: aceite customer-facing delegando ao
  `AceiteService` (SignaturePad + OTP), termos por loja, upload de CHA + validação staff.
- **P3 — EMA/GRU pelo cliente**: videoaula, anexos 5-B/5-C/1-C in-app
  (`DocumentoPdfService` já gera), GRU self-service reusando `GruService`; emissão
  permanece no staff (consome créditos).
- **P4 — Pós-venda**: histórico de locações, avaliações, notificações in-app,
  white-label da loja no portal (backend pronto), i18n EN, PWA.

O restante deste documento é a spec original (válida como referência de UX/contratos).

---

## 1. Visão geral

O **Portal do Cliente** é uma aplicação web (PWA-ready) onde o consumidor final:

1. descobre lojas e modelos de jet ski (catálogo / marketplace),
2. consulta disponibilidade e cria reservas,
3. paga o sinal e acompanha o status,
4. resolve a **habilitação** (apresenta CHA existente **ou** emite a habilitação especial **CHA-MTA-E** com GRU da Marinha),
5. assina os termos obrigatórios (NORMAM-212/DPC + termo de responsabilidade da loja),
6. envia documentos,
7. acompanha o histórico de locações e avalia a experiência.

É um app **novo e separado** do `frontend/jetski-backoffice` (decisão tomada). Compartilha o backend Spring Boot e o Keycloak, mas com **client OIDC próprio** e **população de identidade distinta** (cliente, nunca staff).

### 1.1 Decisões já fechadas

| Tema | Decisão |
|---|---|
| Hospedagem | App **Next.js novo e separado** |
| Identidade | **Self-registration aberto desde o v1** |
| Escopo v1 | Catálogo + reserva + conta · pagamento de sinal · termos + histórico + avaliação · notificações · documentos · fluxo de habilitação |
| Sinal (depósito) | **PIX manual + comprovante** (loja exibe chave/QR; cliente envia comprovante; staff confirma) |
| Habilitação EMA/GRU | **Integrado** — backend gera a GRU e conduz a emissão da CHA-MTA-E (dependência de backend, ver §9.4) |

---

## 2. Princípios de arquitetura

### 2.1 Separação staff × cliente (crítico)

Decorre da arquitetura de identidade já decidida ([memória `identity-linking-architecture`]):

- O cliente **nunca** recebe `Membro` → **por construção não tem acesso ao backoffice**.
- O cliente é a entidade `Cliente` (tenant-scoped), ligada à identidade por uma nova tabela `cliente_identity_provider`.
- **Vínculo JWT→usuário sempre explícito** (claim-token / verificação). **Proibido JIT cego por e-mail** (janela de account-takeover quando o registro abre).
- Keycloak: mesmo realm, **client OIDC separado `jetski-customer-portal`** (público, PKCE). Realm separado (`jetski-customers`) só se exigir isolamento forte (enterprise).

### 2.2 Identidade global × Cliente tenant-scoped

- Uma pessoa = **um login global** (Usuario no Keycloak/DB) que pode estar ligada a **múltiplos `Cliente`** (um por loja/tenant onde já alugou).
- O registro público cria a **identidade global**. O `Cliente` (tenant-scoped) é criado/vinculado **na primeira interação com aquela loja**.
- Se já existir um `Cliente` pré-cadastrado pela loja (mesmo CPF/e-mail), o vínculo exige **verificação (OTP)** — nunca merge automático.

### 2.3 Resolução de tenant

O portal é **marketplace-first** (consumidor navega lojas de vários tenants). O `tenant_id` é resolvido **quando o cliente escolhe a loja/modelo para reservar**, não pelo subdomínio.

- Catálogo público: endpoints `/v1/public/marketplace/*` (cross-tenant).
- A partir da seleção de um modelo, o portal carrega o `tenantId` e todas as chamadas subsequentes (disponibilidade, reserva) são tenant-scoped.
- Deep-link por loja (`/loja/{slug}`) é suportado para campanhas, mas não é obrigatório para o v1.

### 2.4 Estados da conta e verificação de e-mail (anti-fraude / anti-spam)

**Princípio:** são **dois portões independentes**, que protegem contra riscos diferentes. Confundi-los é um erro de design:

| Portão | Protege contra | Quem valida | Mecanismo |
|---|---|---|---|
| **Identidade** (e-mail verificado) | conta fake/spam, e-mail de terceiro, account-takeover | automático | **Keycloak** (`Verify Email` + claim `email_verified`) |
| **Pagamento** (sinal) | comprovante errado/falso | **staff (manual no v1)** | tela de validação no backoffice |

> Verificar e-mail **não** resolve "comprovante errado" — quem resolve é a validação do staff. O **upload do comprovante nunca habilita nada**: apenas leva o sinal para `em análise`. Só o staff move para `confirmado`/`recusado`.

**Estados da conta:**
- **Restrita** (`email_verified=false`): pode navegar, criar pré-reserva, **pagar o sinal**, enviar documentos/habilitação. **Não** pode ter reserva considerada *garantida*; recebe avisos pedindo verificação.
- **Completa** (`email_verified=true`): conta plena.

**Estados do sinal:** `aguardando` → `em análise` (comprovante enviado) → `confirmado` | `recusado` (pelo staff).

**Fluxo decidido — verificação em PARALELO** (não bloquear a conversão do pagamento):
1. No cadastro (passo *Conta* do wizard), o Keycloak dispara o e-mail de verificação → conta **restrita**.
2. O cliente **paga o sinal** normalmente (conta restrita).
3. Em paralelo, verifica o e-mail por **código OTP de 6 dígitos** ou magic-link.
4. A reserva só vira **garantida** quando **sinal `confirmado` (staff) E `email_verified=true`**.
5. **Salvaguarda:** pré-reserva **expira em ~24h** se o e-mail não for verificado (libera o slot; se já houve pagamento, vira pendência de reembolso para o staff).

OTP/magic-link reaproveita o **claim-token/OTP** já planejado para vincular a um `Cliente` pré-existente sem account-takeover (ver §2.1 e memória de identidade).

---

## 3. Stack & estrutura do projeto

Espelha o backoffice para reaproveitar conhecimento do time, mas é um repositório/app isolado.

- **Next.js 15 (App Router) + React 19 + TypeScript**
- **NextAuth + OIDC (Keycloak, PKCE)** — client `jetski-customer-portal`
- **TanStack Query** (server state) · **React Hook Form + Zod** (formulários/validação)
- **shadcn/ui + Tailwind** (mesmo design system base do backoffice; tema próprio do consumidor)
- **i18n: `next-intl`** — **PT-BR (default) + EN** (estrangeiros usam passaporte nos anexos; ver §8)
- **Playwright** (e2e) · **Vitest/RTL** (unit/componentes)
- **PWA** (manifest + service worker) — instalável, foco mobile-web

```
portal-cliente/
  app/
    (public)/                 # catálogo, loja, modelo, disponibilidade — sem auth
    (auth)/                   # login, cadastro, callback OIDC
    (account)/                # área logada do cliente
      reservas/
      reservas/[id]/
      reservas/[id]/pagamento/
      reservas/[id]/habilitacao/
      reservas/[id]/termos/
      locacoes/               # histórico
      locacoes/[id]/
      documentos/
      perfil/
      avaliacoes/
    layout.tsx
  components/
    catalog/  booking/  habilitacao/  termos/  pagamento/  reviews/ ...
  lib/
    api/                      # cliente HTTP tipado por domínio (ver §9)
    auth/                     # NextAuth + token handling + claim-token
    i18n/
  e2e/
```

---

## 4. Mapa de telas (Information Architecture)

| Rota | Auth | Descrição |
|---|---|---|
| `/` | — | Home/marketplace: busca por loja, região, modelo |
| `/loja/[slug]` | — | Vitrine de uma loja (modelos, fotos, políticas) |
| `/modelo/[id]` | — | Detalhe do modelo: preço, fotos, caução, política de combustível, **seletor de data/hora + disponibilidade** |
| `/cadastro` | — | Self-registration (cria identidade global) |
| `/login`, `/auth/callback` | — | OIDC PKCE |
| `/reservar/[modeloId]` | opcional* | Wizard de reserva (ver §5.2) |
| `/conta/reservas` | ✔ | Lista de reservas do cliente |
| `/conta/reservas/[id]` | ✔ | Detalhe + status + próximos passos (pagamento, habilitação, termos) |
| `/conta/reservas/[id]/pagamento` | ✔ | Sinal via PIX manual (§5.4) |
| `/conta/reservas/[id]/habilitacao` | ✔ | Fluxo de habilitação: CHA existente **ou** EMA/CHA-MTA-E (§6) |
| `/conta/reservas/[id]/termos` | ✔ | Assinatura dos termos NORMAM + termo da loja (§7) |
| `/conta/locacoes` | ✔ | Histórico de locações realizadas |
| `/conta/locacoes/[id]` | ✔ | Detalhe da locação: valores, fotos check-in/out, recibo |
| `/conta/documentos` | ✔ | Documentos do cliente (RG/CPF, CHA, comprovantes, GRU) |
| `/conta/perfil` | ✔ | Dados pessoais, contato, preferências de notificação |
| `/conta/avaliacoes` | ✔ | Avaliações pendentes/enviadas |

\* É possível **iniciar** a reserva sem login e exigir cadastro/login para **confirmar** (reduz fricção; alinha com "reserva não depende de login do cliente" da arquitetura de identidade).

---

## 5. Funcionalidades MVP — fluxos principais

### 5.1 Catálogo & disponibilidade (público)

- Listagem de modelos do marketplace (`GET /v1/public/marketplace/modelos`), ordenada por prioridade do tenant.
- Detalhe do modelo (`GET /v1/public/marketplace/modelos/{id}`): nome, fabricante, potência, capacidade, **preço base/hora**, **caução**, política de combustível, mídias.
- **Seletor de período** → consulta disponibilidade pública:
  `GET /v1/tenants/{tenantId}/reservas/disponibilidade?modeloId&dataInicio&dataFimPrevista`
  → retorna `totalJetskis`, `reservasGarantidas`, `aceitaComSinal`, `aceitaSemSinal`.
- UI traduz a disponibilidade em mensagens claras:
  - "Disponível — garanta com sinal" (aceitaComSinal),
  - "Lista de espera / sem garantia" (apenas aceitaSemSinal),
  - "Esgotado" (nenhum).

> **Gap de backend:** não há catálogo **público por loja** (só marketplace global e endpoint staff). Necessário endpoint público tenant-scoped para `/loja/[slug]` (ver §9.2).

### 5.2 Wizard de reserva

**Regra de produto (decidida):** o **pagamento é a barreira de compromisso**. Um cliente **novo (sem conta)** é obrigado a pagar **dentro do próprio fluxo de reserva**, logo após criar a conta — só então a reserva é criada e as demais pendências (habilitação/documentos, termos) aparecem na tela da reserva. Isso evita reservas "fantasmas" e ancora a experiência no compromisso financeiro. Cliente **recorrente (já logado)** pode confirmar e pagar depois, pela tela da reserva.

**Tipo de pagamento (decidido):** o cliente **escolhe** entre **sinal** (parcial, ex.: 30%) **ou valor total** — não é obrigado a pagar sinal e depois o restante. **Sinal só existe em reserva antecipada** (cliente remoto, ainda não no local); no **balcão/presencial a reserva é sempre paga integralmente** (ver `BACKOFFICE_ATENDIMENTO_ASSISTIDO_SPEC.md`). Quando paga sinal, o restante é quitado no check-in.

Passo a passo (cada passo persiste; cliente pode sair e retomar):

**Cliente novo (sem conta):**
1. **Período & modelo** (já escolhidos no detalhe) → revalida disponibilidade.
2. **Dados da reserva** — nº de pessoas, observações.
3. **Conta** — cadastro (OIDC). Cria a identidade global e vincula/cria `Cliente` no tenant (com OTP se houver match pré-existente).
4. **Pagamento (OBRIGATÓRIO) — sinal OU total** — PIX + comprovante, ainda dentro do wizard. Ao enviar o comprovante, a reserva é criada com pagamento **em validação** (prioridade sobe para `ALTA` após o staff confirmar).
5. **→ Tela da reserva**: aparecem as pendências restantes — **habilitação** e **termos** (pagamento já em validação/concluído). Ver §5.6.

**Cliente recorrente (logado):**
1. Período → 2. Dados → 3. **Confirmar** → cria reserva `PENDENTE`/`BAIXA` (`POST /v1/.../reservas`, cliente-scoped — gap §9.3). Pagamento (sinal/total), habilitação e termos ficam como pendências paralelas na tela da reserva.

Em ambos os casos a reserva só fica **pronta para check-in** quando pagamento **E** habilitação **E** termos estão concluídos (ver §5.6).

### 5.3 Conta / perfil

- Dados pessoais (nome, CPF, contato, endereço), gerenciados pelo próprio cliente — **self-service** (gap §9.3: hoje terms/perfil só via staff).
- Preferências de notificação (e-mail / WhatsApp / push).
- Visão consolidada de documentos e habilitação válida.

### 5.4 Pagamento (sinal ou total) — PIX manual + comprovante

Decisão: **sem gateway no v1**. O cliente **escolhe o tipo**: **sinal** (parcial) ou **total**.

- **Tipo de pagamento:** `SINAL` (parcial, ex.: 30% — só em reserva antecipada; restante no check-in) ou `TOTAL` (integral). No **balcão presencial é sempre `TOTAL`** (sem sinal).
- **Modelo de dados:** o pagamento carrega `tipo` (`SINAL`|`TOTAL`), `valorPago` e `status` (aguardando/em análise/confirmado/recusado). "Sinal pago" deixa de ser um simples boolean.

Fluxo:
1. Tela exibe **chave PIX e/ou QR estático da loja**, com a opção **Sinal (30%) / Valor total**, + instruções.
2. Cliente paga no banco dele.
3. Cliente faz **upload do comprovante** (imagem/PDF) via fluxo de presigned URL.
4. Status do pagamento = "em validação".
5. **Staff confirma** no backoffice → `POST /v1/.../reservas/{id}/confirmar-sinal` (já existe; **renomear conceitualmente para "confirmar pagamento"** e aceitar `tipo`/`valorPago`) → reserva sobe para `ALTA` (garantida). Fluxo completo do staff em [`BACKOFFICE_VALIDACAO_SINAL_SPEC.md`](./BACKOFFICE_VALIDACAO_SINAL_SPEC.md).
6. Portal reflete "Pagamento confirmado ✔" (via polling/refetch ou notificação).

> **Gaps de backend:** (a) expor **dados de PIX da loja** (chave/QR); (b) endpoint para o **cliente anexar o comprovante** + escolher `tipo`; (c) generalizar o estado de pagamento (sinal/total). Ver §9.5.

### 5.5 Histórico & recibo

- `/conta/locacoes`: locações finalizadas do cliente, com valores (base, faturável, total), duração, fotos de check-in/out e recibo.
- Requer endpoints **cliente-scoped** de leitura (gap §9.3).

### 5.6 Máquina de estados da reserva (visão do cliente)

```
PRÉ-RESERVA ──(pagamento confirmado + e-mail verificado)──► GARANTIDA ──┐
   │                                                                     ├─► PRONTA_P/_CHECKIN ──(check-in)──► EM_CURSO ──► FINALIZADA ──► (avaliação)
   │  conta (account-level):        requisitos da reserva:               │
   │   • email_verified ✔            • habilitação válida ✔              │
   │   • pagamento confirmado ✔      • termos assinados ✔               │
   │     (sinal OU total)                                                │
   └──(e-mail não verificado em 24h / cancela)──► EXPIRADA / CANCELADA
```

- **GARANTIDA** = `pagamento confirmado` (sinal OU total, pelo staff) **E** `email_verified` (Keycloak). É o gate de compromisso. *(No balcão presencial não há e-mail-gate: o embarque acontece na hora; o claim de conta é assíncrono — ver spec de atendimento assistido.)*
- **PRONTA p/ check-in** = GARANTIDA **E** `habilitação válida` **E** `termos assinados`.
- `email_verified` é **account-level**; pagamento/habilitação/termos são **por reserva**.
- Se o pagamento foi **sinal**, o **restante é quitado no check-in**; se foi **total**, nada a pagar no embarque.

O backend hoje modela `status` + `prioridade` (ALTA/BAIXA) + `sinalPago` (boolean). O portal **não** deve recomputar a regra: sugerido o endpoint de **checklist/prontidão** (gap §9.6) retornando `emailVerified` e o **pagamento** (`tipo` sinal/total + `status` aguardando/em análise/confirmado/recusado + `valorPago`/`saldoRestante`).

---

## 6. Fluxo de habilitação (NORMAM-212/DPC)

É o núcleo regulatório do portal. Toda reserva precisa de um **condutor habilitado**. Dois caminhos:

### 6.1 Triagem

Pergunta inicial: **"Você possui habilitação náutica (Arrais Amador, Motonauta, ou CHA ARA/MSA/CPA/MTA-E)?"**

- **SIM →** caminho A (upload da CHA).
- **NÃO →** caminho B (emissão da **CHA-MTA-E** via EMA + GRU).

### 6.2 Caminho A — possui habilitação

1. Upload da CHA (frente/verso) + dados (número, categoria, validade).
2. Status "habilitação em validação" → staff valida no backoffice.
3. Aprovada → requisito de habilitação ✔.

### 6.3 Caminho B — emissão da CHA-MTA-E (EMA, integrado com GRU)

Habilitação especial e temporária (válida em área restrita, conforme Anexo 5-B). Etapas no portal:

1. **Videoaula da Marinha** — o cliente assiste à videoaula oficial (player embutido ou link), com **registro de conclusão** (timestamp).
2. **Auto-declarações (formulários in-app que geram os PDFs dos anexos pré-preenchidos):**
   - **Anexo 5-C — Autodeclaração de Saúde** (boas condições físicas/mentais; usa lentes corretivas? aparelho auditivo? — sim/não).
   - **Anexo 5-B (campo do locatário)** — declaração de ciência das regras de condução: só na área delimitada; só entre nascer e pôr do sol; sem passageiros; não transferir a terceiros; **máx. 37 km/h (20 nós)**; não abastecer; jamais conduzir sob álcool/entorpecentes; uso obrigatório de lentes/aparelho se houver restrição; ciência das sanções LESTA/RLESTA e art. 299 CP.
   - **Anexo 1-C — Declaração de Residência** — **condicional**: só se o cliente não tiver comprovante de residência. Formulário de endereço com **autopreenchimento por CEP** (ViaCEP, com fallback manual se a consulta falhar) — preenche logradouro/bairro/cidade/UF; cliente completa número/complemento.
3. **Documentos** — upload de RG/identidade + CPF (ou **passaporte**, para estrangeiros — versão EN dos anexos).
4. **Demonstração prática** — **presencial no píder** (5-B exige demonstração prática; sem experiência → obrigatório andar na garupa do instrutor). O portal **agenda/registra** que a demonstração ocorrerá no check-in; o **Atestado 5-B** é finalizado/assinado pelo instrutor no backoffice/mobile.
5. **GRU (taxa da Marinha) — INTEGRADO:**
   - Portal solicita ao backend a **geração da GRU** para a CHA-MTA-E.
   - Exibe **linha digitável / código de barras / QR** + valor + vencimento, com botão "copiar".
   - Cliente paga (no app, se houver pagamento embutido, ou no banco dele).
   - Status acompanhado até **pagamento confirmado** (webhook/polling de backend).
6. **Emissão** — com GRU paga + anexos assinados + demonstração registrada → backend conduz a emissão da **CHA-MTA-E** (válida 30 dias; não vale para emissão de nova CHA). Portal mostra o status e o documento resultante.

> **Dependências de backend (pesadas) — ver §9.4:** geração de GRU, integração com a Marinha para emissão da CHA-MTA-E, e armazenamento dos anexos assinados. O **frontend** é desenhado para: coletar declarações, gerar/assinar PDFs dos anexos, exibir/acompanhar a GRU e refletir o status de emissão. Onde a integração Marinha não estiver pronta, o front degrada para "assistido" (upload de comprovante de GRU + documentos para validação do staff) sem mudar a UI.

### 6.4 Regras de UI da habilitação

- Bloquear avanço da reserva para "pronta" enquanto a habilitação não estiver **válida**.
- CHA-MTA-E **expira em 30 dias** — exibir validade e alertar reemissão.
- Estrangeiro (passaporte) → alternar automaticamente os formulários/PDFs para **EN**.
- Cada declaração tem **checkbox de ciência** + assinatura (§7) — sem aceite, não gera o PDF.

---

## 7. Termos & assinatura

Documentos a assinar por reserva/locação:

1. **Termo de Responsabilidade pelo uso de moto aquática — JET SAVE TURISMO NÁUTICO LTDA** (page 7 do PDF): entrega em perfeitas condições; responsabilidade por danos; **emborcamento → custos R$ 400–2.000** (e ressarcimento integral acima disso); declara aptidão física/psicológica e ausência de álcool/drogas; respeito às normas da Autoridade Marítima.
   - **Multi-tenant:** o texto e o CNPJ são **por loja** (cada tenant tem seu termo). O portal renderiza o termo do tenant; "Jet Save" é o primeiro caso. → endpoint de **template de termo por tenant** (gap §9.7).
2. **Anexos NORMAM** assinados pelo locatário (5-B, 5-C, e 1-C quando aplicável) — quando o caminho B é usado.

**Mecânica de assinatura (v1):**
- **Aceite eletrônico** com evidências: identidade autenticada (OIDC), checkbox por cláusula, timestamp, IP, user-agent, e **hash do documento** assinado (SHA-256). Armazenado como evidência.
- Geração do **PDF final** (anexo/termo) com os dados preenchidos + carimbo de aceite.
- v2 pode evoluir para assinatura via provedor de e-signature/ICP-Brasil.

> **Gap de backend:** hoje a aceitação de termo é um **boolean** em `Cliente` (`accept-terms`, só staff). É preciso modelar **aceite por reserva/locação com evidências e versão do termo**, e permitir o **cliente aceitar/assinar** (não só staff). Ver §9.7.

---

## 8. Internacionalização (i18n)

- **PT-BR default**, **EN** disponível.
- Os anexos 5-B/5-C/1-C **têm versão oficial em inglês** (locatário estrangeiro usa **passaporte**). A escolha do idioma deve **trocar o template do PDF** correspondente, não só a UI.
- Datas, moeda (BRL) e formatos por locale.

---

## 9. Contratos de API — existente vs. a criar

Visão do **frontend**: o que já dá para consumir e o que o backend precisa expor. (Mapeamento detalhado dos controllers atuais feito na investigação; resumo abaixo.)

### 9.1 Já existe e é reutilizável
- `GET /v1/public/marketplace/modelos` e `/{id}` — catálogo.
- `GET /v1/tenants/{tenantId}/reservas/disponibilidade` — disponibilidade (público).
- `POST /v1/.../fotos/upload` + `/{fotoId}/confirm` + `GET /{fotoId}` — **presigned URL** (reutilizável para comprovantes/documentos).
- `POST /v1/.../reservas/{id}/confirmar-sinal` — staff confirma sinal (lado backoffice).
- Modelo de dados de `Reserva`/`Locacao` já completo.

### 9.2 Catálogo público por loja (novo)
- `GET /v1/public/tenants/{slug}/modelos` (+ `/{id}`) — vitrine tenant-scoped para `/loja/[slug]`.

### 9.3 Identidade & área do cliente (novo)
- `POST /v1/public/customers/signup` — self-registration (cria identidade global).
- `POST /v1/public/customers/claim` — vincula login a `Cliente` pré-existente via **claim-token/OTP**.
- `GET/PUT /v1/customers/self` — perfil self-service.
- `POST /v1/public/tenants/{slug}/reservas` **ou** `POST /v1/customers/reservas` — **criar reserva como cliente** (hoje só staff).
- `GET /v1/customers/reservas` (+ `/{id}`) — reservas do próprio cliente.
- `GET /v1/customers/locacoes` (+ `/{id}`) — histórico do próprio cliente.
- **Auth:** introduzir escopo/role **CLIENTE** (distinto de ADMIN_TENANT/GERENTE/OPERADOR/…); token do client `jetski-customer-portal`. Cliente **não** tem `Membro`.

### 9.4 Habilitação / EMA / GRU (novo — dependência pesada)
- `POST /v1/customers/reservas/{id}/habilitacao` — declara caminho A (CHA) ou B (CHA-MTA-E).
- Upload de CHA e documentos via presigned URL.
- `POST /v1/customers/habilitacao/{id}/gru` — **gerar GRU** (retorna linha digitável/QR/valor/vencimento).
- `GET /v1/customers/habilitacao/{id}/gru/status` — status do pagamento da GRU (ou webhook).
- `GET /v1/customers/habilitacao/{id}` — status da emissão da CHA-MTA-E (válida 30 dias).
- Registro de conclusão da **videoaula** e agendamento da **demonstração prática**.
- **Integração externa (backend):** geração da GRU e emissão junto à Marinha. Frontend agnóstico ao provedor; consome status.

### 9.5 Pagamento de sinal — PIX manual (novo)
- `GET /v1/public/tenants/{slug}/pix` — chave/QR estático + instruções da loja.
- `POST /v1/customers/reservas/{id}/comprovante-sinal` — anexa comprovante (presigned URL) e marca "em validação".

### 9.6 Prontidão da reserva (novo, recomendado)
- `GET /v1/customers/reservas/{id}/checklist` — `{ emailVerified: bool, sinalStatus: aguardando|em_analise|confirmado|recusado, habilitacao: ok|pendente|expirada, termos: ok|pendente, garantida: bool, prontaParaCheckin: bool }`. Evita o front recomputar regra de negócio (inclui o gate de conta `emailVerified` — ver §2.4/§5.6).

### 9.7 Termos & aceite (novo)
- `GET /v1/public/tenants/{slug}/termos` — template(s) do termo da loja por idioma/versão.
- `POST /v1/customers/reservas/{id}/aceite` — registra aceite com evidências (hash, IP, UA, timestamp, versão) e gera PDF assinado.

### 9.8 Avaliações (novo — inexistente)
- `POST /v1/customers/locacoes/{id}/avaliacao` — nota (1–5) + comentário.
- `GET /v1/public/tenants/{slug}/avaliacoes` — média/lista pública (opcional para vitrine).

### 9.9 Notificações (novo)
- `GET /v1/customers/notificacoes` + marcar lida.
- Eventos: sinal confirmado, habilitação aprovada/expirando, lembrete de reserva, locação finalizada → avalie.
- Canais: e-mail (já há infra de e-mail), **WhatsApp/SMS** (alinha com o claim-token por WhatsApp da arquitetura), **push** (PWA).

---

## 10. Componentes-chave de UI

- **ModelCard / ModelDetail** — vitrine, preço, caução, política de combustível.
- **AvailabilityPicker** — date-range + tradução do retorno de disponibilidade.
- **BookingWizard** — multi-step com persistência e retomada.
- **ReservationStatusPanel** — checklist visual (sinal / habilitação / termos) com CTAs.
- **PixPaymentCard** — QR + chave copiável + upload de comprovante.
- **HabilitacaoFlow** — triagem + sub-fluxos A/B; player de videoaula; formulários dos anexos; acompanhamento da GRU.
- **AnexoForm** (5-B / 5-C / 1-C) — geração de PDF pré-preenchido + checkboxes de ciência.
- **TermSignature** — render do termo + aceite com evidências.
- **DocumentUploader** — wrapper do fluxo presigned URL (imagem/PDF, compressão, preview).
- **ReviewForm** — estrelas + comentário.
- **NotificationCenter**.

---

## 11. Segurança, LGPD & qualidade

- **OIDC PKCE** obrigatório; tokens nunca em localStorage (httpOnly cookies via NextAuth).
- **Escopo CLIENTE**: o portal só consome endpoints cliente-scoped; nenhum endpoint de backoffice é acessível por construção.
- **LGPD:** consentimento explícito no cadastro; dados sensíveis (saúde no 5-C, documentos) com base legal clara (execução do contrato + obrigação regulatória NORMAM); minimização em logs; retenção configurável por tenant; opção de exportar/excluir dados.
- **Documentos/fotos:** presigned URL, hash SHA-256, prefixo `tenant_id/` no storage.
- **Anti-takeover:** vínculo a `Cliente` pré-existente sempre com OTP; sem JIT por e-mail.
- **Testes:** Playwright cobrindo os fluxos críticos (reserva → sinal → habilitação → termos → check-in pela loja → histórico → avaliação), espelhando o estilo de cobertura BDD do projeto.

---

## 12. Faseamento sugerido

**Fase 1 — fundação (sem regulatório):**
catálogo público + disponibilidade, cadastro/login (CLIENTE), criação de reserva cliente-scoped, área "minhas reservas", perfil.

**Fase 2 — transação:**
sinal PIX manual + comprovante, checklist de prontidão, notificações (e-mail), histórico de locações, avaliação.

**Fase 3 — regulatório:**
termos com aceite + evidências (termo da loja), caminho A da habilitação (upload CHA + validação staff).

**Fase 4 — EMA/GRU integrado:**
videoaula + anexos 5-B/5-C/1-C com geração de PDF, GRU integrada, emissão CHA-MTA-E, i18n EN para estrangeiros, demonstração prática agendada.

> Fases 1–2 entregam um portal de reservas utilizável sem depender da integração pesada da Marinha; a Fase 4 é a de maior risco/dependência de backend.

---

## 13. Dependências de backend (resumo acionável)

Bloqueiam o portal e precisam entrar no backlog do backend:

1. **Role/escopo CLIENTE** + client OIDC `jetski-customer-portal` + tabela `cliente_identity_provider` + claim-token/OTP.
   - **Verificação de e-mail** via Keycloak (`Verify Email` required action + claim `email_verified`); **conta restrita vs. completa** (§2.4); job de **expiração de pré-reserva em 24h** sem verificação; estado do **sinal** (`aguardando/em análise/confirmado/recusado`) com tela de validação no backoffice.
2. Endpoints **cliente-scoped**: signup, perfil, criar/listar reserva, listar locações.
3. Catálogo **público por loja** e **dados PIX** por loja.
4. **Aceite de termo por reserva** com evidências + versão (substituir o boolean atual).
5. **Habilitação**: modelo de dados (caminho A/B), upload CHA/docs, **geração de GRU**, integração de **emissão CHA-MTA-E** com a Marinha, registro de videoaula/demonstração.
6. **Comprovante de sinal** anexável pelo cliente + fila de validação para staff.
7. **Avaliações** (módulo novo, inexistente).
8. **Notificações** ao cliente (e-mail/WhatsApp/push).
9. Endpoint de **checklist/prontidão** da reserva.

> Toda alteração/criação de tabela deve ser refletida em `reset-ambiente-dev.sh` (regra do projeto).

---

## 14. Questões em aberto

1. **GRU integrada:** existe API oficial/credenciada para gerar GRU da Marinha e consultar pagamento, ou será via convênio/banco? Define se a Fase 4 é "integrada" de fato ou "assistida" no v1.

R: Hoje não existe API para gerar GRU da marinha, Em outra fase vamos construir um Robo para emitir essa GRU já com os dados que temos do cliente e retornar o pix/boleto e o numero da GRU

2. **Demonstração prática:** confirmar que é sempre presencial no check-in (impacto no estado "pronta para check-in").

R: Sempre presencial, que será feita por um instrutor, assim o instrutor uma vez conferido os dados do cliente ele confirma se é necessario instruções para o cliente passa elas confirmando e o inicio do passeio começa
3. **Pagamento da GRU dentro do app** vs. cliente paga no banco e o portal só acompanha o status.

R: Para o primeiro momento não vamos ter um gateway de pagamento processando, então o comprovante vai ser conferido manualmente pelo Staff. Essa feature fica para um futuro

4. **Termo por tenant:** versionamento e editor no backoffice, ou template fixo por loja no v1?

R: No v1 vamos fixos, mas cada loja vai ter o seu proprio termo ou usar o default só trocando os dados da loja

5. **WhatsApp:** provedor (API oficial Meta vs. BSP) para notificações e claim-token.

R: Primeiro momento nao vamos focar nessa integração, por email vai ser a forma mais barata

6. **Subdomínio por loja** (`{slug}.portal...`) é desejado para branding, ou path/marketplace basta no v1?

R: No primeiro momento não estamos pensando em subdominio
