# Protótipo clicável — Portal do Cliente + Backoffice (staff)

Protótipo de **frontend** para validação com stakeholders, cobrindo **os dois
lados** do mesmo processo. **Não chama backend**: todos os dados são mock e o
estado vive no navegador (zustand + localStorage).

- **Portal do cliente** (`/`) — vitrine, reserva, sinal, habilitação, termos, conta.
- **Backoffice / staff** (`/staff`) — fila de validação de sinal + embarque assistido.
- Navegue entre os dois pelos links no rodapé.

Specs de referência: [`../../PORTAL_CLIENTE_SPEC.md`](../../PORTAL_CLIENTE_SPEC.md) ·
[`../../BACKOFFICE_VALIDACAO_SINAL_SPEC.md`](../../BACKOFFICE_VALIDACAO_SINAL_SPEC.md) ·
[`../../BACKOFFICE_ATENDIMENTO_ASSISTIDO_SPEC.md`](../../BACKOFFICE_ATENDIMENTO_ASSISTIDO_SPEC.md).

## Como rodar

```bash
npm install
npm run dev        # http://localhost:3003
```

## Roteiro de demonstração (happy path)

1. **/** — catálogo/marketplace → clique num modelo.
2. **Modelo** → "Verificar disponibilidade" → "Reservar agora".
3. **Wizard** → cria a conta (mock) e confirma a reserva.
4. No cliente **novo**, o sinal é **obrigatório** dentro do wizard (PIX +
   comprovante) e a conta nasce **restrita** (e-mail não verificado).
5. **Reserva** (`/conta/reservas/[id]`) — banner pedindo verificar e-mail +
   painel central:
   - **Pagamento do sinal** → *em análise* até o staff confirmar.
   - **Habilitação** → escolha "Não tenho" para ver o fluxo **EMA/CHA-MTA-E**
     (videoaula → anexos 5-C/5-B/1-C → documentos → GRU → demonstração presencial).
   - **Termos** → termo de responsabilidade da loja + aceite eletrônico.
6. **Verificar e-mail** (`/conta/verificar-email`) — OTP de 6 dígitos. A reserva
   só vira **garantida** com **sinal confirmado + e-mail verificado**; com as 3
   etapas + e-mail, fica **Pronta p/ check-in**.
7. **/conta/locacoes** — histórico → avaliar a experiência (estrelas).
8. **/conta/perfil** — dados, notificações por e-mail, LGPD e
   *Reiniciar protótipo* (limpa o estado mock).

Botões marcados com **▶︎ (demo)** simulam ações que, no produto real, são feitas
pelo staff no backoffice (ex.: confirmar comprovante, emitir CHA-MTA-E).

## Roteiro — Backoffice (staff)

Acesse `/staff` (ou o link "Backoffice (staff)" no rodapé).

1. **Painel** — atalhos para "Validar sinais" (com contador) e "Atendimento de balcão".
2. **Sinais a validar** (`/staff/sinais`) — fila dos comprovantes PIX enviados pelos
   clientes. Repare na linha com **valor divergente** (em vermelho) e nos ícones de
   **e-mail verificado/não verificado**. Clique **Revisar**:
   - preview do comprovante, valor recebido (editável), alerta de divergência e de
     capacidade, e **Confirmar** ou **Recusar** (com motivo).
3. **Atendimento de balcão** (`/staff/embarque`) — registro/documentação (NÃO é o
   check-in): Cliente (busca CPF → **pré-conta**) → **Documentos** (+ comprovante
   de residência com **autopreenchimento por CEP** quando o cliente não tem, e
   triagem de habilitação) → **Aluguel** (pagamento do valor **total**, sem sinal)
   → **Habilitação** (CHA coletada ou **GRU → CHA-MTA-E**) → **Termos com
   signature pad** (assine com o mouse) → **Emissão**.
   - Na **Emissão**, o botão **Abrir** mostra o **exemplo do PDF consolidado**
     (`/staff/documento`) — fiel aos anexos da NORMAM-212/DPC (1-C, 5-C, 5-B) +
     Termo da loja, preenchido; use **Imprimir / Salvar PDF**. Também envia à
     Marinha + e-mail (com link de ativação) + **GRU de saída** ao cliente.
   - O **check-in/embarque** (fotos, horímetro, início) é feito à parte.

> O estado do staff é independente do cliente. "Reiniciar protótipo" no perfil do
> cliente não zera a fila de sinais (chaves de storage separadas).

## O que é real vs. mock

- **Real:** stack-alvo (Next.js 15 / React 19 / Tailwind v4), navegação, IA de
  telas e os fluxos regulatórios da NORMAM-212/DPC.
- **Mock:** dados, autenticação (será Keycloak/OIDC PKCE), pagamentos (sem
  gateway no v1), geração de GRU e emissão da CHA-MTA-E (dependências de backend
  descritas na spec).
