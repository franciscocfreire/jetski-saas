# Infraestrutura - Jetski SaaS

## Workflow de Desenvolvimento Local

### 1. Iniciar infraestrutura

```bash
cd infra
docker-compose up -d postgres redis keycloak
```

### 2. Aguardar Keycloak estar pronto

```bash
./wait-for-keycloak.sh
```

### 3. Configurar Keycloak

```bash
./keycloak-setup/setup-keycloak-local.sh
```

Este script cria:
- Realm `jetski-saas`
- Client `jetski-api`
- Roles (ADMIN_TENANT, GERENTE, OPERADOR, etc.)
- Usuários de teste (admin@acme.com, operador@acme.com, admin@plataforma.com)
- Groups com tenant_id
- Protocol mappers

### 4. Iniciar backend (executa migrations)

```bash
cd ../backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Flyway executa automaticamente:
- V999__seed_data_dev.sql → Cria tenant ACME e usuários com UUIDs fixos
- V1003__seed_platform_admin.sql → Cria platform admin

## Sincronização Keycloak ↔ PostgreSQL

### Problema

Keycloak e PostgreSQL precisam estar sincronizados:
- **Keycloak**: Gera UUIDs aleatórios para usuários
- **PostgreSQL**: Migrations têm UUIDs fixos

### Solução em DEV

Os UUIDs dos usuários **admin@acme.com** e **operador@acme.com** estão documentados em:
- `V999__seed_data_dev.sql` (PostgreSQL)
- `setup-keycloak-local.sh` (header, documentação)

**UUIDs fixos atualmente:**
```
admin@acme.com:      b0cd6005-a7c0-4915-a08f-abae4364ae46
operador@acme.com:   820cd5a2-4a6e-4f02-9193-e745b99c4f5e
admin@plataforma.com: 00000000-0000-0000-0000-000000000001
```

### Como resetar tudo do zero

```bash
# 1. Parar tudo
cd infra
docker-compose down -v

# 2. Recriar infraestrutura
docker-compose up -d postgres redis keycloak

# 3. Aguardar Keycloak
./wait-for-keycloak.sh

# 4. Setup Keycloak (UUIDs serão DIFERENTES agora!)
./keycloak-setup/setup-keycloak-local.sh

# 5. Iniciar backend (migrations com UUIDs fixos)
cd ../backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

**IMPORTANTE:** Após recriar Keycloak, os UUIDs no JWT **NÃO** vão bater com PostgreSQL!

### O que fazer quando UUIDs divergem?

**Opção 1: Atualizar V999 com novos UUIDs (recomendado para DEV)**

```bash
# 1. Obter novos UUIDs do Keycloak
curl -s -X POST 'http://localhost:8081/realms/master/protocol/openid-connect/token' \
  -d 'username=admin&password=admin&grant_type=password&client_id=admin-cli' | jq -r .access_token

# Use o token para consultar usuários via Admin API
# Copie os novos UUIDs e atualize V999__seed_data_dev.sql
```

**Opção 2: Aceitar divergência (para testes temporários)**

Usuários do PostgreSQL (sem Keycloak):
- admin@praiadosol.com.br
- gerente@praiadosol.com.br
- operador@praiadosol.com.br

Esses usuários NÃO podem fazer login via Keycloak, mas existem no DB para queries.

**Opção 3: User Provisioning (PRODUÇÃO)**

Em produção, não usar seed data manual. Sempre usar:

```bash
POST /api/v1/tenants/{id}/users/invite
```

Isso garante UUIDs sincronizados automaticamente entre Keycloak e PostgreSQL.

## Credenciais

### Keycloak Admin

- URL: http://localhost:8081/admin
- User: `admin`
- Pass: `admin`

### PostgreSQL

- Host: `localhost:5433`
- Database: `jetski_local`
- User: `jetski`
- Pass: `dev123`

### Tenant ACME (Keycloak)

- **admin@acme.com** / admin123 (ADMIN_TENANT, GERENTE)
- **operador@acme.com** / operador123 (OPERADOR)

### Platform Admin (Keycloak)

- **admin@plataforma.com** / admin123 (PLATFORM_ADMIN, acesso irrestrito)

## Teste de Autenticação

```bash
# Obter token JWT
TOKEN=$(curl -s -X POST 'http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'username=admin@acme.com' \
  -d 'password=admin123' \
  -d 'grant_type=password' \
  -d 'client_id=jetski-api' \
  -d 'client_secret=jetski-secret' | jq -r '.access_token')

# Ver conteúdo do JWT
echo $TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .

# Testar endpoint protegido
curl -X GET 'http://localhost:8090/api/v1/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/members' \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
```

## Troubleshooting

### Erro: 403 Forbidden "No access to tenant"

**Causa:** UUID do JWT não existe na tabela `membro` do PostgreSQL

**Solução 1:** Verificar UUIDs sincronizados

```sql
SELECT u.email, u.id as uuid_pg
FROM usuario u
WHERE u.email IN ('admin@acme.com', 'operador@acme.com');
```

Compare com o `sub` no JWT (decodificar com `jwt.io`)

**Solução 2:** Atualizar V999 com UUIDs corretos do Keycloak

### Keycloak não inicia

```bash
# Ver logs
docker-compose logs keycloak

# Restart
docker-compose restart keycloak

# Aguardar pronto
./wait-for-keycloak.sh
```

### PostgreSQL connection refused

```bash
# Verificar se está rodando
docker-compose ps

# Ver logs
docker-compose logs postgres

# Restart
docker-compose restart postgres
```

## Estrutura de Diretórios

```
infra/
├── docker-compose.yml          # Postgres, Redis, Keycloak
├── keycloak-setup/
│   ├── setup-keycloak-local.sh # Setup Keycloak (porta 8081)
│   └── ...
├── wait-for-keycloak.sh        # Aguarda Keycloak estar pronto
└── README.md                   # Este arquivo
```
