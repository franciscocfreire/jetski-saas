# Ecossistema sintético

Código do [`ECOSSISTEMA_SINTETICO_SPEC.md`](../ECOSSISTEMA_SINTETICO_SPEC.md): as
personas e os sistemas externos simulados (Marinha e PagTesouro) do **espelho de carga**.
TypeScript puro sobre Node 24, **sem dependências** — roda num container `node:24-alpine`
sem `npm install`, e nada de terceiros fica no caminho das credenciais das personas.

## O que existe hoje — fases E3a e E1: o semeador

Cria, **pelas mesmas APIs de produção**, as personas do [`catalogo/e3a.json`](catalogo/e3a.json):

| Persona | Como nasce e o que faz |
|---|---|
| **Operadora de plataforma** | cadastro público → convite lido no Mailpit → ativação → promovida pelo boot do backend (`PLATFORM_ADMIN_EMAILS`, o bootstrap da própria plataforma) → entra no **console** trocando a senha temporária e **configurando o TOTP** → aprova empresas e muda planos |
| **Cliente do portal** (fase E1) | auto-cadastro público → **verifica o e-mail** pelo link que o Keycloak envia → entra no portal **sem senha**, pelo código de 6 dígitos lido no Mailpit → a API a reconhece como cliente |
| **3 empresas de carga** (`carga-*`) | cadastro → ativação → **aprovadas pela operadora** → plano **Enterprise** (o Trial limita a 3 jetskis e 50 locações/mês) → o admin entra no backoffice e cria modelo + 20 jetskis |

Nenhum atalho: a trilha de auditoria do espelho mostra a persona operadora como autora de
cada `TENANT_APPROVED` e `TENANT_PLANO_ALTERADO`.

### Roda sozinho no provisionamento

[`infra/espelho/provisionar-vm.sh`](../infra/espelho/provisionar-vm.sh) (etapa 7) executa
`bootstrap` → reinicia o backend → `semear`. Um `terraform apply` entrega o espelho **já
populado**. O estado — com senhas e segredos TOTP — fica em
`/var/lib/meujet-espelho/personas.json` na VM (0600, root), nunca no repositório.

### Entrar no console do espelho como humano

Use as credenciais da persona operadora:

```bash
ssh ubuntu@<ip> 'sudo cat /var/lib/meujet-espelho/personas.json' | jq '.personas["operador-plataforma"] | {email, senha, otpauth}'
```

O `otpauth://…` é o conteúdo de um QR code de autenticador: gere o QR (`qrencode -t ansiutf8 '<otpauth>'`)
ou digite o `secret=` no seu app, e o código de 6 dígitos passa a valer no login de
`https://admin.<dominio>`.

### Comandos

```bash
node src/semeador.ts bootstrap   # cadastra e ativa a operadora
node src/semeador.ts semear      # operadora entra, aprova, muda plano; empresas ganham frota
node src/semeador.ts resumo      # o que existe (sem segredos)
node src/semeador.ts tokens      # tokens.json do k6 — use ../k6/gerar-tokens.sh
node src/semeador.ts provar-gru  # E2: GRU de ponta a ponta (gera, persona paga, confirma, boleto)
node src/semeador.ts provar-emissao  # E3b: emissão própria + delegada, até o ofício à Capitania
```

Ambiente: `DOMINIO` (obrigatório; recusa `meujet.com.br`), `MAILPIT_URL`, `FAKES_URL`,
`ESTADO`, `CATALOGO`, `SAIDA`. **Idempotente e retomável:** cada passo concluído vai para o estado, e a
frota consulta o que já existe antes de criar.

## Fase E4: o motor de comportamento — "um sábado sintético"

[`src/motor.ts`](src/motor.ts) + [`src/motor/`](src/motor/) leem o cenário
[`catalogo/e4-sabado.json`](catalogo/e4-sabado.json) e fazem um dia de operação acontecer nas
três lojas de emissão, **pelas APIs reais e com o papel que tem cada poder**:

```bash
sintetico/motor.sh <ip-do-espelho>                 # o sábado inteiro: 09h–18h em ~45 min (12×)
CHEGADAS=6 FATOR=36 SEMENTE=7 sintetico/motor.sh <ip>   # dia em miniatura, reproduzível
```

Roda **de fora da VM** (o gerador não disputa CPU com a aplicação; o tráfego entra pela
Cloudflare): o `motor.sh` traz uma cópia do estado das personas (0600, apagada na saída) e abre
túneis SSH para o Mailpit e o `/_controle` dos fakes, em **portas locais próprias (18025/18089)** —
o dev desta máquina costuma ter outro Mailpit em 8025. Usa o **Node local** (≥ 22.18); container
só onde `--network host` é o host de verdade (no Docker Desktop/WSL ele não enxerga os túneis).

| Jornada | O que acontece | Quem age |
|---|---|---|
| **Portal** | cliente recorrente ou **novo** (cadastro → e-mail verificado → CPF) entra pelo código do e-mail, vê vitrine/modelos/disponibilidade, reserva com sinal; 80% enviam comprovante; a loja confirma; 10% não aparecem (no-show); os demais passeiam e metade avalia | cliente; FINANCEIRO confirma o sinal; OPERADOR confirma, aloca, faz check-in/out |
| **Balcão** | freguês ou **gente nova** (consulta do CPF na Marinha + ficha + documentos); 35% têm CHA, 65% tiram a EMA: habilitação → termo → GRU → a cliente paga no PagTesouro sintético (15% desistem → reserva cancelada) → documentos à Marinha; 60% com vendedor | OPERADOR; GERENTE cancela (o OPA nega ao operador) |
| **Manutenção** | OS num jetski parado: fora da frota até concluir (RN06) | GERENTE abre; MECÂNICO inicia e conclui |
| **Telas** | controle do dia e agenda a cada 20 min simulados | OPERADOR |
| **Fechamento** | consolida e fecha o dia (reabre antes, se a data já foi fechada por outra rodada) | FINANCEIRO |
| **Créditos** | saldo < 12 → a loja compra 20 e a operadora de plataforma aprova (login com TOTP) | ADMIN da loja; operadora |

**Tempo comprimido pelos dados** (decisão nº 4): o relógio simulado corre 12×, mas as datas
enviadas à API são **reais e futuras** — a API exige início no futuro e a duração mínima do
modelo em minutos reais. Uma reserva declara 60 min; o passeio dura 5 min reais. O check-in por
reserva não valida janela de horário, então funciona. O que o tempo comprimido **não** alcança:
expiração de pré-reserva do portal (24 h) e jobs de hora fixa — isso é para o soak da E5.

**O funil é dado, não código.** Os números do cenário são **premissas** (produção ainda não tem
operação para calibrar) — locadora de praia, pico no fim da manhã e no meio da tarde. Os sócios
ajustam o JSON. Cada rodada tem **semente**: as mesmas decisões de funil se repetem com
`SEMENTE=<n>` (cada jornada tem o próprio gerador, então a ordem de execução não muda o sorteio).

**Saída:** desfechos por jornada, contadores (locações, emissões, clientes novos…), latência
p50/p95 **por passo** e a lista de falhas, em `relatorios/motor-<data>.json` (fora do git), mais
`http://127.0.0.1:9464/metrics` durante a rodada. Sai com código 1 se houve falha inesperada.
O jetski **sempre volta**: se uma jornada quebra com ele na água, o pier faz um check-out de resgate.

Limites a lembrar: plano Pro = **500 locações/mês** por loja (um sábado gasta ~10–15 em cada);
cada EMA emitida debita 1 crédito; cada cliente novo é uma conta real no Keycloak do espelho.

## Fase E3b: a população de emissão

[`catalogo/e3b.json`](catalogo/e3b.json) + [`src/emissao.ts`](src/emissao.ts), dentro do mesmo `semear`.
Cada linha é uma sequência de chamadas às rotas reais, **pela persona que de fato tem aquele poder**:

| Quem | Faz o quê |
|---|---|
| **EAMA emissora** (`sintetico-eama-marlin`) | declara capitania + registro (`config/emissora`), identidade e **SMTP próprio → Mailpit** (`config/geral`); cadastra 2 instrutores |
| **Operadora de plataforma** | **habilita a emissora** (portão cadastral) e **aprova as compras de créditos** |
| **Instrutores** | assinam pelo **link único**, na rota pública, sem conta — PNG gerado |
| **2 operadoras delegadas** (`-atol`, `-baia`) | **pedem o vínculo**; a EAMA aceita o termo e **designa** instrutores. A Atol tem instrutor próprio: pede aprovação, a EAMA aprova. A Baía fica de reserva para os cenários de kill switch (E5) |
| **Equipe** | gerente, operador, vendedor, mecânico e financeiro (Atol) + operador (Baía): convite do admin → ativação pelo e-mail → primeiro login trocando a senha |
| **Créditos** | a empresa vê o PIX, envia **comprovante sintético** e a plataforma aprova (30 / 20 / 10). O aceite do vínculo **estorna o bônus de adesão** da delegada — o saldo dela é só o comprado |
| **6 clientes do portal** | cadastro → e-mail verificado → login por código → **CPF no perfil** |
| **6 clientes de balcão** | ficha completa + identidade, selfie e comprovante de residência (PNGs de ~200 KB, [`lib/imagem.ts`](src/lib/imagem.ts)) — cadastrados pelo **OPERADOR** da loja, não pelo admin |

Empresas de emissão ficam no plano **Pro** (o Trial só comporta 2 usuários). CPFs não estão no
catálogo: o semeador sorteia um de DV válido por pessoa, guarda no estado da VM e o registra
na Marinha sintética com o nome dela (re-registrado a cada execução — o fake vive em memória).

`provar-emissao` fecha o ciclo com **duas emissões completas** — própria na EAMA, delegada na
Atol: reserva → habilitação EMA (videoaula, anexos, instrutor) → termo assinado → GRU paga no
PagTesouro sintético → `emitir-documentos` → **1 crédito debitado** → **ofício à Capitania no
Mailpit, remetido pelo SMTP da EAMA nos dois casos**, com anexo → painel de emissões delegadas.
Cada execução emite de verdade (debita 2 créditos).

## Fase E6: a TSA sintética

O padrão do produto para carimbo de tempo é a **freetsa.org** (`AssinaturaConfig.padrao()`), que o
sumidouro afunda — até a E6 toda emissão do espelho degradava para "âncora interna" com um WARN,
e os números da E5 foram medidos assim. Agora:

- **`fakes-externos` responde RFC 3161** em `/tsa` (tabela acima; código em [`src/fakes/tsa.ts`](src/fakes/tsa.ts)
  e [`der.ts`](src/fakes/der.ts), DER escrito à mão, sem dependências).
- **O semeador configura `config/assinatura`** de cada empresa de emissão (`catalogo/e3b.json`,
  chave `assinatura`): carimbo na TSA sintética; **PAdES-T ligado só na EAMA** (exercita o segundo
  cliente de TSA do backend, o OpenPDF com SHA-1) e desligado nas delegadas (grupo de controle).
  Confere o valor vivo e só grava se divergir.
- **Trava:** `semeador tokens` lê o `config/assinatura` de cada empresa de emissão e só grava
  `fakesVerificados: true` se todas apontarem para `http://fakes-externos:…/tsa`.
- **Prova:** `provar-emissao` exige os eventos `CARIMBO` — 3 na EAMA (auditoria do PDF do cliente,
  da Marinha e o PAdES com SHA-1), 2 na delegada. Sem exigir o carimbo, uma TSA quebrada passaria
  despercebida: o backend degrada em silêncio.
- **Verificação por fora:** `test/tsa.test.ts` roda `openssl ts -verify` e `openssl cms -verify
  -purpose timestampsign` contra o certificado do fake quando há `openssl` na máquina (verify OK,
  documento adulterado FAILED). O token com certificado tem ~1,7 KB — abaixo do limite de 4 KB
  que o PAdES do OpenPDF reserva.

Medido com a TSA ligada (k6 `emissao` smoke, 1 VU): 27 emissões/min, jornada p95 **2,3 s** — o
carimbo custa pouco; o que pesa na emissão é PDF + base64.

## Fase E2: `fakes-externos` — a Marinha e o PagTesouro sintéticos

Serviço `fakes-externos` da camada `docker-compose.espelho.yml` (código em
[`src/fakes/`](src/fakes/)). O backend chega nele **só por configuração**
(`jetski.gru.marinha-base` / `pagtesouro-base`); nenhuma linha de produção sabe que é fake —
o `GruClient` faz exatamente os mesmos passos que faz contra o governo.

| Borda | O que reproduz |
|---|---|
| `/marinha/**` | [`GRU_HTTP_CONTRACT.md`](../GRU_HTTP_CONTRACT.md) passo a passo: sessão ASP **de uso único** (com o `Set-Cookie` malformado do site real), cascata obrigatória (o item `060;288  ;408` com os dois espaços), 302 com `id_gru`, página-ponte com **token por CPF**, bridge que responde "problema de autenticação" sem cookie/token, boleto (302 → 302 → PDF com o número extraível pelo pdfbox), consulta de nome por CPF |
| `/pagtesouro/**` | `dados-pagamento`, `meios-pagamento/pix` (BR Code EMV com CRC válido + **QR PNG escaneável**, codificador próprio em [`qr.ts`](src/fakes/qr.ts)), `pix-stn/sonda` com máquina de estados: `C0008` pendente → objeto `CONCLUIDO` → `C0026` expirado |
| `/tsa` | **TSA RFC 3161** (fase E6): `POST` de um `TimeStampReq` devolve um `TimeStampResp` com token CMS de verdade (TSTInfo, `signingCertificateV2`, certificado com EKU timeStamping, **assinatura RSA-2048 real**). Aceita sha1/sha256/sha384/sha512, ecoa `nonce`, inclui o certificado se `certReq`; pedido malformado → status 2 `badDataFormat`. `/tsa/cert.pem` e `/tsa/cert.der` publicam o certificado (gerado a cada boot). Evento `CARIMBO` com `ref=` = SHA-256(token)[0:16] — a "Referência" que a página de auditoria do PDF imprime |
| `/_controle/**` | a alavanca dos cenários — abaixo |
| `/metrics` | `fakes_requisicoes_total{etapa,status}`, `fakes_falhas_injetadas_total`, `fakes_latencia_injetada_seconds_*`, `fakes_pagamentos_total`, `fakes_grus{situacao}` — para separar "lentidão que o cenário pediu" de "lentidão do backend" |

Tudo é **marcado como sintético por dentro**: o PIX aponta para `pix.sintetico.invalid`
(nenhum banco conclui o pagamento), o boleto não tem código de barras e diz que não vale.
O estado vive em memória: reiniciar o fake = "a Marinha perdeu as sessões" (GRUs pendentes
passam a responder como expiradas).

### `/_controle` — quem paga é a persona

**Ninguém paga sozinho** (decisão da E2): um PIX fica `PENDENTE` até alguém pagar — a persona
cliente (motor da E4) ou o k6. O `/_controle` não tem autenticação, por isso **só existe em
`127.0.0.1:8089` da VM** (fora do nginx e do túnel; o preflight reprova outra publicação).
De fora: `ssh -L 8089:127.0.0.1:8089 ubuntu@<ip>`.

```bash
C=http://127.0.0.1:8089/_controle
curl $C/estado                                   # config atual + contagens
curl "$C/grus?cpf=52998224725&situacao=PENDENTE" # GRUs que o fake conhece
curl -X POST $C/gru/<idSessao|id_gru|numeroReferencia>/pagar
curl -X POST $C/contribuintes -d '{"cpf":"52998224725","nome":"CLARA CLIENTE"}'  # nome:null = CPF desconhecido
curl "$C/eventos?desde=0&tipo=PAGAMENTO"         # trilha para asserções
curl -X POST $C/reset                            # zera estado E config
curl -X POST $C/config -d '{
  "falhas":     {"marinha": {"taxa": 0.2, "modo": "erro"}},
  "latenciaMs": {"pagtesouro": 3000},
  "bloqueioCpf": {"ligado": true, "limite": 10},
  "autoPagarAposSeg": 45,
  "pixValidadeMin": 30 }'
```

| Chave | Padrão | Efeito |
|---|---|---|
| `falhas.<etapa>` | taxa 0 | etapas `marinha` (→ 503, `MARINHA_INDISPONIVEL`), `bridge` (→ página de autenticação, `BRIDGE_FALHOU`), `pagtesouro` (→ 500, `PAGTESOURO_FALHOU`), `tsa` (→ 200 com `status=2` + `failInfo`, fiel ao RFC: o backend degrada para "âncora interna"). `modo: "pendurar"` segura a conexão até o timeout do backend (20 s na GRU, 8 s na TSA) |
| `latenciaMs.<etapa>` | 0 | atraso fixo por chamada da etapa |
| `bloqueioCpf` | **desligado**, limite 10 | o bloqueio por volume do site real (~8–10 GRUs/dia por CPF → bridge recusa só aquele CPF). Desligado por padrão (decisão da E2); ligar nos cenários de falha da E5 |
| `autoPagarAposSeg` | `null` | se definido, todo PIX vira pago sozinho após N s — atalho para carga pura |
| `pixValidadeMin` | 30 | validade do PIX; vencido, a sonda responde `C0026` e o pagamento é recusado (409) |

CPF sem registro em `/contribuintes` ganha um nome determinístico `… SINTETICO` (DV válido)
ou vazio (DV inválido), como o site real devolve vazio para quem não conhece.

### Checagem manual de fidelidade (decisão nº 7 da spec)

O fake imita o que o robô **observou** do site real; se o site mudar, o fake continua
passando e a produção quebra. De tempos em tempos — e **sempre que a emissão falhar em
produção** — refaça a comparação, à mão, com **uma** GRU real (CPF do próprio operador, nunca
em volume):

1. Em produção, gere 1 GRU pelo balcão com o DevTools → Network aberto no site da Marinha
   **ou** siga a receita curl do [`GRU_ANALISE_HAR.md`](../GRU_ANALISE_HAR.md) §3.
2. Compare, passo a passo, com `src/fakes/servidor.ts`: status (302 nos passos 3/3b/4b), forma
   do `Location`, nome dos inputs da página-ponte, texto do bridge (`idSessao=` no iframe),
   chaves JSON de `dados-pagamento`, `pix` e `sonda`, e o `Content-Type`/charset das páginas ASP.
3. Pontos que o fake **assume** e nenhuma captura confirmou ainda: charset das páginas
   (o fake usa UTF-8), status HTTP da sonda pendente (o fake usa 400; o `GruClient` só olha o
   corpo) e a validade real do PIX. Anote aqui o que confirmar.
4. Divergiu? Corrija `GruClient` **e** o fake no mesmo PR, com o caso novo em `test/fakes.test.ts`.

## Como o login funciona (e por que não é trivial)

O tema de login é Keycloakify (React): **o HTML não tem `<form>`**. A página entrega um
objeto JavaScript `kcContext` — que não é JSON — com a URL de ação, e o React monta o
formulário. [`src/lib/keycloak.ts`](src/lib/keycloak.ts) lê os campos necessários e envia os
mesmos POSTs, atravessando as telas que o Keycloak pedir:

| Client | Telas |
|---|---|
| console (`jetski-platform-console`) | `login` → `login-update-password` → `login-config-totp` (1º acesso) / `login-otp` (depois) |
| portal (`jetski-customer-portal`) | `email-code-id` → `email-code-verify` (pede o código: `mjAction=sendcode`) → `email-code-verify` (`mjAction=verify` + código do e-mail) |
| backoffice (`jetski-backoffice`) | `email-code-id` → `email-code-verify` (SPI `meujet-email-code`, identifier-first em dois POSTs) → `login-update-password` |

Tela desconhecida = erro explícito com o caminho percorrido — é assim que uma mudança no
fluxo de login aparece.

## Testes

```bash
docker run --rm -v "$PWD":/app:ro -w /app node:24-alpine node --test   # unidade
npm run tipos                                                           # tsc --noEmit
```

Unidade: vetores das RFC 4226/6238 (HOTP/TOTP), Base32, parser do `kcContext` contra a
**página real** do tema (`test/fixtures/login.html`), leitura do convite e pote de cookies.
`test/fakes.test.ts` é o **teste de contrato** dos fakes: um robô com o comportamento do
`GruClient` (jar manual, sem seguir redirect, **as mesmas regex**) percorre PIX e boleto, e
cobre cascata ausente, item sem espaços, bridge sem cookie, sessão de uso único, bloqueio por
CPF, expiração, falha/latência injetadas e nome acentuado. (Os HARs originais não foram
versionados — têm CPF real —, então o contrato parte do `GRU_HTTP_CONTRACT.md` e do código.)
O QR foi conferido com um decodificador independente (jsQR) nas versões 1, 8, 9, 11 e 25.

O teste de integração é o próprio espelho: a fase E3a foi validada rodando o semeador em
`us-ashburn-1` e conferindo banco e auditoria.

## Armadilhas já pagas

- **DER não sobrevive a UTF-8.** O servidor dos fakes convertia todo corpo para texto; o
  `TimeStampReq` chegava corrompido e o backend degradava para âncora interna **sem erro**. O
  `Pedido` tem `corpoBytes`, e a prova exige os eventos `CARIMBO`.
- **Config do tenant tem cache no backend:** duas emissões ~1 min depois de trocar o `tsaUrl`
  ainda foram à freetsa (hipótese: TTL do cache de `Tenant`); a prova repetida teve zero. Trocou a
  config? Espere um minuto antes de medir.
- **`@timestamp` do log do backend é hora local com sufixo Z** (`05:45Z` = 08:45 UTC): ao cruzar
  com o Prometheus (UTC de verdade), some 3 h.

- **`@PreAuthorize` mente; o OPA decide por último.** Casos que o motor pisou: GERENTE **não** cria
  vendedor (`vendedor:create`) nem OPERADOR cancela reserva (`reserva:delete`), apesar da anotação
  permitir; MECÂNICO não abre OS (só inicia/conclui). O cenário usa o papel que o OPA aceita.
- **Janela de horário do pier estava em UTC** (check-in/out negados depois das 17h de Brasília) —
  corrigido no PR #64. Num espelho sem essa correção, rode o motor antes das 17h locais.
- **Docker Desktop/WSL:** `--network host` não enxerga túneis SSH do WSL → "fetch failed". Node local.

- **Senha temporária pode começar com `*`** (`*x1Z%Yn35bgG` — caso real). No texto puro do
  e-mail `*` também é negrito; o leitor de convite usa o HTML, onde a senha vem sozinha.
- **Convite de membro ≠ convite de empresa:** o link é igual (`/magic-activate?token=`), mas a
  API é `POST /v1/auth/magic-activate`; a de empresa é `/v1/signup/magic-activate` (dá 404 trocada).
- `GET /v1/capitanias` é catálogo global, mas o filtro de tenant exige `X-Tenant-Id`.
- Provas que leem o Mailpit têm de casar o **id da reserva** no assunto, não só o horário.

- **Parênteses no nome quebram a ativação.** O perfil de usuário do Keycloak recusa
  `( )` em nome (`error-person-name-invalid-character`); o cadastro aceita, e a ativação
  devolve **500**. Vale para clientes reais também — é bug do produto, não do semeador.
- **Sintaxe "apagável" só.** O Node roda `.ts` removendo os tipos; `constructor(private x)`
  e `enum` não funcionam. O `tsconfig.json` liga `erasableSyntaxOnly` para o `tsc` acusar.
- **Convite antigo na caixa.** O Mailpit guarda convites de cadastros anteriores para o
  mesmo e-mail; a busca só considera mensagens posteriores ao cadastro.
- **Telefone em E.164** (`+5511…`) — a API valida.
