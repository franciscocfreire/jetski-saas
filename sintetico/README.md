# Ecossistema sintético

Código do [`ECOSSISTEMA_SINTETICO_SPEC.md`](../ECOSSISTEMA_SINTETICO_SPEC.md): as
personas e (nas próximas fases) os sistemas externos simulados do **espelho de carga**.
TypeScript puro sobre Node 24, **sem dependências** — roda num container `node:24-alpine`
sem `npm install`, e nada de terceiros fica no caminho das credenciais das personas.

## O que existe hoje — fase E3a: o semeador

Cria, **pelas mesmas APIs de produção**, as personas do [`catalogo/e3a.json`](catalogo/e3a.json):

| Persona | Como nasce e o que faz |
|---|---|
| **Operadora de plataforma** | cadastro público → convite lido no Mailpit → ativação → promovida pelo boot do backend (`PLATFORM_ADMIN_EMAILS`, o bootstrap da própria plataforma) → entra no **console** trocando a senha temporária e **configurando o TOTP** → aprova empresas e muda planos |
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
```

Ambiente: `DOMINIO` (obrigatório; recusa `meujet.com.br`), `MAILPIT_URL`, `ESTADO`,
`CATALOGO`, `SAIDA`. **Idempotente e retomável:** cada passo concluído vai para o estado, e a
frota consulta o que já existe antes de criar.

## Como o login funciona (e por que não é trivial)

O tema de login é Keycloakify (React): **o HTML não tem `<form>`**. A página entrega um
objeto JavaScript `kcContext` — que não é JSON — com a URL de ação, e o React monta o
formulário. [`src/lib/keycloak.ts`](src/lib/keycloak.ts) lê os campos necessários e envia os
mesmos POSTs, atravessando as telas que o Keycloak pedir:

| Client | Telas |
|---|---|
| console (`jetski-platform-console`) | `login` → `login-update-password` → `login-config-totp` (1º acesso) / `login-otp` (depois) |
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
