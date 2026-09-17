# Testes de carga (k6)

Fase 3 de [`CAPACIDADE_E_LIMITES.md`](../CAPACIDADE_E_LIMITES.md). Os cenários
rodam no **espelho de carga** — uma VM com a mesma stack de produção e só dado
sintético, montada conforme [`infra/espelho/README.md`](../infra/espelho/README.md) —,
dentro de um **tenant isolado** provisionado pelo fluxo de signup real.

## Regras de segurança — leia antes de rodar

1. **Só contra tenant de carga.** `lib/config.js` recusa rodar se o slug não
   começar com `carga-`. Para forçar (ex.: tenant de dev), passe
   `-e PERMITIR_TENANT=<id>` conscientemente.
2. **Nenhum cenário emite documento.** `/emissoes` e `/gru` estão fora: emitir
   gera **GRU real na Marinha**, que bloqueia por volume (~8–10 por dia por CPF)
   e consome crédito de verdade. Testar emissão exige stub — é trabalho à parte.
3. **Nenhum cenário dispara e-mail.** `/enviar-pix-email` e convites estão fora;
   a cota do Gmail é de 500/dia e é compartilhada com a operação.
4. **Dados sintéticos e rastreáveis.** Todo registro criado leva a marca
   `CARGA`, e os e-mails usam `@exemplo.invalid` (domínio reservado pela RFC
   2606, que nunca resolve) — nem por acidente algo chega a uma caixa real.
5. **Nunca em produção.** Decisão de 16/set/2026: carga roda no espelho. Os
   cenários, o `gerar-tokens.sh` e o semeador **recusam** qualquer
   alvo em `meujet.com.br`; a liberação exige
   `PERMITIR_PRODUCAO="sim, é produção"` e existe só para um smoke pontual e
   consciente.

## Preparo

As empresas de carga, seus admins e a frota **já existem** quando o espelho sobe: o
semeador de personas ([`sintetico/`](../sintetico/README.md)) cria tudo pelas APIs reais,
no provisionamento — inclusive a aprovação, feita pela persona operadora de plataforma.
Não há passo manual.

Só falta obter os tokens:

```bash
./k6/gerar-tokens.sh <ip-do-espelho>          # gera k6/.auth/tokens.json (~12 h de validade)
```

O arquivo traz um refresh token por admin **e o tenant de cada um**: a carga se espalha
por várias empresas ao mesmo tempo, que é como o SaaS opera de verdade (cada VU opera a
sua). `lib/config.js` confere que **todas** são `carga-*` antes de começar.

**Por que o login não é um simples POST:** nenhum client que a API aceita tem ROPC, e a
tela de login é React (sem `<form>` no HTML). O login de verdade — authorization_code +
PKCE, lendo o `kcContext` da página — está em `sintetico/src/lib/keycloak.ts`; o k6 só
troca refresh por access token.

> **Dimensione a frota acima do número de VUs por empresa.** Cada jornada de balcão ocupa
> um jetski entre o check-in e o check-out. O catálogo cria 20 por empresa; o contador
> `jornadas_abortadas_sem_jetski` acusa quando falta.

## Rodar

```bash
export BASE_URL=https://www.<dominio-do-espelho>/api
export ISSUER=https://sso.<dominio-do-espelho>/realms/jetski-saas

# portão de sanidade — sempre primeiro
k6 run -e PERFIL=smoke k6/cenarios/leitura.js

k6 run -e PERFIL=load   k6/cenarios/leitura.js
k6 run -e PERFIL=load   k6/cenarios/balcao.js
k6 run -e PERFIL=stress k6/cenarios/balcao.js
k6 run -e PERFIL=soak -e DURACAO=4h k6/cenarios/leitura.js
k6 run -e PERFIL=spike  k6/cenarios/leitura.js
```

Jornada e intensidade são separadas de propósito: o mesmo cenário roda em todos
os perfis, mudando só `-e PERFIL=`.

### Sem instalar o k6

A imagem oficial resolve, e foi com ela que estes cenários foram validados:

```bash
docker run --rm -v "$PWD:/src" -w /src grafana/k6:latest \
  run -e PERFIL=smoke -e BASE_URL=... -e ISSUER=... \
  k6/cenarios/leitura.js
```

> **Gotcha:** os `-e` precisam vir **depois** do subcomando (`run`/`inspect`).
> Antes do nome da imagem eles viram variável do Docker e o k6 não os enxerga em
> `__ENV` — o sintoma é o cenário rodar sempre no perfil `smoke`, calado.

Para checar um cenário sem gerar carga nenhuma, troque `run` por `inspect`: ele
executa o contexto de init e imprime as opções resolvidas.

### Cenários

| Arquivo | O que modela | O que procura |
|---|---|---|
| `cenarios/leitura.js` | o dia do operador: controle do dia, agenda, frota, lista de clientes | saturação de pool e de threads sob leitura concorrente |
| `cenarios/balcao.js` | atendimento presencial: cadastro → check-in walk-in → check-out → extrato | contenção em escrita, custo do cálculo de cobrança (RN01) |

### Perfis

| Perfil | Forma | Para quê |
|---|---|---|
| `smoke` | 1 VU, 2 min | o ambiente está sadio? |
| `load` | rampa até `VUS` (20), 30 min | aguenta o sábado esperado? |
| `stress` | rampa crescente até quebrar | onde está o joelho da curva? |
| `soak` | `VUS` (10) por 4 h | vaza memória/conexão/ThreadLocal? |
| `spike` | 0 → `PICO` (80) em 1 min | sobrevive às 8h de sábado? |

No `stress` os thresholds são desligados de propósito: estourar a meta **é** o
resultado esperado, não uma falha de teste.

> **O soak é o mais valioso aqui.** O histórico do projeto inclui um vazamento
> de `ThreadLocal` do `TenantContext` que contaminava jobs agendados — defeito
> que só aparece depois de horas.

## O que olhar enquanto roda

Grafana **do espelho** (`https://www.<dominio-do-espelho>/grafana`) → **Saúde de
Produção** (o painel tem esse nome, mas mostra a máquina em que roda),
**Endpoints do Backend** e **Performance do Sistema**. Os sinais que a linha de
base preparou:

| Sinal | Métrica | O que significa estourar |
|---|---|---|
| Fila de threads | `tomcat_threads_busy_threads` | o Tomcat virou o gargalo (teto: 200, o default — o medidor `config_max_threads` ainda reporta `-1`) |
| Espera por conexão | `hikaricp_connections_pending` | o pool de 10 ficou curto |
| Pressão de heap | `jvm_gc_pause_seconds_max` | o G1 de 750 MB não está dando conta |
| Conexões do banco | `pg_stat_activity` | lembrar que o Keycloak divide as 100 do Postgres |
| Latência que só sobe | P95 de `GET /locacoes`, `/clientes`, `/jetskis` | essas listas **não são paginadas** e crescem a cada jornada de balcão — num soak, desconfie delas antes de desconfiar de vazamento |

## Limpeza

No espelho não há nada a preservar: depois de uma rodada destrutiva, o mais
limpo é recriar o banco (ver o README do espelho, "Ciclo de vida"). Para desfazer
só um tenant, exclua a empresa pelo console — não saia apagando linha por SQL:
há RLS e ordem de FK.

## O que ainda não está aqui

- **Upload de fotos no check-in** — a operação mais cara do sistema e a hipótese
  nº 5 da linha de base. Merece cenário e perfil próprios; misturar com o balcão
  produz um P95 que não explica nada (por isso o check-out vai com
  `skipPhotos: true`).
- **Portal do cliente e marketplace** — tráfego público, sem login, com outro
  perfil de cache.
- **Emissão com stub** — exige desarmar a integração com a Marinha primeiro.
