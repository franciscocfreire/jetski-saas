---
name: deploy-prod
description: Deploy em produção do jetski (Oracle ARM, www.meujet.com.br) — checklist, comando e verificações pós-deploy. Use quando o usuário pedir deploy manual, verificação de produção ou diagnóstico de prod.
---

# Deploy em produção

**Produção é outward-facing: confirmar com o usuário antes de executar o deploy**, a menos que ele tenha pedido explicitamente.

## Vias de deploy
1. **CD automático** (preferido): merge na main com CI verde → `cd.yml` (workflow_run) → SSH → `deploy.sh`. Funciona; secrets configurados.
2. **Manual**: `ssh -i ~/.ssh/ssh-key-2026-06-15.key ubuntu@64.181.176.180`, repo em `/home/ubuntu/jetski`, rodar `./deploy.sh` (idempotente: pull → build (keycloak com cache, apps --no-cache) → infra → migrate → verifica RLS → recreate das apps → reload OPA/nginx → config Keycloak → smoke). Passo-a-passo em `DEPLOY.md`. As duas vias convivem.

> **Keycloak/SSO no deploy**: o `deploy.sh` só recria o Keycloak quando a imagem (tema/SPI) ou a config dele muda — o log diz "Keycloak mantido" ou "Keycloak (re)criado". Antes de set/2026 ele era recriado em TODO deploy (atestado de proveniência do BuildKit mudava o digest) e o SSO caía ~3,5 min; a correção é `BUILDX_NO_DEFAULT_ATTESTATIONS=1` no script. Deploy que muda Postgres/Keycloak ainda derruba o SSO: avisar antes.

## Pré-deploy
- [ ] CI verde no commit a deployar
- [ ] Migration nova? Flyway one-shot aplica em prod automaticamente — revisar se é backward-compatible
- [ ] Imagem/serviço novo no compose? Confirmar variante **arm64** (ex.: `flyway/flyway:10`, `opa:1.1.0-static`; alpine/padrão = `exec format error`)
- [ ] Mudou CORS/origem/URL? `PUBLIC_URL` no `.env` do servidor deve casar EXATAMENTE com `https://www.meujet.com.br` (www canônico do tunnel), senão OIDC quebra

## Pós-deploy
- [ ] Smoke do deploy.sh passou
- [ ] Mudou `infra/nginx/nginx.conf`? O deploy.sh já recria o nginx (`--force-recreate`, por causa do inode do bind mount); conferir que a mudança está no ar
- [ ] SSO: o log do deploy diz "Keycloak mantido" (esperado sem mudança de tema/SPI/config) ou "Keycloak (re)criado"? No segundo caso, conferir o login e o alerta "SSO indisponível"
- [ ] Validar um marcador do código novo (rota/campo novo) — não confiar só no "subiu"
- [ ] Login OIDC no backoffice e no portal (`/portal`)
- [ ] Se mexeu em Keycloak: `configure-keycloak-client.sh` roda no deploy.sh; console admin via `ssh -L 8888:127.0.0.1:8080` → `http://localhost:8888/admin/`

## Referências rápidas
- Stack: `docker compose -f docker-compose.yml -f docker-compose.prod.yml`; app como `jetski_app` (RLS); MinIO bucket `jetski-docs`; e-mail é best-effort (não bloqueia emissão).
- Observabilidade (`infra/observability/`, Grafana em `/grafana`) sobe manualmente, fora do deploy.sh.
- Logs: `docker compose logs -f backend` no servidor, ou Grafana/Loki.
