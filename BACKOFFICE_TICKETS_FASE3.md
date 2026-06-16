# Tickets — Backoffice Balcão · Fase 3 (Frontend / UI)

> Deriva de [`BACKOFFICE_IMPLEMENTATION_PLAN.md`](./BACKOFFICE_IMPLEMENTATION_PLAN.md). Continua [`BACKOFFICE_TICKETS_FASE2.md`](./BACKOFFICE_TICKETS_FASE2.md) (backend).
> **App:** `frontend/jetski-backoffice` (Next.js App Router, shadcn/ui, TanStack Query, NextAuth+Keycloak).
> **Referência de UI/fluxo:** protótipo `frontend/portal-cliente` (`/staff/embarque`, `/staff/documento`, `components/SignaturePad.tsx`, `components/AddressForm.tsx`).
> **Padrões do backoffice (reusar):** página em `app/(dashboard)/dashboard/{feature}/page.tsx` · service em `lib/api/services/*.ts` (usa `getTenantId()` + `apiClient`) · tipos em `lib/api/types.ts` · estado servidor via **TanStack Query** (`useQuery`/`useMutation` + `invalidateQueries`) · `tenant-store` (`lib/store/tenant-store.ts`) · toasts **sonner** · sidebar em `components/layout/app-sidebar.tsx`.
> **Importante:** diferente do protótipo (estado mock/zustand), aqui **cada passo do wizard chama a API real** (mutations), encadeando o `reservaId`/`clienteId` criados.

## Dependências / ordem
```
F3.1 componentes base (port) ─┐
F3.2 types.ts ────────────────┤
F3.3 services ────────────────┼─► F3.4 wizard 1-3 ─► F3.5 wizard 4-6 ─► F3.6 PDF/claim ─► F3.7 e2e
(Fase 2 backend pronto) ───────┘
```

---

## F3.1 — Portar componentes base do protótipo
**Tipo:** Task · **Tamanho:** M · **Área:** Frontend

**Objetivo:** trazer os componentes prontos do protótipo, adaptados ao design system do backoffice (shadcn).

**Escopo (criar em `components/`):**
- **SignaturePad** — de `portal-cliente/components/SignaturePad.tsx` (canvas pointer events, mouse/touch, limpar, retorna `hasSignature` + **dataURL** da imagem para upload).
- **AddressForm** (CEP/ViaCEP) — de `portal-cliente/components/AddressForm.tsx` (autopreenche logradouro/bairro/cidade/UF; fallback manual). Adaptar aos `Input`/`Label` do shadcn.
- **Stepper** — extrair o stepper inline do wizard do protótipo para um componente reutilizável.
- **FileUpload** — wrapper do fluxo **presigned URL** (pega URL no backend → PUT no storage → confirma); preview + estados de loading/erro. (Não existe no backoffice.)

**Aceite:** os 4 componentes renderizam isolados (storybook/rota de teste ou na página); CEP autofill funciona; assinatura exporta PNG; upload sobe via presigned.

**Nota:** ajustar imports de `Button/Input/Label` para `components/ui/*` do backoffice (o protótipo tem `ui.tsx` próprio).

---

## F3.2 — Corrigir/expandir `lib/api/types.ts`
**Tipo:** Chore · **Tamanho:** S · **Área:** Frontend

**Objetivo:** alinhar os tipos com o backend (hoje divergem) e cobrir o domínio novo.

**Escopo:**
- Corrigir: `ReservaPrioridade = 'ALTA' | 'BAIXA'` (hoje `NORMAL|ALTA|URGENTE`); `ReservaStatus` incluir `FINALIZADA`/`EXPIRADA` (remover `CONCLUIDA`).
- Adicionar à `Reserva`: `pagamentoTipo` (`SINAL|TOTAL`), `pagamentoStatus`, `pagamentoValorInformado`, `valorTotal`, `documentoEmitidoEm`.
- Novos tipos: `ClientePreContaRequest`, `Habilitacao`/`GruInfo`, `AceiteRequest`, `DocumentoEmitido`, `ClaimRequest`.

**Aceite:** `tsc` limpo; tipos batem com os DTOs do backend (Fase 1/2).

---

## F3.3 — Services de API (balcão)
**Tipo:** Task · **Tamanho:** M · **Área:** Frontend · **Dep:** F3.2

**Objetivo:** camada de chamada para os endpoints da Fase 2.

**Escopo (em `lib/api/services/`):**
- `reservas.ts` (estender): `confirmarPagamento(id,{tipo,valorPago})`, `recusarPagamento(id,{motivo})`, `emitirDocumentos(id)`, `getComprovanteUrl(id)`.
- `clientes.ts` (estender): `criarPreConta(req)`, `buscarPorCpf(cpf)`.
- `habilitacao.ts` (novo): registrar CHA / registrar EMA+GRU (marcar paga).
- `aceite.ts` (novo): gravar aceite + assinatura (presigned p/ a imagem).
- `claim.ts` (novo): gerar/reenviar claim.
- Reusar `getTenantId()` + `apiClient` (token + `X-Tenant-Id`).

**Aceite:** cada método tipado, com tratamento de erro padrão; testes leves de unidade (mock do client) opcionais.

---

## F3.4 — Página `/dashboard/balcao` — wizard (passos 1–3)
**Tipo:** Task · **Tamanho:** M · **Área:** Frontend · **Dep:** F3.1, F3.3

**Objetivo:** o shell do wizard + os 3 primeiros passos, **ligados à API**.

**Escopo:**
- Rota `app/(dashboard)/dashboard/balcao/page.tsx` + item na sidebar (`app-sidebar.tsx`, grupo de operações).
- Shell com **Stepper** (6 passos) e estado do atendimento (clienteId, reservaId).
- **Passo 1 — Cliente:** busca CPF (`buscarPorCpf`) → existe usa / não cria **pré-conta** (`criarPreConta`). Tratar caso "CPF com identidade ativa" (mensagem de verificação).
- **Passo 2 — Documentos:** **FileUpload** (RG/CPF/foto) + **comprovante de residência** com toggle "tem"/"não tem → **AddressForm (CEP)**" (Declaração 1-C). Triagem de habilitação (tem CHA?).
- **Passo 3 — Aluguel & pagamento:** seleciona modelo/duração (reusa catálogo) → **cria reserva** (`POST /reservas`) → registra **pagamento total** (`confirmarPagamento` tipo=TOTAL, origem balcão).
- TanStack Query (mutations + invalidação) + toasts (sonner) + estados de loading/erro.

**Aceite:** percorre passos 1–3 contra o backend real, criando cliente+reserva+pagamento; persistência de estado ao avançar/voltar.

---

## F3.5 — Wizard (passos 4–6) + emissão
**Tipo:** Task · **Tamanho:** L · **Área:** Frontend · **Dep:** F3.4

**Objetivo:** habilitação/GRU, termos com assinatura, e a emissão dos documentos.

**Escopo:**
- **Passo 4 — Habilitação:** CHA (upload) **ou** EMA (videoaula assistida + anexos + **GRU manual**: nº/valor/marcar paga) via `habilitacao` service. Nota: demonstração 5-B é no check-in (à parte).
- **Passo 5 — Termos:** render do termo + **SignaturePad** → grava **aceite + imagem** (`aceite` service, presigned).
- **Passo 6 — Emissão:** botão **Emitir** → `emitirDocumentos(id)`; mostra **PDF [Abrir/Baixar]**, status **"✓ Enviado à Marinha"** / **"✓ E-mail ao cliente (+ claim)"** e a **GRU [Imprimir]**. (UI espelha o protótipo `/staff/embarque` passo Emissão.)
- Bloqueios: emitir só com habilitação resolvida + termos assinados.

**Aceite:** fluxo completo no backoffice gera documentos reais (download abre o PDF do storage); estados de envio refletidos; auditado no backend.

---

## F3.6 — Visualização do PDF + GRU + status do claim
**Tipo:** Task · **Tamanho:** S–M · **Área:** Frontend · **Dep:** F3.5

**Objetivo:** acesso pós-emissão aos artefatos e ao status da conta do cliente.

**Escopo:**
- Visualizar/baixar o **PDF emitido** (presigned download) e a **GRU**.
- Na **ficha do cliente** (ou tela da reserva): `status_conta` (PRE_CONTA/CONVIDADA/ATIVA), botão **reenviar claim**.
- (Opcional) embed do PDF via `<iframe>`/nova aba.

**Aceite:** operador reabre uma reserva emitida, baixa PDF/GRU e reenvia o claim.

---

## F3.7 — e2e Playwright (fluxo de balcão)
**Tipo:** Task · **Tamanho:** M · **Área:** Frontend/QA · **Dep:** F3.5

**Objetivo:** cobertura fim-a-fim da UI contra o backend.

**Escopo (em `e2e/tests/`, fixture `authenticatedPage` de `e2e/fixtures/auth.ts`):**
- Cenário: abrir `/dashboard/balcao` → criar pré-conta → documentos (+CEP) → aluguel/pagamento total → habilitação/GRU → assinatura (desenhar no canvas) → emitir → ver PDF/GRU.
- Mockar/estabilizar envios externos conforme padrão; asserts nos estados visíveis e no download.

**Aceite:** spec verde no CI; segue o padrão dos specs existentes (`e2e/tests/cadastros.spec.ts`).

---

## Definition of Done (comum)
- [ ] TanStack Query (loading/error/empty) + toasts (sonner) em todas as ações.
- [ ] Sem `any` solto; `tsc`/lint limpos.
- [ ] Responsivo (operador pode usar tablet no balcão).
- [ ] Reuso dos componentes da F3.1 (sem duplicar SignaturePad/AddressForm/Stepper/Upload).
- [ ] e2e cobrindo o caminho feliz (F3.7).
- [ ] PR pequeno e revisável, ligado ao ticket.

## Fora de escopo (vai p/ Fase 5 — hardening)
- Gating fino de UI por papel (FINANCEIRO/GERENTE), i18n EN dos anexos (estrangeiros), DataTable genérica reutilizável, acessibilidade avançada.

## Marco ao fim da Fase 3
**Demo para stakeholders:** um operador conclui um atendimento de balcão **real** no backoffice (contra o backend da Fase 2), gera e baixa o PDF consolidado, dispara os envios (Marinha + cliente) e o cliente recebe o link de ativação. Backoffice balcão **pronto para piloto**.
