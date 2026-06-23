# Robô GRU — Geração automática da GRU da Marinha (SPEC)

> Status: **proposta** (planejamento) — 23/jun/2026
> Objetivo v1: gerar automaticamente a **GRU** (Guia de Recolhimento da União) da Marinha
> para a **Carteira de Habilitação de Amador** (CHA-MTA-E / via EMA), retornando
> **número + valor + PIX (copia-e-cola/QR) + PDF + vencimento**, a partir dos dados que já
> temos do cliente. Mantém o preenchimento **manual** como fallback.
> Fora do v1: consulta de status de pagamento, múltiplas capitanias (só SP no v1).

## 1. Contexto e estado atual

Hoje a GRU é **manual**: o operador acessa o site da Marinha
(`https://dpc1.marinha.mil.br/scam/emitgruscam/solicitar_servico.asp`), preenche o
formulário, gera a GRU e **digita** `gruNumero`/`gruValor` no balcão. Os campos já existem em
`ReservaHabilitacao` (`gruNumero`, `gruValor`, `gruPago`, `gruPagoEm`).

Já temos **todos os dados** do cliente (`Cliente`): `nome`, `documento` (CPF), `rg`,
`orgaoEmissor`, `dataNascimento`, e `enderecoJson` (cep, logradouro, número, complemento,
bairro, cidade, estado/uf). O `PORTAL_CLIENTE_SPEC.md` já previa esta fase: *"vamos construir
um Robô para emitir essa GRU... e retornar o pix/boleto e o número da GRU"*.

**Não há API** — o site é ASP legado com **dropdowns em cascata via AJAX**
(Organização → Categoria → Tipo de Serviço → Item de Serviço). Por isso a abordagem é **RPA
com navegador headless (Playwright)**, não engenharia reversa de HTTP.

Seleções fixas do v1 (Capitania SP):
- **Organização**: Capitania dos Portos de São Paulo
- **Categoria**: Amador
- **Tipo de Serviço**: Serviços Administrativos
- **Item de Serviço**: Carteira de Habilitação de Amador (confirmar texto exato no spike)

## 2. Spike de descoberta (PRÉ-REQUISITO — bloqueia o build)

Não dá pra implementar o robô sem o fluxo real. **Capturar uma vez, gerando uma GRU de teste**
e enviar:

**a) HAR do navegador** — DevTools → aba **Network** → marcar *Preserve log* → percorrer o
fluxo inteiro (selecionar organização/categoria/tipo/item → preencher dados → gerar GRU) →
botão direito → **Save all as HAR** → me enviar o arquivo. Isso revela os endpoints AJAX,
campos POST e a resposta final.

**b) Prints de cada etapa** (organização, categoria, tipo, item, formulário de dados, tela
final da GRU com o PIX/QR).

**c) Confirmar especificamente:**
1. **Tem CAPTCHA / reCAPTCHA / código de imagem** em algum passo (especialmente no envio)?
2. **Formato da saída**: a GRU vem como **PDF**? Página HTML? Onde está o **PIX copia-e-cola**
   e/ou **QR code**? Tem **boleto/código de barras** também?
3. **Número da GRU, valor e vencimento** — onde aparecem (texto exato, rótulos).
4. **Validações** do site: o que ele exige (CPF válido, endereço completo, email?) e como
   reage a erro (mensagem, campo destacado).
5. **Texto EXATO** das opções dos dropdowns (Item de Serviço costuma ter nome longo).
6. O fluxo pede **login**? (aparentemente não, mas confirmar.)

> ⚠️ Se houver **CAPTCHA** no envio, a automação total fica inviável sem intervenção humana —
> nesse caso o v1 vira "robô pré-preenche e o operador resolve o CAPTCHA + confirma" (semi-RPA).
> O spike decide isso.

## 2.1 O que o spike revelou (23/jun, parcial — sem CAPTCHA)

**Fluxo em DOIS sistemas:** Marinha (formulário/GRU) → redireciona para **PagTesouro**
(`pagtesouro.tesouro.gov.br`, plataforma de pagamento do Tesouro Nacional) que gera o **PIX**.

**Marinha — cascata AJAX (POSTs em `dpc1.marinha.mil.br/scam/emitgruscam/`):**
| Passo | Endpoint | Params (códigos reais SP/Amador/CHA) |
|---|---|---|
| Órgão→Recolhimento | `objTipoRecolhimentoAjax.asp` | `v_cd_orgao=89310` (Capitania SP) |
| →Tipo serviço | `objTipoServicoAjax.asp` | `+v_id_tipo_recolhimento=1` (Amador) |
| →Serviços | `objServicosAjax.asp` | `+v_tipo_servico=060` (Serv. Administrativos) |
| →Qtde | `objQtdeServicoAjax.asp` | `v_sel_servico_adm=060;288;408` (288 = CHA) |
| Contribuinte | `objContribuinte.asp` | `v_nr_contribuinte=<CPF>`, `v_tipo_documento=CPF` |
| CEP→Cidade | `objCidadeAjax.asp` | `v_cep=<cep>`, `v_nr_endereco`, `v_complemento_contribuinte` |

**PagTesouro — `GET /api/pagamentos/dados-pagamento?idSessao=<uuid>` (JSON):**
`valor` (ex.: 60.32), `numeroReferencia` (ex.: "60893100225672026" = **número da GRU**),
`referencia`, `descricao` ("13508 - CHA EXAME/RENOV/2A VIA/..."), `pspsMeioTaxa[]` (PIX = id 2,
"Tesouro Nacional", taxa 0; cartão = Mercado Pago/etc. com taxa ~1.56%).

**PagTesouro — geração do PIX (API JSON limpa):**
```
POST pagtesouro.tesouro.gov.br/api/pagamentos/meios-pagamento/pix   (Content-Type: application/json)
body: {"idSessao":"<uuid>","informacoesAdicionais":[{"nome":"Origem","valor":"PagTesouro"},
                                                     {"nome":"Serviço","valor":"<descricao>"}]}
→ {"conteudo":"00020101...TESOURO NACIONAL...",  // PIX copia-e-cola (EMV)
   "imagem":"<base64 PNG>",                       // QR code
   "dataExpiracao":"24/06/2026 20:10", "url":"apipixstn.tesouro.gov.br/v2/..."}
```
Confirmação de pagamento (Fase 2): `GET/ws .../api/sonda-pgto-ws/notificacoes-pgto-ws/...`
(websocket de notificação de pagamento).

**Geração da GRU (Marinha) — sem token!** O form `frm_registro` em `solicitar_servico.asp`
faz `POST atualiza_gru.asp` com `target="_blank"` (por isso abre nova aba e some do Network da
aba original). **NÃO tem `__VIEWSTATE` / CSRF / token** — é POST de formulário puro com os
campos da cascata + contribuinte + endereço. O retorno (nova aba) traz o `id_gru` e o botão
"Pagar", que leva a `pagtesouro_form.asp?id_gru=...&cpf_cnpj=...&svc=...&nome=...&qtd=`.

**Fluxo completo HTTP (reverse-engineered 100% — net-export), numa única sessão (cookie jar):**
```
1. GET  dpc1.../scam/emitgruscam/solicitar_servico.asp        → cookie ASPSESSIONID...
2. POSTs cascata (obj{TipoRecolhimento,TipoServico,Servicos,QtdeServico,Contribuinte,Cidade}Ajax.asp)
   p/ preparar o estado da sessão (org 89310, recolh 1, serv 060, item 060;288;408, CPF, CEP)
3. POST dpc1.../scam/emitgruscam/pagtesouro.asp  (form urlencoded ~363B)
        → 302 Location: pagtesouro_form.asp?id_gru=<N>&cpf_cnpj=&svc=&nome=&qtd=   ← gera o id_gru
4. GET  dpc1.../scam/emitgruscam/pagtesouro_form.asp?id_gru=<N>&...
        → HTML com <form name=frm_envio action="/pagtesouro/index.php"> (auto-submit), campos:
          req, token (server-side, no HTML), id_gru, cpf_cnpj, nome, id_svc, qtd
5. POST dpc1.../pagtesouro/index.php  (req, token, id_gru, cpf_cnpj, nome, id_svc, qtd)
        → HTML que redireciona p/ pagtesouro.tesouro.gov.br/#/pagamento?idSessao=<uuid>
          (extrair idSessao do corpo via regex)
6. GET  pagtesouro.tesouro.gov.br/api/pagamentos/dados-pagamento?idSessao=<uuid>
        → { valor, numeroReferencia (=nº GRU), referencia, descricao, pspsMeioTaxa[] }
7. POST pagtesouro.tesouro.gov.br/api/pagamentos/meios-pagamento/pix
        body { idSessao, informacoesAdicionais:[{Origem:PagTesouro},{Serviço:<descricao>}] }
        → { conteudo (PIX copia-e-cola EMV), imagem (QR png b64), dataExpiracao, url }
```
Cookie ASP **server-side e single-use** → o robô faz 1→5 **na mesma sessão**, sem reaproveitar
URLs. PagTesouro (passos 6-7) é REST/JSON, autenticado pelo `idSessao` (sem cookie do PagTesouro
no fluxo capturado). **`token` e `idSessao` são gerados server-side e lidos do HTML** — não há
nada computado por JS no cliente.

**Campos do form `frm_registro` (do fragmento `objServicosAjax.asp`) — POST pagtesouro.asp:**
`cd_orgao` (89310), `cd_servico` (item CHA), `tipo_documento` (CPF), `nr_contribuinte` (CPF),
`ds_contribuinte` (nome), `nr_telefone`, `ds_email`, `sg_sexo` (radio), `fg_incluir_contribuinte`
(hidden), `cep_contribuinte`, `ender_contribuinte`, `nr_endereco`, `complemento_contribuinte`,
`bairro_contribuinte`, `municipio_contribuinte`, `uf_contribuinte`. Botões: `bt_pgtesouro` →
`pagtesouro.asp` (PIX/cartão); `bt_gerar_boleto` → `atualiza_gru.asp` (boleto). **Reverse-eng
COMPLETO — não faltam capturas.**

**Mapeamento `cliente` → form:** `cd_orgao`=tenant.capitania (89310 SP); `cd_servico`=item CHA;
`nr_contribuinte`=`cliente.documento` (CPF); `ds_contribuinte`=`cliente.nome`;
`nr_telefone`/`ds_email`=`cliente.telefone`/`email`; `sg_sexo`=`cliente.genero`;
`cep/ender/nr_endereco/complemento/bairro/municipio/uf`=`cliente.enderecoJson`.

`gruNumero` = `numeroReferencia`; `gruValor` = `valor`; PIX (copia-e-cola/QR/`dataExpiracao`) do
PagTesouro. Confirmação de pagamento (Fase 2): websocket `sonda-pgto-ws`. A GRU PDF (Marinha)
traz linha digitável/código de barras para banco.

**REVISÃO DE ARQUITETURA:** como NÃO há CAPTCHA nem token JS e tudo é HTTP, **não precisa de
Playwright/Chromium nem de microsserviço separado** — dá para implementar com **`WebClient` +
cookie jar dentro do backend Java** (um `GruClient`/`GruService` no módulo `locacoes`). Decisão
anterior (microsserviço Node+Playwright) fica **substituída** por HTTP-no-monólito, salvo se na
implementação aparecer algum passo que exija navegador. Falta só **1 captura** para travar os
nomes exatos dos campos do POST `atualiza_gru.asp` e o handoff (ver §10).

## 3. Arquitetura

Microsserviço **robô GRU** isolado (**Node/TypeScript + Playwright**), chamado pelo backend
Java via HTTP — mesmo padrão do OPA (`WebClient`). Isola o navegador headless, dependências e
fragilidade do monólito; escala e falha sozinho.

```
Backend (Java)  --HTTP-->  robô-gru (Node + Playwright)  --headless browser-->  site Marinha
   GruService                 POST /gru/gerar
   (persiste, idempotência)   (stateless: 1 GRU por chamada)
```

- **Rede**: robô na rede interna (não exposto publicamente). Auth backend↔robô por **segredo
  compartilhado** (header) ou mTLS.
- **PII**: o robô recebe CPF/endereço → **não logar PII**, processar e descartar; container
  efêmero. Egress estável (IP fixo) para reduzir bloqueio do site gov.
- **Deploy**: novo serviço no `docker-compose` (imagem com Playwright + Chromium). `mem_limit`
  dedicado (browser headless consome memória).

## 4. Contrato do robô (HTTP)

```
POST /gru/gerar        (header: X-Robo-Secret)
{
  "organizacao": "Capitania dos Portos de São Paulo",
  "categoria": "Amador",
  "tipoServico": "Serviços Administrativos",
  "itemServico": "Carteira de Habilitação de Amador ...",
  "contribuinte": {
    "nome": "...", "cpf": "...", "rg": "...", "orgaoEmissor": "...",
    "dataNascimento": "1990-05-01",
    "email": "...", "telefone": "...",
    "endereco": { "cep":"", "logradouro":"", "numero":"", "complemento":"",
                  "bairro":"", "cidade":"", "uf":"" }
  }
}
→ 200
{
  "gruNumero": "...", "gruValor": 12.34, "vencimento": "2026-07-15",
  "pixCopiaECola": "000201...", "pixQrPngBase64": "iVBOR...",   // QR opcional
  "pdfBase64": "JVBERi0...",                                     // PDF da GRU
  "geradoEm": "2026-06-23T20:00:00Z"
}
→ 4xx/5xx { "erro": "CAPTCHA_REQUIRED" | "VALIDACAO" | "SITE_INDISPONIVEL" | "TIMEOUT" ,
            "detalhe": "..." }
```

**Fluxo Playwright (alto nível, refinar com o spike):** abrir página → selecionar
organização → aguardar AJAX → categoria → tipo → item → preencher contribuinte/endereço →
submeter → aguardar GRU → extrair número/valor/vencimento + PIX (copia-e-cola/QR) + baixar PDF
→ retornar. Com **retry** (1-2x) em falha transitória e **timeout** por etapa.

## 5. Integração no backend (Java)

- **`GruService`** (módulo `locacoes`, junto de `HabilitacaoService`): chama o robô via
  `WebClient` (padrão do `OPAConfig`), persiste o resultado, garante idempotência.
- **Endpoint**: `POST /v1/tenants/{tenantId}/reservas/{id}/habilitacao/gru` (já previsto no
  `PORTAL_CLIENTE_SPEC.md`) → monta o `contribuinte` a partir de `Cliente` (reusa o
  `parseEndereco` do `EmissaoService`), chama o robô, salva e retorna o PIX/valor/vencimento.
- **Persistência** — novos campos em `reserva_habilitacao` (migração Flyway + refletir no
  `reset-ambiente-dev.sh`):
  `gru_pix_copia_e_cola`, `gru_vencimento`, `gru_pdf_s3_key`, `gru_status`
  (`NAO_GERADA|GERADA|PAGA`), `gru_gerada_em`. (Mantém `gru_numero`/`gru_valor`/`gru_pago`.)
- **PDF da GRU** → `storageService.putObject` com chave
  `{tenantId}/reserva/{reservaId}/gru.pdf` (mesmo padrão do documento consolidado).
- **Idempotência (backend-side)**: se já existe `gruNumero` válido e não vencido → retorna o
  existente; só chama o robô se não houver GRU ou estiver vencida. Cada chamada ao robô gera
  uma **nova obrigação de pagamento** — nunca chamar em duplicidade.
- **Config por tenant**: qual **organização/capitania** usar (tenant de SP → Capitania de SP).
  Adicionar `tenant.capitania_org` (ou reaproveitar cidade/UF para resolver). `tenant.pixChave`
  e `marinhaEmail` já existem.
- **Fallback manual preservado**: se o robô falhar (CAPTCHA, site fora, validação), o operador
  ainda digita `gruNumero`/`gruValor` como hoje. A automação é uma conveniência, não um
  bloqueio.
- **Evento de auditoria**: `GruGeradaEvent` (reusa o sistema async de audit-events).

## 6. Frontend (backoffice + portal)

No fluxo de **habilitação / balcão** (via EMA): botão **"Gerar GRU"** →
- chama o endpoint → mostra **PIX copia-e-cola** (com botão copiar) + **QR** + **valor** +
  **vencimento** + **baixar PDF da GRU**.
- mantém os campos manuais de `gruNumero`/`gruValor` como **fallback** (editáveis se o robô
  falhar).
- estado de loading (gera em segundos a dezenas de segundos — é navegador headless).

## 7. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| **Site gov muda** (quebra o robô) | Fallback manual sempre disponível; seletores robustos; smoke/monitor; alertar quando falhar |
| **CAPTCHA / anti-bot** | Spike confirma; se houver, vira semi-RPA (operador resolve) — decisão pós-spike |
| **Bloqueio de IP / rate-limit** | Egress estável, pacing, retry com backoff, 1 GRU por vez por tenant |
| **GRU duplicada** | Idempotência backend-side (não regenerar se já existe e não vencida) |
| **Extração errada do PIX/valor** | Validar formato (PIX EMV, valor numérico); falhar explícito em vez de gravar lixo |
| **Confirmação de pagamento** | Deferido (v2): consultar status na Marinha **ou** cliente envia comprovante (modelo atual) |
| **PII** | Robô não loga CPF/endereço; tráfego interno; segredo backend↔robô |
| **Legal/ToS** | Uso legítimo (pagar taxa que o cliente deve); revisar ToS do site; o robô só automatiza o que um humano faria |

## 8. Fases

- **Fase 0 — Spike** (§2): capturar o fluxo real (HAR + prints + CAPTCHA + saída). **Bloqueante.**
- **Fase 1 — v1**: robô (Playwright) gera a GRU para SP + endpoint + persistência + UI +
  fallback manual. (Esta spec.)
- **Fase 2** (futuro): consulta de status de pagamento (robô "consultar GRU"), múltiplas
  capitanias/organizações, retomada/retry resiliente, monitoramento dedicado.

## 9. Decisões

- ~~Microsserviço Node+Playwright~~ → **HTTP no backend Java** (`WebClient` + cookie jar). O
  spike provou: sem CAPTCHA, sem `__VIEWSTATE`/token, PagTesouro é REST/JSON → não precisa de
  navegador nem serviço separado. (Reavaliar só se a implementação esbarrar em algo que exija
  browser.)
- v1 = **só gerar** a GRU (número+valor+PIX+PDF) + **fallback manual**; status de pagamento
  (websocket `sonda-pgto-ws`) e multi-capitania ficam para depois.

## 10. Próximo passo

Falta **1 captura**: o **POST `atualiza_gru.asp`** (a nova aba que abre ao clicar em gerar/pagar
— `target="_blank"`). Como abre em outra aba, a forma confiável de capturar é uma das:
- **`chrome://net-export`** → *Start Logging to Disk* → refazer o fluxo até a aba da GRU →
  *Stop* → me enviar o `.json` (captura TODAS as abas), **ou**
- DevTools → ⚙ Settings → marcar **"Auto-open DevTools for popups"** → refazer → exportar o HAR
  da aba nova.

Quero ver: os **nomes exatos dos campos** do POST `atualiza_gru.asp` (o corpo) e o **HTML de
resposta** (o `id_gru` e o link `pagtesouro_form.asp`). Com isso travo o contrato e parto para a
implementação (`GruService` + `GruClient` no módulo `locacoes`).

Arquivos relacionados: `ReservaHabilitacao`, `HabilitacaoService`, `EmissaoService`
(`parseEndereco`), `Cliente`, `tenant` (`marinhaEmail`/`pixChave`), `OPAConfig` (padrão
WebClient), `PORTAL_CLIENTE_SPEC.md`.
