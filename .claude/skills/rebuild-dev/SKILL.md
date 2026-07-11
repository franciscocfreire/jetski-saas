---
name: rebuild-dev
description: Rebuild seguro do ambiente dev do jetski (backend/frontend/portal) com verificação anti-cache e vars do túnel. Use quando precisar subir código novo nos containers dev ou quando "a mudança não apareceu" após um rebuild.
---

# Rebuild dev sem armadilhas

## Comando base
```bash
./rebuild.sh                # tudo
./rebuild.sh backend        # só backend
./rebuild.sh frontend       # só frontend
./rebuild.sh portal         # só o portal do cliente (basePath /portal)
./rebuild.sh backend --no-cache   # força recompilação
```
O script já injeta `PUBLIC_URL` (default `https://www.pegaojet.com.br`). **Nunca** usar `docker compose up -d --build <svc>` solto — perde as vars do túnel e quebra o login.

## SEMPRE verificar se o build realmente aconteceu
`./rebuild.sh backend` pode sair 100% `CACHED` e manter a imagem antiga (deploy falso, exit 0):

```bash
# antes E depois do rebuild:
docker images --format "{{.Repository}}:{{.Tag}} {{.CreatedSince}} {{.ID}}" | grep -E "backend|frontend|portal"
```
- Sha/idade não mudou → build cacheado → repetir com `--no-cache`.
- No log, procurar `Compiling N source files` (recompilou) vs só `#NN CACHED`.
- Não rodar o rebuild com `| tail -1` em background — mascara o no-op.

## Validação final (obrigatória)
Testar um **marcador do código novo** (rota nova, campo novo na resposta, texto novo na UI) antes de declarar sucesso. Imagem velha gera resultados enganosos.

## Se havia migration nova no meio
Rebuild NÃO aplica migrations (Flyway desativado em dev). Aplicar o bloco idempotente via psql — ver skill `nova-migration`. Sintoma de esquecimento: 500 "relation does not exist".

## Se o login quebrar após mexer nos containers
Containers foram recriados sem as vars do túnel. Corrigir:
```bash
NEXTAUTH_URL="https://www.pegaojet.com.br" \
PORTAL_PUBLIC_URL="https://cliente.pegaojet.com.br" \
PORTAL_NEXTAUTH_URL="https://cliente.pegaojet.com.br" \
KEYCLOAK_ISSUER="https://www.pegaojet.com.br/realms/jetski-saas" \
JETSKI_FRONTEND_URL="https://www.pegaojet.com.br" \
JETSKI_EXTERNAL_URL="https://www.pegaojet.com.br" \
docker compose up -d --force-recreate frontend backend portal
```
Sintomas: redirect para `http://nginx/...` (DNS_PROBE_FINISHED_NXDOMAIN) ou "nenhuma empresa" no backoffice (JWT rejeitado por issuer mismatch, 401 em `/v1/users/me/tenants`).

## Reset completo (última opção)
`./reset-ambiente-dev.sh` — recria banco, realm Keycloak, seeds e migrations. Destrutivo para dados de dev; confirmar com o usuário se houver dados que importam.
