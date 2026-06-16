# Plano de Implementação — Backoffice (prioridade: Atendimento de Balcão)

> **Status:** Plano aprovável · **Data:** 2026-06-15
> **Specs base:** [`BACKOFFICE_ATENDIMENTO_ASSISTIDO_SPEC.md`](./BACKOFFICE_ATENDIMENTO_ASSISTIDO_SPEC.md) · [`BACKOFFICE_VALIDACAO_SINAL_SPEC.md`](./BACKOFFICE_VALIDACAO_SINAL_SPEC.md) · [`PORTAL_CLIENTE_SPEC.md`](./PORTAL_CLIENTE_SPEC.md)
> **Protótipo de referência (UI/fluxos):** `frontend/portal-cliente` (`/staff/*`, `/staff/documento`)

## Decisões que guiam o plano (2026-06-15)
1. **Sequência:** **Balcão primeiro** (autônomo, não depende do portal). Validação de pagamento + portal vêm depois (par).
2. **PDF:** **OpenPDF** (já no `pom.xml`), layout montado programaticamente. O HTML do protótipo (`/staff/documento`) é a **referência visual** fiel aos anexos.
3. **Envio à Marinha (v1):** **e-mail com o PDF anexado** para um endereço **configurável por tenant** (Capitania/contato). Sem integração/API externa no v1.
4. **GRU (v1):** **assistida/manual** (operador informa nº + valor + marca paga). Sem geração/integração automática.
5. **Pagamento no balcão:** sempre **valor total**, confirmado direto (sem fila). Sinal só existe em reserva remota (portal).

---

## 1. Veredito de prontidão

**Pronto para implementar.** Fundações críticas já existem no backend; o que falta é incremental e de baixo risco. Maior esforço novo: **geração de PDF (OpenPDF, manual)** e a **emissão/orquestração** (PDF→storage→e-mail Marinha→claim). A identidade do cliente (pré-conta/ativação) **reaproveita** a máquina de convite/Keycloak existente.

### Ativos existentes (reuso) — grounded
| Necessidade | Já existe | Local |
|---|---|---|
| Provisionar usuário no Keycloak | `KeycloakAdminService.createUserWithPassword()` | `shared/internal/keycloak/` |
| Token/magic-link + TTL + status | `Convite`, `MagicLinkTokenService`, `UserInvitationService`, `AccountActivationController` | `usuarios/` |
| Vincular sub↔usuário | `IdentityProviderMappingService.linkProvider()` (`usuario_identity_provider`) | `usuarios/api/` |
| Eventos + auditoria | `ApplicationEventPublisher` + `@EventListener @Async` → `AuditEventListener` | `*/domain/event/`, `audit/internal/` |
| Storage S3/MinIO | `StorageService` (+ Local/MinIO) — presigned | `shared/storage/` |
| E-mail | `EmailService` (Spring Mail, HTML inline) | `shared/email/` |
| PDF/planilha | OpenPDF 1.3.35 + POI 5.2.5 (libs no pom, sem código) | `pom.xml` |
| Pagamento da reserva | `ReservaService.confirmarSinal()` (`:634`), `ReservaController` (`:327`) | `locacoes/` |
| Autorização | `@PreAuthorize` + OPA `ABACAuthorizationInterceptor` + `ActionExtractor` | `shared/authorization/` |
| Teste integração | `AbstractIntegrationTest` (Testcontainers) + MockMvc `jwt()` | `backend/src/test/.../integration/` |
| Padrão de módulo (front) | `clientes` (page + service + types + TanStack Query) | `frontend/jetski-backoffice` |

### Lacunas a construir (no plano)
- `StorageService.putObject(key, bytes, contentType)` — salvar PDF gerado no servidor (hoje só presigned).
- `EmailService` com **anexo** (MimeMessage) — enviar o PDF.
- **Página de Reservas** no backoffice (não existe; service existe).
- UI faltante no backoffice: **DataTable/Stepper/SignaturePad/Upload/AddressForm** — **portar do protótipo** (já prontos lá).
- Cada endpoint novo: **política OPA + `ActionExtractor`** (não só `@PreAuthorize`).
- Toda tabela/coluna nova: **Flyway (próx. V0xx) + `reset-ambiente-dev.sh`**.

---

## 2. Fases (balcão primeiro)

Premissa de estimativa: ~1 dev backend + ~1 dev frontend. Tamanhos: S (≤1d), M (2–3d), L (~1sem), XL (>1sem).

### Fase 0 — Spikes & preparação · ~2–3 dias
- **F0.1 (M)** Spike OpenPDF: serviço `DocumentoPdfService` que monta **1 anexo** (ex.: Termo de Responsabilidade) com OpenPDF, fiel ao layout do protótipo, gera bytes + **hash SHA-256**. Valida fonte/quebras/assinatura (imagem).
- **F0.2 (S)** Spike `StorageService.putObject` em MinIO (salvar os bytes do PDF + obter download).
- **F0.3 (S)** Spike `EmailService` com anexo (MimeMessage) enviando o PDF a um e-mail de teste.
- **DoD:** PDF de 1 anexo gerado, salvo no storage e enviado por e-mail num teste de integração.

### Fase 1 — Fundações de backend (compartilhadas) · ~1–1.5 sem
- **F1.1 (M)** Migração Flyway `V0xx` + `reset-ambiente-dev.sh`:
  - `reserva`: `pagamento_tipo`, `pagamento_status`, `pagamento_valor_informado`, `pagamento_validado_por`, `pagamento_validado_em`, `pagamento_motivo_recusa`, `valor_total`, `documento_emitido_em`.
  - `reserva_comprovante` (id, tenant_id, reserva_id, s3_key, url, hash, tipo, enviado_em, ativo) + **RLS**.
  - `cliente`: `origem` (PORTAL|BALCAO), `status_conta` (PRE_CONTA|CONVIDADA|ATIVA|SEM_LOGIN).
  - `cliente_identity_provider` (espelha `usuario_identity_provider`) + **RLS**.
  - `documento_emitido` (id, tenant_id, reserva_id, s3_key, hash, destinos_json, emitido_em) + **RLS**.
  - (claim) reaproveitar `convite` ou criar `claim_token` cliente-scoped — decidir em F2.6.
- **F1.2 (S)** `StorageService.putObject(...)` + impls Local/MinIO.
- **F1.3 (M)** `EmailService`: suporte a **anexo** + métodos `sendPagamentoConfirmado/Recusado`, `sendClaimCliente`, `sendDocumentos(pdf)` e `sendDocumentosMarinha(pdf, destino)`. Implementar em Smtp + Dev.
- **F1.4 (M)** Eventos + listeners de auditoria: `PagamentoConfirmadoEvent`, `PagamentoRecusadoEvent`, `PreContaCriadaEvent`, `ClaimEnviadoEvent`, `ContaAtivadaEvent`, `DocumentosEmitidosEvent`.
- **F1.5 (M)** OPA + `ActionExtractor`: registrar novas actions (`confirmar-pagamento`, `recusar-pagamento`, `emitir-documentos`, `criar-pre-conta`, `claim`); incluir **FINANCEIRO** onde a spec de sinal define. Atualizar políticas rego.
- **DoD:** migrações sobem limpas (Flyway + reset script), RLS valida isolamento por tenant em teste, eventos novos auditados.

### Fase 2 — Balcão (backend) · ~1.5–2 sem
- **F2.1 (M)** Generalizar pagamento: `ReservaService.confirmarSinal` → **confirmar pagamento** (aceita `tipo`/`valorPago`); novo `recusarPagamento(motivo)`. `confirmar-sinal` mantém compat. Atualizar `@PreAuthorize` (+FINANCEIRO) + OPA. Balcão = **confirma direto** (origem=BALCAO).
- **F2.2 (M)** Pré-conta: `ClienteController` aceita criação `origem=BALCAO` + `status_conta=PRE_CONTA`; **dedupe por CPF** (match → exige verificação/OTP, sem merge automático).
- **F2.3 (M)** Habilitação/CHA-MTA-E + GRU (manual): persistir CHA (categoria/nº/validade) **ou** dados EMA + **GRU manual** (nº/valor/pago). Anexos como dados estruturados.
- **F2.4 (M)** Assinatura no balcão: endpoint de **aceite com evidências** (operador, timestamp, IP/dispositivo, **imagem da assinatura**, hash, `origem=BALCAO`). Armazenar imagem via storage.
- **F2.5 (L)** `DocumentoPdfService` (OpenPDF): montar o **PDF consolidado** completo — Anexos **1-C, 5-C, 5-B-1, 5-B-2** + **Termo da loja**, preenchidos, com assinaturas e rodapés. Usar `/staff/documento` como espelho de layout.
- **F2.6 (L)** `POST /reservas/{id}/emitir-documentos`: orquestra gerar PDF → `putObject` (storage) → registrar `documento_emitido` (hash) → **e-mail à Marinha** (endereço por tenant) → **e-mail ao cliente** (PDF + link de claim) → retornar URL de download + dados da GRU. Idempotente + evento de auditoria.
- **F2.7 (M)** Claim-token cliente: **reusar** `UserInvitationService`/`MagicLinkTokenService`/`AccountActivationController` adaptado à população **cliente** (cria usuário Keycloak com **role CLIENTE, sem `Membro`**; vincula `cliente_identity_provider`). Gerar/enviar/reenviar; endpoint público de validação (ativação). TTL **7 dias**.
- **F2.8 (M)** Config por tenant: e-mail da Marinha + dados PIX/loja (para os termos). Migration/seed + endpoint de leitura.
- **DoD:** via API, um atendimento completo gera o PDF correto, arquiva, envia à Marinha + cliente, e o cliente consegue ativar a conta por link. Testes de integração cobrindo o caminho feliz + dedupe + recusa.

### Fase 3 — Balcão (frontend backoffice) · ~1.5 sem
- **F3.1 (M)** Portar do protótipo p/ `components/`: **SignaturePad**, **AddressForm** (CEP/ViaCEP), **Stepper**, e o **documento** (visualização/impressão). Adaptar ao design system do backoffice (shadcn) e ao `apiClient`/`tenant-store`.
- **F3.2 (S)** Corrigir `lib/api/types.ts`: `ReservaPrioridade='ALTA'|'BAIXA'`, `ReservaStatus` (FINALIZADA/EXPIRADA), + campos `pagamento_*`, `valorTotal`.
- **F3.3 (M)** `services`: `reservas.ts` (confirmar/recusar pagamento, emitir-documentos, comprovante), `clientes.ts` (pré-conta, busca CPF), `claim.ts` (gerar/reenviar).
- **F3.4 (L)** Página `/dashboard/balcao` (wizard 6 passos: Cliente → Documentos+CEP → Aluguel/pagamento → Habilitação/GRU → Termos/assinatura → Emissão) + item na sidebar (`app-sidebar.tsx`).
- **F3.5 (S)** Tela/visualização do **PDF emitido** (download do storage) + GRU.
- **F3.6 (M)** e2e Playwright (fixture `authenticatedPage`): fluxo de balcão fim-a-fim (mockando envios externos).
- **DoD:** operador conclui um atendimento real no backoffice contra o backend, baixa o PDF e dispara os envios. Demo para stakeholders.

### Fase 4 — Portal + Validação de pagamento (par) · depois do balcão
- Portal: reserva cliente-scoped + **upload de comprovante** (alimenta a fila).
- Backoffice: **página de Reservas** + **fila "Pagamentos a validar"** (Financeiro + filtro em Reservas) + **dialog de revisão** (confirmar/recusar, divergência, capacidade). Backend já em F1/F2.
- (Detalhado em fase própria quando priorizado.)

### Fase 5 — Endurecimento · contínuo
- Gating de UI por papel (FINANCEIRO/GERENTE), LGPD (consentimento/retenção), observabilidade (traceId, cobertura de auditoria), **anexos em EN** (estrangeiros), revisão de segurança, e2e no CI.

---

## 3. Riscos & mitigações
| Risco | Mitigação |
|---|---|
| **OpenPDF manual** trabalhoso/fidelidade | Spike F0.1 cedo; usar `/staff/documento` como espelho pixel-a-pixel; isolar em `DocumentoPdfService` testável por anexo |
| **Envio à Marinha** (processo externo indefinido) | v1 = e-mail por tenant + arquivamento + download; abstrair `EnvioMarinha` p/ trocar por API depois |
| **OPA** bloquear rotas novas silenciosamente | Checklist: toda rota nova = política rego + `ActionExtractor` + teste de autorização |
| **Schema em 2 lugares** (Flyway + reset script) | DoD de cada migração inclui atualizar `reset-ambiente-dev.sh` e rodar o reset limpo |
| **Identidade cliente** (CLIENTE sem Membro) | Reusar máquina de convite; teste garantindo que cliente **não** recebe `Membro` nem acessa backoffice |
| **GRU manual** divergir do real | Encapsular; F3 do roadmap troca por geração automática quando houver |

---

## 4. O que "fica de pé" ao fim do Balcão (marco)
Uma loja consegue, **sem portal**: registrar um cliente walk-in (pré-conta), coletar documentos (com declaração de residência via CEP), cobrar o **valor total**, tratar **CHA/CHA-MTA-E + GRU**, colher **assinatura** com evidências, e **emitir o PDF consolidado** (arquivado, enviado à Marinha e ao cliente, com GRU de saída) — tudo auditado. O **check-in/embarque** segue no fluxo existente de `locacoes`.

## 5. Estimativa macro (balcão)
Fase 0 (~3d) + Fase 1 (~1–1.5sem) + Fase 2 (~1.5–2sem) + Fase 3 (~1.5sem) ≈ **5–6 semanas** com 1 backend + 1 frontend, incluindo testes. (Estimativa de planejamento, a refinar por ticket.)

## 6. Próximos passos imediatos
1. Aprovar este plano e abrir os tickets da **Fase 0 + Fase 1**.
2. Rodar o **spike OpenPDF (F0.1)** — destrava a maior incerteza.
3. Definir, com o cliente piloto (Jet Save), o **e-mail da Marinha** por tenant e os dados de loja para os termos.
