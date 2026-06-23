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

## 9. Decisões já tomadas

- Robô = **microsserviço separado** (Node/TS + Playwright), chamado via HTTP.
- v1 = **só gerar** a GRU (número+valor+PIX+PDF) + **fallback manual**; status de pagamento e
  multi-capitania ficam para depois.
- Spike conduzido pelo **dono** (grava HAR/prints e envia).

## 10. Próximo passo

Fazer o **spike (§2)** e me enviar o **HAR + prints**. Com isso eu: confirmo CAPTCHA, mapeio
os campos/dropdowns e o formato da saída (PIX/PDF), e refino o fluxo Playwright + o contrato do
robô antes de escrever código.

Arquivos relacionados: `ReservaHabilitacao`, `HabilitacaoService`, `EmissaoService`
(`parseEndereco`), `Cliente`, `tenant` (`marinhaEmail`/`pixChave`), `OPAConfig` (padrão
WebClient), `PORTAL_CLIENTE_SPEC.md`.
