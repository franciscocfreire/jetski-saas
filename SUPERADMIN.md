# Super Admin (Platform Admin) — Como cadastrar

Guia operacional para criar/gerenciar o **super admin de plataforma** do MeuJet.

## O que é o super admin

É um usuário com **acesso total à plataforma** (`unrestricted_access = true`):

- Vê o painel **Plataforma › Empresas** no backoffice.
- **Aprova / suspende / reativa** empresas (tenants).
- Pode **entrar e operar qualquer empresa** (god mode), mesmo sem ser membro — o
  isolamento continua garantido pelo RLS (ele opera uma empresa por vez, via a empresa
  selecionada no switcher).

Tecnicamente, é uma linha na tabela `usuario_global_roles` com `unrestricted_access = true`.
As políticas OPA (`policies/authz/authorization.rego`) liberam tudo para esse usuário
(`allow if { is_platform_admin }`).

> Diferença para o admin de empresa (`ADMIN_TENANT`): este administra **uma** empresa
> (onde tem `membro`). O super admin é **global**, acima de todas as empresas.

## Como o super admin é concedido

Há duas formas; ambas escrevem na mesma tabela `usuario_global_roles`:

1. **Seed por variável de ambiente (recomendado):** `PLATFORM_ADMIN_EMAILS` (lista de
   emails separados por vírgula). No boot do backend, o `PlatformAdminSeeder` promove
   cada email **que já tiver um `usuario`** a `unrestricted_access = true` (idempotente).
2. **SQL direto** no banco (útil para promover na hora, sem reiniciar).

### ⚠️ Pré-requisito: o usuário precisa existir

O seeder só promove emails que **já têm conta** (`usuario`). Uma conta só nasce quando a
pessoa **se cadastra e ativa** (clica no magic-link e define a senha). Ou seja, a ordem é
sempre: **cadastrar → ativar → promover**.

## Bootstrap do 1º super admin em PRODUÇÃO

Como o cadastro de empresa agora nasce `PENDENTE_APROVACAO` e precisa de um super admin
para liberar, o primeiro super admin precisa ser criado manualmente (galinha-e-ovo):

1. **Cadastre uma empresa** (signup) usando o email que será o super admin.
   (O email de ativação depende do SMTP configurado — ver `GMAIL_USER`/`GMAIL_APP_PASSWORD`
   no `.env`; o app password do Gmail vai **sem espaços**.)
2. **Ative a conta** pelo link recebido por email (define a senha). Agora existe o `usuario`.
3. No servidor de produção, adicione o email ao `.env`:
   ```bash
   cd <DEPLOY_PATH>            # ex.: ~/jetski
   echo 'PLATFORM_ADMIN_EMAILS=seu-email@dominio.com' >> .env
   # vários: PLATFORM_ADMIN_EMAILS=a@x.com,b@x.com
   ```
   > Valores com espaço precisam de aspas no `.env` (o `deploy.sh` faz `source` do arquivo).
4. **Reinicie o backend** para o seeder rodar:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --force-recreate --no-deps backend
   # ou: re-rode o workflow de CD no GitHub Actions
   ```
5. **Logue** com esse email no backoffice → aparece **Plataforma › Empresas** → aprove as
   empresas pendentes (inclusive a sua, do passo 1).

### Alternativa: SQL direto (sem reiniciar)

Depois que o `usuario` existir (passo 2), promova direto no banco:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres \
  psql -U jetski -d jetski_prod -c \
"INSERT INTO usuario_global_roles (usuario_id, roles, unrestricted_access, created_at, updated_at)
 SELECT id, ARRAY['PLATFORM_ADMIN'], true, now(), now()
 FROM usuario WHERE email='seu-email@dominio.com'
 ON CONFLICT (usuario_id) DO UPDATE SET unrestricted_access = true;"
```

O efeito é imediato no próximo request (o cache de acesso expira em ~5 min; um novo login
ou aguardar resolve). Mesmo assim, deixe o email em `PLATFORM_ADMIN_EMAILS` para o seeder
re-garantir a promoção em deploys futuros.

## Em DESENVOLVIMENTO

Os usuários de dev (`admin@acme.com`, etc.) já existem após `./reset-ambiente-dev.sh`.
Promova um deles setando a env antes de subir o backend:

```bash
PLATFORM_ADMIN_EMAILS=admin@acme.com docker compose up -d --build backend
```

(ou o mesmo SQL acima, com `-d jetski_dev`). Logue como `admin@acme.com / admin123` para ver
o painel. Para despromover, remova a linha do `usuario_global_roles` (ver abaixo).

> Observação: `admin@acme.com` é **dado de dev** — não existe em produção.

## Verificar quem é super admin

```bash
docker compose ... exec -T postgres psql -U jetski -d jetski_prod -c \
"SELECT u.email, g.roles, g.unrestricted_access
 FROM usuario_global_roles g JOIN usuario u ON u.id = g.usuario_id
 WHERE g.unrestricted_access = true;"
```

No app: o backend retorna `accessType = UNRESTRICTED` em `GET /v1/user/tenants` e o switcher
passa a listar **todas** as empresas.

## Revogar o super admin

```bash
docker compose ... exec -T postgres psql -U jetski -d jetski_prod -c \
"DELETE FROM usuario_global_roles WHERE usuario_id = (SELECT id FROM usuario WHERE email='alguem@dominio.com');"
```

E **remova** o email de `PLATFORM_ADMIN_EMAILS` no `.env` (senão o seeder o promove de novo
no próximo boot).

## Notas e cuidados

- **Poder total:** o super admin acessa e opera qualquer empresa. Conceda com parcimônia.
- **Por empresa, uma de cada vez:** ele seleciona a empresa no switcher (define o
  `X-Tenant-Id`) e o RLS escopa os dados àquela empresa. Não há agregação cross-tenant
  num único request (isso exigiria bypass de RLS e foi deixado para depois).
- **Não bloqueia o login dele:** o "gate" de empresa não-operacional (em análise/suspensa)
  é isento para o super admin — ele consegue inspecionar empresas em qualquer status.
- **Onde mexer no código:** `PlatformAdminSeeder`, `usuario_global_roles`, OPA
  `authorization.rego` (`is_platform_admin`), `PlatformTenantController/Service`. Spec geral
  em `ONBOARDING_EMPRESA_SPEC.md`.
