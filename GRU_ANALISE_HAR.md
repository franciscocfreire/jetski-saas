# GRU da Marinha + PIX — Análise do HAR e engenharia reversa do fluxo

> Documento de manutenção. Explica **como** chegamos às requisições do robô de GRU
> (`GruClient`), **por que** cada uma é necessária, os gotchas descobertos no teste real,
> e **como ajustar** no futuro (trocar capitania, mudar serviço, debugar, virar skill).
>
> Relacionados: `GRU_HTTP_CONTRACT.md` (contrato passo-a-passo), `GRU_ROBO_SPEC.md`
> (proposta inicial), código em `backend/.../locacoes/internal/gru/GruClient.java` +
> `GruService.java`. Validado ponta a ponta contra o site real em **24/jun/2026**.

---

## 1. Objetivo

Gerar automaticamente a **GRU** (Guia de Recolhimento da União) da Capitania para a
**Carteira de Habilitação de Amador (CHA / CHA-MTA-E)** e obter o **PIX** (copia-e-cola + QR),
hoje feito manualmente pelo operador no site da Marinha. Sem API oficial → engenharia reversa
do site, feita **só com HTTP** (sem navegador/Playwright).

## 2. Os três sistemas envolvidos

| # | Sistema | Tecnologia | Papel |
|---|---------|-----------|-------|
| A | `dpc1.marinha.mil.br/scam/emitgruscam/` | ASP clássico (IIS) | Formulário de emissão da GRU, dropdowns em cascata, gera o `id_gru` |
| B | `dpc1.marinha.mil.br/pagtesouro/` | PHP | "Ponte" que cria a sessão de pagamento no PagTesouro |
| C | `pagtesouro.tesouro.gov.br` | SPA Vue + API REST | Gateway de pagamento do Tesouro Nacional; gera o PIX |

O pulo do gato: **A e B compartilham a mesma sessão (cookie ASP)**, e B chama C para criar uma
**`idSessao`**, que autentica as chamadas REST de C (sem cookie/token de C).

## 3. Metodologia da análise

1. **Capturar HAR no navegador** (DevTools → Network → *Preserve log* LIGADO) fazendo o fluxo
   manual completo, até o QR aparecer. Como o botão "Pagar GRU com PIX" abre **nova aba**,
   capturamos **2 HARs**: a aba inicial (cascata + geração) e a aba do pagamento.
2. **Parsear o HAR com Python** (é um JSON): listar `entries[].request` (método, URL, `postData`)
   e `entries[].response` (status, headers, `content.text`). Filtrar por host/endpoint.
   - Achar os corpos exatos: `postData.text` de cada POST → é a fonte da verdade dos campos.
   - Achar o que extrair: regex no `response.content.text` (`id_gru`, `token`, `idSessao`).
3. **Reproduzir com `curl`** (cookie jar `-c/-b`, `-D -` para ler `Location`, `--data-raw` para
   enviar o corpo byte a byte). curl é tolerante a cookies malformados → ótimo para isolar
   problemas. Iterar via curl é muito mais rápido que rebuildar o backend.
4. **Comparar request a request** o que o navegador mandou (HAR) vs o que o robô manda, até
   bater. Diferença de **um campo** ou **um header** quebra silenciosamente.

> ⚠️ A captura HAR **não exporta cookies** (`Cookie`/`Set-Cookie` são omitidos). Para inspecionar
> cookies, use `curl -v` ou o `chrome://net-export` (net-export loga headers, mas **não** corpos
> de request).

## 4. O fluxo de 7 passos (e como cada um foi descoberto)

```
[A] 1. GET  solicitar_servico.asp                 → cria sessão ASP (cookie ASPSESSIONID)
[A] 2. POST obj*Ajax.asp  (cascata, 6 chamadas)   → registra órgão/serviço/contribuinte na sessão
[A] 3. POST pagtesouro.asp (form frm_registro)    → 302 Location: pagtesouro_form.asp?id_gru=N
[A] 4. GET  pagtesouro_form.asp?id_gru=N&...       → HTML com <form frm_envio> + token (auto-submit)
[B] 5. POST pagtesouro/index.php (req,token,...)   → HTML com iframe → idSessao (regex no corpo)
[C] 6. GET  api/pagamentos/dados-pagamento?idSessao → JSON: valor, numeroReferencia(=nº GRU), descrição
[C] 7. POST api/pagamentos/meios-pagamento/pix      → JSON: conteudo(PIX), imagem(QR b64), dataExpiracao
```

- **Passo 2 (cascata)** veio dos HARs `dpc1`/`novo`: 6 POSTs `obj*Ajax.asp` que o JS dispara ao
  preencher os dropdowns. **É necessária** para inicializar o estado da sessão (sem ela, B recusa).
- **Passo 3** — o corpo exato (`cd_orgao`, `id_tipo_recolhimento`, `tipo_servico`, `cd_servico`,
  contribuinte…) veio do `postData.text` do POST `pagtesouro.asp`. O `id_gru` sai do `Location`.
- **Passo 4** — o `Location` do passo 3 aponta para `pagtesouro_form.asp`, cujo HTML tem o
  `<form name="frm_envio">` com o `token`. Extração por regex `name="token"...value="..."`.
- **Passo 5** — o form do passo 4 auto-submete (`onload`) para `pagtesouro/index.php`. A resposta
  é um HTML "redirecionando" com `<iframe src='…/#/pagamento?idSessao=<uuid>'>`. Regex pega o `idSessao`.
- **Passos 6-7** — vieram do 2º HAR (aba do PagTesouro): chamadas REST puras autenticadas só pelo
  `idSessao` no corpo/URL. **Sem** cookie/Authorization de C.

## 5. Conclusões e gotchas (o que quebrou no teste real)

1. **Cookie jar MANUAL é obrigatório.** O `java.net.http.HttpClient` (e o `CookieManager` do JDK)
   **rejeita** o `Set-Cookie` malformado da Marinha (`httpOnly;secure;`, sem `name=value`) e acaba
   **descartando a sessão ASP**. Sintoma: o passo 5 responde
   `<script>alert('Houve um problema de autenticação!')</script>` (página ~2546 bytes, sem iframe).
   **Solução:** gerenciar cookies à mão — guardar só `name=value` (parte antes do primeiro `;`),
   ignorar tokens inválidos, reenviar via header `Cookie`. (Implementado em `GruClient.send/storeCookies`.)
2. **Passo 3 precisa de `id_tipo_recolhimento=1` + `tipo_servico=060`** e **NÃO** deve enviar
   `nr_telefone/ds_email/sg_sexo` (o site não os submete). Sem os dois primeiros, B não gera `id_gru`.
3. **O `token` é por-CPF, não estático.** GARDENIA→`141600814172120`, GABRIELA→`147600014772120`.
   Parecia "estático/cacheado" porque, para o **mesmo CPF**, é sempre igual.
4. **⚠️ A Marinha BLOQUEIA um CPF após ~8-10 GRUs no mesmo dia.** O bridge passa a responder
   "problema de autenticação" **só para aquele CPF** (outro CPF funciona normalmente). Isso nos
   enganou por várias rodadas. **Regra:** nunca martelar testes com o mesmo CPF; usar CPF real do
   cliente; em produção, 1 GRU por reserva (idempotência já cobre — não regerar GRU válida).
5. Passos 6-7 (PagTesouro) **não usam cookie** — o `idSessao` no corpo/URL autentica.

## 6. Como AJUSTAR no futuro

### 6.1 Parâmetros configuráveis (`application.yml` / env `JETSKI_GRU_*`)
O `GruClient` expõe tudo via `@Value("${jetski.gru.*}")`:

| Propriedade | Default | Significado |
|-------------|---------|-------------|
| `jetski.gru.cd-orgao` | `89310` | Código do órgão = **Capitania** (ver tabela §7) |
| `jetski.gru.tipo-recolhimento` | `1` | `1` = Amador |
| `jetski.gru.tipo-servico` | `060` | Serviços Administrativos |
| `jetski.gru.item-servico` | `060;288  ;408` | Item CHA (⚠️ os 2 espaços são significativos) |
| `jetski.gru.marinha-base` | `https://dpc1.marinha.mil.br` | Base A/B (e para apontar a mock em teste) |
| `jetski.gru.pagtesouro-base` | `https://pagtesouro.tesouro.gov.br` | Base C |
| `jetski.gru.timeout-seconds` | `20` | Timeout por requisição |
| `jetski.gru.cascata-habilitada` | `true` | Liga/desliga o passo 2 |

### 6.2 Trocar a CAPITANIA por empresa (multi-tenant)
Hoje `cd-orgao` é **global** (`89310` = SP). Para suportar capitanias diferentes por tenant:
1. Adicionar um campo no tenant (ex.: `tenant.capitania_org` = `89310`) — coluna + migration +
   reset-ambiente-dev.sh.
2. Passar esse valor para `GruClient.gerar(...)` (hoje ele lê do `@Value`; trocar para receber o
   `cdOrgao` por parâmetro, com fallback no default).
3. **Verificar o `item-servico` da nova capitania**: o código do item CHA (`060;288  ;408`) pode
   variar por órgão. Confirme capturando a cascata daquela capitania (resposta de
   `objServicosAjax.asp` com o `v_cd_orgao` novo) **ou** um HAR manual rápido. Se variar, torne
   `item-servico` também configurável por tenant.
4. A lista de códigos de capitania está na §7 (extraída do `<select>` de `solicitar_servico.asp`).

### 6.3 Debugar quando voltar a falhar
- Logs do `GruClient` (WARN) já dizem **em qual passo** parou e o `erroCodigo`
  (`MARINHA_INDISPONIVEL` 1-3, `BRIDGE_FALHOU` 4-5, `PAGTESOURO_FALHOU` 6-7) + se houve
  `authProblem` no passo 5.
- **Primeiro suspeito:** CPF bloqueado por excesso (§5.4) → testar com outro CPF.
- **Reproduzir com curl** (receita na §3) é o caminho mais rápido — não precisa rebuildar o backend.
  Comparar o `postData.text` de um HAR novo vs o corpo que o robô monta.
- Se o site mudar a estrutura (ex.: novo campo no form, token em outro lugar), capturar **HAR novo**
  e ajustar os corpos/regex no `GruClient` (`formGerarGru`, `formBridge`, `ID_GRU/TOKEN/ID_SESSAO`).

### 6.4 Virar uma skill / serviço reutilizável
O fluxo é autocontido em `GruClient` (sem dependência de Spring além de `@Value`/`ObjectMapper`).
Para extrair como skill/microserviço: mover `GruClient` + `GruContribuinte` + `GruResultado` para
um módulo isolado, expor `gerar(contribuinte, cdOrgao, itemServico)` e manter a config externa.
A doc de contrato (`GRU_HTTP_CONTRACT.md`) é suficiente para reimplementar em qualquer linguagem.

## 7. Códigos das Capitanias (campo `cd_orgao`)

Extraídos do `<select>` de `solicitar_servico.asp` (24/jun/2026). Lista parcial (capitanias;
há também agências e delegacias — recapturar a página para a lista completa):

| Código | Capitania |
|--------|-----------|
| **89310** | **CAPITANIA DOS PORTOS DE SÃO PAULO** (atual) |
| 81310 | Capitania dos Portos do Espírito Santo |
| 81330 | Capitania dos Portos do Rio de Janeiro |
| 81333 | Capitania dos Portos de Macaé |
| 82310 | Capitania dos Portos da Bahia |
| 82320 | Capitania dos Portos de Sergipe |
| 83310 | Capitania dos Portos de Alagoas |
| 83320 | Capitania dos Portos do Ceará |
| 83330 | Capitania dos Portos da Paraíba |
| 83340 | Capitania dos Portos de Pernambuco |
| 83350 | Capitania dos Portos do Rio Grande do Norte |
| 84310 | Capitania dos Portos da Amazônia Oriental |
| 84320 | Capitania dos Portos do Maranhão |
| 84330 | Capitania dos Portos do Piauí |
| 85320 | Capitania dos Portos do Paraná |
| 85330 | Capitania dos Portos do Rio Grande do Sul |
| 85340 | Capitania dos Portos de Santa Catarina |
| 89320 | Capitania Fluvial da Hidrovia Tietê-Paraná |

> Para a lista completa e atualizada:
> `curl -s https://dpc1.marinha.mil.br/scam/emitgruscam/solicitar_servico.asp | grep -oiE "<option[^>]*value=['\"][0-9]+['\"][^<]*</option>"`

## 8. Resumo da validação (24/jun/2026)
Geração real bem-sucedida via UI do backoffice: GRU `60893100226022026`, valor **R$ 60,32**,
PIX copia-e-cola EMV + QR PNG + vencimento. `GruService` persiste número/valor/PIX/expiração em
`reserva_habilitacao` e é **idempotente** (reaproveita GRU válida não vencida). Em falha, devolve
`sucesso=false` + `erroCodigo` → backoffice cai no **fluxo manual** (operador digita número/valor).
