# Jornada — Emissão de Arrais Amador (temporário) + Aluguel de Jetski (Balcão)

> Jornada de referência do atendimento de balcão + mapeamento ao que já está
> implementado (roteiro de evolução). Canal foco: **balcão** (presencial assistido).
> Há também o canal **online** (portal do cliente), fora deste documento.

## Contexto
SaaS B2B para locadoras de jetski que atendem turistas. Muitos clientes não têm habilitação
náutica, então o sistema emite uma **habilitação amador TEMPORÁRIA (CHA-MTA-E, via EMA)**
junto com o aluguel. Integrações: **Marinha/Capitania** (emite a GRU e recebe a documentação
por e-mail) e **PagTesouro/Tesouro Nacional** (pagamento da GRU por PIX/boleto).

## Atores
Operador de balcão · Cliente (locatário) · Instrutor (EAMA) · Marinha/Capitania · PagTesouro.

## Jornada (passo a passo)
1. **Identificação & cadastro** — cliente se identifica (CPF); busca na base local; se novo,
   pré-preenche o nome consultando o CPF na Marinha. Coleta dados básicos (nome,
   celular/WhatsApp, e-mail).
2. **Passeio & preço** — seleciona o **modelo** de jetski e o **tempo** do passeio; o sistema
   **apresenta o preço** na hora.
3. **Dados complementares** — RG/órgão emissor, nacionalidade, naturalidade, endereço
   residencial (reaproveitado se já salvo).
4. **Documentos** — coleta para validação e **armazenamento**: documento de identidade,
   comprovante de residência e selfie (upload ou foto pela câmera/webcam); vinculados ao
   cliente e reutilizáveis.
5. **Decisão de habilitação** — já tem CHA válida (informa categoria/número/validade) ou vai
   **emitir a temporária** (passos 6–7)?
6. **Emissão da CHA temporária**
   a. Gera e cobra a **GRU** (PIX/QR ou boleto) via PagTesouro; verificação automática do
      pagamento + comprovante.
   b. Pode enviar o **1º e-mail ao cliente com o número da GRU**.
   c. Pré-requisitos: **vídeos** (videoaula) → **termos** → **assinatura** (autodeclaração de
      saúde 5-C e demais anexos).
7. **Instrutor** — escolhe o instrutor (EAMA) do atestado de demonstração (5-B-1).
8. **Emissão da documentação & preparo p/ Marinha** — gera o PDF consolidado (anexos
   NORMAM-212 + identidade + comprovante + selfie), disponibiliza ao cliente (download/e-mail)
   e prepara o envio à Marinha. **REGRA: o e-mail à Marinha só sai se TODA a documentação
   estiver cumprida.**
9. **Desfecho — reserva + fila de espera**
   - Tudo cumprido → **reserva CONFIRMADA**.
   - Faltou documento → **reserva PENDENTE** (cliente se compromete a entregar depois; ao
     completar, libera o envio à Marinha).
   - Em ambos os casos, entra na **FILA DE ESPERA** para realizar o passeio.

## Regras de negócio
- E-mail à Marinha exige documentação 100% completa (bloqueado caso contrário).
- Reserva CONFIRMADA = tudo pronto; PENDENTE = falta documento (segue na fila).
- Fila de espera independe de pendência; o cliente aguarda o jetski do modelo escolhido ficar
  disponível por **horário**.
- O jetski **não é consumido na reserva** — a alocação acontece no **embarque (check-in)**.
- GRU é obrigação de pagamento: não duplicar; reaproveitar GRU válida; PIX vence; pagamento
  confirmado é idempotente.
- Pagamento do aluguel é integral no balcão. Tudo coletado é reutilizável numa próxima visita.

## Estados da reserva
PENDENTE → CONFIRMADA → EM CURSO (locação) → FINALIZADA · (CANCELADA / EXPIRADA).

---

## Mapeamento ao código atual (o que já temos × gaps)

| Passo da jornada | Hoje no código | Gap |
|---|---|---|
| 1. Identificação & cadastro | `StepCliente` (busca CPF, pré-conta, consulta nome Marinha, celular único +país) | ok |
| 2. Passeio & preço | `StepAluguel` (modelo + duração + preço) — porém hoje vem **depois** de Documentos | reordenar p/ logo após identificação |
| 3. Dados complementares | `StepDocumentos` (RG/órgão/nac./natur./endereço reaproveitado) | ok |
| 4. Documentos | `StepDocumentos` anexos (identidade/comprovante/selfie, upload+webcam) → `cliente_anexo` | ok |
| 5. Decisão habilitação | `StepHabilitacao` (CHA vs EMA) | ok |
| 6a. GRU | `GruClient`/`GruService` (PIX/boleto/verificar pagamento/comprovante) | ok |
| 6b. 1º e-mail com nº da GRU | — | **falta** |
| 6c. Vídeos→termos→assinatura | videoaula (checkbox), `StepTermos` (assinatura), anexos 5-C | vídeo é só checkbox |
| 7. Instrutor | `StepHabilitacao` (select de instrutor EMA) | ok |
| 8. Emissão + envio Marinha | `EmissaoService` gera PDF + envia Marinha **e** cliente | **falta travar Marinha por documentação completa** |
| 9. Reserva confirmada/pendente | emissão confirma a reserva (PENDENTE→CONFIRMADA) | falta "pendente por falta de doc" + compromisso de entrega |
| 9. Fila de espera | — (jetski alocado só no embarque) | **falta o conceito de fila por modelo/horário** |

## Implementado nesta leva (jun/2026)
- **Wizard reordenado**: Cliente → **Passeio & Preço** → Documentos → Habilitação → Termos → Emissão.
- **Travar Marinha por documentação completa** (habilitação resolvida + termos + anexos NORMAM
  5-C/regras/residência + instrutor). Completo → reserva CONFIRMADA; faltando → PENDENTE (segue na fila).
  `StepEmissao` lista as pendências.
- **1º e-mail ao cliente com o nº da GRU** (botão no drawer).
- **Fila de espera** (`/dashboard/fila`): atendimentos concluídos aguardando embarque, por modelo/horário;
  jetski alocado só no embarque (não consome unidade na reserva).
- **Checklist de pré-requisitos (EMA)** com tick por item no drawer da reserva.

> Pendente/futuro: vídeo como conteúdo de fato (hoje é checkbox); refinos da fila (chamar próximo,
> notificação ao cliente); pendências entregues depois → reenvio automático à Marinha quando completar.
