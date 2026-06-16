# Tickets — Backoffice Balcão · Fase 2 (Backend do atendimento)

> Deriva de [`BACKOFFICE_IMPLEMENTATION_PLAN.md`](./BACKOFFICE_IMPLEMENTATION_PLAN.md). Continua [`BACKOFFICE_TICKETS_FASE0-1.md`](./BACKOFFICE_TICKETS_FASE0-1.md) (fundações).
> **Escopo da fase:** **somente backend** do atendimento de balcão (a UI é a Fase 3). Decisões: balcão = pagamento **total** confirmado direto · PDF **OpenPDF manual** · envio Marinha por **e-mail por tenant** · GRU **manual**.
> **Regras transversais** (ver DoD comum no fim): DB → Flyway + `reset-ambiente-dev.sh` + RLS; endpoint novo → `@PreAuthorize` + `ActionExtractor` + política OPA.

## Dependências / ordem
```
(Fase 1: F1.A pagamento/comprovante/doc · F1.B identidade · F1.C config tenant ·
         F1.D putObject · F1.E e-mail anexo · F1.F eventos · F1.G OPA/RBAC)

F2.1 pagamento (confirmar/recusar/total) ── dep F1.A,F1.F,F1.G
F2.2 pré-conta cliente ──────────────────── dep F1.B
F2.3 habilitação/CHA-MTA-E + GRU ────────── dep F1.A (migr própria)
F2.4 assinatura/aceite + evidências ─────── dep F1.D (migr própria)
F2.5 DocumentoPdfService (OpenPDF) ───────── dep F0.1, consome F2.2/F2.3/F2.4
F2.6 emitir-documentos (orquestra) ──────── dep F2.5,F1.C,F1.D,F1.E,F1.F,F1.G
F2.7 claim-token do cliente ─────────────── dep F1.B (reusa convite/Keycloak)
F2.8 teste integração fim-a-fim (API) ───── dep F2.1..F2.7
```
Reuso: a **criação da reserva** no balcão usa o `POST /v1/tenants/{tid}/reservas` existente (staff) — não há ticket novo para isso.

---

## F2.1 — Pagamento: confirmar/recusar (sinal/total) + balcão direto
**Tipo:** Task · **Tamanho:** M · **Área:** Backend · **Dep:** F1.A, F1.F, F1.G

**Objetivo:** generalizar o "sinal" para **pagamento** (sinal|total), com recusa e confirmação direta no balcão.

**Escopo:**
- `ReservaService.confirmarSinal(id, valorSinal)` (`locacoes/internal/ReservaService.java:634`) → generalizar para **`confirmarPagamento(id, tipo, valorPago, origem)`**: seta `pagamento_status=CONFIRMADO`, `pagamento_tipo`, `valorPago`, `pagamento_validado_por/em`; BAIXA→ALTA; mantém a checagem de capacidade. Publica `PagamentoConfirmadoEvent`.
- Novo **`recusarPagamento(id, motivo)`**: `pagamento_status=RECUSADO`, grava motivo, mantém PENDENTE/BAIXA. Publica `PagamentoRecusadoEvent`.
- **Balcão = confirmação direta:** quando `origem=BALCAO` e `tipo=TOTAL`, confirma sem passar por fila (registra forma de pagamento).
- Controller: manter `POST .../confirmar-sinal` (compat) + aceitar `tipo`/`valorPago`; novo `POST .../recusar-pagamento`. **RBAC**: ADMIN_TENANT/GERENTE/OPERADOR/**FINANCEIRO** (`@PreAuthorize` + OPA action `confirmar-pagamento`/`recusar-pagamento`).
- Disparar e-mails (`sendPagamentoConfirmado/Recusado`) quando remoto (não no balcão presencial).

**Aceite:**
- Confirmar total (balcão) → reserva ALTA, `pagamento_status=CONFIRMADO`, auditado.
- Recusar → volta pendente + motivo + auditado + e-mail (remoto).
- Idempotência: confirmar já confirmado → 409.
- Testes de autorização (FINANCEIRO permitido; CLIENTE negado).

---

## F2.2 — Pré-conta do cliente (origem=BALCAO) + dedupe por CPF
**Tipo:** Task · **Tamanho:** M · **Área:** Backend · **Dep:** F1.B

**Objetivo:** registrar o cliente no balcão sem login, com dedupe seguro.

**Escopo:**
- `ClienteController`/serviço: criar `Cliente` com `origem=BALCAO`, `status_conta=PRE_CONTA` (nome, CPF, contato, endereço opcional).
- **Busca por CPF** (`GET .../clientes?cpf=`) para dedupe no balcão.
- **Regra anti-takeover:** se houver `Cliente` com mesmo CPF **já com identidade ativa**, **não** mescla automático → retorna estado que exige verificação (OTP) — no v1, sinalizar e bloquear o merge (OTP pode ser stub/registrado).
- Publicar `PreContaCriadaEvent`. RBAC: OPERADOR+ (action `criar-pre-conta`).

**Aceite:** cria pré-conta; CPF repetido sem identidade → reusa; CPF com identidade ativa → exige verificação; auditado.

---

## F2.3 — Habilitação (CHA) e emissão CHA-MTA-E + GRU (manual)
**Tipo:** Task · **Tamanho:** M · **Área:** Backend/DB · **Dep:** F1.A

**Objetivo:** persistir a habilitação do condutor e os dados da CHA-MTA-E/GRU (manual no v1).

**Escopo:**
- **Migração** (Flyway + reset script) — `reserva_habilitacao` (ou campos na reserva): `via` (CHA|EMA), CHA (categoria, número, validade), e p/ EMA: `videoaula_em`, anexos (flags/JSON 5-C/5-B/1-C), **GRU** (`gru_numero`, `gru_valor`, `gru_pago`, `gru_pago_em`) **+ RLS**.
- Endpoints: registrar via CHA (com upload via presigned) **ou** registrar EMA + **GRU manual** (nº/valor/marcar paga).
- Validação: emissão de documentos (F2.6) só com habilitação resolvida (CHA anexada ou GRU paga).
- **Nota regulatória:** Atestado 5-B (demonstração prática) é concluído no **check-in** (à parte) — flexibilizado; não bloqueia a emissão no balcão.

**Aceite:** persiste ambos os caminhos; GRU manual marcável como paga; estado consultável; auditado.

---

## F2.4 — Assinatura no balcão (aceite + evidências)
**Tipo:** Task · **Tamanho:** M · **Área:** Backend/DB · **Dep:** F1.D

**Objetivo:** registrar a assinatura presencial com evidências para os anexos/termo.

**Escopo:**
- **Migração** (Flyway + reset) — `reserva_aceite` (id, tenant_id, reserva_id, operador_id, metodo (`signature_pad`|`papel`), assinatura_s3_key, hash_doc, ip, user_agent, origem=BALCAO, aceito_em) **+ RLS**.
- Endpoint para gravar o aceite + **salvar a imagem da assinatura** (PNG) via `StorageService.putObject`.
- Capturar evidências (IP/dispositivo/timestamp). RBAC: OPERADOR+.

**Aceite:** aceite gravado com imagem e evidências; recuperável; auditado.

---

## F2.5 — `DocumentoPdfService` (OpenPDF) — anexos + termo
**Tipo:** Task · **Tamanho:** L · **Área:** Backend · **Dep:** F0.1 (graduação); consome F2.2/F2.3/F2.4

**Objetivo:** gerar o **PDF consolidado** completo, fiel aos modelos da Marinha.

**Escopo:**
- Montar com OpenPDF, **uma seção por anexo** (página própria): **1-C** (Declaração de Residência), **5-C** (Saúde, tabela SIM/NÃO), **5-B-1** (Atestado EAMA/instrutor), **5-B-2** (locatário, regras a–j, validade 30d), **Termo da loja** (cláusulas 1–9).
- Preencher com dados reais (cliente, reserva, GRU, loja/tenant) + **inserir imagem da assinatura** (de F2.4).
- Calcular **SHA-256**; retornar bytes + hash.
- Espelho visual: `frontend/portal-cliente/app/staff/documento/page.tsx`.

**Aceite:** PDF com as 5 seções preenchidas e assinadas; teste compara campos-chave; revisão visual aprovada. Cada anexo testável isoladamente.

**Risco:** layout manual trabalhoso — isolar por anexo, reaproveitar helpers do spike F0.1.

---

## F2.6 — Endpoint `emitir-documentos` (orquestração)
**Tipo:** Task · **Tamanho:** L · **Área:** Backend · **Dep:** F2.5, F1.C, F1.D, F1.E, F1.F, F1.G

**Objetivo:** orquestrar a emissão: gerar → arquivar → enviar → registrar.

**Escopo:**
- `POST /v1/tenants/{tid}/reservas/{id}/emitir-documentos`:
  1. `DocumentoPdfService` gera o PDF (F2.5);
  2. `StorageService.putObject` salva (key `tenant/reserva/{id}/documento.pdf`);
  3. registra `documento_emitido` (s3_key, hash, destinos, `documento_emitido_em`);
  4. **e-mail à Marinha** (endereço por tenant, F1.C) com o PDF;
  5. **e-mail ao cliente** com o PDF + **link de claim** (F2.7);
  6. devolve URL de download (presigned) + dados da GRU de saída.
- **Idempotente** (reemissão controlada); publica `DocumentosEmitidosEvent` (com destinos). RBAC: OPERADOR+ (action `emitir-documentos`).

**Aceite:** chamada gera/arquiva/envia; reemissão não duplica; auditado; e-mails capturados em teste; download válido.

---

## F2.7 — Claim-token do cliente (ativação de conta)
**Tipo:** Task · **Tamanho:** M · **Área:** Backend/Sec · **Dep:** F1.B

**Objetivo:** transformar a pré-conta em conta ativável, **reusando** a máquina de convite/Keycloak.

**Escopo:**
- Geração/armazenamento do token (reusar `convite` ou nova `claim_token` cliente-scoped — **decidir e migrar** se nova) com **TTL 7 dias**, single-use; reenvio invalida anterior.
- Envio por **e-mail** (F1.E) — SMS/WhatsApp ficam para fase posterior.
- **Validação/ativação (endpoint público):** reusar `AccountActivationController`/`UserInvitationService`/`MagicLinkTokenService`; provisionar usuário Keycloak via `KeycloakAdminService.createUserWithPassword` com **role CLIENTE** e **sem `Membro`**; vincular `cliente_identity_provider` (via padrão de `IdentityProviderMappingService.linkProvider`); `cliente.status_conta` PRE_CONTA→CONVIDADA→ATIVA.
- Publicar `ClaimEnviadoEvent`/`ContaAtivadaEvent`.

**Aceite:**
- Gerar/reenviar token; validar cria usuário Keycloak (CLIENTE) + vínculo + status ATIVA.
- **Garantia por teste:** cliente ativado **não** recebe `Membro` nem acessa backoffice.
- Token expirado/usado → erro tratado.

**Nota:** a **página de ativação** (UI) reaproveita o fluxo de ativação existente (staff) adaptado; tratada na Fase 3/portal.

---

## F2.8 — Teste de integração fim-a-fim (API) do balcão
**Tipo:** Task · **Tamanho:** M · **Área:** Backend/QA · **Dep:** F2.1–F2.7

**Objetivo:** garantir o caminho completo do atendimento via API.

**Escopo (sobre `AbstractIntegrationTest` + MockMvc `jwt()`):**
- Fluxo: criar pré-conta → criar reserva → confirmar pagamento total → registrar habilitação/GRU → gravar assinatura → `emitir-documentos` → gerar/validar claim.
- Assertivas: `documento_emitido` criado + hash; e-mails (Marinha+cliente) disparados (mock); auditoria com os eventos; cliente ativado sem `Membro`.
- Mockar serviços externos (Keycloak admin, e-mail, OPA conforme padrão dos testes existentes).

**Aceite:** teste verde cobrindo o caminho feliz + 2 erros (dedupe CPF c/ identidade; emitir sem habilitação).

---

## Definition of Done (comum — ver também `BACKOFFICE_TICKETS_FASE0-1.md`)
- [ ] Testes unit/integration verdes; sem regressão na suíte (~747).
- [ ] (DB) Flyway **e** `reset-ambiente-dev.sh` atualizados; reset roda limpo; **RLS** testada.
- [ ] (endpoint) `@PreAuthorize` + `ActionExtractor` + política **OPA** + teste de autorização.
- [ ] Eventos de auditoria emitidos e verificados.
- [ ] PR pequeno e revisável, ligado ao ticket.

## Marco ao fim da Fase 2
Atendimento de balcão **completo via API**: pré-conta → reserva → pagamento total → habilitação/GRU → assinatura → emissão (PDF arquivado + e-mail Marinha/cliente + GRU) → claim de conta. Pronto para a **Fase 3 (UI do backoffice)** plugar.
