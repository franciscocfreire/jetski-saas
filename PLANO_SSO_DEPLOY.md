# Plano — SSO sem queda a cada deploy e alerta que enxerga a queda

> Status: **plano** (13/set/2026). Nada implementado.
> Origem: indisponibilidade do SSO de produção durante os deploys dos PRs #12 e #13.

## 1. O que aconteceu

Horários em BRT (UTC−3).

| Hora | Evento | Evidência |
|---|---|---|
| 17:57 | Deploy do PR #12 (segurança P0) recria **Postgres** (compose de prod mudou: SCRAM + `pg_hba`) e **Keycloak** | log do CD `34782328554` |
| 17:58 a 18:01 | Keycloak sem resposta TCP | `net_response_result_code{server="keycloak"}` |
| 18:01 | Alerta "Dependência interna fora do ar" dispara | e-mail FIRING 21:01 UTC |
| 18:06 | Alerta resolvido; deploy termina | e-mail RESOLVED 21:06 UTC |
| 18:07 | Deploy do PR #13 (só frontend e testes) **recria o Keycloak de novo** | log do CD `34782694744` |
| 18:08 a 18:12 | Segunda queda do Keycloak, **sem alerta** | mesma métrica |

Não houve deploys simultâneos: o CD tem `concurrency: cd-prod` e o segundo esperou o primeiro.

**Causa 1 — o Keycloak é recriado em todo deploy.** O `deploy.sh` faz `build keycloak` e
depois `up -d keycloak`. No PR #13 todas as etapas do build vieram do cache e a config da
imagem é a mesma do deploy anterior (`sha256:2b7583e7…`), mas o BuildKit gera um **atestado
de proveniência** novo a cada build. A lista de manifestos muda (`ad7a188e…` → `674f1fd8…`),
o compose vê "imagem nova" e recria o container. O comentário do `deploy.sh` que promete
"deploy sem mudança de tema = no-op" deixou de valer.

**Causa 2 — a recriação coincide com os builds pesados.** Logo depois de subir a infra, o
`deploy.sh` roda `build --no-cache` de backend e três frontends na mesma VM. O Keycloak, que
sobe em ~11 s num servidor ocioso, ficou ~3,5 min sem responder nas duas vezes. Hipótese
forte, não medida: disputa de CPU.

**Causa 3 — o alerta não vê quedas curtas.** A regra é TCP interna em `keycloak:8080`, com
`for: 3m`, avaliação a cada 1 min e telegraf a cada 30 s. A segunda queda ficou no limite e
não disparou. E TCP aberto não garante login funcionando (a porta abre antes do realm estar
pronto).

## 2. Ajuste A — não recriar o Keycloak à toa

### Mudanças

1. **Desligar os atestados no build do Keycloak.** No `deploy.sh`, antes de
   `$COMPOSE build keycloak`: `export BUILDX_NO_DEFAULT_ATTESTATIONS=1` (ou `provenance: false`
   no `build` do serviço, se a versão do compose do servidor aceitar — conferir). Sem atestado,
   build 100% em cache devolve o mesmo digest e o `up -d` não recria.
2. **Guarda explícita, para não depender só do item 1.** Guardar o id da imagem antes do build
   e comparar depois; se não mudou, subir com `up -d --no-recreate keycloak`. Registrar no log
   qual caminho foi tomado ("Keycloak mantido" × "Keycloak recriado: imagem mudou").
3. **Tirar a recriação da janela dos builds pesados.** Reordenar o `deploy.sh`: fazer os builds
   de backend e frontends **antes** do `up -d` da infra. Quando o Keycloak precisar mesmo ser
   recriado (tema, SPI, config ou dependência mudou), esperar `http://keycloak:9000/health/ready`
   com timeout antes de seguir.
4. Corrigir o comentário do `deploy.sh` para descrever o comportamento real.

Recriar junto com o Postgres continua correto quando a config do Postgres muda (caso do PR #12);
o objetivo é que isso aconteça **só** nesses casos.

### Validação

- No servidor, fora de deploy: `docker compose … build keycloak` duas vezes seguidas e comparar
  `docker images --digests jetski-keycloak` — com o item 1, o digest não pode mudar.
- No primeiro deploy sem mudança de Keycloak: o log mostra `Container jetski-keycloak Running`
  (não `Recreate`) e `net_response_result_code{server="keycloak"}` fica em 0 o tempo todo.
- Num deploy que muda o tema: o Keycloak é recriado, o `deploy.sh` espera o `health/ready` e a
  queda fica abaixo de 1 min.

### Risco e reversão

Tema ou SPI alterados e **não** aplicados, se a guarda errar para o lado de não recriar. Por isso
a guarda compara o id da imagem (muda quando o conteúdo muda) e loga a decisão. Reverter é
reverter o commit do `deploy.sh`.

## 3. Ajuste B — alerta que enxerga a queda do SSO

> **Implementado (13/set/2026)** na branch `feat/alerta-sso`, validado com containers
> descartáveis (telegraf `--test` e exportando, Grafana 10.1.5 com o provisionamento).
> Diferença para o desenho abaixo: além de `tagexclude = ["result", "status_code"]`, foi
> preciso `fieldexclude = ["result_type"]` — o campo texto vira label no
> `prometheus_client` e criaria uma série nova a cada falha, segurando o alerta aceso por
> 3 min depois da volta. Aplicar em prod é manual (ver descrição do PR).

### Mudanças

1. **Duas sondas HTTP no telegraf** (`inputs.http_response`, a cada 30 s; o telegraf já está na
   rede da aplicação):
   - **Interna:** `http://keycloak:9000/health/ready` (`KC_HEALTH_ENABLED` já está ligado em prod).
     Mede "Keycloak pronto com banco", não só porta aberta.
   - **Pública:** `https://sso.meujet.com.br/realms/jetski-saas/.well-known/openid-configuration`,
     com `User-Agent` de navegador (sem ele o Cloudflare devolve 403 "1010"). Mede o caminho do
     usuário: túnel, nginx e Keycloak.
2. **Regra nova "SSO indisponível"** em `alertas.yml`: dispara quando qualquer sonda falha
   (`http_response_result_code != 0` ou código HTTP diferente de 200) por **1 min**, severidade
   critical. A regra TCP atual continua, para o Postgres, Redis e OPA.
3. **Painel**: disponibilidade do SSO (interna × pública) no dashboard de saúde.

### Validação

- Em dev, com a stack de observabilidade: `docker stop jetski-keycloak` por 90 s. O alerta tem
  que disparar em até ~2 min e resolver sozinho após o `start`.
- Em prod, depois do Ajuste A: o próximo deploy sem mudança de Keycloak não gera alerta nenhum.
- Lembrete: em prod, regra provisionada por arquivo exige recriar o Grafana (e o telegraf, para
  a config nova).

## 4. Ordem sugerida

1. **B primeiro**: dá visibilidade imediata e mede o efeito do A.
2. **A depois**: itens 1 e 4 são pequenos; 2 e 3 mexem na ordem do deploy e merecem um deploy
   acompanhado.

## 5. Decisões pendentes

- **Silenciar alertas durante deploy?** Recomendo não silenciar: com o Ajuste A, um alerta
  durante deploy passa a ser sinal real de problema.
- **Onde fica a sonda pública.** Do próprio servidor ela não detecta queda do link da VM; um
  monitor externo (fora da Oracle) cobriria isso, mas é outro serviço a manter.
- **Janela de deploy com mudança de infra** (Postgres, Keycloak): avisar antes ou agendar fora
  do horário de uso.
