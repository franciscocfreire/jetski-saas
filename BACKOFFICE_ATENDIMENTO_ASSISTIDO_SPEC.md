# Especificação — Atendimento de Balcão (Backoffice)

> **Status:** Proposta v1 · **Data:** 2026-06-15 · **App:** `frontend/jetski-backoffice`
> **Relacionados:** [`PORTAL_CLIENTE_SPEC.md`](./PORTAL_CLIENTE_SPEC.md) · [`BACKOFFICE_VALIDACAO_SINAL_SPEC.md`](./BACKOFFICE_VALIDACAO_SINAL_SPEC.md)
> **Escopo:** o funcionário conduz o **registro/onboarding administrativo** do cliente pelo balcão (registro → documentos → pagamento do aluguel → GRU/CHA-MTA-E → termos → **emissão dos documentos**), para clientes **sem celular/sem acesso digital**. Cria uma **pré-conta** + **claim-token** para o cliente **ativar a conta** depois.
> **Fora deste fluxo:** o **check-in/embarque** (fotos, horímetro, início do passeio) é um momento **à parte**, na hora do passeio — reusa o check-in existente em `locacoes`.

> Por que uma spec focada (e não "todo o backoffice"): o backoffice já é um app **funcional em desenvolvimento ativo**; re-documentá-lo inteiro criaria uma 2ª fonte de verdade que diverge do código. Specs valem para o que **ainda não existe**. Para o que já existe, ver §11 (índice) + `IMPLEMENTATION_STATUS.md`.

---

## 1. Problema & objetivo

Nem todo cliente vai usar o portal: pode não ter smartphone, e-mail, ou intimidade digital — especialmente no balcão de uma operação de praia. Hoje o fluxo regulatório (NORMAM-212: CHA-MTA-E, anexos, termos), o pagamento e a emissão dos documentos pressupõem o cliente agindo no app.

**Objetivo:** permitir que um **funcionário** execute o **registro e a documentação completos** em nome do cliente, presencialmente, **sem exigir** que o cliente tenha conta/app — produzindo os documentos da Marinha e deixando uma **pré-conta** para o cliente **assumir depois**, de modo a:
- registrar o cliente e coletar documentos;
- receber o **pagamento do aluguel** (total) e, quando aplicável, a **GRU** (CHA-MTA-E);
- colher a **assinatura dos termos** com evidências;
- **emitir o PDF consolidado** (enviado à Marinha + e-mail) e a **GRU de saída**;
- manter histórico ligado a uma identidade real e habilitar reservas futuras self-service.

> O **check-in/embarque** (fotos, horímetro, início) **não faz parte** deste fluxo — é feito à parte na hora do passeio.

---

## 2. Princípios (herdados da arquitetura de identidade)

- **Reserva/embarque NÃO dependem de login do cliente** — o funcionário opera tudo.
- **Pré-conta = `Cliente` (tenant-scoped) + claim-token**; **não** cria credencial no Keycloak até o cliente ativar.
- **Vínculo sempre explícito** (claim-token single-use amarrado ao `cliente_id`). **Sem JIT cego por e-mail** (anti account-takeover quando o registro público está aberto).
- **Cliente nunca recebe `Membro`** → sem acesso ao backoffice por construção.
- Fusão com `Cliente` pré-existente (mesmo CPF) **exige verificação (OTP)** — nunca merge automático.
- O fluxo assistido **espelha** o do portal (mesmas etapas e estados), apenas **operado pelo staff**; as duas pontas convergem na mesma `Reserva`/`Locacao`.

---

## 3. RBAC — quem opera

- **Embarque assistido (reserva, sinal, habilitação, termos, check-in):** OPERADOR, GERENTE, ADMIN_TENANT.
- **Demonstração prática + Atestado 5-B:** **Instrutor** (papel/atributo do funcionário; pode ser OPERADOR habilitado). Confere dados do cliente e confirma a instrução no embarque.
- **Validação/registro de sinal:** ver `BACKOFFICE_VALIDACAO_SINAL_SPEC.md` (recomendado incluir FINANCEIRO).

---

## 4. Estados da pré-conta

Distinto do `email_verified` (que é o portão de identidade do **portal**). Aqui o eixo é **posse da conta**:

```
PRE_CONTA ──(claim enviado)──► CONVIDADA ──(cliente ativa via token)──► ATIVA
   │  (Cliente existe, sem login)            (cria credencial Keycloak + vincula
   │                                          cliente_identity_provider)
   └──(cliente recusa conta digital)──► SEM_LOGIN  (fica só como Cliente operável pelo staff)
```

- **PRE_CONTA:** `Cliente` criado pelo balcão; sem identidade no Keycloak.
- **CONVIDADA:** claim-token gerado e enviado (e-mail/SMS/WhatsApp/QR).
- **ATIVA:** cliente abriu o token, definiu credencial → `cliente_identity_provider` vinculado àquele `cliente_id`. A partir daí, é um cliente self-service normal.
- **SEM_LOGIN:** cliente não quer conta digital → segue existindo como `Cliente` (histórico preservado), reativável depois.

---

## 5. Fluxo de atendimento de balcão (núcleo)

Wizard no backoffice operado pelo funcionário. **Importante:** este fluxo é o **registro/onboarding administrativo** — **NÃO inclui o check-in/embarque** (fotos, horímetro, início do passeio), que é um momento **à parte**, na hora do passeio (reusa o check-in existente em `locacoes`). Cada etapa persiste; pode pausar/retomar.

1. **Registro do cliente**
   - Busca por **CPF** (ou nome/telefone). Se já existe `Cliente` → usa (e oferece reenviar claim se ainda PRE_CONTA/CONVIDADA).
   - Se não existe → **criar pré-conta**: nome, CPF, contato (e-mail e/ou celular), endereço. Marca origem = `BALCAO`. Dedupe por CPF; match com identidade ativa → **bloqueia/OTP**.

2. **Coleta de documentos** — digitaliza RG/CNH/passaporte, CPF, foto do cliente. **Comprovante de residência:** se tem → anexa; **se não tem → Declaração de Residência (Anexo 1-C)** com formulário de endereço de **autopreenchimento por CEP** (ViaCEP; fallback manual). Triagem de habilitação: **tem CHA?** → anexa a CHA; **não tem?** → será emitida **CHA-MTA-E** (passo 4).

3. **Pagamento do aluguel (valor TOTAL)** — **no balcão não existe sinal**: paga-se **integralmente** na hora. Funcionário seleciona o modelo/período e registra o recebimento (dinheiro / PIX / maquininha) → **direto a `CONFIRMADO`** (registrando a forma), **sem** fila de validação. *(Sinal é exclusivo de reserva antecipada/remota — `PORTAL_CLIENTE_SPEC.md` §5.4.)*

4. **Habilitação — CHA-MTA-E (NORMAM-212)** — só para quem **não tem** habilitação:
   - cliente assiste à **videoaula** ali, funcionário preenche os **anexos 5-C / 5-B / 1-C** (assinatura no balcão — §6);
   - **GRU:** gera a GRU e **registra o pagamento da GRU** (taxa da Marinha) → habilita a emissão da CHA-MTA-E (assistida no v1; ver portal §6.3/§14).
   - **Demonstração prática (Atestado 5-B):** feita pelo **instrutor no check-in/embarque** (à parte, não neste fluxo).
   - Quem **tem CHA** pula este passo (GRU não necessária).

5. **Assinatura dos termos** — **Termo de Responsabilidade da loja** (+ anexos) → **assinatura no balcão** via signature pad (§6).

6. **Emissão dos documentos** — gera um **PDF consolidado** (documentos + anexos/termos + assinatura) **fiel aos modelos da NORMAM-212/DPC** (Anexos 1-C, 5-C, 5-B) + Termo de Responsabilidade da loja. *(Há um exemplo preenchido navegável no protótipo em `/staff/documento`.)* O PDF:
   - é **baixável** pelo operador/cliente;
   - é **enviado automaticamente à Marinha**;
   - em **paralelo, vai por e-mail ao cliente** (com o PDF **+ o link de ativação da conta** — o claim, §7);
   - e gera a **GRU de saída** (guia) para **imprimir/entregar ao cliente**.

> O cliente sai do balcão **registrado, pago e documentado**, sem ter tocado no app. A conta é ativada quando ele quiser (claim no e-mail). O **embarque (check-in)** acontece depois, na hora do passeio.

---

## 6. Assinatura no balcão (evidências)

Como o cliente não está autenticado no portal, a assinatura presencial precisa de evidência própria.

**Decisão (fechada 2026-06-15):** **signature pad no v1** — um **canvas** de assinatura que funciona com **dedo (touch), caneta ou mouse** (desktop do balcão), capturando a assinatura manuscrita do cliente. **Papel assinado digitalizado** fica como **fallback**.

- **Evidências gravadas (obrigatórias):** quem operou (`operador_id`), `cliente_id`, **timestamp**, **IP/dispositivo** do balcão, **hash SHA-256** do PDF gerado, **método de assinatura** (`signature_pad` | `papel_digitalizado`), a **imagem da assinatura** (e, quando houver, foto do cliente/documento), e **`origem: BALCAO`**.
- Gera o **PDF final** dos anexos/termo com os dados preenchidos + a assinatura capturada + carimbo de aceite presencial.
- Diferença do portal: lá a evidência é o **login OIDC** do próprio cliente; aqui é **presencial mediado por funcionário** — registrado explicitamente no metadado do aceite.

---

## 7. Claim-token (pré-conta → conta ativa)

- **Token single-use**, **TTL de 7 dias** (decisão fechada), amarrado a `cliente_id + tenant_id`, com nonce e hash em repouso. Reenvio gera novo token e **invalida o anterior**.
- **Ativação (no portal, público):** cliente abre o link/QR → confirma contato (e-mail/celular) → define credencial → Keycloak cria a conta → grava `cliente_identity_provider(provider=keycloak, provider_id=sub, cliente_id)`.
- **Anti-takeover:** se o `cliente_id` já tiver identidade vinculada, ou o contato pertencer a outra conta, **não** vincula automaticamente → exige OTP/verificação.
- **Reenvio/expiração:** funcionário pode reenviar (novo token invalida o anterior); tokens expirados pedem novo envio.
- Reaproveita o mesmo mecanismo do **magic-link de staff** já existente (`Convite`/`AccountActivation`), adaptado para a população cliente.

---

## 8. Canais de notificação (claim) — e-mail + SMS + WhatsApp

Decisão: incluir os **três** no v1 (público de balcão pode não ter e-mail). **Camada de canal plugável** (`NotificationChannel`), com seleção/fallback.

**Provedor (decisão fechada 2026-06-15):** **abstrair agora e decidir na integração.** E-mail + **QR no balcão** funcionam de imediato (sem custo/integração externa); o BSP de WhatsApp e o gateway de SMS são escolhidos na hora de integrar, atrás da abstração `NotificationChannel` — sem travar a spec nem o cronograma.

- **E-mail:** infra já existe (`EmailService`) — estender para mensagens de cliente (hoje só convite/reset).
- **SMS:** exige provedor (ex.: gateway SMS) — custo por mensagem.
- **WhatsApp:** exige **BSP / API oficial Meta** (templates aprovados, custo por conversa) — **maior dependência**; ver portal §14 Q5.
- **QR/link no balcão:** independe de o cliente receber a mensagem — funcionário mostra/imprime; cliente abre no próprio celular ali.
- **Fallback:** se não houver e-mail nem celular → só **QR/link no balcão**, ou cliente segue **SEM_LOGIN**.

> Reconciliação com o portal: as notificações **transacionais** do portal seguem **e-mail-first** (portal §9.9); o **claim** do atendimento assistido usa os 3 canais. A mesma camada de canal serve aos dois.

---

## 9. Modelo de dados

Migração Flyway nova + refletir em `reset-ambiente-dev.sh` (regra do projeto).

- **`cliente`** (existe): acrescentar `origem` (`PORTAL`|`BALCAO`), `status_conta` (`PRE_CONTA`|`CONVIDADA`|`ATIVA`|`SEM_LOGIN`).
- **`cliente_identity_provider`** (planejada na arquitetura): `cliente_id`, `provider`, `provider_id` (sub), `vinculado_em`.
- **`claim_token`** (nova): `id`, `tenant_id`, `cliente_id`, `token_hash`, `canais_enviados[]`, `expira_em`, `usado_em`, `criado_por` (funcionário), `ativo`.
- **`reserva_comprovante`** e campos `sinal_*` — ver spec de sinal.
- Aceites presenciais: estender o registro de aceite (portal §9.7) com `origem=BALCAO`, `operador_id`, `metodo_assinatura`.

---

## 10. API

Reusa o existente (reserva, walk-in check-in, fotos/midia presigned) e adiciona:

- `POST /v1/tenants/{tid}/clientes` — criar **pré-conta** (origem BALCAO) [já há `ClienteController`; garantir suporte a pré-conta].
- `GET /v1/tenants/{tid}/clientes?cpf=` — busca/dedupe no balcão.
- `POST /v1/tenants/{tid}/clientes/{id}/claim` — gerar + enviar claim-token (body: canais).
- `POST /v1/tenants/{tid}/clientes/{id}/claim/reenviar`.
- `POST /v1/public/customers/claim/validar` — (portal, público) valida token e ativa a conta (cria credencial + vincula identity provider).
- Habilitação/EMA, termos e pagamento (total): mesmos contratos das specs de portal/pagamento, porém com **contexto de funcionário** (auditoria registra `operador_id` + `origem=BALCAO`).
- **Emissão de documentos (novo):** `POST /v1/tenants/{tid}/reservas/{id}/emitir-documentos` → gera o **PDF consolidado** (documentos + anexos/termos + assinatura), retorna URL de download (presigned), **dispara o envio à Marinha**, o **e-mail ao cliente** (PDF + link de claim) e disponibiliza a **GRU de saída**. Idempotente; registra `documento_emitido_em`.
- **Auditoria:** eventos `PreContaCriada`, `ClaimEnviado`, `ContaAtivada`, `DocumentosEmitidos` (com destinos: Marinha/e-mail), além dos de pagamento/reserva.

---

## 11. Índice leve dos módulos do backoffice (já existentes)

Para não re-documentar — fonte de verdade é o código + `IMPLEMENTATION_STATUS.md`. O atendimento assistido **reusa**:

| Módulo | Papel no atendimento de balcão |
|---|---|
| `reservas` | criar reserva / disponibilidade |
| `locacoes` | **check-in (à parte, na hora do passeio)** — fotos, horímetro, billing |
| `clientes` (em `usuarios`/tenant) | criar/buscar `Cliente`, aceite de termos |
| `fotos/midia` | presigned URL (docs, comprovante, fotos) |
| `pagamentos` | (hoje foco em vendedores) — registro de sinal a evoluir |
| `comissoes`/`fechamento`/`combustivel`/`despesas`/`manutencao`/`frota`/`dashboard`/`marketplace` | operação geral (fora do escopo deste fluxo) |

Itens **novos** que este fluxo exige: pré-conta + claim-token, canais SMS/WhatsApp, assinatura no balcão, e os deltas de sinal (spec própria).

---

## 12. UI do backoffice (telas novas)

- **Wizard "Atendimento de balcão"** (stepper): **Cliente → Documentos → Aluguel (pagamento total) → Habilitação (CHA ou GRU/CHA-MTA-E) → Termos (signature pad) → Emissão**. **Sem check-in.**
- **Tela de emissão:** PDF consolidado **[Baixar]**, status **"✓ Enviado à Marinha"**, **"✓ E-mail ao cliente (PDF + link de ativação)"**, e **GRU de saída [Imprimir]** para o cliente.
- **Ficha do cliente:** status da conta (PRE_CONTA/CONVIDADA/ATIVA/SEM_LOGIN), histórico, habilitação válida, documentos.
- O **check-in** continua na tela de `locacoes` existente (à parte).
- Reuso de shadcn/ui + `apiClient` + TanStack Query (padrões do backoffice).

---

## 13. Edge cases
- **Sem e-mail e sem celular:** só QR/link no balcão, ou segue **SEM_LOGIN** (Cliente operável pelo staff).
- **Cliente recusa conta digital:** mantém `Cliente`/histórico; pode ativar no futuro.
- **Duplicidade (mesmo CPF):** dedupe na busca; merge só com OTP.
- **Menores / terceiro conduzindo:** o condutor habilitado pode diferir do pagante; o **locatário** que assina os anexos é o **condutor**.
- **Estrangeiro:** passaporte + anexos em **EN** (portal §8).
- **Claim expirado/extraviado:** reenvio gera novo token (invalida anterior).
- **Cliente ativa depois e já tinha outro `Cliente`** em outro tenant: identidade global liga a múltiplos `Cliente` (portal §2.2).

---

## 14. Faseamento
- **F1:** pré-conta (origem BALCAO) + wizard (Cliente → Documentos → Aluguel pago total → Habilitação CHA/GRU → Termos com **signature pad** → **Emissão do PDF** consolidado) + claim por **e-mail** + **QR no balcão**. (Check-in segue na tela existente de `locacoes`.)
- **F2:** SMS + WhatsApp (provedor/BSP) para o claim, status de canais, reenvio; **envio automático à Marinha** integrado (se houver canal); e-mail do PDF ao cliente.
- **F3:** habilitação/EMA assistida completa (anexos PDF + GRU automatizada), integração com a spec de pagamento evoluída (recusa/estorno), relatórios.

---

## 15. Decisões fechadas (2026-06-15)
1. **Assinatura no balcão:** **signature pad no v1** (canvas — dedo/caneta/mouse) + evidências completas (operador, timestamp, IP/dispositivo, hash SHA-256 do PDF, origem=BALCAO, imagem da assinatura); papel digitalizado como fallback (§6).
2. **WhatsApp/SMS:** **provedor abstraído** — e-mail + QR no balcão já no F1; BSP/SMS decididos na integração, atrás de `NotificationChannel` (§8).
3. **Pagamento no balcão:** **sempre valor TOTAL** (sem sinal); recebimento presencial vai **direto a `CONFIRMADO`**, sem fila (§5.3).
4. **Claim-token:** **TTL de 7 dias**, single-use; reenvio invalida o anterior (§7).
5. **Criar pré-conta:** **qualquer OPERADOR**, com **dedupe obrigatório por CPF** (match → bloqueia/OTP, sem merge automático) (§5.1).

> Faseamento atualizado: com signature pad no v1, a captura de assinatura entra na **F1** (antes prevista para F2).
