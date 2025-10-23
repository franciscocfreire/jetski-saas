# 🔄 Reset Completo do Keycloak - Passo a Passo

## 📋 O que será feito:

1. ✅ Parar Keycloak
2. ✅ Apagar database `keycloak` completamente
3. ✅ Recriar database limpo
4. ✅ Limpar cache/dados do Keycloak
5. ✅ Iniciar Keycloak do zero
6. ✅ Importar realm `jetski-saas` com usuários corretos

## ⚠️ IMPORTANTE

Este processo vai **APAGAR TODOS OS DADOS** do Keycloak:
- Realms
- Usuários
- Clients
- Configurações

**Use apenas em ambiente de desenvolvimento local!**

## 🚀 Opção 1: Script Automatizado (Recomendado)

### Passo 1: Executar reset

```bash
cd /home/franciscocfreire/repos/jetski

# Executar script de reset
./infra/keycloak-setup/reset-keycloak.sh
```

**O script vai pedir confirmação:**
```
⚠️  ATENÇÃO: Isso vai APAGAR todos os dados do Keycloak!

Tem certeza que deseja continuar? (digite 'SIM' em maiúsculas):
```

Digite: `SIM` (em maiúsculas)

### Passo 2: Aguardar conclusão

O script vai:
1. Parar Keycloak
2. Dropar database
3. Recriar database
4. Limpar dados
5. Iniciar Keycloak
6. Aguardar ficar pronto (30 segundos)

**Saída esperada:**
```
=========================================
  ✓✓✓ RESET CONCLUÍDO COM SUCESSO!
=========================================

📊 Status:
  Database: keycloak (limpo)
  Keycloak: rodando na porta 8081
  Admin: admin / admin
```

### Passo 3: Importar Realm

```bash
# Aguardar alguns segundos para garantir que Keycloak está 100% pronto
sleep 10

# Importar realm com usuários de teste
./infra/keycloak-setup/setup-keycloak-local.sh
```

**Resultado esperado:**
```
✓ Realm jetski-saas criado
✓ Roles criadas (OPERADOR, GERENTE, ADMIN_TENANT, etc.)
✓ Client jetski-api criado
✓ Protocol mapper (tenant_id) configurado
✓ Usuários criados:
  - admin@acme.com / admin123 (ADMIN_TENANT, GERENTE)
  - operador@acme.com / operador123 (OPERADOR)
```

### Passo 4: Verificar

```bash
# Testar login com usuário de teste
curl -X POST http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'username=admin@acme.com' \
  -d 'password=admin123' \
  -d 'grant_type=password' \
  -d 'client_id=jetski-api' \
  -d 'client_secret=jetski-secret' | jq -r '.access_token' | head -c 50

# Deve retornar um token JWT
```

## 🔧 Opção 2: Manual (Passo a Passo)

### 1. Parar Keycloak

```bash
pkill -f "keycloak.*8081"

# Verificar
ps aux | grep keycloak | grep 8081
# Não deve retornar nada
```

### 2. Apagar Database

```bash
PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres <<EOF
-- Desconectar conexões
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'keycloak' AND pid <> pg_backend_pid();

-- Dropar database
DROP DATABASE IF EXISTS keycloak;

-- Dropar usuário
DROP USER IF EXISTS keycloak;
EOF
```

### 3. Recriar Database

```bash
PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres <<EOF
-- Criar usuário
CREATE USER keycloak WITH PASSWORD 'keycloak123';

-- Criar database
CREATE DATABASE keycloak OWNER keycloak;

-- Grants
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
EOF

# Grant no schema public
PGPASSWORD=keycloak123 psql -h localhost -p 5433 -U keycloak -d keycloak <<EOF
GRANT ALL ON SCHEMA public TO keycloak;
EOF
```

### 4. Limpar Diretório de Dados

```bash
KC_DIR="/home/franciscocfreire/apps/keycloak-26.4.1"

# Remover dados H2 (se existir)
rm -rf "$KC_DIR/data/h2"

# Remover temp
rm -rf "$KC_DIR/data/tmp"
```

### 5. Iniciar Keycloak

```bash
./infra/keycloak-setup/start-keycloak-postgres.sh
```

**Aguardar mensagem:**
```
=========================================
✓✓✓ Keycloak INICIADO COM SUCESSO!
=========================================
```

### 6. Importar Realm

```bash
# Aguardar 10 segundos
sleep 10

# Importar realm
./infra/keycloak-setup/setup-keycloak-local.sh
```

## ✅ Verificações

### 1. Keycloak está rodando?

```bash
curl -sf http://localhost:8081/health/ready && echo "✓ Keycloak OK" || echo "✗ Keycloak com problema"
```

### 2. Database foi recriado?

```bash
PGPASSWORD=keycloak123 psql -h localhost -p 5433 -U keycloak -d keycloak -c "\dt" | wc -l

# Deve retornar número > 50 (Keycloak cria ~150 tabelas)
```

### 3. Realm existe?

```bash
curl -s http://localhost:8081/realms/jetski-saas/.well-known/openid-configuration | jq -r '.issuer'

# Deve retornar: http://localhost:8081/realms/jetski-saas
```

### 4. Usuários foram criados?

Acessar Admin Console:
```
URL: http://localhost:8081/admin
Usuário: admin
Senha: admin

Navegar: jetski-saas realm → Users
Deve ter: admin@acme.com, operador@acme.com
```

### 5. Backend consegue conectar?

```bash
# Verificar logs do backend
tail -50 /tmp/backend.log | grep -i keycloak

# Não deve ter erros de conexão
```

## 🧪 Testar Fluxo Completo

### 1. Obter Token

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/jetski-saas/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'username=admin@acme.com' \
  -d 'password=admin123' \
  -d 'grant_type=password' \
  -d 'client_id=jetski-api' \
  -d 'client_secret=jetski-secret' | jq -r '.access_token')

echo "Token obtido: ${TOKEN:0:50}..."
```

### 2. Chamar API

```bash
curl -s http://localhost:8090/api/v1/user/tenants \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" | jq

# Deve retornar lista de tenants do usuário
```

## 🔧 Troubleshooting

### Keycloak não inicia

```bash
# Ver logs
tail -100 /tmp/keycloak-postgres.log

# Verificar porta livre
lsof -i :8081

# Verificar PostgreSQL
PGPASSWORD=keycloak123 psql -h localhost -p 5433 -U keycloak -d keycloak -c '\conninfo'
```

### Setup realm falha

```bash
# Verificar se Keycloak está pronto
curl -v http://localhost:8081/health/ready

# Aguardar mais tempo
sleep 20

# Tentar novamente
./infra/keycloak-setup/setup-keycloak-local.sh
```

### Backend não consegue conectar

```bash
# Verificar issuer-uri
cat backend/src/main/resources/application-local.yml | grep issuer-uri

# Deve ser: http://localhost:8081/realms/jetski-saas

# Testar endpoint
curl http://localhost:8081/realms/jetski-saas/.well-known/openid-configuration
```

## 📊 Resumo

Após reset completo você terá:

**Keycloak:**
- ✅ Database limpo
- ✅ Realm: `jetski-saas`
- ✅ Client: `jetski-api` (secret: jetski-secret)
- ✅ Usuários de teste prontos

**Usuários:**
| Email | Senha | Roles | Tenant ID |
|-------|-------|-------|-----------|
| admin@acme.com | admin123 | ADMIN_TENANT, GERENTE | a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 |
| operador@acme.com | operador123 | OPERADOR | a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 |

**Admin Console:**
- URL: http://localhost:8081/admin
- User: admin / admin

## 🎯 Próximos Passos

1. ✅ Keycloak resetado e funcionando
2. 🔜 Testar no Postman
3. 🔜 Desenvolver novos endpoints
