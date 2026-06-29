# Deploy de Produção — MeuJet

Stack completa via Docker Compose num servidor **Oracle Cloud ARM (Ubuntu 24.04, 11 GB / 2 OCPU)**.
Storage real de documentos em **MinIO**, ingress público por **Cloudflare Tunnel**, CD por **GitHub Actions**.

> Princípio: **deploy não-destrutivo**. `deploy.sh` nunca roda DROP/TRUNCATE; migrations são
> idempotentes (Flyway) e a RLS é verificada a cada deploy (aborta se faltar isolamento).

## Arquitetura

```
Internet ──HTTPS──> Cloudflare ──tunnel──> cloudflared ──> nginx ──┬─> /api      backend (jetski_app, RLS)
                                                                    ├─> /realms   keycloak
                                                                    └─> /         frontend (Next.js)
backend ──> postgres (jetski_prod) · redis · opa · minio (jetski-docs)
flyway (one-shot, superuser) aplica migrations antes do backend
```

Nada é exposto na internet diretamente: as portas do compose ficam em `127.0.0.1`.
O único caminho de entrada é o Cloudflare Tunnel.

## Arquivos

| Arquivo | Papel |
|---|---|
| `docker-compose.yml` | base (dev) |
| `docker-compose.prod.yml` | override de produção (segredos, cloudflared, flyway, limites) |
| `.env.prod.example` | modelo de segredos → copie para `.env` no servidor |
| `deploy.sh` | deploy não-destrutivo (pull → migrate → verifica RLS → build → up) |
| `infra/prod/01-init-roles.sql` | cria role `jetski_app` (NOSUPERUSER/NOBYPASSRLS) + grants |
| `infra/prod/02-verify-rls.sql` | guarda: falha se tabela com `tenant_id` estiver sem RLS |
| `infra/prod/server-bootstrap.sh` | one-time: instala Docker + clona repo |
| `.github/workflows/cd.yml` | CD: push na main (após CI) → SSH → `deploy.sh` |

## 1. Bootstrap do servidor (uma vez)

```bash
ssh -i ~/.ssh/ssh-key-2026-06-15.key ubuntu@64.181.176.180
# no servidor:
bash <(curl -fsSL https://raw.githubusercontent.com/<owner>/<repo>/main/infra/prod/server-bootstrap.sh) \
     https://github.com/<owner>/<repo>.git
newgrp docker   # ou logout/login para usar docker sem sudo
```

> Repo privado? Configure um deploy key/PAT antes do clone, ou clone manualmente.

## 2. Configurar segredos

```bash
cd ~/jetski
cp .env.prod.example .env
nano .env   # preencha TODOS os segredos (senhas fortes, NEXTAUTH_SECRET, MinIO, tunnel token)
```

Gere segredos:
```bash
openssl rand -base64 32   # NEXTAUTH_SECRET
openssl rand -base64 24   # senhas de banco/MinIO
```

## 3. Cloudflare Tunnel

1. Cloudflare Zero Trust → **Networks → Tunnels → Create a tunnel** (Cloudflared).
2. Copie o **token** e cole em `CLOUDFLARE_TUNNEL_TOKEN` no `.env`.
3. Em **Public Hostnames** do tunnel, adicione:
   - Hostname: `meujet.com.br` (e/ou `www`) → Service: `http://nginx:80`
4. O DNS (CNAME) é criado automaticamente pelo Cloudflare.

## 4. Primeiro deploy

```bash
cd ~/jetski
./deploy.sh
```

`deploy.sh` sobe a infra, cria o role, aplica migrations (V001–V013, inclui seed do tenant ACME),
verifica RLS, builda backend/frontend (ARM nativo), sobe nginx + cloudflared, e **configura o
client Keycloak** `jetski-backoffice` (confidencial + secret + PKCE + redirects do `PUBLIC_URL`,
via `infra/prod/configure-keycloak-client.sh`, idempotente). Ao final faz smoke check em
`http://127.0.0.1:8090/api/actuator/health`.

Acesse: **https://meujet.com.br** — login com os usuários do realm (ex: `admin@acme.com`).

## 5. CD (deploys seguintes)

Configure em **GitHub → Settings → Secrets and variables → Actions**:

| Secret | Valor |
|---|---|
| `SSH_HOST` | `64.181.176.180` |
| `SSH_USER` | `ubuntu` |
| `SSH_PRIVATE_KEY` | conteúdo de `~/.ssh/ssh-key-2026-06-15.key` |
| `DEPLOY_PATH` | `/home/ubuntu/jetski` |

A partir daí, todo push na `main` que passar no **CI** dispara o **CD**, que faz
`git reset --hard origin/main` + `deploy.sh` no servidor. Também dá para acionar
manualmente em **Actions → CD → Run workflow** (com opção de pular o rebuild).

## Operação

```bash
C="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
$C ps                       # status
$C logs -f backend          # logs
$C exec -T postgres pg_dump -U jetski jetski_prod | gzip > backup-$(date +%F).sql.gz   # backup DB
# MinIO: dados em volume jetski_minio_data; backup via `mc mirror` ou snapshot do volume
```

### Gotchas (aprendidos no dev)
- **Cache de build**: `deploy.sh` usa `build --no-cache` no backend + `--force-recreate` (cache reaproveitava imagem velha).
- **OPA** carrega `/policies` sem `--watch` → `deploy.sh` faz `restart opa` após pull.
- **Storage** persiste em volumes (`minio_data`, `storage_data`) → sobrevive a recreate.
- **Migrations** rodam pelo container `flyway` (superuser); o backend roda com `jetski_app` (RLS).
