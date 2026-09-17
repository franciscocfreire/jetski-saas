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
```

Ambiente: `DOMINIO` (obrigatório; recusa `meujet.com.br`), `MAILPIT_URL`, `FAKES_URL`,
`ESTADO`, `CATALOGO`, `SAIDA`. **Idempotente e retomável:** cada passo concluído vai para o estado, e a
frota consulta o que já existe antes de criar.

## Fase E2: `fakes-externos` — a Marinha e o PagTesouro sintéticos

Serviço `fakes-externos` da camada `docker-compose.espelho.yml` (código em
[`src/fakes/`](src/fakes/)). O backend chega nele **só por configuração**
(`jetski.gru.marinha-base` / `pagtesouro-base`); nenhuma linha de produção sabe que é fake —
o `GruClient` faz exatamente os mesmos passos que faz contra o governo.

| Borda | O que reproduz |
|---|---|
| `/marinha/**` | [`GRU_HTTP_CONTRACT.md`](../GRU_HTTP_CONTRACT.md) passo a passo: sessão ASP **de uso único** (com o `Set-Cookie` malformado do site real), cascata obrigatória (o item `060;288  ;408` com os dois espaços), 302 com `id_gru`, página-ponte com **token por CPF**, bridge que responde "problema de autenticação" sem cookie/token, boleto (302 → 302 → PDF com o número extraível pelo pdfbox), consulta de nome por CPF |
| `/pagtesouro/**` | `dados-pagamento`, `meios-pagamento/pix` (BR Code EMV com CRC válido + **QR PNG escaneável**, codificador próprio em [`qr.ts`](src/fakes/qr.ts)), `pix-stn/sonda` com máquina de estados: `C0008` pendente → objeto `CONCLUIDO` → `C0026` expirado |
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
| `falhas.<etapa>` | taxa 0 | etapas `marinha` (→ 503, `MARINHA_INDISPONIVEL`), `bridge` (→ página de autenticação, `BRIDGE_FALHOU`), `pagtesouro` (→ 500, `PAGTESOURO_FALHOU`). `modo: "pendurar"` segura a conexão até o timeout do backend (20 s) |
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

- **Parênteses no nome quebram a ativação.** O perfil de usuário do Keycloak recusa
  `( )` em nome (`error-person-name-invalid-character`); o cadastro aceita, e a ativação
  devolve **500**. Vale para clientes reais também — é bug do produto, não do semeador.
- **Sintaxe "apagável" só.** O Node roda `.ts` removendo os tipos; `constructor(private x)`
  e `enum` não funcionam. O `tsconfig.json` liga `erasableSyntaxOnly` para o `tsc` acusar.
- **Convite antigo na caixa.** O Mailpit guarda convites de cadastros anteriores para o
  mesmo e-mail; a busca só considera mensagens posteriores ao cadastro.
- **Telefone em E.164** (`+5511…`) — a API valida.
