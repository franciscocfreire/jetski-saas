# Capacidade, Confiabilidade e Limites — Meu Jet

> **Status:** plano proposto (set/2026). **F0 executada em 15/set/2026** —
> resultados e correções em [`LINHA_DE_BASE.md`](LINHA_DE_BASE.md).
> **Objetivo:** saber, com número, quantas locadoras a infra atual comporta,
> onde ela quebra, e provar periodicamente que o que prometemos é entregue.

## 1. O modelo: três perguntas separadas

A maior armadilha aqui é tratar "garantir", "medir" e "testar limite" como uma
coisa só e pular direto para o teste de carga. Um relatório dizendo "aguenta
420 req/s" não responde nenhuma pergunta de negócio. Separamos assim:

| Pergunta | Nome | Entregável |
|---|---|---|
| A plataforma entrega o que promete? | **Confiabilidade (SLO)** | SLIs por jornada + orçamento de erro + alertas de sintoma |
| Quanto ela aguenta *do que importa*? | **Capacidade** | unidade de carga de negócio + linha de base + projeção |
| Onde e **como** ela quebra? | **Limite** | testes de carga em espelho + jogos de falha |

A ordem importa: sem (1) não há critério de aprovação para o teste; sem (2) o
resultado não converte para "cabem N empresas".

### 1.1 A unidade de carga é a locadora, não o request

Toda a medição é normalizada por **tenant-dia de operação**, porque é a única
unidade que serve para precificar, para decidir quando escalar e para conversar
com os sócios. O fator de conversão que precisamos estabelecer na Fase 0:

```
1 locadora média = J jetskis
                 = L locações/dia, concentradas em ~6h de sábado/domingo
1 locação        = R requests (balcão + portal)
                 + F fotos de check-in/out (1–5 MB cada, já comprimidas no cliente)
                 + E emissões (EMA/GRU) e e-mails
                 = S MB/mês de storage permanente
```

Com isso, "a VM atual comporta X tenants" vira uma conta, e não um chute — e
alimenta diretamente a decisão de preço que está em aberto.

## 2. Ponto de partida (o que já existe — auditado em 15/set/2026)

**A favor — a instrumentação já está pronta**, o que encurta muito o caminho:

- Prometheus (7d) com métricas do backend, host (node-exporter) e por container
  (Telegraf); Loki (7d) com os logs de todos os containers.
- 8 dashboards provisionados, incluindo **Endpoints do Backend** (P50/P95, 4xx/5xx
  por rota), **Performance do Sistema** (JVM/threads/Hikari) e **Infraestrutura**.
- 9 alertas por e-mail (backend fora do ar, 5xx > 5%, disco > 80%, memória > 90%,
  pico de ERROR, dependência interna, SSO, túnel Cloudflare, backup não rodou).
- Métricas de negócio por tenant (`jetski_*`) do módulo `metrics`.
- Backup diário com trava, retenção 14d e cópia off-site via rclone.
- Coleção Newman/Postman com as jornadas completas — base pronta para virar
  cenário de carga.

**Contra — o que falta:**

- **Nenhum teste de carga** existe (`k6`/`gatling`/`jmeter`: zero ocorrências no repo).
- **Nenhum ambiente onde seja seguro quebrar** — prod é VM única com clientes reais;
  o dev é WSL2 e não é comparável a 2 OCPU ARM.
- Alertas são todos de **causa** (infra), nenhum de **sintoma por jornada**
  (emissão falhando, check-in lento, SMTP recusando).
- Nenhum **drill de restore** cronometrado: o backup roda, mas o RTO/RPO real
  nunca foi medido.
- Nenhum **SLO declarado** — sem meta, todo número é opinião.

## 3. Hipóteses de gargalo (a falsificar, em ordem de risco)

Levantadas por leitura de configuração; cada uma vira um experimento na Fase 4.

> **Veredito da F0 (15/set/2026)** — ver `LINHA_DE_BASE.md` §2:
> a **#2 se confirmou em produção** (uma query pesada saturou 1 das 2 vCPUs e
> o Prometheus foi morto por OOM); a **#3 se confirmou em parte** — o teto de
> `MaxHeapSize` é mesmo 376 MB, mas o pico de uso é 226 MB, e o que pesa é a
> JVM ter caído no **SerialGC** (pausa máxima de 1.036 ms, ociosa);
> a **#4 não tem evidência de problema** (0 conexões pendentes,
> 0 timeouts) e desce na fila; a **#1 segue de pé** (18 de 100 conexões em uso
> hoje, mas o Keycloak pode reivindicar 100 sozinho sob carga de login).
> Dois achados novos entraram na lista: o **Prometheus a 98,7% do seu limite**
> de memória e a **ausência de métricas de thread do Tomcat**.

1. **Postgres é o SPOF e está sem contenção de conexões.**
   `docker-compose.prod.yml` sobe o Postgres só com `listen_addresses` e `hba_file`
   → `max_connections` fica no default **100**. No mesmo banco vivem o app *e o
   Keycloak* (schema `keycloak`), e o Keycloak sem `KC_DB_POOL_MAX_SIZE` assume
   pool **100** sozinho. Somando Hikari (10), o `flyway` do deploy e o `pg_dump`
   do backup, um pico de logins pode esgotar as conexões e derrubar banco **e**
   login juntos. *Risco alto, correção barata — tratar como item de segurança
   independente do plano.*
2. **Nenhum container tem limite de CPU.** Há `mem_limit` em todos, mas nenhum
   `cpus:`. São 2 OCPUs compartilhadas entre app, banco, Keycloak, 3 Next.js,
   MinIO, Loki, Prometheus — e ainda o `docker build` do CD e o backup diário.
   Sem isolamento, latência errática sob carga é esperada e o teste vai medir
   ruído de vizinho, não capacidade.
3. **Heap do backend no default.** Sem `JAVA_OPTS`/`MaxRAMPercentage` no
   Dockerfile ou no compose → a JVM assume ~25% do `mem_limit` de 1500m,
   ou seja **~375 MB** de heap. Geração de PDF, relatórios e upload de fotos
   são justamente o caminho crítico. Medir GC e heap sob carga antes de mexer.
4. **Hikari com 10 conexões** pode ser estreito ou folgado — medir
   `hikaricp_connections_pending`, já plotado no dashboard Performance.
5. **Upload de fotos é o caminho crítico do sábado** e o mais caro: rotas com
   `client_max_body_size` de 32–64 MB e `proxy_read_timeout` de até 600s, cada
   upload segurando uma thread do Tomcat. Merece cenário de carga próprio.
6. **Um disco para tudo**: Postgres + MinIO + Loki + Prometheus + cache de build.
   O alerta de 80% existe, mas não há projeção de *quando* estoura por tenant/mês.
7. **Soma dos `mem_limit` ≈ 6,4 GB** (app) **+ ~1,9 GB** (observabilidade) de 11 GB.
   A folga real é menor do que parece: o page cache do Postgres sai do host.
8. **Tudo numa VM e um túnel.** Não é defeito — é uma decisão de custo. O ponto
   é declará-la no SLO (99,5%/mês ≈ 3,6h de indisponibilidade tolerada) em vez
   de descobrir na hora.

## 4. Fases

### F0 — Linha de base ✅ *executada em 15/set/2026*
Extrair do Prometheus/Loki o que prod já faz. **Sem isto, nenhum outro número
tem escala.** Coletar por 7 dias (janela de retenção), destacando o pico de
sábado:

- req/s e P50/P95/P99 por rota (`http_server_requests_seconds_*`)
- CPU/RAM por container (Telegraf) e do host (node-exporter)
- conexões ativas/pendentes do Hikari e do Postgres (`pg_stat_activity`)
- tamanho do banco, do bucket MinIO e taxa de crescimento diária
- volume de negócio no período (`jetski_*`): locações, check-ins, emissões

**Entregável:** tabela de linha de base + o fator de conversão da §1.1, medido
em cima do tenant mais ativo.

> **Resultado:** extrator repetível em `infra/observability/linha-de-base.sh`,
> análise em `LINHA_DE_BASE.md`, saída bruta em `docs/capacidade/`.
> **O fator de conversão não pôde ser medido**: produção tem **zero locações** e
> 87% do tráfego é auto-monitoração. Ele passa a ser construído por premissas de
> negócio na F3 e validado com carga sintética — e não extraído da telemetria.
> Em compensação, **prod hoje é um campo de testes seguro**, janela que fecha no
> primeiro cliente real.

### F1 — SLO e alertas de sintoma (≈1,5 dia)
Decidir com os sócios (é decisão de negócio, não técnica) 4 SLOs de jornada:

| Jornada | SLI proposto | Meta inicial sugerida |
|---|---|---|
| Login (backoffice/portal) | % de logins concluídos < 5s | 99% / mês |
| Check-in com fotos | % de uploads aceitos < 10s | 99% / mês |
| Emissão EMA/GRU | % de emissões sem erro da plataforma | 99,5% / mês |
| Reserva no portal | % de reservas criadas sem 5xx | 99,5% / mês |
| Disponibilidade geral | minutos com backend up | 99,5% / mês |

Cada SLO vira painel + alerta de **queima de orçamento de erro** — o alerta que
avisa que o cliente está sofrendo, complementando os 9 atuais, que avisam que a
máquina está sofrendo.

### F2 — Ambiente-espelho (≈1–2 dias) — *o bloqueio principal*

> **Decidido em 16/set/2026: espelho, e nenhum teste de carga em produção.**
> Runbook e travas em [`infra/espelho/README.md`](infra/espelho/README.md). Três
> premissas abaixo caíram na execução e estão corrigidas lá: (1) a VM **não** é
> gratuita — o Always Free de A1 caiu para 2 OCPU / 12 GB em jun/2026 e a produção
> já usa tudo; (2) o dataset **não** sai do restore do backup — isso copiaria CPF,
> documentos e fotos reais para outra máquina; o espelho usa só dado sintético;
> (3) "sem Cloudflare público" não se sustenta — o espelho precisa de domínio e
> túnel próprios, porque nginx, vitrine e certificado dependem do hostname.

Não dá para buscar o ponto de ruptura em produção com clientes reais.
Recomendação: **segunda VM Oracle A1 idêntica** (2 OCPU / 11 GB) — o free tier
A1 dá 4 OCPU / 24 GB por tenancy e prod usa metade, então em princípio cabe;
*confirmar a cota disponível é o primeiro passo desta fase*. Mesmo compose,
mesmo `docker-compose.prod.yml`, sem Cloudflare público.

Dois ganhos de brinde:
- o dataset do espelho sai do **restore do backup diário**, o que valida o
  restore de graça, toda vez;
- vira também o ambiente de ensaio de deploy/migração arriscada.

**Pré-requisito inegociável — desarmar as integrações externas no espelho:**
o teste não pode emitir **GRU real na Marinha** (há bloqueio por volume, ~8–10/dia/CPF),
não pode disparar e-mail real (Gmail, 500/dia) e não pode mexer em créditos/metering
de faturamento. Stubs/kill switches antes da primeira rodada.

*Alternativa mais barata, se a cota não permitir:* janela noturna em prod só
para smoke e soak leve, com abortar automático por SLO. Não serve para stress.

### F3 — Cenários de carga (≈2–3 dias)
Ferramenta: **k6** — binário único, roda em ARM, exporta direto para o
Prometheus que já temos, cenários em JS (mesma linguagem dos testes Playwright).
Os fluxos saem de `backend/postman/JORNADAS_COMPLETAS.md`, já escritos.

| Tipo | Pergunta que responde | Duração |
|---|---|---|
| **Smoke** | o espelho está sadio? | 2 min |
| **Load** | aguenta o pico de sábado × N tenants? | 30 min |
| **Stress** | onde está o joelho da curva? (rampa até quebrar) | até quebrar |
| **Soak** | vaza memória/conexão/ThreadLocal em 4h? | 4h |
| **Spike** | sobrevive ao 8h da manhã de sábado (0 → pico em 1 min)? | 10 min |

O **soak** é o mais valioso neste sistema, dado o histórico de vazamento de
`TenantContext` em scheduler — é exatamente o tipo de defeito que só aparece
depois de horas.

### F4 — Rodadas e endurecimento (≈2–3 dias)
Para cada hipótese da §3: medir → ajustar um parâmetro por vez → re-medir →
registrar. Saída: `max_connections`/`KC_DB_POOL_MAX_SIZE` explícitos, `cpus:`
por container, heap dimensionado, Hikari dimensionado — cada um com o número que
justificou a mudança.

### F5 — Jogos de falha (≈1 dia + recorrente)
Provar a recuperação em vez de supor. No espelho primeiro; os dois primeiros,
depois, em prod em janela combinada:

- matar o Postgres → o backend volta sozinho? em quanto tempo?
- matar o Keycloak → quem já está logado continua? (JWT válido até expirar)
- **drill de restore cronometrado** → declarar **RTO e RPO reais** (hoje o RPO
  é ≤24h por desenho do backup diário — isso é aceitável?)
- encher o disco a 95% → o alerta chega antes do banco parar?
- derrubar o túnel Cloudflare → o alerta de conexões dispara?

### F6 — Portões contínuos (≈1 dia)
- smoke de carga do k6 no CI após cada deploy (falha = rollback);
- dashboard "Capacidade" com o consumo por tenant e a projeção de estouro;
- revisão trimestral da linha de base (a carga cresce, o número envelhece).

## 6. Limites medidos no espelho (17/set/2026)

Fase E5 do ecossistema sintético: k6 de fora da VM contra o espelho (`us-ashburn-1`, A1.Flex
2 OCPU / 12 GB, a mesma stack de produção, limites por IP do nginx afrouxados). Números do
Prometheus do espelho, por minuto, cruzados com o resumo do k6 (`k6/resultados/`).

### 6.1 O joelho da curva: ~100 req/s, e é CPU

| Cenário (stress, rampa até 150 it/s) | Abaixo do joelho | No joelho | Platô |
|---|---|---|---|
| **Balcão** (cadastro → check-in → check-out → extrato) | 95 req/s, p95 **120 ms**, CPU 88% | 107 req/s, p95 1,2 s, CPU 99,7% | ~120 req/s, **12 locações/s**, p95 3,8–4,0 s |
| **Portal** (vitrine pública + reserva online + comprovante) | 114 req/s, p95 **156 ms**, CPU 88% | 124 req/s, p95 2,0 s, CPU 99,7% | ~135 req/s, 13 reservas/s, p95 3,6 s |

O que acontece no joelho, nos dois casos e na mesma ordem: a **CPU do host chega a 100%**
(o backend fica com ~40% dela; o resto é Postgres, Keycloak, nginx e os Next.js), o **pool
do Hikari (10) vira fila** (~190 conexões esperando), as **200 threads do Tomcat ocupam** e
a latência sobe uma ordem de grandeza sem a vazão subir junto. A heap nunca passou de 390 MB
(teto 750 MB) e o GC não apareceu — a hipótese nº 3 da §3 **não se confirma**; a nº 2 (sem
`cpus:`, tudo disputa as 2 OCPUs) **se confirma como o gargalo real**; a nº 1/nº 4 (pool) é
**sintoma**: com a CPU saturada, aumentar o pool só muda onde a fila se forma.

**Folga em relação a hoje:** produção faz 0,30 req/s de média e 3,6 de pico
(`LINHA_DE_BASE.md`); o "sábado sintético" de três lojas de emissão (motor da E4, §sintetico)
custa 0,3 req/s e 6% de CPU. O joelho está **~300× acima da média** e **~30× acima do pico**
atuais. Na VM atual, em ordem de grandeza, cabem **dezenas a poucas centenas de lojas
operando ao mesmo tempo** antes de a latência sair da meta — o número exato depende do mix
(emissão custa 10× mais que leitura) e é o que a F4 vai afinar.

### 6.2 Emissão: o caminho caro

| | |
|---|---|
| 1 VU | 20 emissões/min; jornada completa (3 documentos de 230 KB + habilitação + termo + GRU + PIX + PDF + e-mail) **p95 3,2 s** |
| 4 VUs + 6 de leitura | 1,6 emissões/s; CPU do host **81–97%** |

Uma emissão custa, em CPU, o que ~10 leituras de tela custam. É o cenário que dimensiona a
VM se a emissão delegada crescer; PDF, carimbo de tempo e base64 são os suspeitos (perfilar
na F4).

### 6.3 Resiliência: a Marinha caída não derruba o resto

`k6/cenarios/resiliencia.js` — 4 VUs emitindo + 6 VUs de "tráfego inocente" (outras
empresas, telas do balcão), 3 min por modo, falha injetada no `/_controle` dos fakes:

| Modo | Emissão | Tráfego inocente (meta p95 < 800 ms) |
|---|---|---|
| controle (sem falha) | 293 concluídas, jornada p95 2,9 s | p95 **253 ms** |
| **Marinha fora** (503 em tudo) | 619 caíram no **fluxo manual**, 0 concluídas, **0 × 5xx** | p95 **200 ms** |
| **PagTesouro lento** (8 s por chamada) | 28 concluídas, jornada 26 s | p95 **404 ms** |
| **PagTesouro pendurado** (timeout de 20 s) | 36 fallbacks | p95 **194 ms** |

Veredito: o monolito **não** deixa a integração externa contaminar quem não depende dela —
as threads presas na "Marinha" (até 20 s cada) não esgotaram o Tomcat nesta intensidade. Em
volume maior de emissão simultânea isso muda (200 threads / 20 s = 10 emissões/s presas
bastam); um pool ou timeout dedicado à GRU é o endurecimento óbvio.

### 6.4 Defeitos que só a carga mostrou

1. **Reserva do portal respondia 500 sob saturação** — `TaskRejectedException` do executor
   `@Async` (fila de 500, `AbortPolicy`): uma métrica assíncrona derrubava a reserva.
   9.540 × 500 no stress do portal. **Corrigido no PR #66** (`CallerRunsPolicy`).
2. **Deadlock no check-in concorrente do mesmo jetski → 500** (137 × no stress do balcão):
   duas transações validam disponibilidade sem travar, inserem a locação (lock compartilhado
   do FK) e disputam o `UPDATE jetski`. Correção sugerida nas pendências do
   `IMPLEMENTATION_STATUS.md` (lock pessimista na validação).
3. **Check-in/check-out chegam ao OPA como `locacao:create`** (rotas com hífen não casam com
   o `ActionExtractor`): o RBAC fino do pier e a janela de horário do `context.rego` são
   letra morta. Pendência registrada.

### 6.5 Soak (1 h): motor em tempo real + k6 leve

`k6/soak.sh <ip> 60 3`: o motor de personas em tempo real (5 chegadas/h, com emissão, GRU,
manutenção) + 3 VUs de leitura por 60 min, logo depois dos dois stress.

| | antes | depois |
|---|---|---|
| heap do backend | 170 MB | 144 MB |
| threads da JVM | 48 | 49 |
| Hikari ativas / esperando | 0 / 0 | 0 / 0 |
| pausa de GC (5 min) | 0 | 0 |

**Sem sinal de vazamento.** Mas o soak achou o limite que chega primeiro na vida real, e
ele não é req/s: **o crescimento dos dados em listas sem paginação**. Depois do stress, cada
empresa de carga tinha ~8.400 clientes e ~4.000 locações; `GET /clientes` e
`GET /locacoes/controle-do-dia` devolvem **tudo**. Com 3 VUs: **2,3 MB por request**,
7,8 GB em uma hora, p95 de **8,9 s** medido pelo k6 — enquanto o servidor gastava ~300 ms
(o resto é transferência pelo túnel). Uma loja movimentada acumula isso em um ano de
operação. Pendência registrada: paginar (ou filtrar por padrão) as duas rotas. Os SLOs de
leitura precisam ser medidos com volume de dados realista, não só com tráfego.

### 6.6 O que fazer com isso (entra na F4)

1. `cpus:` por container e medir a fatia do **Postgres** na CPU sob o stress do balcão — é o
   primeiro suspeito dos 60% que não são do backend.
2. Repetir o stress com Hikari 20 e 30 **depois** de tratar a CPU; hoje o pool é sintoma.
3. Corrigir os defeitos 2 e 3 acima; reavaliar a janela de horário do pier quando ela passar
   a valer de verdade.
4. Pool/timeout dedicado às chamadas de GRU (§6.3) antes de a emissão delegada escalar.
5. Perfilar a emissão (§6.2): onde vão os ~2,5 s de CPU por documento.

## 5. Por onde começar

~~1. Extrair a linha de base do Prometheus (F0).~~ ✅ feito — `LINHA_DE_BASE.md`.

Revisado depois da F0:

1. ~~**Preparar os instrumentos antes de qualquer teste de carga.**~~ ✅ feito
   **e verificado em produção** em 16/set/2026 — ver `LINHA_DE_BASE.md` §4.4.
   Resta uma pendência menor (§4.5): `tomcat_threads_config_max_threads` reporta
   `-1`, então o denominador da saturação de threads é o default 200, implícito.
2. **Declarar os SLOs com os sócios** (F1). É decisão, não implementação — e é o
   caminho crítico, porque define o critério de aprovação do teste.
3. **Montar o espelho** (F2) conforme `infra/espelho/README.md`: domínio próprio,
   VM `VM.Standard.A1.Flex` 2 OCPU / 12 GB em `sa-saopaulo-1` — **paga**, porque o
   Always Free de A1 caiu para 2 OCPU / 12 GB por tenancy e a produção já o consome
   inteiro; desligada entre rodadas, custa o volume de boot.
4. ~~Rodar um smoke de carga em produção.~~ **Descartado em 16/set/2026:** carga
   não roda em produção. Os scripts do `k6/` recusam `meujet.com.br`.

*(Os 11 containers vizinhos saíram da lista: medidos em ~1,6% de CPU e 1,3 GB de
RAM, com 7,4 GB livres, eles não bloqueiam nada — ver `LINHA_DE_BASE.md` §2.5.)*

Em paralelo, independente do plano: **corrigir o pareamento de conexões
Postgres↔Keycloak** (§3.1). É um risco de indisponibilidade que já existe hoje,
não precisa de teste de carga para ser justificado.
