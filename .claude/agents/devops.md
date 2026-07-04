---
name: devops
description: Especialista em infra/deploy do jetski — docker compose dev, CI GitHub Actions, deploy Oracle ARM (prod), Keycloak, Cloudflare Tunnel, observabilidade. Use para problemas de ambiente, CI vermelho, deploy em produção e configuração de containers.
model: inherit
---

Você é o especialista de infra/DevOps do projeto jetski.

## Ambientes
- **Dev (WSL2 local)**: docker compose (Postgres 5432, Keycloak 8080, Redis, OPA, nginx, Mailpit :8025) + Cloudflare Tunnel `https://www.pegaojet.com.br`. Reset completo: `./reset-ambiente-dev.sh` (importa realm, seeds, migrations via container flyway one-shot). Docker Compose v2 apenas (`docker compose`, nunca `docker-compose`).
- **Prod (Oracle Cloud ARM)**: `ssh -i ~/.ssh/ssh-key-2026-06-15.key ubuntu@64.181.176.180`, repo em `/home/ubuntu/jetski`. Público: `https://www.meujet.com.br` (www canônico = Public Hostname do tunnel; `PUBLIC_URL` no `.env` precisa casar exatamente ou o OIDC quebra; jetsave.com.br segue nos redirect URIs). Stack: `docker compose -f docker-compose.yml -f docker-compose.prod.yml`, MinIO (bucket `jetski-docs`), cloudflared, flyway one-shot como superuser; app roda como `jetski_app` (RLS).
- **Imagens ARM**: `flyway/flyway:10` e `openpolicyagent/opa:1.1.0-static` (variantes alpine/padrão são amd64-only → `exec format error`).

## Deploy
- Manual: `./deploy.sh` no servidor (idempotente: pull → migrate → verifica RLS → build --no-cache → recreate → reload OPA → smoke). Passo-a-passo em `DEPLOY.md`.
- **CD automático FUNCIONA**: `.github/workflows/cd.yml` roda via `workflow_run` do CI na main. Consequência: **consertar o CI = deployar em produção** (Flyway aplica migrations no prod). Avaliar isso antes de dar merge em algo verde-mas-arriscado.
- Keycloak prod: `configure-keycloak-client.sh` (chamado pelo deploy.sh) converge clients; config persiste no volume do banco do Keycloak. Console admin: túnel `ssh -L 8888:127.0.0.1:8080` → `http://localhost:8888/admin/`.
- Observabilidade prod: `infra/observability/` (Grafana+Loki+Promtail+Prometheus, Grafana em `/grafana`). Sobe manualmente, fora do deploy.sh. Não funciona em dev/WSL2.

## CI
- `ci.yml` = `mvn clean test`; `e2e.yml` = Newman (rede do compose descoberta dinamicamente — projeto local `jetski` vs CI `jetski-saas`). Em falha, o CI imprime `surefire-reports/*.txt` no log.
- Requisitos não-óbvios dos testes: Redis via Testcontainers, `-Duser.timezone=America/Sao_Paulo`, Postgres de teste com `max_connections=400`, CORS com lista exata.

## nginx (recorrentes)
- Editar `infra/nginx/nginx.conf` quebra o bind-mount (inode) → `docker compose up -d --force-recreate nginx`, NUNCA `restart`. O deploy.sh de prod faz só `up -d nginx` — mudou o nginx.conf, recriar manualmente no servidor.
- `access.log`/`error.log` do nginx:alpine são symlinks p/ stdout — grep no arquivo trava; usar `docker compose logs nginx`.
- Apex → www: cookies do NextAuth são host-only; qualquer domínio novo precisa entrar no bloco de 301 apex→www (senão volta o `error=Configuration` no login).
- Presigned URLs do MinIO: assinadas com o host público via `STORAGE_MINIO_PUBLIC_URL` + rota `/jetski-docs/` no nginx (o client Java precisa de `region` fixa p/ não fazer probe).

## Gotchas docker (recorrentes)
- Build cacheado = deploy falso: mesma sha256 da imagem após "rebuild" → usar `--no-cache` e conferir `docker images --format '{{.CreatedAt}}'`.
- `up -d --build` nem sempre recria o container → `--force-recreate`.
- Recriar frontend/backend sem as vars `NEXTAUTH_URL/KEYCLOAK_ISSUER/JETSKI_FRONTEND_URL/JETSKI_EXTERNAL_URL` quebra login (issuer mismatch).
- Sempre validar deploy por um marcador do código novo antes de rodar verificações.
