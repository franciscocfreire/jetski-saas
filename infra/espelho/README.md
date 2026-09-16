# Espelho de testes de carga

Uma segunda VM com **a mesma stack de produção**, provisionada do zero, só com
dados sintéticos. É onde rodam os cenários de [`k6/`](../../k6/README.md).

> **Decisão (16/set/2026):** nada de teste de carga em produção. Mesmo com a
> plataforma sem operação real, rodar carga lá significa escrever no banco de
> produção, depender de limpeza manual e perder a opção de buscar o ponto de
> ruptura. O espelho custa algum dinheiro e algum preparo — em troca, pode ser
> quebrado à vontade, quantas vezes for preciso.

## O que é igual e o que muda

**Igual — senão a medição não vale para produção:** mesma shape de VM, mesma
região, mesmo `docker-compose.prod.yml` (perfil `prod`, `mem_limit`s, JVM com
G1 e 750 MB), mesmo nginx, mesmo Keycloak, mesmo túnel Cloudflare no caminho, e
o stack de observabilidade — que é o instrumento do teste.

**Muda — só o que encosta no mundo real:**

| | Produção | Espelho |
|---|---|---|
| Domínio | `meujet.com.br` | domínio próprio (ver abaixo) |
| Túnel Cloudflare | túnel de produção | túnel **novo** |
| E-mail | Gmail | **Mailpit** (nada sai da máquina) |
| Backup | diário + off-site | **nenhum** |
| Login Google | configurável | desligado |
| Dados | reais | **só sintéticos** |
| Deploy | CD automático na `main` | manual (`./deploy.sh`) |

Tudo isso é imposto, não só recomendado: com `MEUJET_AMBIENTE=espelho` no
`.env`, o `deploy.sh` roda [`preflight.sh`](preflight.sh) antes de tocar em
qualquer container e aborta se algo apontar para produção.

## Onde hospedar

**VM Oracle `VM.Standard.A1.Flex`, 2 OCPU / 12 GB, `sa-saopaulo-1`, Ubuntu 24.04**
— a mesma de produção. "Igual" é o ponto: mesmo modelo de CPU (Ampere), mesma
memória, mesma distância até a Cloudflare.

Isso **provavelmente não é gratuito.** Desde 15/jun/2026 o Always Free de Ampere A1
é de 2 OCPU / 12 GB por tenancy, e a produção já consome tudo. A tenancy já é
Pay-As-You-Go (desde mar/2022), então a VM pode ser criada direto e o excedente
vai para a fatura. (Há relatos de contas PAYG que mantiveram a cota antiga de
4 OCPU / 24 GB — se for o caso, a primeira fatura mostra custo zero.) Ordem de
grandeza — confira na calculadora da Oracle antes:

- ligada 24 h × 7: ~US$ 0,04/h → ~US$ 28/mês
- **desligada entre rodadas:** a Oracle não cobra OCPU/RAM de instância parada,
  só o volume de boot (~US$ 1–2/mês) — uma sessão de testes de 8 h sai por centavos

Na região de São Paulo a criação de A1 às vezes falha com *Out of host capacity*;
insistir em outro horário ou em outro domínio de disponibilidade costuma resolver.

## Por que um domínio próprio

Não dá para usar subdomínios de `meujet.com.br` (tipo `app-carga.meujet.com.br`):

1. **O nginx roteia por `server_name` fixo.** `cliente.`, `admin.` e `sso.` só
   são reconhecidos para os domínios listados em `infra/nginx/nginx.conf`; um host
   desconhecido cai no servidor padrão, e o portal e o SSO respondem conteúdo
   errado — sem erro, só comportamento estranho.
2. **A vitrine captura `*.meujet.com.br`.** O `middleware.ts` do backoffice
   reescreve a raiz de qualquer subdomínio não reservado para `/loja/{slug}`.
3. **Certificado.** O certificado universal da Cloudflare cobre um nível
   (`*.meujet.com.br`), não dois (`app.carga.meujet.com.br`).

O repositório já tem o precedente: o dev usa `pegaojet.com.br`, listado no
`nginx.conf` ao lado de `meujet.com.br`. O espelho segue o mesmo desenho.

**Domínio escolhido: `jetsave.com.br`** (16/set/2026) — já na conta Cloudflare,
sem tráfego e fora do túnel de produção (conferido nos logs do cloudflared). Foi o
domínio original da plataforma, por isso já aparecia no `nginx.conf` e no
middleware; faltava só o `sso.`, e o CORS do backend, ambos incluídos junto com
este kit.

## Passo a passo

### 1. Domínio e código

Feito para o `jetsave.com.br`: zona na Cloudflare, `server_name` do apex,
`cliente.`, `admin.` e `sso.` no `nginx.conf`, `HOST_VITRINE` no middleware e
origens no CORS do backend. Para trocar de domínio um dia, é repetir esses quatro
pontos — o preflight reprova enquanto o nginx não conhecer o domínio.

### 2. VM e túnel

1. Crie a VM (shape acima) e rode o bootstrap, igual à produção
   (`infra/prod/server-bootstrap.sh`, ver [`DEPLOY.md`](../../DEPLOY.md) §1).
2. Cloudflare Zero Trust → **Networks → Tunnels → Create a tunnel** — um túnel
   **novo**, com nome inconfundível (ex.: `meujet-espelho`). Em *Public Hostnames*,
   aponte o apex, `www`, `app`, `cliente`, `admin` e `sso` do domínio do espelho
   para `http://nginx:80`.
3. Anote o UUID do túnel novo **e** o do túnel de produção.

### 3. `.env` — gerado, nunca copiado

```bash
./infra/espelho/gerar-env.sh jetsave.com.br
nano .env     # token do túnel, ESPELHO_TUNNEL_ID, PROD_TUNNEL_ID
./infra/espelho/preflight.sh
```

`gerar-env.sh` cria segredos novos e se recusa a sobrescrever um `.env` existente.

### 4. Deploy e observabilidade

```bash
git checkout <branch>        # o espelho não recebe CD: deploy é manual
./deploy.sh
docker compose --env-file .env -f infra/observability/docker-compose.observability.yml up -d
```

Confirme que o espelho é mesmo igual à produção nos pontos que o teste mede
(os mesmos sinais de [`LINHA_DE_BASE.md`](../../LINHA_DE_BASE.md) §4.4):
`jvm_gc_max_data_size_bytes` ≈ 786 MB, rótulo `gc="G1 Young Generation"`,
métricas `tomcat_threads_*` presentes e Prometheus com `mem_limit` de 1 GiB.
**Não use `docker exec ... java -XX:+PrintFlagsFinal` para isso** — ele sobe uma
JVM nova sem o `$JAVA_OPTS` e mostra a ergonomia padrão.

### 5. Primeiro operador de plataforma

O tenant de carga nasce pendente de aprovação, e um espelho novo não tem ninguém
que aprove. Pelo caminho real:

1. Cadastre uma empresa qualquer com o seu e-mail em `https://www.<dominio>`.
2. Abra o Mailpit — `ssh -L 8025:127.0.0.1:8025 ubuntu@<ip-do-espelho>` e
   `http://localhost:8025` — e siga o link de ativação.
3. Ponha o e-mail em `PLATFORM_ADMIN_EMAILS` no `.env` e rode `./deploy.sh` de novo
   (ou só recrie o backend): o boot promove o usuário a operador.

### 6. Tenant de carga e testes

Siga [`k6/README.md`](../../k6/README.md) apontando para o espelho
(`BASE_URL=https://www.<dominio>/api`, `ISSUER=https://sso.<dominio>/realms/jetski-saas`).
A aprovação acontece em `https://admin.<dominio>`; o link de ativação do admin
chega no Mailpit.

**Rode o k6 de fora do espelho.** Na mesma VM, o gerador de carga disputa as
2 OCPUs com a aplicação e o resultado não vale nada.

## Armadilhas

| Se… | …acontece | Proteção |
|---|---|---|
| o `.env` do espelho tiver o **token do túnel de produção** | o cloudflared do espelho entra como réplica do túnel de produção e a Cloudflare manda **usuários reais** para o espelho | preflight decodifica o token e exige o UUID do túnel do espelho |
| o espelho tiver o **`BACKUP_RCLONE_REMOTE` de produção** | o `backup.sh` faz `rclone sync`, que **apaga do destino** o que não existe na origem — os backups reais somem | preflight reprova; `deploy.sh` não instala o timer no espelho |
| o espelho tiver **credencial do Gmail** | e-mails de teste consomem a cota de 500/dia da operação | preflight reprova; todo e-mail vai ao Mailpit |
| o stress derrubar o backend | o alerta "Backend fora do ar" dispara | alertas do espelho vão para o Mailpit, não para quem responde por produção |
| alguém rodar o `deploy.sh` do espelho **na VM de produção** | — | preflight reprova se encontrar o timer de backup de produção na máquina |
| o k6 passar pela Cloudflare sem regra | *Bot Fight Mode*/*Browser Integrity Check* bloqueiam o k6 (erro **1010**) | crie uma regra de WAF que pule essas checagens **só nos hostnames do espelho** |

E dois limites conhecidos, que não afetam o k6:

- **E-mails do próprio Keycloak não chegam.** O realm é importado com AUTH e
  STARTTLS ligados, que o Mailpit não tem — código de login do portal e reset de
  senha pelo Keycloak não funcionam. O backoffice entra por senha, então o teste
  não depende disso.
- **O alerta "Backup diário não rodou" vai disparar.** Esperado: o espelho não faz
  backup.

## Dados: só sintéticos

**Nunca restaure um backup de produção no espelho.** O backup carrega CPF,
documentos, fotos e assinaturas de clientes reais; copiá-lo para outra máquina
multiplica a superfície de exposição e é tratamento de dado pessoal sem finalidade
(LGPD). Para o teste de carga, dado sintético é melhor de qualquer forma: dá para
gerar o volume que se quiser.

O drill de restore (F5 do plano) também mexe com dado real e é outra conversa —
em máquina isolada, destruída ao final.

## Ciclo de vida

- **Entre rodadas:** pare a instância no console da Oracle.
- **Depois de uma rodada destrutiva** (stress, soak): o mais limpo é recriar o
  banco — `docker compose ... down -v` no espelho e deploy de novo. O espelho não
  tem nada a preservar.
- **Ao encerrar a fase de testes:** termine a instância e o volume de boot, apague o
  túnel e as regras de WAF.
