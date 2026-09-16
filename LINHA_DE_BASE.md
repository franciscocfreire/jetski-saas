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
| **Heap usado, pico** | **399 MB** |
| Não-heap | 497 MB |
| **Coletor** | **SerialGC** (ergonômico) |
| Pausa de GC máxima | **1.036 ms** (major) / 415 ms (alloc. failure) |
| Tempo total em GC, 7d ocioso | 196 s |

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

### 2.2 O backend está no teto do heap estando ocioso

`MaxHeapSize = 376 MB` (25% do `mem_limit` de 1500 MB, escolha ergonômica da JVM)
e o **pico de heap usado foi 399 MB**. Ou seja: a aplicação já opera colada no
limite — sem nenhum usuário.

Pior, a JVM escolheu **SerialGC**: um container de 1500 MB fica abaixo do limiar
de 1792 MB que a JVM usa para classificar a máquina como "server class", então
ela cai no coletor mono-thread com parada total do mundo. Isso explica a **pausa
máxima de 1.036 ms** — um segundo de aplicação congelada, ociosa.

Sob carga real isso degenera em GC thrashing antes de qualquer outro gargalo.
É o gargalo nº 1 e tem correção barata: heap explícito + `-XX:+UseG1GC`.

*(Correção da hipótese §3.3 do plano: eu havia estimado ~375 MB por dedução;
a medição confirmou 376 MB — mas o achado que importa, o SerialGC, eu não tinha visto.)*

### 2.3 O instrumento de medição quebra antes do medido

**Prometheus a 98,7% do seu `mem_limit` de 512 MB.** Carga maior gera mais séries,
mais séries geram mais memória — ele será morto por OOM exatamente quando for
mais necessário. Aumentar esse limite é pré-requisito do primeiro teste de carga,
não consequência dele.

### 2.4 Uma consulta derrubou a monitoração — a hipótese "sem limite de CPU" se confirmou sozinha

Durante esta própria coleta, uma query de P95 sobre 7 dias saturou uma das
2 vCPUs e deixou o Prometheus sem responder por minutos (queries triviais
passaram a estourar 25 s de timeout). Nenhum container tem `cpus:` definido, então
o Prometheus compete de igual para igual com o backend e o banco.

Isso é a hipótese §3.2 do plano se manifestando **sem precisar de teste de carga**.
O `load average` de pico de **6,37 em 2 vCPUs** conta a mesma história.

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
| §3.3 (heap) | hipótese | **confirmada e agravada**: 376 MB + SerialGC + pausa de 1 s |
| §3.4 (Hikari 10) | hipótese | **sem evidência de problema** (0 pendentes, 0 timeouts) — despriorizar |

### Pré-requisitos do primeiro teste de carga (antes da F3)

1. Subir o `mem_limit` do Prometheus (§2.3) — senão o medidor morre no meio.
2. Expor métricas de thread do Tomcat (§2.7) — senão o gargalo mais provável fica invisível.
3. Definir heap e coletor do backend explicitamente (§2.2) — ou o teste só vai medir SerialGC.
4. Decidir o destino dos 11 containers vizinhos (§2.5) — ou o número não significa nada.

Os itens 1–3 são de configuração, não de código.
