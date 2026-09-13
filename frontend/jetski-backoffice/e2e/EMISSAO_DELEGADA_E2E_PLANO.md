# Plano — E2E Playwright da emissão delegada (EAMA emissora × operadora)

> Status: **plano** (13/set/2026). Nada implementado ainda.
> Regra de negócio: `EMISSAO_DELEGADA_SPEC.md` §4.2 e decisão J. Correção do remetente e
> da assinatura do ofício: PR #9 — **este teste falha sem ele**, e é justamente isso que
> ele deve garantir.

## 1. O que o teste garante

Duas empresas criadas na hora, uma **EAMA emissora** e uma **operadora delegada**, passam
pelo fluxo real no navegador. Ao final, o teste prova:

| # | Regra | Onde se verifica |
|---|---|---|
| R1 | O vínculo é bilateral: a operadora convida, a EAMA aceita o termo | UI das duas empresas |
| R2 | No balcão da operadora, o instrutor listado é **só** o da EAMA | dropdown da emissão |
| R3 | O crédito é debitado **da operadora** | API de saldo |
| R4 | O ofício vai para o `marinha_email` **da EAMA** | Mailpit: destinatário |
| R5 | O ofício é **remetido pela EAMA**: SMTP e From dela | Mailpit: cabeçalho From |
| R6 | Reply-To = `email_oficial` da EAMA | Mailpit: Reply-To |
| R7 | Assinatura = "EAMA {emissora} … operado por {operadora}", sem contatos da operadora | Mailpit: corpo HTML |
| R8 | A via do cliente sai **pela operadora** | Mailpit: From da via do cliente |
| R9 | A EAMA é notificada e vê a emissão no painel "Emissões em meu nome" | Mailpit + UI da EAMA |
| R10 | O reenvio pelo painel da EAMA também sai pela EAMA e não debita crédito | Mailpit + saldo |
| R11 | Kill switch: EAMA bloqueia e a operadora não emite; liberar volta a permitir | UI/API |
| R12 | Tudo acima com **RLS real**: o backend de dev/CI roda como `jetski_app`, sem bypass | implícito |

O R12 é o ganho que os testes de integração não dão. Eles rodam como superusuário, então a
leitura do SMTP e do cadastro da EAMA a partir da operadora (janela RLS do PR #9) só é
exercida de verdade aqui.

## 2. Desenho

- **Arquivo:** `e2e/tests/emissao-delegada.spec.ts`, em `test.describe.serial`. Os passos
  dependem uns dos outros, então retry refaz a jornada inteira.
- **Dois contextos de navegador**, um por empresa, cada um logado com o admin dela. Nada de
  trocar empresa pelo seletor da sidebar.
- **Dados isolados por execução.** Um sufixo único (`e2e-<timestamp>`) em slugs, e-mails e
  razões sociais. O teste não usa a `acme`: o admin dela tem 2FA e os dados são compartilhados.
- **Preparo pela API, jornada pela UI.** O que é pré-requisito (cadastro, habilitação, plano,
  créditos) vai por API na fixture. O que é a regra sob teste (vínculo, balcão, painel, kill
  switch) vai pela tela.
- **E-mails verificados pelo Mailpit.** As duas empresas recebem SMTP próprio apontando para
  `mailpit:1025`, cada uma com um `smtp_from` diferente. Assim o From de cada mensagem diz
  exatamente quem remeteu.

## 3. Preparo (fixture `e2e/fixtures/delegada.fixture.ts`)

| Passo | Como | Credencial |
|---|---|---|
| 1. Criar EAMA e operadora | `POST /v1/signup/tenant` → `GET /v1/test/last-email` → `POST /v1/signup/magic-activate` (padrão do `global-setup.ts`) | pública |
| 2. Aprovar as duas | `POST /v1/platform/tenants/{id}/approve` (o signup nasce `PENDENTE_APROVACAO`) | plataforma |
| 3. Mesma capitania (CPSP) nas duas; `eama_registro` na EAMA | `PUT /v1/tenants/{t}/config/emissora` | admin de cada empresa |
| 4. Habilitar a EAMA | `POST /v1/platform/tenants/{id}/habilitar-emissora` | plataforma |
| 5. Config geral das duas | `PUT /v1/tenants/{t}/config/geral`: `marinha_email`, `email_oficial`, `responsavel_nome`, `telefone`, `email_remetente` e SMTP (`mailpit`, 1025, usuário/senha fictícios, starttls off, `smtp_from` distinto). A operadora recebe **também** um `marinha_email` próprio, para provar que ele nunca é usado | admin de cada empresa |
| 6. Plano "só delegada" na operadora | Plano dedicado `E2E Só delegada` com `["EMISSAO_DELEGADA"]` → `POST /v1/platform/tenants/{id}/plano`. Ver decisão D2 | plataforma |
| 7. Créditos da operadora | `POST /v1/platform/creditos/{tenantId}` | plataforma |
| 8. Instrutores | Um na EAMA e um na operadora (este não pode aparecer no dropdown, R2): `POST /v1/tenants/{t}/instrutores` | admin de cada empresa |
| 9. Modelo na operadora | `POST` de modelo (fábrica de `fixtures/test-data.ts`) | admin da operadora |

**Tokens:**
- **Admin de cada empresa:** lido de `/api/auth/session` do contexto já logado (padrão de `fixtures/auth.ts`).
- **Plataforma:** ROPC no client `jetski-test` com o operador de plataforma de dev (`PLATFORM_ADMIN_EMAILS`, hoje `admin@acme.com`). O ROPC não passa pelo 2FA do navegador. Esse client é **só dev/CI** (revisão técnica ago/2026, P0).

## 4. Jornada

**A. Vínculo (UI)**
1. Operadora abre `/dashboard/emissao-delegada`, informa o slug da EAMA, escolhe "Sou a
   operadora (convido a EAMA)" e clica "Convidar". Espera o badge "Convite pendente".
2. EAMA abre a mesma página, clica "Ver termo e aceitar", marca o termo e confirma. Espera
   "Ativa" nas duas telas.
3. Mailpit: e-mail de convite para a EAMA e de aceite para a operadora. API: bônus de adesão
   da operadora estornado, créditos comprados preservados.

**B. Designação (UI, opcional)**
4. EAMA abre "Instrutores designados", marca o instrutor dela e salva.

**C. Balcão da operadora (UI)**
5. Cliente novo com e-mail único do run, aluguel, habilitação sem CHA (EMA).
6. **GRU sem o robô da Marinha.** Não clicar em "Gerar PIX", que emite uma GRU real. Ler o id
   da reserva na URL, chamar `PUT /v1/tenants/{t}/reservas/{id}/habilitacao` com
   `{via: EMA, gruPago: true, gruNumero: "E2E-<run>"}` e recarregar o passo.
7. Documentos, orientações (bloquear `*.youtube.com` e `*.ytimg.com` para cair na confirmação
   manual), termos (traços no canvas) e pagamento "depois". Tudo com os `data-testid` do balcão.
8. Emissão: o dropdown `balcao-emissao-instrutor` lista o instrutor da EAMA e **não** o da
   operadora (R2). Selecionar e clicar `balcao-emissao-emitir`.
9. Esperar "Enviado à Marinha" e "E-mail ao cliente". O envio é assíncrono por padrão, e a
   tela faz polling por até 60 s.
10. API: saldo da operadora caiu 1 (R3).

**D. E-mails (Mailpit API)**

| Mensagem | Destinatário | From | Reply-To | Corpo |
|---|---|---|---|---|
| Ofício à Capitania | `marinha_email` da EAMA, **nunca** o da operadora (R4) | `smtp_from` da EAMA (R5) | `email_oficial` da EAMA (R6) | "O EAMA <b>{EAMA}</b>", credenciamento da EAMA, "operado por <b>{operadora}</b>", sem e-mail/telefone da operadora (R7). Anexo "{Nome} {CPF}.pdf" |
| Via do cliente | e-mail do cliente | `smtp_from` da operadora (R8) | — | anexo presente |
| Notificação à EAMA | `email_remetente` da EAMA | `smtp_from` da operadora | — | "emitiu documentação NORMAM-212 em nome da sua EAMA" (R9) |

**E. Painel da EAMA (UI)**
11. "Emissões em meu nome" mostra a linha (operadora, condutor, instrutor, GRU) e a contagem
    "{mês} · {operadora}: 1" (R9).
12. "Reenviar" abre `window.prompt`: aceitar vazio (destino padrão). Novo ofício no Mailpit
    com "(reenvio)" no assunto, From da EAMA e "operado por" (R10). Saldo da operadora
    inalterado.

**F. Kill switch**
13. EAMA clica "Bloquear emissão". A operadora prepara uma segunda reserva (passo C por API,
    para ser rápido) e tenta emitir: 400 "suspensa pelo parceiro" (R11).
14. EAMA clica "Liberar". A emissão da segunda reserva passa.

**G. Encerramento**
15. Revogar o vínculo (`window.confirm`) e seguir a decisão D3 para as empresas criadas.

**Negativo, em teste separado e só por API:** empresas de capitanias diferentes não formam
vínculo (400).

## 5. Trabalho de código antes do teste

1. **`data-testid` na página "Emissão delegada"**, que hoje não tem nenhum: slug do
   parceiro, papel, "Convidar", linha e badge de cada parceria, "Ver termo e aceitar", checkbox
   e confirmação do termo, "Bloquear emissão", "Liberar", "Revogar", diálogo de designação,
   tabela e contagens do painel, "PDF" e "Reenviar".
2. **`data-testid` no passo de emissão:** linhas de status "Enviado à Marinha" e "E-mail ao
   cliente" e o botão "Reenviar".
3. **Helper do Mailpit** (`e2e/helpers/mailpit.ts`): busca com polling por destinatário e
   assunto (`/api/v1/search`), lê From, Reply-To, HTML e anexos (`/api/v1/message/{id}`),
   extrai o código de login. Filtra pelos endereços únicos do run, **sem limpar a caixa**,
   que é compartilhada.
4. **Helper de login por contexto** (`e2e/helpers/login.ts`): identifier-first
   (`#identifier` → `#mj-send-code`), código lido do Mailpit, `storageState` por usuário.
   O `wizard.mjs` já tem essa lógica e serve de base.
5. **Helper de API** (`e2e/helpers/api.ts`): chamadas com o token do admin e `X-Tenant-Id`,
   mais o token de plataforma por ROPC.
6. **Plano dedicado** (decisão D2).
7. **Guarda de ambiente:** o `baseURL` padrão do `playwright.config.ts` é **produção**. A spec
   aborta se `PLAYWRIGHT_BASE_URL` não estiver definido ou apontar para `meujet.com.br`, porque
   ela cria empresas e dispara e-mails.

## 6. Riscos a validar primeiro (spike)

| Risco | Como validar | Saída se falhar |
|---|---|---|
| JavaMail com `mail.smtp.auth=true` contra Mailpit sem autenticação | Configurar SMTP de um tenant de dev para `mailpit:1025` e emitir | `MP_SMTP_AUTH_ACCEPT_ANY=1` e `MP_SMTP_AUTH_ALLOW_INSECURE=1` no serviço `mailpit` do compose |
| ROPC do `jetski-test` para o operador de plataforma após o upgrade do Keycloak 26.7 (houve 400) | `curl` do token | Operador de plataforma de teste sem 2FA, logado pelo console |
| Não existe endpoint para criar plano | — | Decisão D2 |
| Recarregar o passo de habilitação reflete a GRU paga pela API | Teste manual no dev | `balcao-hab-prosseguir-sem-gru` e depois a API |
| Envio assíncrono passa de 60 s com dois SMTPs | Medir no spike | Assert pelo Mailpit com timeout próprio, sem depender da tela |

## 7. Decisões pendentes

- **D1. Onde roda.** Recomendo **fase 1 local contra o dev** (`PLAYWRIGHT_BASE_URL` do túnel de
  dev) e **fase 2 no CI**. Hoje o CI não roda Playwright: o job e2e sobe só backend, OPA,
  Keycloak e Postgres, com Newman e sem frontend nem seeds do reset. Levar para o CI exige um
  job novo com o frontend no compose.
- **D2. Plano "só delegada".** Recomendo um **SQL idempotente no `global-setup`**
  (`INSERT … ON CONFLICT`), igual ao `EmissaoDelegadaIntegrationTest`. A alternativa, um
  endpoint de plataforma para criar plano, é feature nova.
  **Não** usar `PUT /planos/{id}/modulos` num plano existente: ele muda todas as empresas
  daquele plano.
- **D3. Empresas criadas por execução.** Recomendo excluir no fim pela plataforma
  (`POST /v1/platform/tenants/{id}/excluir`). A alternativa é deixar acumular com prefixo
  `e2e-` e limpar com o `reset-ambiente-dev.sh`.
- **D4. Habilitar emissora pela UI do console.** O plano faz por API. Se também for regra a
  garantir, vale um teste curto e separado no `plataforma-console`.

## 8. Fases de entrega

1. **Spike:** riscos da §6, sem código de teste.
2. **Base:** `data-testid`, helpers (Mailpit, login, API), guarda de ambiente e plano dedicado.
3. **Jornada feliz:** blocos A, C, D e E. Cobre R1 a R10 e R12.
4. **Kill switch e negativo:** blocos F e G e o teste de capitanias diferentes (R11).
5. **CI** (se D1 aprovado): job com frontend e Mailpit, rodando só esta spec.
