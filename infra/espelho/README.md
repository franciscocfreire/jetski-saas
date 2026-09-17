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

- **computação**, só enquanto ligada: ~US$ 0,04/h (2 OCPU + 12 GB) → ~US$ 28/mês se
  ficar 24 h × 7; uma sessão de testes de 8 h sai por ~US$ 0,30
- **disco**, sempre, ligada ou parada: o volume de boot de **200 GB** — o mesmo de
  produção, porque na OCI o IOPS e a vazão crescem com o tamanho e um disco menor
  mediria outro Postgres — fica na casa de **US$ 8–9/mês**

Então: desligada entre rodadas, o espelho custa ~US$ 9/mês mais as horas de teste;
destruído ao fim da fase, custa zero.

Pode rodar também em **outra conta ou região** — o que muda na leitura dos
resultados está em [`terraform/README.md`](terraform/README.md#outra-conta-ou-outra-região).

São Paulo tem um único domínio de disponibilidade, e a criação de A1 às vezes falha
com *Out of host capacity*; tentar de novo mais tarde costuma resolver.

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

> **Caminho recomendado: Terraform** ([`terraform/`](terraform/README.md)). Ele
> cria a VM, a rede, o túnel, o DNS e as regras da zona, e a VM se provisiona
> sozinha (cloud-init → `provisionar-vm.sh`), executando os passos 3 e 4 abaixo
> exatamente como descritos. Os passos 2–4 manuais ficam como referência e
> alternativa.

### 1. Domínio e código

Feito para o `jetsave.com.br`: zona na Cloudflare, `server_name` do apex,
`cliente.`, `admin.` e `sso.` no `nginx.conf`, `HOST_VITRINE` no middleware e
origens no CORS do backend. Para trocar de domínio um dia, é repetir esses quatro
pontos — o preflight reprova enquanto o nginx não conhecer o domínio.

### 2. VM e túnel *(o Terraform faz)*

1. Crie a VM (shape acima) e rode o bootstrap, igual à produção
   (`infra/prod/server-bootstrap.sh`, ver [`DEPLOY.md`](../../DEPLOY.md) §1).
2. Cloudflare Zero Trust → **Networks → Tunnels → Create a tunnel** — um túnel
   **novo**, com nome inconfundível (ex.: `meujet-espelho`). Em *Public Hostnames*,
   aponte o apex, `www`, `app`, `cliente`, `admin` e `sso` do domínio do espelho
   para `http://nginx:80`.
3. Anote o UUID do túnel novo **e** o do túnel de produção.

### 3. `.env` — gerado, nunca copiado *(o Terraform faz, dentro da VM)*

```bash
./infra/espelho/gerar-env.sh jetsave.com.br
nano .env     # token do túnel, ESPELHO_TUNNEL_ID, PROD_TUNNEL_ID
./infra/espelho/preflight.sh
```

`gerar-env.sh` cria segredos novos e se recusa a sobrescrever um `.env` existente.

### 4. Deploy e observabilidade *(o Terraform faz, dentro da VM)*

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

### 5. Personas *(o provisionamento faz)*

A etapa 7 do `provisionar-vm.sh` roda o semeador ([`sintetico/`](../../sintetico/README.md)):
a **operadora de plataforma sintética** nasce pelo cadastro público, é promovida pelo boot
do backend, entra no console configurando TOTP e **aprova as empresas de carga pela API** —
sem nenhum clique. O espelho sobe já com 3 empresas `carga-*` no plano Enterprise, 20
jetskis cada.

Para entrar você mesmo no console (`https://admin.<dominio>`), use as credenciais da
persona — senha e `otpauth://` para o seu app autenticador estão em
`/var/lib/meujet-espelho/personas.json` na VM (ver o README do `sintetico/`).

### Como entrar no console do espelho

O console (`https://admin.<dominio>`) exige 2FA, e o único operador de um espelho novo é
a **persona operadora sintética**. Você entra com as credenciais dela:

1. **Pegue e-mail, senha e o link do autenticador** (ficam só na VM, arquivo 0600):

   ```bash
   ssh ubuntu@<ip-do-espelho> 'sudo cat /var/lib/meujet-espelho/personas.json' \
     | jq '.personas["operador-plataforma"] | {email, senha, otpauth}'
   ```

2. **Ponha a persona no seu app autenticador** (Google Authenticator, Authy, 1Password…) —
   uma vez por espelho. O `otpauth://…` é o conteúdo de um QR code:

   ```bash
   sudo apt install -y qrencode            # se ainda não tiver
   qrencode -t ansiutf8 '<cole o otpauth aqui>'
   ```

   Escaneie o QR no terminal. Sem `qrencode`: no app, escolha "inserir chave manualmente" e
   digite o valor de `secret=` do link (tipo *baseado em tempo*, 6 dígitos, 30 s).

3. **Entre em `https://admin.<dominio>`** com o e-mail e a senha do passo 1, e o código de
   6 dígitos do app.

O IP do espelho e as URLs saem de `terraform output` (em `infra/espelho/terraform/`).
A senha e o segredo TOTP **mudam a cada espelho recriado** — repita os passos 1 e 2.

Para entrar num **backoffice** de empresa de carga (`https://app.<dominio>`), o mesmo arquivo
traz e-mail e senha dos admins (`.personas["carga-praia-norte"]` etc.) — eles não têm 2FA.

> Tudo isso é dado sintético de um ambiente descartável. **Nunca** reutilize essas senhas,
> e não copie o `personas.json` para fora da sua máquina.

### 6. Testes

```bash
./k6/gerar-tokens.sh <ip-do-espelho>
```

e siga [`k6/README.md`](../../k6/README.md).

**Rode o k6 de fora do espelho.** Na mesma VM, o gerador de carga disputa as
2 OCPUs com a aplicação e o resultado não vale nada.

## Armadilhas

| Se… | …acontece | Proteção |
|---|---|---|
| o `.env` do espelho tiver o **token do túnel de produção** | o cloudflared do espelho entra como réplica do túnel de produção e a Cloudflare manda **usuários reais** para o espelho | preflight decodifica o token e exige o UUID do túnel do espelho |
| o espelho tiver o **`BACKUP_RCLONE_REMOTE` de produção** | o `backup.sh` faz `rclone sync`, que **apaga do destino** o que não existe na origem — os backups reais somem | preflight reprova; `deploy.sh` não instala o timer no espelho |
| o espelho tiver **credencial do Gmail** | e-mails de teste consomem a cota de 500/dia da operação | preflight reprova; todo e-mail vai ao Mailpit |
| uma persona ou cenário emitir documento e o backend usar o endereço **real** da Marinha/PagTesouro | GRU real emitida para um CPF sintético — que pode ser de uma pessoa de verdade (não existe faixa de CPF de teste) | duas camadas: as bases da GRU apontam para `fakes-externos`, e os domínios reais resolvem para `127.0.0.1` dentro do container do backend (conexão recusada); o preflight reprova se faltar qualquer uma, conferindo o compose **renderizado** |
| o stress derrubar o backend | o alerta "Backend fora do ar" dispara | alertas do espelho vão para o Mailpit, não para quem responde por produção |
| alguém rodar o `deploy.sh` do espelho **na VM de produção** | — | preflight reprova se encontrar o timer de backup de produção na máquina |
| o k6 passar pela Cloudflare com as proteções de bot ligadas | *Bot Fight Mode*/*Browser Integrity Check* bloqueiam o k6 (erro **1010**) | o Terraform desliga as duas **na zona `jetsave.com.br` inteira** — seguro porque a zona é só do espelho, e o plano gratuito não pula Bot Fight Mode por hostname |
| o Terraform acabar gerenciando o **túnel de produção** (ex.: `terraform import` errado) | um `apply` reescreveria as rotas de produção | pré-condição reprova o plano se o túnel do state tiver o UUID de produção; o `.gitignore` e o compartimento próprio limitam o resto |

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

Com o Terraform (em `infra/espelho/terraform/`):

| Quero… | Comando |
|---|---|
| desligar entre rodadas | `terraform apply -var ligada=false` |
| religar | `terraform apply` |
| espelho do zero, no mesmo commit de produção | `terraform apply -replace=oci_core_instance.espelho -var git_ref=<commit-de-produção>` |
| encerrar a fase de testes | `terraform destroy` |

Depois de uma rodada destrutiva (stress, soak), recriar a VM é o jeito mais limpo
de voltar a um banco vazio — o espelho não tem nada a preservar.

Sem Terraform: pare/termine a instância no console, e apague túnel e DNS no painel.
