# Especificação — Validação de Pagamento (sinal/total) no Backoffice (lado Staff)

> **Status:** Proposta v1 · **Data:** 2026-06-15 · **App:** `frontend/jetski-backoffice`
> **Contraparte:** [`PORTAL_CLIENTE_SPEC.md`](./PORTAL_CLIENTE_SPEC.md) §2.4, §5.4, §9.5 (lado do cliente)
> **Escopo:** o fluxo pelo qual o staff **revisa o comprovante PIX** enviado pelo cliente (em **reserva antecipada/remota**) e **confirma ou recusa** o pagamento. No v1 não há gateway — a validação é **manual**.

> **Importante (modelo de pagamento):** o pagamento remoto pode ser **sinal** (parcial) **ou total** — o cliente escolhe (portal §5.4). Esta fila valida **ambos**. **Pagamento de balcão NÃO entra aqui** (é presencial, sempre total, confirmado direto pelo operador — ver `BACKOFFICE_ATENDIMENTO_ASSISTIDO_SPEC.md` §5.3). O termo "sinal" no nome do endpoint/legado permanece, mas conceitualmente é "**pagamento**".

---

## 1. Contexto

No portal, o cliente paga (sinal **ou** total) via **PIX manual** e envia o **comprovante** (imagem/PDF). Isso **não habilita nada sozinho** — apenas leva o pagamento para `EM_ANÁLISE`. Quem move para `CONFIRMADO`/`RECUSADO` é o **staff**, neste fluxo. É o portão de **pagamento**, distinto do portão de **identidade** (e-mail verificado via Keycloak) — ver portal §2.4.

A reserva só fica **GARANTIDA** quando **pagamento `CONFIRMADO` (staff) E `email_verified` (Keycloak)**.

No protótipo do portal, o botão *"▶︎ Simular confirmação do staff"* representa exatamente a ação especificada aqui.

---

## 2. Estado atual (grounded) vs. lacunas

### Já existe
- **Endpoint** `POST /v1/tenants/{tenantId}/reservas/{id}/confirmar-sinal` — `ReservaController.java:319`.
  - RBAC: `@PreAuthorize("hasAnyRole('ADMIN_TENANT','GERENTE','OPERADOR')")`.
  - Body `ConfirmarSinalRequest { valorSinal: BigDecimal (>0) }`; resposta `ReservaResponse`.
  - Serviço `ReservaService.confirmarSinal()` (`:633-681`): valida `podeConfirmarSinal()` (PENDENTE/CONFIRMADA e sinal ainda não pago), faz **BAIXA→ALTA**, `sinalPago=true`, `valorSinal`, `sinalPagoEm=now`, e **bloqueia se a capacidade garantida do modelo no período estiver esgotada** (`:650-669`).
- **Reserva** (`Reserva.java`): campos `sinalPago`, `valorSinal`, `sinalPagoEm`, `prioridade` (ALTA/BAIXA), `status` (PENDENTE/CONFIRMADA/CANCELADA/FINALIZADA/EXPIRADA), `expiraEm`.
- **Auditoria assíncrona** (`audit/internal/AuditEventListener.java`) com eventos `ReservationCreated/Confirmed/Cancelled` — padrão pronto para um novo evento de sinal.
- **Presigned URL** (`PhotoController` + `FotoService`) — mecânica de upload reutilizável.
- **Backoffice**: `lib/api/client.ts` (axios com `Authorization` + `X-Tenant-Id`), `lib/api/services/reservas.ts` (list/getById/create/confirmar/alocarJetski), shadcn/ui completo, `sonner` (toast), TanStack Query.

### Lacunas a fechar
- **Sem estado de sinal** rico: só existe o boolean `sinalPago`. Falta `EM_ANÁLISE`/`RECUSADO`.
- **Sem recusa/undo**: não há como recusar comprovante sem cancelar a reserva inteira.
- **Sem vínculo de comprovante à reserva**: `Foto` é escopada em `locacao` e `FotoTipo` não tem tipo de comprovante.
- **Sem evento/auditoria** específico de sinal; **sem e-mail** de confirmação/recusa (EmailService só tem convite/reset).
- **Sem FINANCEIRO** no RBAC do confirmar-sinal (decisão §5).
- **Backoffice**: não há página de reservas, nem `confirmarSinal()` no service, nem fila de validação. Tipos do front **divergem** do backend (`ReservaPrioridade`, `ReservaStatus`) — corrigir.

---

## 3. Modelo de dados (mudanças no backend)

Migração Flyway nova (**V037**) — e refletir em `reset-ambiente-dev.sh` (regra do projeto).

**`reserva` — novos campos (pagamento):**
| Campo | Tipo | Uso |
|---|---|---|
| `pagamento_tipo` | enum/varchar | `SINAL` (parcial) · `TOTAL` (integral). Balcão é sempre `TOTAL` |
| `pagamento_status` | enum/varchar | `AGUARDANDO` · `EM_ANALISE` · `CONFIRMADO` · `RECUSADO` |
| `pagamento_valor_informado` | numeric(10,2) | valor que o cliente declarou ter pago |
| `pagamento_validado_por` | uuid (usuario) | quem confirmou/recusou |
| `pagamento_validado_em` | timestamptz | quando |
| `pagamento_motivo_recusa` | text | obrigatório na recusa |
| `valor_total` | numeric(10,2) | valor cheio da reserva (para calcular saldo restante quando `SINAL`) |

> `sinalPago`/`valorSinal` permanecem para retrocompatibilidade: `sinalPago` **derivado** de `pagamento_status = CONFIRMADO`. Quando `pagamento_tipo = SINAL`, o **saldo restante** (`valor_total − valor pago`) é quitado no check-in; quando `TOTAL`, saldo = 0. Nomes `sinal_*` no código legado podem ser mantidos, mas o conceito é **pagamento**.

**Comprovante — tabela dedicada `reserva_comprovante`** (não poluir `Foto`, que é de locação):
| Campo | Tipo |
|---|---|
| `id` | uuid |
| `tenant_id` | uuid (RLS) |
| `reserva_id` | uuid (FK) |
| `s3_key`, `url` | text |
| `hash_sha256` | text (integridade) |
| `tipo` | enum (`PIX`) |
| `enviado_em` | timestamptz |
| `ativo` | boolean (reenvios: o último ativo é o vigente; histórico preservado) |

Reaproveita a mecânica de **presigned URL** do `FotoService` (generalizar para aceitar `reserva_id`).

---

## 4. Máquina de estados do sinal

```
        cliente envia              staff CONFIRMA
AGUARDANDO ─comprovante─► EM_ANALISE ──────────────► CONFIRMADO
   ▲                          │                         │ (prioridade BAIXA→ALTA, sinalPago=true)
   │                          │ staff RECUSA (+motivo)  │
   └──────────────────────────┘◄── volta p/ AGUARDANDO ◄┘ (estorno: fora do v1)
                              (reenvio do cliente)
```

- **EM_ANALISE → CONFIRMADO**: reusa `confirmar-sinal` (BAIXA→ALTA, capacidade) + grava `sinal_validado_por/em`, emite evento, notifica.
- **EM_ANALISE → RECUSADO**: novo endpoint; **mantém** `BAIXA`/`PENDENTE`, grava motivo, notifica o cliente a reenviar. Não cancela a reserva.
- Acoplamento com o portal (portal §5.6): `GARANTIDA = CONFIRMADO + email_verified`. Se o cliente pagou mas **não verificou e-mail em 24h**, a pré-reserva **expira** → vira **pendência de estorno** (ver §10, gap).

---

## 5. RBAC — quem valida

**Decisão (fechada 2026-06-15):** **confirmar/recusar** liberado para **ADMIN_TENANT, GERENTE, OPERADOR e FINANCEIRO** (sem segregação de função). Balcão valida rápido na operação; financeiro tem visão de conciliação. Hoje o `@PreAuthorize` do `confirmar-sinal` **não** inclui FINANCEIRO — **mudança a fazer**: `hasAnyRole('ADMIN_TENANT','GERENTE','OPERADOR','FINANCEIRO')` (e o novo `recusar-sinal` idem).

- **Ver fila/auditoria:** acima + (opcional) somente leitura para VENDEDOR da própria carteira.

---

## 6. API

### Existente (ajustar)
- `POST /v1/tenants/{tid}/reservas/{id}/confirmar-sinal` — **acrescentar**: setar `sinal_status=CONFIRMADO`, `sinal_validado_por/em`; emitir `SinalConfirmadoEvent`; disparar e-mail; incluir **FINANCEIRO** no RBAC.

### Novos
- `POST /v1/tenants/{tid}/reservas/{id}/recusar-sinal`
  Body `{ motivo: string (obrigatório) }` → `sinal_status=RECUSADO`, mantém BAIXA, emite `SinalRecusadoEvent`, notifica cliente. RBAC igual ao confirmar.
- `GET /v1/tenants/{tid}/reservas?sinalStatus=EM_ANALISE` — **filtro** novo na listagem (a lista já filtra por `status`); base da fila.
- `GET /v1/tenants/{tid}/reservas/{id}/comprovante-sinal` — presigned **download** do comprovante vigente (preview do staff).
- (lado cliente, portal §9.5) `POST .../reservas/{id}/comprovante-sinal` — upload presigned + cria `reserva_comprovante` e seta `EM_ANALISE`.

### Auditoria (novos eventos)
- `SinalConfirmadoEvent { tenantId, reservaId, operadorId, valorSinal, sinalPagoEm }`
- `SinalRecusadoEvent { tenantId, reservaId, operadorId, motivo }`
- Listeners em `AuditEventListener` (ações `SINAL_CONFIRMADO` / `SINAL_RECUSADO`, entidade `RESERVA`, com `dadosAnteriores/Novos`, `traceId`, `ip`, `userAgent`).

### Notificações (gap)
- `EmailService.sendSinalConfirmado(...)` e `sendSinalRecusado(..., motivo)` (hoje inexistentes). Recomendado **event-driven**: um `EmailNotificationListener` ouve os eventos acima (desacopla do service). Canais v1: **e-mail** (portal §9.9; WhatsApp fora do v1).

---

## 7. UI do backoffice

### 7.1 Fila "Sinais a validar" (inbox)
**Decisão (fechada):** a fila vive em **ambos** os lugares — página dedicada no **Financeiro** (`app/(dashboard)/dashboard/financeiro/sinais/page.tsx`, com contador no menu) **e** um **atalho/filtro** em Reservas (`sinalStatus=EM_ANALISE`). Mesma fonte de dados, dois pontos de acesso.

```
┌ Sinais a validar  (3)                         [ Atualizar ] ┐
├─────────────────────────────────────────────────────────────┤
│ Reserva   Cliente          Modelo / Data        Esperado  Informado  Compr.  Enviado    Ação      │
│ RSV-2038  Marina A. ✓email  Spark · 28/jun 14h   R$ 72,00  R$ 72,00   [img]   há 8 min  [Revisar] │
│ RSV-2041  João P.  ⚠e-mail  GTX300 · 22/jun 10h  R$270,00  R$200,00   [pdf]   há 1 h    [Revisar] │
│ RSV-2049  Ana M.   ✓email   VX · 30/jun 09h      R$ 90,00  R$ 90,00   [img]   há 2 h    [Revisar] │
└─────────────────────────────────────────────────────────────┘
```
- Filtros: loja/período, **valor divergente**, **e-mail não verificado**.
- Sinaliza divergência (informado ≠ esperado) e status de e-mail (afeta a garantia, não a validação do sinal).

### 7.2 Drawer/Dialog de revisão
```
┌ Revisar sinal — RSV-2041 ───────────────────────────────────┐
│  [ Pré-visualização do comprovante ]   Reserva                │
│  ┌───────────────────────┐            Cliente: João Pereira   │
│  │  comprovante (img/pdf) │            CPF: 123... · ⚠ e-mail  │
│  │  zoom / abrir          │            Modelo: GTX 300         │
│  └───────────────────────┘            Data: 22/jun 10:00 · 2h │
│                                        Valor esperado: R$270,00│
│  Valor recebido: [ R$ 200,00 ]  ← prefill do informado        │
│  ⚠ Valor diverge do esperado (-R$70,00)                       │
│  ⚠ Capacidade: 2/3 garantidas neste horário                  │
│                                                              │
│  [ Recusar ▾ (motivo) ]                 [ Confirmar sinal ]   │
└─────────────────────────────────────────────────────────────┘
```
- **Preview** do comprovante (imagem com zoom; PDF em viewer/abrir em nova aba) via presigned download. Comprovante é **opcional**: se não houver anexo, exibir "sem comprovante — confirme apenas se verificou o pagamento" (a confirmação continua permitida e auditada).
- **Valor recebido** editável (prefill = informado); alerta se ≠ esperado.
- **Confirmar** → `confirmar-sinal { valorSinal }`; trata erro de **capacidade esgotada** com mensagem clara.
- **Recusar** → exige **motivo** (select: "comprovante ilegível", "valor insuficiente", "não identificado", "outro" + texto) → `recusar-sinal`.
- Pós-ação: `toast` (sonner) + `queryClient.invalidateQueries(['reservas','sinais'])` + fecha drawer + decrementa badge.

### 7.3 Na reserva (detalhe)
- Badge de **status do sinal** (Aguardando/Em análise/Confirmado/Recusado) + **trilha**: quem validou, quando, valor, motivo (se recusado), link p/ comprovante. Reenvio do cliente reabre como `EM_ANALISE`.

### 7.4 Camada de dados (front)
- `reservas.ts`: novos `confirmarSinal(id, { valorSinal })`, `recusarSinal(id, { motivo })`, `listSinaisPendentes()`, `getComprovanteUrl(id)`.
- **Corrigir tipos** em `lib/api/types.ts`: `ReservaPrioridade = 'ALTA'|'BAIXA'`, `ReservaStatus` inclui `FINALIZADA`/`EXPIRADA` (remover `CONCLUIDA`/`NORMAL`/`URGENTE`), adicionar `sinalStatus`, `sinalPagoEm`, `expiraEm`.
- Reuso: `dialog`/`alert-dialog`, `badge`, `table`, `select`, `textarea`, `use-toast`, `apiClient`.

---

## 8. Idempotência & concorrência
- Confirmar sobre `CONFIRMADO` → **409** (já guardado por `podeConfirmarSinal()`); UI desabilita ação se status mudou (refetch otimista).
- Dois operadores na mesma reserva → última ação perde com 409/optimistic-lock; mostrar "reserva já atualizada por outro usuário, recarregue".
- Recusar sobre `CONFIRMADO` → permitido? **Não** no v1 (seria estorno). Só `EM_ANALISE → RECUSADO`.

---

## 9. Notificações ao cliente
- **Confirmado:** "Seu sinal de R$X foi confirmado — reserva garantida (se e-mail verificado)." Se e-mail ainda não verificado, reforçar o CTA de verificação.
- **Recusado:** "Não conseguimos validar seu comprovante: {motivo}. Reenvie pelo portal." → link para reenvio.

---

## 10. Edge cases & regras
- **Valor divergente (decisão fechada):** **sem auto-aceite** no v1 — qualquer divergência exige o staff **confirmar explicitamente o valor recebido** (auditado) ou recusar. Tolerância automática por tenant fica para o futuro (não no v1).
- **Comprovante (decisão fechada):** **opcional** para confirmar — é recomendável anexar, mas o staff pode confirmar sem anexo (ex.: viu o PIX cair na conta). A ação fica auditada de todo modo.
- **Capacidade esgotada** no momento de confirmar (`:663`): a confirmação **falha** com mensagem clara; oferecer "manter em análise / lista de espera".
- **Comprovante ilegível/suspeito:** recusar com motivo; histórico de reenvios preservado (`reserva_comprovante.ativo`).
- **Pagou mas não verificou e-mail (expira 24h):** vira **pendência de estorno**. **Gap:** não há mecanismo de estorno/refund hoje — registrar como tarefa financeira manual no v1; modelar `estorno` no futuro.
- **Reenvio após recusa:** novo comprovante → `EM_ANALISE`, reentra na fila.

---

## 11. Faseamento
- **F1 (mínimo):** filtro `sinalStatus`, página de reservas + fila, `confirmar-sinal` no front (já existe no back), preview de comprovante, **recusar-sinal** (novo), evento+auditoria. Sem e-mail.
- **F2:** notificações por e-mail (event-driven), badge/contador no menu, correção de tipos, histórico de reenvios.
- **F3:** estorno/refund, tolerância de valor por tenant, segregação de função (RBAC fina), relatórios de conciliação (FINANCEIRO).

---

## 12. Checklist de implementação
**Backend**
1. Migração V037: campos `sinal_*` na `reserva` + tabela `reserva_comprovante` (+ `reset-ambiente-dev.sh`).
2. `recusar-sinal` (endpoint+serviço) e ajuste do `confirmar-sinal` (status, validadoPor/em, RBAC +FINANCEIRO).
3. Generalizar presigned upload p/ `reserva_comprovante` (+ tipo); endpoint de download p/ staff.
4. Eventos `SinalConfirmadoEvent`/`SinalRecusadoEvent` + listeners de auditoria.
5. `EmailService.sendSinalConfirmado/Recusado` (+ listener de notificação).
6. Filtro `?sinalStatus=` na listagem.

**Backoffice**
7. `reservas.ts`: `confirmarSinal`, `recusarSinal`, `listSinaisPendentes`, `getComprovanteUrl`.
8. Corrigir `types.ts` (enums divergentes) + novos campos.
9. Página/fila `dashboard/financeiro/sinais` + drawer de revisão + badge no menu.
10. Status do sinal + trilha na tela de reserva; toasts + invalidação de cache.

---

## 13. Decisões fechadas (2026-06-15)
1. **RBAC:** confirmar/recusar para **ADMIN_TENANT, GERENTE, OPERADOR, FINANCEIRO** (sem segregação). Incluir FINANCEIRO no `@PreAuthorize` (§5).
2. **Comprovante:** **opcional** para confirmar (recomendável anexar; staff pode confirmar sem, auditado) (§7.2/§10).
3. **Valor divergente:** **sem auto-aceite** — staff confirma o valor recebido explicitamente ou recusa; tolerância automática não entra no v1 (§10).
4. **Estorno:** **registro manual** no v1 (tarefa financeira); modelagem real na F3 (§10/§11).
5. **Local da fila:** **ambos** — página dedicada no Financeiro (com contador) + atalho/filtro em Reservas (§7.1).
