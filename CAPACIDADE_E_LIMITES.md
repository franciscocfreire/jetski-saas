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
2 OCPU / 12 GB, a mesma stack de produção, limites por IP do nginx afrouxados). Cada número
diz de onde veio: **k6** (resumos em `k6/resultados/`) ou **Prometheus** do espelho (por minuto,
por container, por rota). Esta seção foi revisada de forma adversarial (26 achados) antes de
entrar; o que era inferência virou medida ou virou "hipótese".

### 6.1 O joelho da curva: ~100 req/s, e é CPU do host

| Cenário (stress, rampa até 150 it/s) | Abaixo do joelho | No joelho | Platô |
|---|---|---|---|
| **Balcão** (cadastro → check-in → check-out → extrato) | 95 req/s, p95 **120 ms**, CPU 88% | 107 req/s, p95 1,2 s, CPU 99,7% | ~120 req/s, p95 3,8–4,0 s |
| **Portal** (vitrine pública + reserva online + comprovante) | 114 req/s, p95 **156 ms**, CPU 88% | 124 req/s, p95 2,0 s, CPU 99,7% | ~135 req/s, p95 3,6 s |

*(req/s e p95 do backend pelo Prometheus, 1 min; CPU = host.)* O que acontece no joelho, nos
dois casos e na mesma ordem: a **CPU do host chega a 100%**, o **pool do Hikari (10) vira
fila** (~190 conexões esperando), as **200 threads do Tomcat ocupam** e a latência sobe uma
ordem de grandeza sem a vazão subir junto.

**Quem gasta a CPU** (Prometheus, `docker_container_cpu_usage_percent`, média na janela do
platô; 200% = 2 OCPU):

| container | stress balcão | stress portal | sábado sintético (E4) |
|---|---|---|---|
| backend | **72%** | **79%** | 1,2% |
| postgres | 24% | 37% | 1,3% |
| alloy (logs) | 25% | 15% | 1,1% |
| opa | 24% | 9% | — |
| cloudflared | 20% | 25% | — |
| nginx | 6% | 7% | — |
| keycloak | 1,5% | 3% | 0,7% |

Leituras: (1) o backend é a maior fatia, mas **observabilidade + OPA custam o mesmo que o
Postgres** — alloy a 25% num host de 2 OCPU é o primeiro candidato a `cpus:`; (2) o Keycloak
não aparece (os cenários renovam token uma vez a cada 5 min); (3) a heap nunca passou de
390 MB (teto 750 MB) e não houve pausa de GC relevante — a hipótese nº 3 da §3 **não se
confirma**; a nº 2 (sem `cpus:`, tudo disputa as 2 OCPUs) **se confirma com a tabela acima**;
a nº 1/nº 4 (pool) é **sintoma**: com a CPU saturada, aumentar o pool só muda onde a fila se
forma (a testar na F4, depois de limitar CPU).

**Sobre "locações por segundo":** no platô do balcão o k6 concluiu 12 jornadas/s, mas **52%
dos check-ins receberam 400 por jetski ocupado** (13.082 de 25.040 iterações; `http_req_failed`
13%) — a frota das três empresas de carga tem 60 jetskis, e a jornada segura o jetski por
segundos. **12/s é o teto da frota nesta configuração, não do servidor.** O número de
capacidade é o de req/s; para medir locações/s é preciso repetir o stress com um jetski por VU.

**Folga em relação a hoje** (Prometheus de produção, só `/v1/*` do backend, 7 dias): média
**0,010 req/s**, pico de 1 min **3,5 req/s**. O "sábado sintético" de três lojas (motor da E4)
custa 0,3 req/s e 6% de CPU. O joelho está **~30× acima do pico** atual e quatro ordens de
grandeza acima da média. Na VM atual cabem, em ordem de grandeza, **dezenas de lojas operando
ao mesmo tempo** antes de a latência sair da meta — o número exato depende do mix (§6.2) e
é o que a F4 vai afinar.

### 6.2 Emissão: o caminho caro

| rodada (k6) | o que | resultado |
|---|---|---|
| `emissao` smoke, 1 VU, 60 s | ficha com **3 documentos** de 230 KB + habilitação + termo + GRU + PIX + PDF + e-mail | 20 emissões/min; jornada p95 **3,2 s**; p95 por request 499 ms |
| `resiliencia` controle, 4 VUs de emissão (**1 documento**) + 6 VUs de leitura, 3 min | idem, um anexo só | 293 emissões (1,6/s), jornada p95 2,9 s; **CPU do host 81–97%** |

A emissão é o cenário que dimensiona a VM se a emissão delegada crescer: com 4 VUs ela leva
o host perto da saturação. Quanto custa **uma** emissão em CPU não foi isolado (a rodada
misturou emissão e leitura) — perfilar na F4: PDF, carimbo de tempo e base64 são os suspeitos.
Nota: nas rodadas acima o carimbo de tempo RFC 3161 **não** foi exercido (o `tsaUrl` padrão é a
freetsa.org, afundada pelo sumidouro). Com a **TSA sintética da E6** ligada (2 carimbos por emissão,
3 com PAdES): 27 emissões/min a 1 VU, jornada p95 **2,3 s** — o carimbo custa pouco; o que pesa
é PDF + base64. Repetir o stress de emissão com a TSA ligada fica para a F4.

### 6.3 Resiliência: a Marinha caída não derruba o resto

`k6/cenarios/resiliencia.js` — 4 VUs emitindo + 6 VUs de "tráfego inocente" (outras
empresas, telas do balcão), 3 min por modo, falha injetada no `/_controle` dos fakes:

| Modo | Emissão | Tráfego inocente (meta p95 < 800 ms) |
|---|---|---|
| controle (sem falha) | 293 concluídas, jornada p95 2,9 s | p95 **253 ms** |
| **Marinha fora** (503 em tudo) | 619 caíram no **fluxo manual**, 0 concluídas, **0 × 5xx** | p95 **200 ms** |
| **PagTesouro lento** (8 s por chamada) | 28 concluídas, jornada 26 s | p95 **404 ms** |
| **PagTesouro pendurado** (timeout de 20 s) | 36 fallbacks | p95 **194 ms** |

Veredito: nesta intensidade o monolito **não** deixa a integração externa contaminar quem
não depende dela. Em volume maior isso muda (200 threads / 20 s = 10 emissões/s presas
bastam para esgotar o Tomcat); um pool ou timeout dedicado à GRU é o endurecimento óbvio.

### 6.4 Defeitos que só a carga mostrou

1. **Reserva do portal respondia 500 sob saturação** — `TaskRejectedException` do executor
   `@Async` (fila de 500, `AbortPolicy`): uma métrica assíncrona derrubava a reserva.
   9.540 × 500 no stress do portal (k6 e Prometheus concordam). **Corrigido no PR #66**
   (`CallerRunsPolicy`).
2. **500 no check-in concorrente do mesmo jetski** — 137 × HTTP 500 no backend (136 no k6 +
   1 EOF) com `deadlock detected … while locking tuple in relation "jetski"`. A **causa exata é
   hipótese**: o log do Postgres do espelho não guarda o `DETAIL` com as duas queries (a
   explicação "lock compartilhado do FK × UPDATE" não fecha com a matriz de locks do Postgres,
   porque o `UPDATE` não muda coluna de chave). Próximo passo registrado nas pendências:
   ligar `log_lock_waits`, reproduzir com dois walk-ins simultâneos no mesmo jetski e provar
   que um lock pessimista na validação elimina o 500 (o segundo tem de receber o 400 de
   negócio).
3. **Check-in/check-out chegam ao OPA como `locacao:create`** (rotas com hífen não casam com
   o `ActionExtractor`): o RBAC fino do pier e a janela de horário do `context.rego` (PR #64)
   são letra morta. Pendência registrada.

### 6.5 Soak (1 h): motor em tempo real + k6 leve

`k6/soak.sh <ip> 60 3`: o motor de personas em tempo real (5 chegadas/h, com emissão, GRU,
manutenção) + 3 VUs do cenário `leitura` por 60 min, logo depois dos dois stress.

| JVM do backend (Prometheus) | antes | depois |
|---|---|---|
| heap usada (instantânea) | 170 MB | 144 MB |
| threads | 48 | 49 |
| Hikari ativas / esperando | 0 / 0 | 0 / 0 |
| pausa de GC (5 min) | 0 | 0 |

**Sem sinal de vazamento** — com a ressalva de que a fotografia "depois" foi tirada ~1 h após
o k6 acabar e mede heap usada, não heap viva; o `soak.sh` agora tira a foto no fim do k6, usa
`jvm_gc_live_data_size_bytes` e o máximo de conexões pendentes na janela (a madrugada
inteira, com os jobs de hora fixa, ainda não foi rodada).

O que o soak achou de verdade é **o limite que chega primeiro na vida real, e ele não é
req/s: listas sem paginação crescendo com os dados**. Depois do stress, cada empresa de carga
tinha ~8.400 clientes e ~4.000 locações, e o cenário `leitura` abre as telas que devolvem
**tudo**:

| rota (GET) | p95 no servidor (Prometheus) | n |
|---|---|---|
| `/locacoes` | **9,9 s** (máx. 12,9 s) | 651 |
| `/clientes` | 534 ms | 650 |
| `/locacoes/controle-do-dia` | 523 ms | 658 |
| `/reservas/agenda`, `/jetskis` | 46 ms, 39 ms | ~658 |

No k6: p95 total 8,9 s, dos quais **8,5 s esperando a primeira resposta** (`http_req_waiting`)
e 0,5 s recebendo — é tempo de servidor (serialização de 4.000 locações), não de rede. Foram
**8,2 GB** (7,6 GiB) em 3.430 requests, 2,3 MB de média e vários MB por lista pesada. Uma loja
movimentada acumula esse volume em meses de operação. Pendência registrada: paginar (ou
filtrar por padrão) `GET /locacoes` e `GET /clientes`; `controle-do-dia` só pesou porque o
stress fez 4.000 check-ins no mesmo dia. Os SLOs de leitura precisam ser medidos com volume de
dados realista, não só com tráfego.

O motor achou ainda um defeito **do próprio motor**: a jornada do portal guardava o access
token e o reutilizava após esperas de até 25 min reais (401) — corrigido (token pedido a cada
uso).

### 6.6 O que fazer com isso (entra na F4)

1. `cpus:` por container — começando por **alloy** e **opa**, que juntos custam o mesmo que o
   Postgres no stress — e medir de novo.
2. Repetir o stress com Hikari 20 e 30 **depois** de tratar a CPU; hoje o pool é sintoma.
3. Paginar `GET /locacoes` e `GET /clientes` (§6.5) — é o limite que a primeira loja real vai
   sentir.
4. Provar a causa do deadlock e corrigir; corrigir o `ActionExtractor` e reavaliar a janela de
   horário do pier quando ela passar a valer.
5. Pool/timeout dedicado às chamadas de GRU (§6.3) antes de a emissão delegada escalar.
6. Isolar o custo de CPU de uma emissão (§6.2), com a TSA sintética ligada (E6).

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
