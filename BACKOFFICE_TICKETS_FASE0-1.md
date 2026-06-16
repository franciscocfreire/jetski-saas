# Tickets — Backoffice Balcão · Fase 0 (Spikes) e Fase 1 (Fundações)

> Deriva de [`BACKOFFICE_IMPLEMENTATION_PLAN.md`](./BACKOFFICE_IMPLEMENTATION_PLAN.md). Decisões aplicadas: balcão primeiro · PDF OpenPDF (manual) · envio à Marinha por e-mail por tenant · GRU manual.
> **Legenda:** Tipo (Spike/Task/Chore) · Tamanho (S ≤1d · M 2–3d · L ~1sem) · Área (DB/Backend/Infra/Sec).
> **Regras transversais (todo ticket de DB):** refletir em **Flyway** (próximo V livre em `backend/src/main/resources/db/migration`) **e** em **`reset-ambiente-dev.sh`**; habilitar **RLS** por tenant; rodar `./reset-ambiente-dev.sh` limpo como parte do DoD.
> **Regra transversal (todo endpoint novo):** além de `@PreAuthorize`, registrar **action no `ActionExtractor`** e **política OPA (rego)** — senão o `ABACAuthorizationInterceptor` bloqueia.

## Ordem sugerida / dependências
```
F0.1 ─┐
F0.2 ─┼─ (spikes, paralelos) ──► graduam em F1.D / F2.5
F0.3 ─┘
F1.A (migr pagamento/comprovante/doc) ─┐
F1.B (migr cliente identity) ──────────┼─► F2.* (balcão backend)
F1.C (config tenant) ──────────────────┘
F1.D (StorageService.putObject) ──► F2.6 (emitir-documentos)
F1.E (EmailService anexo) ─────────► F2.6
F1.F (eventos+auditoria) ──────────► F2.* 
F1.G (OPA + RBAC FINANCEIRO) ──────► F2.1
```

---

# FASE 0 — Spikes (de-risk, ~2–3 dias)

## F0.1 — Spike: gerar 1 anexo em PDF com OpenPDF
**Tipo:** Spike · **Tamanho:** M · **Área:** Backend

**Objetivo:** provar que conseguimos montar, com **OpenPDF** (já no `pom.xml`), um anexo fiel ao layout oficial, e obter bytes + hash.

**Escopo:**
- Criar esqueleto `DocumentoPdfService` (pode virar a classe real da F2.5) que gere **o Termo de Responsabilidade da loja** (1 página) preenchido com dados mock.
- Usar como **espelho visual** o protótipo `frontend/portal-cliente/app/staff/documento/page.tsx` (fonte serifada, títulos, cláusulas 1–9, linha de assinatura, rodapé).
- Inserir uma **imagem de assinatura** (PNG mock) no PDF.
- Calcular **SHA-256** dos bytes.

**Fora de escopo:** os demais anexos (1-C/5-C/5-B), storage, e-mail.

**Critérios de aceite:**
- Um teste gera o PDF em bytes, salva em `target/` e o hash é estável para a mesma entrada.
- Revisão visual confirma fidelidade razoável ao termo do protótipo.

**Notas técnicas:** OpenPDF 1.3.35 (`com.github.librepdf:openpdf`). Layout é manual (parágrafos/tabelas/`Chunk`/`Image`). Avaliar esforço por anexo para calibrar F2.5.

---

## F0.2 — Spike: `StorageService.putObject` (salvar bytes)
**Tipo:** Spike · **Tamanho:** S · **Área:** Backend/Infra

**Objetivo:** o backend precisa **salvar** um arquivo gerado (PDF). Hoje `StorageService` só faz presigned URL.

**Escopo:**
- Provar um `putObject(key, bytes, contentType)` contra **MinIO** (impl `MinIOStorageService`) e **Local** (`LocalFileStorageService`).
- Recuperar via `generatePresignedDownloadUrl` existente.

**Critérios de aceite:** bytes salvos sob `tenant_id/...` e baixáveis pela URL presigned, em teste de integração.

**Notas técnicas:** `shared/storage/StorageService.java` (+ impls). Graduará em **F1.D** (vira método oficial da interface).

---

## F0.3 — Spike: e-mail com anexo
**Tipo:** Spike · **Tamanho:** S · **Área:** Backend

**Objetivo:** enviar o PDF como **anexo** (hoje `EmailService` só manda HTML inline, sem anexo).

**Escopo:**
- Provar envio via `JavaMailSender` + `MimeMessageHelper(multipart=true)` com um PDF anexado (usar `DevEmailService`/Greenmail ou log em dev).

**Critérios de aceite:** e-mail com anexo gerado e capturado em teste (sem SMTP real).

**Notas técnicas:** `shared/email/SmtpEmailService.java`. Graduará em **F1.E**.

---

# FASE 1 — Fundações de backend (~1–1.5 semana)

## F1.A — Migração: modelo de pagamento + comprovante + documento emitido
**Tipo:** Task · **Tamanho:** M · **Área:** DB

**Objetivo:** generalizar "sinal" → **pagamento** (sinal/total) e suportar comprovante e documento emitido.

**Escopo (Flyway próx. V + `reset-ambiente-dev.sh`):**
- `reserva`: `pagamento_tipo` (SINAL|TOTAL), `pagamento_status` (AGUARDANDO|EM_ANALISE|CONFIRMADO|RECUSADO), `pagamento_valor_informado` numeric(10,2), `pagamento_validado_por` uuid, `pagamento_validado_em` timestamptz, `pagamento_motivo_recusa` text, `valor_total` numeric(10,2), `documento_emitido_em` timestamptz.
  - Backfill: `pagamento_status = CONFIRMADO` onde `sinal_pago=true`; `pagamento_tipo = SINAL` default; `valor_total` a partir do valor estimado.
- `reserva_comprovante` (id, tenant_id, reserva_id FK, s3_key, url, hash_sha256, tipo (PIX), enviado_em, ativo) **+ RLS**.
- `documento_emitido` (id, tenant_id, reserva_id FK, s3_key, hash_sha256, destinos_json, emitido_em) **+ RLS**.
- Índices `(tenant_id, reserva_id)`.

**Critérios de aceite:**
- Migração sobe no Flyway e via `reset-ambiente-dev.sh` limpo.
- RLS isola por tenant (teste: tenant A não lê comprovante de B).
- Entidades JPA + repositórios criados (`Reserva` atualizado; `ReservaComprovante`, `DocumentoEmitido`).

**Notas:** RLS espelhar padrão de `assinatura` (policies por SELECT/INSERT/UPDATE/DELETE com `current_setting('app.tenant_id')`). `sinalPago`/`valorSinal` permanecem para compat.

---

## F1.B — Migração: identidade do cliente (pré-conta + provider)
**Tipo:** Task · **Tamanho:** S–M · **Área:** DB

**Objetivo:** suportar pré-conta de balcão e vínculo de identidade do cliente.

**Escopo (Flyway + reset script):**
- `cliente`: `origem` (PORTAL|BALCAO, default PORTAL), `status_conta` (PRE_CONTA|CONVIDADA|ATIVA|SEM_LOGIN, default ATIVA p/ legados).
- `cliente_identity_provider` (id, tenant_id, cliente_id FK, provider, provider_id, vinculado_em) **+ RLS** — espelha `usuario_identity_provider`.
- Índice único `(provider, provider_id)`.

**Critérios de aceite:** migração limpa (Flyway+reset), RLS por tenant, entidade/repositório `ClienteIdentityProvider`.

**Notas:** referência: `usuario_identity_provider` + `IdentityProviderMappingService`.

---

## F1.C — Migração/seed: configuração por tenant (Marinha + loja)
**Tipo:** Task · **Tamanho:** S · **Área:** DB/Backend

**Objetivo:** guardar o **e-mail da Marinha** (destino do PDF) e dados de loja usados nos termos (razão social, CNPJ, chave PIX).

**Escopo:**
- Adicionar colunas/JSON ao `tenant` (ou tabela `tenant_config`): `marinha_email`, `pix_chave`, e reaproveitar razão social/CNPJ existentes.
- Endpoint **GET** de leitura da config (staff) + seed para o tenant ACME/Jet Save.

**Critérios de aceite:** config legível via API; seed presente no reset script.

**Decisão pendente (com Jet Save):** valor real de `marinha_email` por tenant.

---

## F1.D — `StorageService.putObject` (oficializar)
**Tipo:** Task · **Tamanho:** S · **Área:** Backend/Infra · **Depende:** F0.2

**Objetivo:** método oficial para salvar bytes gerados (PDF/assinatura).

**Escopo:**
- Adicionar `void putObject(String key, byte[] bytes, String contentType)` à interface `StorageService` e implementar em `LocalFileStorageService` e `MinIOStorageService`.
- Convenção de key: `tenant_id/reserva/{id}/documento.pdf`, `.../comprovante_{n}`, `.../assinatura.png`.

**Critérios de aceite:** teste de integração salva e baixa (via presigned) em ambas as impls.

---

## F1.E — `EmailService`: anexos + mensagens novas
**Tipo:** Task · **Tamanho:** M · **Área:** Backend · **Depende:** F0.3

**Objetivo:** enviar PDF por e-mail (Marinha e cliente) + notificações de pagamento.

**Escopo:**
- Suporte a **anexo** (MimeMessage/MimeMessageHelper) em `SmtpEmailService` (e log em `DevEmailService`).
- Métodos: `sendDocumentosMarinha(to, pdf, meta)`, `sendDocumentosCliente(to, pdf, claimLink)`, `sendPagamentoConfirmado(...)`, `sendPagamentoRecusado(..., motivo)`, `sendClaimCliente(to, claimLink)`.
- Templates HTML inline (padrão atual).

**Critérios de aceite:** testes cobrindo cada método (assunto/destinatário/anexo presente) sem SMTP real.

**Notas:** `shared/email/`. Manter idioma PT; EN fica para hardening.

---

## F1.F — Eventos de domínio + auditoria
**Tipo:** Task · **Tamanho:** M · **Área:** Backend

**Objetivo:** auditar as ações novas (padrão do projeto).

**Escopo:**
- Criar eventos (records) em `*/domain/event/`: `PagamentoConfirmadoEvent`, `PagamentoRecusadoEvent`, `PreContaCriadaEvent`, `ClaimEnviadoEvent`, `ContaAtivadaEvent`, `DocumentosEmitidosEvent` (com `destinos`).
- Publicar via `ApplicationEventPublisher` nos serviços (na F2 ao implementar as ações).
- Adicionar listeners em `audit/internal/AuditEventListener.java` (ação/entidade/dadosAnteriores/Novos/traceId/ip).

**Critérios de aceite:** ao disparar cada evento, registro correspondente aparece em `auditoria` (teste de integração com 1 evento representativo).

---

## F1.G — Autorização: OPA + RBAC (incluir FINANCEIRO)
**Tipo:** Task · **Tamanho:** M · **Área:** Sec/Backend

**Objetivo:** liberar as ações novas no modelo de autorização **duplo** sem bloqueio silencioso.

**Escopo:**
- Registrar actions no `ActionExtractor` (`shared/authorization/`): `confirmar-pagamento`, `recusar-pagamento`, `emitir-documentos`, `criar-pre-conta`, `claim-cliente`.
- Atualizar **políticas OPA (rego)** usadas pelo `OPAAuthorizationService` para essas actions/roles.
- **RBAC:** incluir **FINANCEIRO** em confirmar/recusar pagamento (`@PreAuthorize` + OPA), conforme `BACKOFFICE_VALIDACAO_SINAL_SPEC.md` §5.
- Garantir por construção que **cliente (CLIENTE) não acessa** rotas de backoffice.

**Critérios de aceite:**
- Testes de autorização: papel permitido passa; papel proibido recebe 403; rota nova não é bloqueada pelo interceptor OPA.
- FINANCEIRO confirma/recusa pagamento; OPERADOR/GERENTE/ADMIN também.

**Notas:** `ABACAuthorizationInterceptor.preHandle` chama OPA em **todas** as rotas; localizar o bundle rego e a action map.

---

## Definition of Done (vale para todos)
- [ ] Código + testes (unit/integration) verdes; cobertura do caminho novo.
- [ ] (DB) Flyway **e** `reset-ambiente-dev.sh` atualizados; `./reset-ambiente-dev.sh` roda limpo.
- [ ] (DB) RLS testada (isolamento por tenant).
- [ ] (endpoint) `@PreAuthorize` + `ActionExtractor` + política OPA + teste de autorização.
- [ ] Sem regressão na suíte existente (~747 testes).
- [ ] PR pequeno e revisável; descrição liga ao ticket.
