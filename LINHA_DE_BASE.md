# Linha de base de capacidade — produção

> Fase 0 de [`CAPACIDADE_E_LIMITES.md`](CAPACIDADE_E_LIMITES.md).
> **Coleta:** 15/set/2026 22:54 BRT, janela de 7 dias (limite da retenção do Prometheus).
> **Saída bruta:** [`docs/capacidade/linha-de-base-2026-09-15.txt`](docs/capacidade/linha-de-base-2026-09-15.txt)
> **Como repetir:** `ssh -i CHAVE ubuntu@HOST 'bash -s' < infra/observability/linha-de-base.sh > docs/capacidade/linha-de-base-AAAA-MM-DD.txt`
> Tudo somente leitura — nenhuma escrita, nenhum restart.

## 1. O retrato

### Ambiente

| | |
|---|---|
| VM | Oracle ARM, 2 vCPU, 11,9 GB, disco 193 GB (20% usado, 156 GB livres) |
| Uptime | 92 dias |
| Containers | **29 no total, 18 do Meu Jet** |
| Retenção do Prometheus | 1 semana |

### Tráfego (7 dias)

| Métrica | Valor |
|---|---|
| Total de requests | 180.653 |
| Média | **0,30 req/s** |
| Pico (janela de 5 min) | **3,59 req/s** |
| Distribuição | 92% GET, 7% POST |

Top endpoints por volume:

| Endpoint | Requests | O que é |
|---|---|---|
| `/metrics` | 40.253 | scrape do Prometheus |
| `/actuator/prometheus` | 40.116 | scrape do Prometheus |
| `/actuator/health` | 20.090 | healthcheck |
| `/realms/{realm}` | 20.073 | Keycloak |
| `/realms/{realm}/.../token` | 12.519 | emissão de token |
| `/realms/{realm}/.well-known/{alias}` | 12.231 | descoberta OIDC |
| `/health/ready` | 12.074 | healthcheck |
| `/admin/realms/{realm}/client-session-stats` | 10.018 | polling de sessões |
| `/v1/tenants/{tenantId}/locacoes` | 1.092 | **negócio** |
| `/v1/suporte/atual` | 618 | **negócio** |

**~87% do tráfego é a plataforma falando consigo mesma** (monitoração, healthcheck,
descoberta OIDC e token). O tráfego humano cabe em poucos milhares de requests por semana.

### Latência e erros

| Métrica | Valor |
|---|---|
| P95 global | **40 ms** |
| P99 global | 50 ms |
| Maior latência já observada | 110 ms (`/actuator/prometheus`) |
| SUCCESS / CLIENT_ERROR / SERVER_ERROR | 175.263 / 5.190 / **26** |
| 5xx | 22 em `/actuator/health`, 4 em `openid-configuration` |
| `RequestRejectedException` | **1.018** (StrictHttpFirewall) |

### Recursos — o custo de ociosidade

| Métrica | Valor |
|---|---|
| CPU do host, média 7d | **10,4%** |
| CPU do host, pico (10 min) | **75,1%** |
| Load average 1m, pico | **6,37** (em 2 vCPU = 3,2× sobrecarga) |
| RAM disponível | 7,8 GB agora; **6,3 GB no mínimo** |
| Disco | 156 GB livres, **−2,16 GB em 7 dias** (~0,31 GB/dia) |
| Build cache do Docker | **11,55 GB** (10,54 GB recuperáveis) |

Pico de memória por container:

| Container | Pico | % do limite |
|---|---|---|
| jetski-keycloak | 728 MB | 58% |
| jetski-backend | 590 MB | 38% |
| **jetski-prometheus** | **530 MB** | **98,7%** ⚠ |
| outline | 469 MB | 44% (não é Meu Jet) |
| kroki-mermaid | 306 MB | 29% (não é Meu Jet) |
| drawio | 280 MB | 52% (não é Meu Jet) |

### Banco, pool e JVM

| Métrica | Valor |
|---|---|
| Tamanho do banco | **21 MB** |
| Storage MinIO | **220 MB** |
| Conexões Postgres | **18 de 100** (10 Hikari ocioso, 4 Keycloak/admin) |
| `shared_buffers` / `work_mem` | 160 MB / 4 MB (defaults) |
| Hikari: ativas / pool / esperando / timeouts | 5 / 10 / **0** / **0** |
| **MaxHeapSize do backend** | **376 MB** (25% do limite de 1500 MB, ergonômico) |
| Heap usado pelo backend, pico 7d | **226 MB** (60% do teto) |
| Não-heap do backend | 249 MB (Metaspace 158, code cache 71, class space 20) |
| RSS do container backend | 566 MB |
| **Coletor do backend** | **SerialGC** (ergonômico) |
| Pausa de GC máxima | **1.036 ms** (major) / 415 ms (alloc. failure) |

> **Correção (mesma sessão).** A primeira versão desta tabela trazia "heap usado
> 399 MB" e "não-heap 497 MB". Estavam errados: as queries usavam `sum()` sem
> agrupar por `job`, e o **Keycloak também expõe `jvm_memory_used_bytes`** — os
> números somavam as duas JVMs. Os valores acima são só do backend. O extrator
> já foi corrigido para agrupar por `job`; o arquivo bruto de 15/set preserva as
> linhas originais, sem filtro, como foram coletadas.

### Autenticação e negócio

| Métrica | Valor |
|---|---|
| Sessões ativas, pico | 7 (4 backoffice + 2 console + 1 portal) |
| **Eventos de `login`** | **10.740** |
| `refresh_token` / `invalid_token` | 992 / **761** |
| Trocas de contexto de tenant | 3.033 |
| **Locações** | **0** (total e nos últimos 30 dias) |
| Reservas | 12 acumuladas (1 cancelada na janela) |
| Emissões na janela | 5 DOCUMENTO + 7 GRU + 4 PREVIA |
| Tenants | 11 (4 ativos) |
| Clientes / jetskis / usuários | 7 / 8 / 15 |
| Documentos emitidos / auditoria | 9 / 304 linhas |

## 2. Sete achados

### 2.1 Não existe carga de produção para servir de base — e isso muda a Fase 0

**Zero locações registradas.** 12 reservas, 7 clientes, banco de 21 MB. 87% do
tráfego é auto-monitoração. A telemetria de prod descreve uma plataforma **no ar,
porém sem operação**.

Consequência direta para o plano: **o fator de conversão "1 locadora = X req/s"
não pode sair da telemetria**, porque não há locadora operando. Ele terá de ser
*modelado* a partir de premissas de negócio (quantos jetskis, quantas locações
por sábado, quantas fotos por locação) e depois *validado* no espelho com carga
sintética. A Fase 0 entrega, então, duas coisas diferentes do previsto:

- **o piso** — quanto a stack consome sem ninguém usando (é o que está medido aqui);
- **o modelo sintético** — a ser construído na Fase 3, não extraído aqui.

O lado bom: sem usuários reais, **prod é hoje um ambiente onde é seguro testar**.
Essa janela fecha no primeiro cliente de verdade. É um argumento forte para
antecipar os testes de carga em vez de esperar o espelho da Fase 2.

### 2.2 A JVM do backend está mal dimensionada — e o coletor é o problema

`MaxHeapSize = 376 MB`, apenas 25% do `mem_limit` de 1500 MB. Não é escolha de
ninguém: é a ergonomia da JVM (`MaxRAMPercentage` default de 25%). O pico de uso
foi de **226 MB (60% do teto)**, então não há exaustão hoje — mas sobram só
150 MB de folga para toda a carga futura, enquanto **1 GB do container fica
alocado e sem uso**.

O achado grave é outro: a JVM escolheu **SerialGC**. Um container de 1500 MB fica
abaixo do limiar de 1792 MB que a JVM usa para classificar a máquina como
"server class", então ela cai no coletor mono-thread com parada total do mundo.
Isso explica a **pausa máxima de 1.036 ms** — um segundo de aplicação congelada,
estando ociosa. Sob carga real, uma pausa dessas sob um pool de 10 conexões é o
caminho mais curto para timeouts em cascata.

Correção barata, e só de configuração: heap explícito + `-XX:+UseG1GC`.

*(Correção da hipótese §3.3 do plano: a estimativa de ~375 MB estava certa, mas
o diagnóstico "heap esgotado" não — veio de somar backend e Keycloak na mesma
query. O que de fato justifica a mudança é o SerialGC, que eu não tinha visto.)*

### 2.3 O instrumento de medição morreu no meio da medição

**Prometheus a 98,7% do seu `mem_limit` de 512 MB** — e não era teoria: durante
esta coleta ele **foi morto pelo OOM killer**. O kernel registrou

```
Memory cgroup out of memory: Killed process (prometheus) anon-rss:513908kB
```

às 01:50:57 de 16/set UTC, e o container voltou com `RestartCount=1`. O gatilho
foi uma consulta minha de P95 sobre 7 dias.

Em repouso o consumo é modesto — ~95 MB de memória anônima mais ~375 MB de page
cache do TSDB, que é recuperável. O que estoura o teto é o **pico de uma única
consulta cara**. Duas correções, não uma: mais memória *e* um limite de amostras
por consulta, para que a consulta cara falhe sozinha em vez de derrubar o servidor.

### 2.4 A hipótese "sem limite de CPU" se confirmou sozinha

O mesmo episódio confirma a §3.2 do plano **sem precisar de teste de carga**: uma
consulta saturou uma das 2 vCPUs e, como nenhum container tem `cpus:` definido, o
Prometheus disputa CPU de igual para igual com o backend e o banco. O
`load average` de pico de **6,37 em 2 vCPUs** (3,2× de sobrecarga) conta a mesma
história por outro caminho.

*(Correção: durante a coleta eu li a recuperação do Prometheus como "falso
alarme, voltou a responder". Não foi — ele havia sido morto e reiniciado.)*

### 2.5 A VM não é só do Meu Jet

Dos 29 containers, **11 não são da plataforma**: outline (+postgres +redis),
drawio, kroki ×3, excalidash ×2, vikunja, oauth2-proxy. Juntos, mais de **1,4 GB
de pico de memória** e uma fatia relevante das 2 vCPUs.

Qualquer conta de capacidade ("cabem N locadoras nesta VM") é inválida enquanto
esses vizinhos não forem contabilizados ou movidos. É uma decisão a tomar antes
da Fase 3, não um detalhe.

### 2.6 10.740 logins por semana para 7 sessões

Com ~15 usuários cadastrados e pico de 7 sessões simultâneas, o Keycloak
registrou **10.740 eventos de `login`** e serviu **12.519 requests de token** —
5º endpoint mais chamado da plataforma. Há um laço de autenticação em máquina
(provavelmente o admin client do backend reautenticando a cada chamada), somado a
**761 falhas de `refresh_token`**, que é o ruído já conhecido do refresh.

Importa para capacidade porque é **custo fixo que escala junto com a carga**:
cada request de token consome CPU de hashing e uma conexão. Vale instrumentar a
origem antes do teste, senão o resultado do teste vem contaminado.

### 2.7 Não dá para ver saturação de threads

Não existe nenhuma métrica `tomcat_threads_*` exposta. Sob carga, o sintoma
clássico é a fila do connection pool ou do executor do Tomcat encher — e hoje
**esse sinal é cego**. Habilitar o binder do Tomcat no Micrometer é pré-requisito
do teste de carga, junto com o item 2.3.

## 3. O que isso muda no plano

| Item | Antes | Depois desta medição |
|---|---|---|
| F0 | extrair o perfil de uso real | ✅ feito — mas entrega o **piso de ociosidade**; o perfil de uso terá de ser sintético |
| F2 (espelho) | bloqueio principal | **menos urgente**: prod sem operação real é um campo de testes seguro *por ora* |
| F3 (carga) | depois do espelho | **pode começar antes**, aproveitando a janela sem clientes |
| §3.1 (conexões PG↔KC) | risco alto | mantido: 18/100 em uso hoje, mas o Keycloak pode reivindicar 100 sozinho sob carga |
| §3.2 (sem `cpus:`) | hipótese | **confirmada em produção** durante esta coleta |
| §3.3 (heap) | hipótese | **confirmada em parte**: o teto de 376 MB não está esgotado (pico de 226 MB), mas o **SerialGC com pausa de 1 s** é pior do que a hipótese original |
| §3.4 (Hikari 10) | hipótese | **sem evidência de problema** (0 pendentes, 0 timeouts) — despriorizar |

### Pré-requisitos do primeiro teste de carga (antes da F3)

| # | Item | Estado |
|---|---|---|
| 1 | Subir o `mem_limit` do Prometheus (§2.3) — senão o medidor morre no meio | ✅ feito, §4 |
| 2 | Expor métricas de thread do Tomcat (§2.7) — senão o gargalo mais provável fica invisível | ✅ feito, §4 |
| 3 | Definir heap e coletor do backend explicitamente (§2.2) — ou o teste só mede SerialGC | ✅ feito, §4 |
| 4 | Decidir o destino dos 11 containers vizinhos (§2.5) — ou o número não significa nada | ⬜ decisão pendente |

## 4. Ajustes aplicados (15/set/2026)

Os três itens de configuração, com o antes/depois verificado.

### 4.1 Prometheus: teto de memória e guarda de consulta
`infra/observability/docker-compose.observability.yml`

- `mem_limit: 512m` → **`1g`**
- `--query.max-samples=10000000` (default: 50M) e `--query.timeout=1m`

A segunda parte é a que realmente resolve: sem teto de amostras, uma única
consulta cara volta a levar o processo inteiro junto, com qualquer `mem_limit`.
Com o teto, quem falha é a consulta. 10M amostras ≈ 160 MB de pico por consulta.

### 4.2 Métricas de thread do Tomcat
`backend/src/main/resources/application.yml`

```yaml
server:
  tomcat:
    mbeanregistry:
      enabled: true
```

O Micrometer só publica os medidores `tomcat.*` com o registro de MBeans ligado.
Habilita `tomcat_threads_busy_threads` e `tomcat_threads_config_max_threads` —
o par que mostra a fila de requests enchendo. Vale para todos os perfis.

### 4.3 Heap e coletor do backend
`backend/Dockerfile` + `docker-compose.yml`

```
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
```
```yaml
JAVA_OPTS: >-
  -XX:+UseG1GC -XX:MaxRAMPercentage=50
  -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp
```

A forma exec pura do `ENTRYPOINT` não expande variáveis, por isso o `sh -c`; o
`exec` mantém a JVM em PID 1 e preserva o SIGTERM da parada limpa. `MaxRAMPercentage`
em vez de `-Xmx` para o valor acompanhar o `mem_limit` de cada ambiente.

Verificado com a própria imagem base (`eclipse-temurin:21-jre-alpine`, container
de 1500 m, reproduzindo prod):

| | MaxHeapSize | Coletor |
|---|---|---|
| Antes | 394.264.576 (376 MB) | SerialGC |
| Depois | 786.432.000 (750 MB) | G1GC |

Sem `JAVA_OPTS` definido o container volta ao comportamento antigo em vez de
quebrar — a mudança do `ENTRYPOINT` é segura sozinha.

### 4.4 Aplicado e verificado em produção (16/set/2026, 02:37–02:52 UTC)

| Ajuste | Antes | Depois | Como foi verificado |
|---|---|---|---|
| Heap do backend | 376 MB | **750 MB** | `jvm_gc_max_data_size_bytes` = 786.432.000 |
| Coletor | SerialGC | **G1** | rótulo `gc="G1 Young Generation"` |
| Pausa máxima de GC | 1.036 ms | **21 ms** | `jvm_gc_pause_seconds_max` (provisório: 6 min de uptime) |
| Métricas do Tomcat | inexistentes | **família `tomcat.*` inteira** | `tomcat_threads_busy`, `connections_*`, `global_request_*`, `servlet_*`, `sessions_*` |
| `mem_limit` do Prometheus | 512 MiB | **1 GiB** | `HostConfig.Memory` = 1073741824 |
| Guarda de consulta | ausente | **`--query.max-samples=10000000` + `--query.timeout=1m`** | `Config.Cmd` |

**Teste de regressão do OOM.** A mesma subquery de P95 sobre 7 dias que matou o
processo às 01:50:57 foi reexecutada depois do ajuste: **completou com sucesso** e
o container ficou de pé (`RestartCount=0`, `OOMKilled=false`). O pico de memória
anônima medido logo após foi de **539 MB** — contra o teto anterior de 512 MB.
Era essa a distância exata entre viver e morrer. O histórico do TSDB sobreviveu
ao recreate (dados de 6 dias atrás continuam consultáveis).

> **Gotcha de verificação.** `docker exec jetski-backend java -XX:+PrintFlagsFinal`
> **não serve** para conferir a JVM em execução: inicia um processo novo, sem o
> `$JAVA_OPTS` que o entrypoint expande, e devolve a ergonomia padrão. Isso produziu
> um falso negativo ("o deploy não pegou") logo após o CD. Confira pelo que o
> processo real publica — `jvm_gc_max_data_size_bytes` e o rótulo `gc=` — ou por
> `docker inspect -f '{{.Config.Env}}'`.

### 4.5 Pendência residual: `tomcat_threads_config_max_threads = -1`

O medidor existe, mas reporta `-1` em vez do teto de threads. Não é virtual
threads (não estão habilitadas) nem executor customizado (não há nenhum no
código) — é o Tomcat não expondo o atributo neste arranjo de conector. Na prática
o denominador é o default de 200, mas fica implícito, e `busy / max` não fecha.

Correção candidata, **ainda não verificada**: fixar `server.tomcat.threads.max: 200`
no `application.yml`. Pina o valor e provavelmente faz o MBean reportá-lo. Como
exige um deploy do backend, vale juntar à próxima leva em vez de um ciclo só para
isso. Até lá, os painéis devem usar 200 como constante.
