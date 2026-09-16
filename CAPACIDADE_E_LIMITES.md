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

## 5. Por onde começar

~~1. Extrair a linha de base do Prometheus (F0).~~ ✅ feito — `LINHA_DE_BASE.md`.

Revisado depois da F0:

1. ~~**Preparar os instrumentos antes de qualquer teste de carga.**~~ ✅ feito —
   ver `LINHA_DE_BASE.md` §4. Falta **deployar**: 4.2 e 4.3 mudam a imagem do
   backend (rebuild) e 4.1 recria o container do Prometheus.
2. **Declarar os SLOs com os sócios** (F1). É decisão, não implementação — e é o
   caminho crítico, porque define o critério de aprovação do teste.
3. **Decidir o destino dos 11 containers vizinhos** (outline, kroki, drawio,
   vikunja…) que dividem as 2 vCPUs com a plataforma. Enquanto eles estiverem
   lá, "cabem N locadoras nesta VM" não é uma frase com significado.
4. **Antecipar a F3 em produção**, aproveitando a janela sem clientes reais, em
   vez de esperar o espelho da F2 — que passa a ser desejável, não bloqueante.

Em paralelo, independente do plano: **corrigir o pareamento de conexões
Postgres↔Keycloak** (§3.1). É um risco de indisponibilidade que já existe hoje,
não precisa de teste de carga para ser justificado.
