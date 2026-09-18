# Ecossistema sintético do espelho — SPEC

> **Status:** decisões tomadas (§7); implementação por fases (§6) em andamento.
> **Contexto:** o espelho de carga ([`infra/espelho/`](infra/espelho/README.md)) roda a
> mesma stack de produção. Hoje ele é uma casca: sem ninguém operando, sem Marinha,
> sem pagamento. Esta spec transforma o espelho num **ecossistema sintético completo** —
> sistemas externos simulados e personas que se comportam como os usuários reais,
> operando pelas **mesmas APIs** de produção.

## 1. Princípios

1. **O código de produção não muda para o espelho.** Nada de flag "auto-aprovar" ou
   caminho de teste no backend. Quem difere são as **bordas**: os sistemas externos
   viram fakes e as pessoas viram personas. Tudo o que é código Meu Jet roda igual.
2. **Personas agem pelas APIs reais.** A empresa de carga é aprovada pelo operador de
   plataforma chamando `POST /v1/platform/tenants/{id}/approve` — com evento, assinatura
   de trial e auditoria, exatamente como em produção. Login pelo fluxo real do Keycloak,
   inclusive 2FA.
3. **Fakes fiéis ao contrato, não ao "caminho feliz".** O fake da Marinha reproduz
   sessão por cookie, token de ponte, `idSessao`, expiração do PIX, bloqueio por volume
   por CPF e indisponibilidade — porque é isso que o código precisa aguentar.
4. **Falha fechada.** Uma configuração errada no espelho nunca pode alcançar a Marinha,
   o Tesouro ou uma caixa de e-mail real (ver §5).
5. **Dado 100% sintético — e isolado.** E-mails em `@exemplo.invalid` (RFC 2606), nomes
   de persona inconfundíveis. **CPF: não existe faixa reservada para teste** (os 8
   primeiros dígitos são o número-base e o 9º é a região fiscal; a única regra oficial é
   que 11 dígitos iguais nunca são emitidos) — um CPF sintético de DV válido **pode
   coincidir com o de uma pessoa real**. A proteção não é a faixa, é o isolamento: o dado
   nunca sai do espelho (§5). A faixa 9xx fica só como marcador de dado de teste.

## 2. Inventário: o que a plataforma fala com o mundo

Levantado no código em 17/set/2026 (chamadas HTTP de saída, SMTP, navegador).

### 2.1 Backend → sistemas externos

| Integração | Onde | Para quê | Endereço configurável? | Efeito real se vazar |
|---|---|---|---|---|
| **Marinha — SCAM** (`dpc1.marinha.mil.br`) | `GruClient` | gerar GRU (PIX e boleto), consultar nome por CPF | ✅ `jetski.gru.marinha-base` | **GRU real emitida**; bloqueio da Marinha por volume (~8–10/dia/CPF) |
| **Tesouro — PagTesouro** (`pagtesouro.tesouro.gov.br`) | `GruClient` | dados do pagamento, gerar PIX, sondar pagamento | ✅ `jetski.gru.pagtesouro-base` | cobrança PIX real |
| **Carimbo de tempo RFC 3161** (ex.: `freetsa.org`) | `CarimboTempoService` | carimbar assinaturas | ✅ por empresa (`AssinaturaConfig.tsaUrl`); vazio = âncora HMAC própria, sem chamada | carimbo real em TSA de terceiro |
| **SMTP da plataforma** | `SmtpEmailService` (15 serviços) | convite, aprovação, fatura, OTP de aceite, PIX de reserva, claim… | ✅ `PLATFORM_SMTP_*` | e-mail real a cliente |
| **SMTP da empresa emissora** | `SmtpSenderFactory` | **ofício à Capitania** (anexo com dados do cliente) | ✅ por empresa; **AUTH sempre ligado**, STARTTLS por empresa | ofício real a uma Capitania |
| **SMTP do Keycloak** | realm + SPI `meujet-email-code` | código de login do portal, reset de senha | ✅ realm | e-mail real |
| Google (IdP) | Keycloak broker | "Entrar com Google" | ✅ realm | login real no Google |

Não há API de WhatsApp: o OTP por WhatsApp é um link `wa.me` montado pelo backend.
PIX de reserva e compra de créditos geram BR Code **localmente** e a confirmação é manual
(comprovante + validação do operador) — não há PSP a simular, há **comportamento** a simular.

### 2.2 Navegador → terceiros (só jornadas via UI)

ViaCEP, IBGE (municípios), YouTube/Vimeo (videoaula obrigatória do balcão), Google Maps,
Instagram. São leituras públicas sem efeito colateral. Só importam para personas que
dirigem o **navegador** (Playwright); cenários de API (k6, motor de personas) não as tocam.

## 3. Arquitetura

```
                       ┌──────────────── VM do espelho ────────────────┐
motor de personas ──►  │ nginx ─► backend ─► fakes-externos            │
 (fora da VM)          │              │        ├─ /marinha  (SCAM)     │
k6 (fora da VM) ────►  │              │        ├─ /pagtesouro          │
                       │              │        ├─ /tsa  (RFC 3161)     │
                       │              │        └─ /_controle (cenários)│
                       │              └─► mailpit (SMTP realista + API)│
                       │ keycloak ─────────► mailpit                   │
                       └───────────────────────────────────────────────┘
```

### 3.1 `fakes-externos` — um container, várias bordas

Serviço próprio, na camada `docker-compose.espelho.yml`. O backend aponta para ele só
por configuração (`jetski.gru.marinha-base=http://fakes-externos:8080/marinha` etc.).

**Marinha (SCAM)** — reproduz [`GRU_HTTP_CONTRACT.md`](GRU_HTTP_CONTRACT.md) passo a passo:
- passo 1 cria cookie `ASPSESSIONID…`; sessão **single-use** (reabrir = "sem sessão");
- cascata (passo 2) valida os códigos (`89310`, `060;288  ;408` com os espaços);
- passo 3 responde **302** com `Location: pagtesouro_form.asp?id_gru=<N>…`;
- passo 4 devolve o HTML com `token` gerado; passo 5 devolve HTML com `idSessao` UUID;
- boleto (3b–5b) devolve um PDF sintético com número extraível pelo mesmo regex;
- `objContribuinte.asp` devolve nome para CPFs sintéticos.

**PagTesouro** — `dados-pagamento`, `meios-pagamento/pix` (BR Code EMV **válido na
estrutura**, marcado como sintético, QR PNG real do texto), `pix-stn/sonda` com
**máquina de estados**: `PENDENTE` (array de erro `C0008`) → `CONCLUIDO` (objeto com
`situacao`), `idSessao` consultável por horas, PIX com `dataExpiracao` curta.

**TSA** — responde RFC 3161 com token assinado por uma CA sintética (ex.: `openssl ts`).
Só é chamado por empresas-persona configuradas com `tsaUrl` apontando para o fake.

**`/_controle`** — a alavanca dos cenários, usada pelo motor de personas e pelo k6.
**Nunca exposta**: o espelho é público, então `/_controle` escuta só em `127.0.0.1` da VM,
fora do nginx e do túnel; quem roda de fora chega por túnel SSH (como o Mailpit).
Operações:
- `POST /_controle/gru/{idSessao}/pagar` (ou pagamento automático após N minutos);
- falhas por etapa com a mesma taxonomia do `GruClient`: `MARINHA_INDISPONIVEL`,
  `BRIDGE_FALHOU`, `PAGTESOURO_FALHOU`;
- latência por endpoint; **bloqueio por volume por CPF/dia** (padrão: o observado, ~8–10);
- `GET /_controle/eventos` para asserções; `POST /_controle/reset`;
- `GET /metrics` (Prometheus): chamadas por etapa, falhas e **latência injetada** — para
  o Grafana do espelho separar "lentidão que o cenário pediu" de "lentidão do backend".

**Tecnologia sugerida:** serviço pequeno em TypeScript/Node. WireMock foi considerado e
preterido: sessão single-use, tokens encadeados entre passos e máquina de estados com
controle externo ficam mais claros em código do que em stubs declarativos.

### 3.2 SMTP realista (Mailpit)

O espelho hoje **desliga AUTH/STARTTLS no backend** (`SPRING_APPLICATION_JSON`) para
conversar com o Mailpit. Isso é uma diferença de produção e **não cobre** o SMTP das
empresas (AUTH fixo em `SmtpSenderFactory`) nem o do Keycloak — por isso o código de login
do portal não chega hoje no espelho.

Proposta em dois degraus:

- **E1 (só configuração, sem CA):** o Mailpit aceita qualquer credencial sem exigir TLS
  (`MP_SMTP_AUTH_ACCEPT_ANY` + `MP_SMTP_AUTH_ALLOW_INSECURE`). As empresas-persona usam
  `starttls=false` (já é por empresa; o AUTH fixo do `SmtpSenderFactory` passa a ser
  aceito). O Keycloak do espelho ganha um script próprio de SMTP — o
  `configure-keycloak-smtp.sh` tem AUTH/STARTTLS fixos e pula sem credencial. O override
  do backend (`SPRING_APPLICATION_JSON`) continua: é configuração, não código.
- **Refinamento opcional:** STARTTLS com certificado de CA sintética confiada só no
  espelho, para tirar também o override. Custo: truststore da JVM do backend e do
  Keycloak. Só vale se a diferença de TLS se mostrar relevante.

A API do Mailpit é também o "celular" das personas: links de ativação, códigos de login
do portal, OTP de aceite.

### 3.3 Motor de personas

**Duas camadas, dois papéis:**

| | Motor de personas | k6 |
|---|---|---|
| Pergunta | o sistema se comporta direito com gente "de verdade" ao longo do tempo? | quanto o sistema aguenta? |
| Forma | jornadas longas e com estado (reserva hoje → pagamento depois → passeio no horário) | muitas jornadas curtas em paralelo |
| Volume | realista | alto |
| Onde roda | fora da VM | fora da VM (gerador não disputa CPU com a aplicação) |

O motor lê um **catálogo declarativo** de personas e comportamentos (§4) e executa as
jornadas pelas APIs, com tempo comprimido (ver §7, decisão 3). Expõe métricas Prometheus
(jornadas iniciadas/concluídas/abandonadas por persona).

O **semeador** é idempotente e retomável, com arquivo de estado — a mesma lógica do
semeador (`sintetico/`): rodar de novo nunca cria a persona duas vezes.

**2FA das personas:** o operador de plataforma configura o TOTP como uma pessoa faria —
lê a chave na tela de configuração do Keycloak (modo "não consigo escanear") e passa a
gerar os códigos. O segredo fica no arquivo de estado (0600), nunca no repositório.

## 4. Personas

### 4.1 Catálogo

| Persona | Nasce por | Principais ações (sempre via API real) |
|---|---|---|
| **Operador da plataforma** | cadastro + `PLATFORM_ADMIN_EMAILS` (o bootstrap do próprio sistema) | aprova empresas; habilita emissora; lança créditos; aprova compra de créditos; sessão de suporte |
| **Operador da EAMA emissora** | cadastro → aprovado pelo operador | configura emissão, SMTP (fake) e e-mail da Capitania (fake); cadastra e aprova instrutores; **aceita o vínculo**; emite (GRU no fake) |
| **Instrutor** | cadastrado pela EAMA / link de assinatura | assina pelo link único |
| **Operador da empresa delegada** | cadastro → aprovado | **solicita vínculo** à EAMA; opera balcão; emite em nome da EAMA |
| **Vendedor / Gerente / Mecânico** | convite do admin da empresa | balcão, fechamento, manutenção (bloqueia agenda) |
| **Cliente** | cadastro pelo portal (código no Mailpit) | reserva com sinal PIX; envia comprovante; faz EMA/GRU self-service; avalia |
| **Empresa de carga** | cadastro → aprovada | alvo do k6 |

### 4.2 Comportamento (parâmetros a calibrar)

Produção ainda não tem operação real (linha de base: zero locações), então os parâmetros
nascem de **premissas de negócio** e se ajustam depois com dado real:

- chegada de reservas por dia da semana e hora (pico sábado de manhã);
- funil do portal: visita → reserva → sinal pago → comparece (taxas de abandono,
  pagamento atrasado, **no-show**, reserva expirada em 24 h);
- GRU: paga na hora / paga horas depois / nunca paga; PIX que expira e é regerado;
- Marinha: taxa de indisponibilidade; CPF que bate no bloqueio por volume;
- operação: jetski que entra em manutenção no meio do dia; fechamento diário.

**O que o tempo comprimido NÃO alcança:** a reserva só exige início no futuro e o job de
expiração roda a cada 5 min — comprimir pelos dados funciona. Mas há jobs de **hora
fixa**: fim de trial (05:15), exclusão agendada (05:45), faturamento (06:00), manutenção
preventiva (06:00), métricas da plataforma (04:15). Só uma rodada que **atravesse a
madrugada** (soak) os exercita.

O catálogo é **dado**, não código: adicionar uma persona ou mudar um funil é editar o
arquivo, e cada rodada registra a semente para ser reproduzível.

## 5. Travas (falha fechada)

1. **Preflight** (`infra/espelho/preflight.sh`) reprova se `jetski.gru.*-base` não apontar
   para `fakes-externos`, ou se alguma empresa-persona tiver `tsaUrl` fora do fake.
2. **Sumidouro de DNS no backend do espelho** (`extra_hosts`): `dpc1.marinha.mil.br`,
   `pagtesouro.tesouro.gov.br` e `freetsa.org` resolvem para `127.0.0.1` **dentro do
   container do backend**, onde nada escuta. Se a configuração falhar e o código usar o
   endereço padrão, a conexão é recusada na hora — nunca chega ao sistema real. (O fake é
   alcançado pelo nome do serviço, `fakes-externos`, só via configuração explícita.)
3. **SMTP sem saída:** a única saída SMTP da VM é o Mailpit; nenhuma persona tem
   credencial de provedor real, e endereços de destino são `@exemplo.invalid`.
4. **CPF sintético só circula com os fakes no lugar.** Como um CPF de DV válido pode ser
   de alguém real (§1.5), o motor de personas e o semeador **recusam rodar** se o preflight
   não confirmar que Marinha/PagTesouro apontam para `fakes-externos`. A faixa 9xx é
   marcador para limpeza, não proteção.
5. As travas já existentes continuam: k6 e scripts recusam `meujet.com.br`.

## 6. Fases

| Fase | Entrega | Destrava |
|---|---|---|
| **E0** ✅ | travas: preflight das bases + sumidouro de DNS | tudo o que vem depois com segurança |
| **E3a** ✅ | semeador mínimo pela API ([`sintetico/`](sintetico/README.md)): operador de plataforma (TOTP automatizado) + empresas de carga aprovadas | **fim dos cliques manuais**; smoke do k6 — não depende de fakes nem de SMTP novo |
| **E1** ✅ | SMTP por configuração (Mailpit aceita AUTH; Keycloak do espelho → Mailpit) | e-mail do Keycloak → persona cliente e login por código |
| **E2** ✅ | `fakes-externos` ([`sintetico/src/fakes/`](sintetico/README.md)): Marinha + PagTesouro + `/_controle` + métricas; **teste de contrato** com um robô que repete o `GruClient` (os HARs não foram versionados — CPF real), incluindo nomes acentuados; prova de vida `semeador provar-gru` no provisionamento. Pendente para a E5: scrape do `/metrics` pelo Prometheus do espelho; o charset real das páginas ASP segue na checagem manual | emissão de ponta a ponta no espelho |
| **E3b** ✅ | demais personas ([`sintetico/src/emissao.ts`](sintetico/README.md)): EAMA habilitada, 2 delegadas + vínculo, instrutores (link único, aprovação), equipe, créditos comprados, clientes de portal e balcão; prova `semeador provar-emissao` (própria + delegada até o ofício) | espelho populado a cada `terraform apply` |
| **E4** ✅ | motor de comportamento ([`sintetico/src/motor.ts`](sintetico/README.md), `sintetico/motor.sh`): jornadas de portal, balcão (CHA/EMA), manutenção, telas e fechamento, com relógio 12×, funil em [`catalogo/e4-sabado.json`](sintetico/catalogo/e4-sabado.json), semente e relatório por passo | "um sábado sintético" |
| **E5** ✅ | k6 por persona ([`k6/README.md`](k6/README.md): portal, emissão com fakes, plataforma) + `resiliencia` com falha injetada pelo `/_controle` + `rodar.sh`/`soak.sh` + limites do nginx afrouxados só no espelho + `/metrics` dos fakes no Prometheus; limites medidos em [`CAPACIDADE_E_LIMITES.md`](CAPACIDADE_E_LIMITES.md) | capacidade **e** resiliência |
| **E6** | TSA fake; IdP Google fake (opcional) | reforço jurídico e login social no espelho |

E0 vem primeiro por segurança. **E3a vem logo depois** porque resolve a dor imediata
(aprovar empresa de carga à mão) sem depender de nada. E1–E2 são a fundação do resto: sem
os fakes nenhuma persona pode emitir; sem o e-mail do Keycloak o cliente não entra no portal.

## 7. Decisões (tomadas em 17/set/2026)

| # | Tema | Decisão |
|---|---|---|
| 1 | Login do operador sintético | **TOTP automatizado** pelo fluxo real do console — exercita o 2FA |
| 2 | Tecnologia de semeador, motor e fakes | **TypeScript/Node**, uma stack só |
| 3 | Onde roda o semeador | **Dentro da VM, no provisionamento** — o `terraform apply` entrega o espelho populado; credenciais das personas em arquivo 0600 na VM |
| 4 | Tempo | **Comprimir pelos dados** (reservas para daqui a minutos, pagamento agendado no fake); jobs de hora fixa só por soak que atravesse a madrugada |
| 5 | Limites por IP do nginx | **Afrouxar só no espelho**, para medir a aplicação; um cenário separado, com o limite real, valida a proteção |
| 6 | Calibração do funil (§4.2) | Perfil inicial **proposto na implementação** (locadora de praia, pico no fim de semana, taxas conservadoras), em arquivo de dados; os sócios ajustam depois |
| 7 | Fidelidade do fake da Marinha | **Checagem manual documentada**: roteiro para gerar 1 GRU real e comparar com o contrato, de tempos em tempos ou quando produção falhar; sem automação contra o site do governo |
| 8 | Contas de seed com senha pública (`admin@acme.com` etc.) | **Ficam como estão** no espelho. Reavaliar quando os fakes e as personas entrarem: hoje nenhuma empresa do espelho emite e nenhum e-mail sai |

### Decisões da E2 (17/set/2026)

| Tema | Decisão |
|---|---|
| Pagamento do PIX no fake | **A persona paga** (`POST /_controle/gru/{id}/pagar`); ninguém paga sozinho. `autoPagarAposSeg` existe, desligado |
| Bloqueio por volume por CPF | **Desligado por padrão** (limite 10 quando ligado); entra nos cenários de falha da E5 |
| QR e boleto | **QR real e escaneável, sem dependência** (codificador próprio); PDF mínimo aceito pelo pdfbox. Ambos marcados como sintéticos; PIX aponta para `*.invalid` |
| Alcance | **Só o espelho** nesta fase. O fake é autocontido (um `node servidor.ts`), então oferecê-lo ao dev/CI — onde o e2e ainda gera GRU real — é um PR separado |

### Decisões da E3b (17/set/2026)

| Tema | Decisão |
|---|---|
| População | **Média**: 1 EAMA + 2 delegadas (a 2ª reservada para kill switch na E5), equipe completa numa delegada, 12 clientes (6 portal, 6 balcão) |
| Créditos | **Compra com comprovante + aprovação** pela operadora de plataforma; cortesia só como recarga |
| Documentos do cliente | **Imagens sintéticas geradas** (~200 KB), com todas as exigências da Marinha ligadas |
| Prova de vida | **Duas emissões completas** (própria e delegada) no provisionamento |

### Decisões da E4 (17/set/2026)

| Tema | Decisão |
|---|---|
| Janela de horário do pier em UTC (bug de produto achado no mapeamento) | **Corrigida em PR separado** (#64): a policy usa o relógio da loja |
| Onde roda o motor | **De fora, por túnel SSH** (`sintetico/motor.sh`), como a §3.3 previa |
| Compressão | **12×** — o sábado (09h–18h) em ~45 min; fator é parâmetro |
| Clientes | **Fixos + novos durante o dia** (cadastro no portal e no balcão em plena operação) |

### Decisões da E5 (17/set/2026)

| Tema | Decisão |
|---|---|
| Personas no k6 | **Portal, emissão EMA (com fakes) e operadora de plataforma**, além de leitura e balcão |
| Falhas externas | **Marinha fora do ar** e **PagTesouro lento/pendurado**; o bloqueio por CPF fica para depois |
| Busca do limite | **Rodar o stress agora** e registrar no CAPACIDADE_E_LIMITES.md |
| Soak | **Perfil entregue + 1 h de prova**; a madrugada inteira fica com um comando (`k6/soak.sh <ip> 480`) |

## 8. Fora de escopo

- Simular a Cloudflare (o espelho já passa pela real).
- Carga no navegador (ViaCEP, YouTube…) — só se a E5 incluir jornadas Playwright.
- Mudar o comportamento de produção para facilitar o teste.
