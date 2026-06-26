# GRU — Contrato HTTP detalhado (implementação)

> Sequência exata para gerar a GRU da Marinha + PIX do PagTesouro, **só HTTP** (sem navegador).
> Base do `GruClient` (WebClient + cookie jar). Derivado dos HARs/net-export do spike (23/jun).
> Valores de exemplo: Capitania SP, CHA Amador, CPF `28318538854`, CEP `11095460`.

## Sessão e regras gerais
- **Uma sessão só** (cookie jar). O passo 1 cria o cookie `ASPSESSIONIDxxxx` (ASP/IIS); todos os
  passos 1→5 reusam o **mesmo cookie**. A sessão é **single-use** (reabrir URLs depois = "sem
  sessão").
- `User-Agent` de browser real (a Marinha pode rejeitar UA vazio). `Accept-Language: pt-BR`.
- **Não seguir redirects automaticamente** no passo 3 (precisamos ler o `Location` p/ pegar o
  `id_gru`). Demais passos: ler o corpo.
- Passos 6-7 (PagTesouro) são REST/JSON, autenticados pelo **`idSessao`** no corpo/URL — **sem
  cookie/Authorization** do PagTesouro.
- Timeouts por passo (ex.: 15s) + 1 retry em falha transitória. Logar **sem PII** (CPF/endereço).

---

## Passo 1 — Abrir o formulário (cria a sessão)
```
GET https://dpc1.marinha.mil.br/scam/emitgruscam/solicitar_servico.asp
```
→ `200` + `Set-Cookie: ASPSESSIONIDxxxx=...`. **Guardar o cookie.**

## Passo 2 — Cascata (prepara o estado da sessão)
Headers: `Content-Type: application/x-www-form-urlencoded`, `Referer: .../solicitar_servico.asp`.
Cada um é um POST (corpo urlencoded). Codes da Capitania SP / Amador / Serv. Admin / CHA:

```
POST .../emitgruscam/objTipoRecolhimentoAjax.asp   v_cd_orgao=89310
POST .../emitgruscam/objTipoServicoAjax.asp         v_cd_orgao=89310&v_id_tipo_recolhimento=1
POST .../emitgruscam/objServicosAjax.asp            v_cd_orgao=89310&v_id_tipo_recolhimento=1&v_tipo_servico=060
POST .../emitgruscam/objQtdeServicoAjax.asp         v_sel_servico_adm=060;288  ;408
POST .../emitgruscam/objContribuinte.asp            v_nr_contribuinte=<CPF>&v_tipo_documento=CPF
POST .../emitgruscam/objCidadeAjax.asp              v_cep=<CEP>&v_nr_endereco=<n>&v_complemento_contribuinte=<c>
```
> ⚠️ `v_sel_servico_adm` = `060;288  ;408` — **preservar os espaços** (campo de largura fixa).
> Verificar na implementação se o passo 2 é necessário para o passo 3 (o servidor pode guardar a
> seleção na sessão). Se `pagtesouro.asp` funcionar só com o form do passo 3, a cascata vira
> opcional (mas é barata; manter por segurança).

## Passo 3 — Gerar a GRU
```
POST https://dpc1.marinha.mil.br/scam/emitgruscam/pagtesouro.asp
Content-Type: application/x-www-form-urlencoded
Referer: .../solicitar_servico.asp
body (campos do form frm_registro):
  cd_orgao=89310
  cd_servico=060;288  ;408
  tipo_documento=CPF
  nr_contribuinte=<CPF>
  ds_contribuinte=<NOME>
  nr_telefone=<tel>            ds_email=<email>
  sg_sexo=<M|F>               fg_incluir_contribuinte=<0|1>
  cep_contribuinte=<cep>      ender_contribuinte=<logradouro>
  nr_endereco=<num>           complemento_contribuinte=<compl>
  bairro_contribuinte=<bairro> municipio_contribuinte=<cidade>  uf_contribuinte=<uf>
```
→ **`302`** com `Location: pagtesouro_form.asp?id_gru=<N>&cpf_cnpj=<cpf>&svc=060;288  ;408&nome=<NOME>&qtd=`
→ **extrair `id_gru`** do Location. (Não seguir o redirect automaticamente.)
> Confirmar o conjunto exato de campos/ordem na 1ª execução real (corpo tinha 363 bytes). Os
> nomes acima vêm do fragmento `objServicosAjax.asp`.

## Passo 4 — Página-ponte (pega o `token`)
```
GET https://dpc1.marinha.mil.br/scam/emitgruscam/pagtesouro_form.asp?id_gru=<N>&cpf_cnpj=<cpf>&svc=060;288  ;408&nome=<NOME>&qtd=
```
→ `200` HTML com um form auto-submit. Extrair os hidden inputs:
```html
<form name="frm_envio" action="https://dpc1.marinha.mil.br/pagtesouro/index.php" method="post">
  <input name="req"      value="dpc1.marinha.mil.br">
  <input name="token"    value="141600814172120">   ← gerado server-side; LER daqui
  <input name="id_gru"   value="<N>">
  <input name="cpf_cnpj" value="<cpf>">
  <input name="nome"     value="<NOME>">
  <input name="id_svc"   value="060;288  ;408">
  <input name="qtd"      value="">
```
→ **extrair `token`** (e confirmar id_gru/cpf_cnpj/nome/id_svc).

## Passo 5 — Bridge → cria o `idSessao` no PagTesouro
```
POST https://dpc1.marinha.mil.br/pagtesouro/index.php
Content-Type: application/x-www-form-urlencoded
Referer: .../pagtesouro_form.asp?id_gru=<N>...
body: req=dpc1.marinha.mil.br&token=<token>&id_gru=<N>&cpf_cnpj=<cpf>&nome=<NOME>&id_svc=060;288  ;408&qtd=
```
→ `200` HTML que redireciona p/ `pagtesouro.tesouro.gov.br/#/pagamento?idSessao=<uuid>`.
→ **extrair `idSessao`** do corpo via regex `idSessao=([0-9a-f-]{36})`.

## Passo 6 — Dados do pagamento (PagTesouro)
```
GET https://pagtesouro.tesouro.gov.br/api/pagamentos/dados-pagamento?idSessao=<uuid>
```
→ `200` JSON:
```json
{ "contribuinte": {"tipoIdentificador":"CPF","codigoIdentificador":"<cpf>","nome":"<NOME>"},
  "descricao": "13508 - CHA EXAME/RENOV/2A VIA/CORRESP./EQUIVAL-TODAS CAT",
  "valor": 60.32,
  "referencia": "84512778",
  "numeroReferencia": "60893100225672026",     ← NÚMERO DA GRU
  "pspsMeioTaxa": [ {"id":"2","nome":"Tesouro Nacional","tipoPagamento":"PIX","valorTaxa":0}, ... ] }
```
→ guardar `valor` (gruValor), `numeroReferencia` (gruNumero), `descricao`.

## Passo 7 — Gerar o PIX
```
POST https://pagtesouro.tesouro.gov.br/api/pagamentos/meios-pagamento/pix
Content-Type: application/json
Origin: https://pagtesouro.tesouro.gov.br   Referer: https://pagtesouro.tesouro.gov.br/
body: { "idSessao": "<uuid>",
        "informacoesAdicionais": [ {"nome":"Origem","valor":"PagTesouro"},
                                   {"nome":"Serviço","valor":"<descricao do passo 6>"} ] }
```
→ `200` JSON:
```json
{ "conteudo": "00020101021226930014br.gov.bcb.pix...TESOURO NACIONAL...",  ← PIX copia-e-cola (EMV)
  "imagem": "<base64 PNG do QR>",
  "dataExpiracao": "24/06/2026 20:10",
  "url": "apipixstn.tesouro.gov.br/v2/..." }
```
→ guardar `conteudo` (pixCopiaECola), `imagem` (QR), `dataExpiracao` (vencimento do PIX).

---

## Saída do `GruClient`
```
GruResultado {
  gruNumero      = dados-pagamento.numeroReferencia
  gruValor       = dados-pagamento.valor
  descricao      = dados-pagamento.descricao
  pixCopiaECola  = pix.conteudo
  pixQrPngBase64 = pix.imagem
  pixExpiracao   = pix.dataExpiracao        // "dd/MM/yyyy HH:mm"
  idGru          = id_gru (passo 3)         // referência interna da Marinha
}
```

## Tratamento de erro (mapear para fallback manual)
- Passo 1/2/3 não-200/302, sem cookie, sem `id_gru` no Location → `MARINHA_INDISPONIVEL`.
- Passo 4 sem `token` / passo 5 sem `idSessao` → `BRIDGE_FALHOU`.
- Passo 6/7 não-200 ou sem `conteudo` → `PAGTESOURO_FALHOU`.
- Qualquer erro → o backend mantém o **fluxo manual** (operador digita gruNumero/valor).

## Alternativa: BOLETO (PDF) em vez de PIX
Mesmos passos 1-2 (sessão + cascata). A partir daí, em vez de `pagtesouro.asp`:
```
3b. POST .../scam/emitgruscam/atualiza_gru.asp   (MESMO corpo do frm_registro do passo 3)
    → 302 Location: imprime_gru.asp?v_id_gru=<N>&v_cd_recolhimento=060   (extrair id_gru)
4b. GET  .../scam/emitgruscam/imprime_gru.asp?v_id_gru=<N>&v_cd_recolhimento=060
    → 302 Location: /scam/emitgruscam/gru/tmp/<arquivo>.pdf
5b. GET  .../scam/emitgruscam/gru/tmp/<arquivo>.pdf   → 200 application/pdf (o boleto)
```
Implementado em `GruClient.gerarBoleto` (GET binário p/ o PDF). `GruService.gerarBoleto` salva o
PDF no storage (`{tenant}/reserva/{id}/gru-boleto.pdf`, campo `gru_pdf_s3_key`) e devolve URL
presignada. Endpoint `POST .../habilitacao/gru/boleto`. ⚠️ Boleto e PIX geram **id_gru distintos**
(GRUs separadas) — escolher um método por reserva; idempotência reaproveita o boleto não pago.

## Verificação de pagamento (PIX) + comprovante
Guardar o `idSessao` (passo 5) ao gerar o PIX. Depois, consultar quando quiser:
```
GET https://pagtesouro.tesouro.gov.br/api/pagamentos/pix-stn/sonda?idSessao=<uuid>
   headers: Accept: application/json, Referer: https://pagtesouro.tesouro.gov.br/
```
- **PAGO** → resposta é um **objeto** com `"situacao":{"codigo":"CONCLUIDO","data":"...Z"}` +
  `idPagamento`, `numeroReferencia`, `refTran`, `contribuinte`, `valor`.
- **PENDENTE/não-pago** → array de erro `[{"codigo":"C0008","descricao":"Erro desconhecido..."}]`.
- O `idSessao` continua consultável **horas após** a geração (pagamento tardio funciona).

O **comprovante** ("Imprimir comprovante" do site) é só `window.print()` de um HTML — todos os
dados vêm do `pix-stn/sonda`. Geramos o PDF nós mesmos (`GruComprovantePdfService`/OpenPDF).
Existe tb o caminho `comprovante_pgt.php?id=base64(id_gru)` (HTML), não usado.
Há ainda um WebSocket SockJS/STOMP (`/api/sonda-pgto-ws/notificacoes-pgto-ws`) para push em
tempo real, mas o REST acima é suficiente para verificação sob demanda.

Implementado: `POST .../habilitacao/gru/verificar-pagamento` (marca gruPago + gera comprovante),
`GET .../habilitacao/gru/comprovante/download` (stream do PDF). Campos `gru_id_sessao` e
`gru_comprovante_s3_key` (V016).

## Notas de implementação
- **Confirmar na 1ª execução real**: corpo exato do passo 3 (363B) e do passo 5 (133B), e se a
  cascata (passo 2) é necessária. Fazer 1 chamada de teste controlada (sem volume).
- **Respeito ao site gov**: 1 GRU por vez por tenant, retry comedido, sem paralelismo agressivo.
- `dataExpiracao` curta → mostrar o vencimento ao cliente e permitir regerar (nova GRU) se vencer.
- O `numeroReferencia`/`valor` só existem após o passo 6 — a "geração" só é considerada completa
  com o PIX (passo 7) obtido.
