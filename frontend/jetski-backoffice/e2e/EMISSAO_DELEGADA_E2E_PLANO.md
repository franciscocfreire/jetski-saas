# Plano — E2E Playwright da emissão delegada (EAMA emissora × operadora)

> Status: **plano revisado** (13/set/2026, 2ª versão — endpoints, ordem do preparo, token de
> plataforma e kill switch conferidos no código). Nada implementado ainda.
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
| 1. Criar EAMA e operadora | `POST /v1/signup/tenant` → `GET /v1/test/last-email` → `POST /v1/signup/magic-activate` (padrão do `global-setup.ts`). A resposta traz a **senha temporária** do admin; o primeiro login exige trocá-la (`#password-new`/`#password-confirm`), e o `performTraditionalLogin` já trata isso | pública |
| 2. Aprovar as duas | `POST /v1/platform/tenants/{id}/approve` (o signup nasce `PENDENTE_APROVACAO`). **É a aprovação que cria a assinatura Trial**, cujo plano tem `modulos = NULL` (= todos os módulos, inclusive `EMISSAO_PROPRIA`). Por isso o passo 6 tem que vir depois deste | plataforma |
| 3. Mesma capitania (CPSP) nas duas; `eama_registro` na EAMA | `PUT /v1/tenants/{t}/config/emissora` | admin de cada empresa |
| 4. Habilitar a EAMA | `POST /v1/platform/tenants/{id}/habilitar-emissora` | plataforma |
| 5. Config geral das duas | `PUT /v1/tenants/{t}/config/geral`: `marinha_email`, `email_oficial`, `responsavel_nome`, `telefone`, `email_remetente` e SMTP (`mailpit`, 1025, usuário/senha fictícios, starttls off, `smtp_from` distinto). A operadora recebe **também** um `marinha_email` próprio, para provar que ele nunca é usado | admin de cada empresa |
| 6. ~~Plano "só delegada" na operadora~~ | **Removido em 13/set/2026 (spec §8.M).** A operadora fica na Trial (todos os módulos): é a parceria em vigor que a torna delegada, não o plano. A jornada passa a provar isso (passo A confere o modo DELEGADA). Antes, sem trocar o plano, a emissão caía no portão da V050 | — |
| 7. Créditos da operadora | `POST /v1/platform/creditos/{tenantId}` com `{quantidade, motivo}` (motivo obrigatório) | plataforma |
| 8. Instrutores | Um na EAMA e um na operadora: `POST /v1/tenants/{t}/instrutores`. O da operadora nasce antes da parceria, então sem pedido de aprovação: não aparece no dropdown (R2). O passo H pede a aprovação, a EAMA aprova (aí ele assina) e depois remove (spec §8.N) | admin de cada empresa |
| 9. Modelo na operadora | `POST` de modelo (fábrica de `fixtures/test-data.ts`) | admin da operadora |

**Tokens:**
- **Admin de cada empresa:** lido de `/api/auth/session` do contexto já logado (padrão de `fixtures/auth.ts`). Os admins criados pelo signup não têm 2FA, então o login é só senha.
- **Plataforma:** ROPC no client público `jetski-test` (direct grant habilitado, é o que o Newman usa; **só dev/CI**, revisão técnica ago/2026 P0) com um **operador de plataforma dedicado ao e2e, sem 2FA**. Não usar o `admin@acme.com`: em dev ele tem TOTP cadastrado à mão, e o realm não tem fluxo de direct grant próprio, então vale o padrão do Keycloak, cujo passo "Conditional OTP" exige o código para quem tem TOTP e o ROPC falha com `invalid_grant`. O backend não valida `azp`, então o token do `jetski-test` serve para `/v1/platform/**`; o poder vem de `usuario_global_roles`.
  Como criar o operador: usuário no Keycloak (mesma chamada de admin API que o `setup-keycloak-dev.sh` já faz) + e-mail dele em `PLATFORM_ADMIN_EMAILS`, que o `PlatformAdminSeeder` promove no boot **desde que o `usuario` já exista** (nasce no primeiro login). Ou seja: criar no Keycloak, logar uma vez, reiniciar o backend. Alternativa sem reinício: conceder pela tela `/operadores` do console com um admin já existente.

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
13. EAMA clica "Bloquear emissão". A operadora tenta **reemitir a mesma reserva** por API,
    `POST /v1/tenants/{t}/reservas/{id}/emitir-documentos?reemitir=true`: 400 "suspensa pelo
    parceiro" (R11). Sem `reemitir=true` a idempotência devolveria o documento existente
    antes de checar o vínculo. Não vale a pena uma segunda reserva: o aceite por API exige
    assinatura em base64 e repetir o balcão dobra o tempo do teste.
14. EAMA clica "Liberar". A mesma chamada agora emite um documento novo (key versionada),
    debita mais 1 crédito e dispara os e-mails de novo. Conferir o saldo e o segundo ofício
    no Mailpit, que também cobre o caminho de reemissão.

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
   assunto (`/api/v1/search?query=to:… subject:…`, ordenar pela chave `Date`, não `Created`),
   lê From, Reply-To, HTML e anexos (`/api/v1/message/{id}`). Filtra pelos endereços únicos
   do run, **sem limpar a caixa**, que é compartilhada com o `wizard.mjs` e com quem usa o dev.
4. **Helper de login por contexto** (`e2e/helpers/login.ts`): extrair o
   `performTraditionalLogin` do `global-setup.ts` para receber uma `page` e devolver o
   `storageState`. Ele já faz o identifier-first (`#identifier` → `#mj-send-code` → `#password`
   → `#mj-login-password`) e a troca de senha obrigatória do primeiro login. O código por
   e-mail (lido do Mailpit, como no `wizard.mjs`) fica só como fallback.
5. **Helper de API** (`e2e/helpers/api.ts`): chamadas com o token do admin e `X-Tenant-Id`,
   mais o token de plataforma por ROPC.
6. **Plano dedicado** (decisão D2).
7. **Guarda de ambiente:** o `baseURL` padrão do `playwright.config.ts` é **produção**. A spec
   aborta se `PLAYWRIGHT_BASE_URL` não estiver definido ou apontar para `meujet.com.br`, porque
   ela cria empresas e dispara e-mails.

## 6. Riscos a validar primeiro (spike)

> **Resultado do spike (13/set/2026):**
> - JavaMail com `mail.smtp.auth=true`, usuário/senha fictícios e starttls off **envia ao
>   Mailpit sem nenhuma configuração extra**; From e Reply-To chegam intactos.
> - `admin@acme.com` **não serve**: tem senha, WebAuthn e OTP, e o ROPC falha.
> - Operador dedicado `e2e.plataforma@meujet.test` criado no Keycloak dev e ligado por SQL
>   (`usuario` + `usuario_identity_provider` + `usuario_global_roles`). O ROPC no `jetski-test`
>   sai com `iss=https://sso.pegaojet.com.br/...` e as rotas de plataforma respondem 200
>   pelo nginx local e pelo Cloudflare. O token expira em poucos minutos: pedir um novo por uso.
> - Plano: não há endpoint de criação; `plano.nome` é único → `INSERT … ON CONFLICT (nome)`.
> - GRU paga pela API: retomar o balcão com `?reserva=<id>` recarrega a habilitação do
>   servidor e marca a GRU como paga. Na primeira passagem, o id da reserva sai da resposta
>   do `POST` de reserva (a URL não o traz).
> - Cloudflare devolve 403 (1010) a clientes HTTP sem User-Agent; com o UA do Playwright passa.
> - Tempo do envio assíncrono com dois SMTPs: medido na fase 3, na primeira emissão real.

| Risco | Como validar | Saída se falhar |
|---|---|---|
| JavaMail com `mail.smtp.auth=true` contra Mailpit sem autenticação | Configurar SMTP de um tenant de dev para `mailpit:1025` e emitir | `MP_SMTP_AUTH_ACCEPT_ANY=1` e `MP_SMTP_AUTH_ALLOW_INSECURE=1` no serviço `mailpit` do compose |
| ROPC do `jetski-test` com o operador dedicado (o `PlatformAdminSeeder` só promove quem já tem `usuario`) | `curl` do token e `GET /v1/platform/tenants` com ele | Conceder o papel pela tela `/operadores` do console, uma vez, no dev |
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
- **D3. Empresas criadas por execução.** Recomendo excluir no fim pela plataforma:
  `POST /v1/platform/tenants/{id}/excluir` com `{modo: "IMEDIATO", confirmacaoSlug}` (o modo
  `CARENCIA` só suspende e expurga em D+30; o export de arquivamento roda antes do expurgo nos
  dois modos, então cada execução deixa um zip). A alternativa é deixar acumular com prefixo
  `e2e-` e limpar com o `reset-ambiente-dev.sh`.
- **D4. Habilitar emissora pela UI do console.** O plano faz por API. Se também for regra a
  garantir, vale um teste curto e separado no `plataforma-console`.

## 8. Fases de entrega

> **Andamento (13/set/2026):** spike ✅ e fase 2 ✅ na branch `feat/e2e-emissao-delegada`.
> Decisões D1–D4 aprovadas como recomendadas. A smoke `e2e/delegada/preparo.spec.ts` passa
> no dev (3 testes, ~14 s): cria o par por API, faz os dois logins, confere as âncoras da
> página Emissão delegada e lê o Mailpit, e exclui as empresas no fim.
>
> Rodar: `PLAYWRIGHT_BASE_URL=https://app.pegaojet.com.br npm run test:e2e:delegada`
> (`E2E_MANTER_EMPRESAS=1` mantém as empresas para inspeção).
>
> **Fase 3 ✅ (13/set/2026):** `e2e/delegada/jornada.spec.ts` passa no dev — 8 testes,
> ~40 s depois do preparo (negativo de capitanias, A vínculo, B designação, C balcão,
> D e-mails, E painel/reenvio, F kill switch, G revogação). Cobre R1–R12.
> Requer o backend do dev com os PRs #9 e #12 (rebuild a partir da main) e a V069 aplicada.
>
> Rodar só a jornada: `PLAYWRIGHT_BASE_URL=https://app.pegaojet.com.br npm run test:e2e:delegada -- jornada`
>
> Armadilhas encontradas na fase 3:
> - **Checkbox dentro de Dialog do Radix: nunca `click({ force: true })`.** O conteúdo entra
>   animado (200 ms); o `force` clica no meio da animação, fora do diálogo, e o Radix fecha.
>   Sem `actionTimeout` a próxima ação esperava 5 min por um elemento que não existia mais.
> - **GRU paga pela API + recarga**, nunca `?reserva=<id>`: a retomada pula Documentos e o
>   ofício fica BLOQUEADO por pendências (identidade, selfie, naturalidade, residência).
> - Todas as pendências da Marinha são obrigatórias por padrão; o balcão precisa passar por
>   Documentos, Orientações (videoaula pelo fallback, com o YouTube bloqueado) e Termos.
>
> Armadilha do dev: `rebuild.sh <serviço>` rodado de um worktree **recria** Postgres,
> Keycloak, OPA e nginx (o compose vê os bind mounts em outro caminho). O Keycloak leva
> ~25 s para voltar; os helpers esperam e repetem em erro de rede. Não rode a suíte durante
> um rebuild.

1. **Spike:** riscos da §6, sem código de teste.
2. **Base:** `data-testid`, helpers (Mailpit, login, API), guarda de ambiente e plano dedicado.
3. **Jornada feliz:** blocos A, C, D e E. Cobre R1 a R10 e R12.
4. **Kill switch e negativo:** blocos F e G e o teste de capitanias diferentes (R11).
5. **CI** (se D1 aprovado): job com frontend e Mailpit, rodando só esta spec.
