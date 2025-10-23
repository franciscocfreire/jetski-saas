# 🔐 Scripts Keycloak - Guia de Uso

## 📋 Scripts Disponíveis

### 🏠 Ambiente LOCAL (100% sem Docker)

**PostgreSQL**: Porta 5433 (sistema local)

| Script | Descrição | Uso |
|--------|-----------|-----|
| `start-keycloak-local.sh` | Inicia Keycloak com PostgreSQL local (5433) | Para desenvolvimento LOCAL |
| `reset-keycloak-local.sh` | Reset completo do Keycloak LOCAL | Limpar e reiniciar do zero |
| `setup-keycloak-local.sh` | Importa realm e usuários de teste | Após start ou reset |

### 🐳 Ambiente DOCKER

**PostgreSQL**: Porta 5432 (Docker)

| Script | Descrição | Uso |
|--------|-----------|-----|
| `start-keycloak-postgres.sh` | Inicia Keycloak com PostgreSQL Docker (5432) | Para ambiente DEV com Docker |
| `reset-keycloak.sh` | Reset completo do Keycloak Docker | ⚠️ Usa Docker! |
| `setup-keycloak.sh` | Setup para ambiente Docker | N/A |

## 🎯 Qual usar?

### Você quer ambiente 100% local (sem Docker)?

✅ **Use os scripts `-local`**

```bash
# Iniciar Keycloak LOCAL
./infra/keycloak-setup/start-keycloak-local.sh

# Reset completo LOCAL
./infra/keycloak-setup/reset-keycloak-local.sh

# Importar realm LOCAL
./infra/keycloak-setup/setup-keycloak-local.sh
```

### Você quer ambiente com Docker?

✅ **Use os scripts sem `-local`**

```bash
# Iniciar Keycloak Docker
./infra/keycloak-setup/start-keycloak-postgres.sh

# Reset completo Docker
./infra/keycloak-setup/reset-keycloak.sh
```

## 🚀 Fluxo Recomendado - LOCAL

### Primeira vez:

```bash
# 1. Iniciar Keycloak LOCAL
cd /home/franciscocfreire/repos/jetski
./infra/keycloak-setup/start-keycloak-local.sh

# 2. Aguardar estar pronto (10 segundos)
sleep 10

# 3. Importar realm com usuários
./infra/keycloak-setup/setup-keycloak-local.sh
```

### Reset completo:

```bash
# 1. Reset (vai pedir confirmação "SIM")
./infra/keycloak-setup/reset-keycloak-local.sh

# 2. Aguardar 10 segundos
sleep 10

# 3. Importar realm
./infra/keycloak-setup/setup-keycloak-local.sh
```

### Apenas reiniciar:

```bash
# Parar
pkill -f "keycloak.*8081"

# Iniciar novamente
./infra/keycloak-setup/start-keycloak-local.sh
```

## 📊 Diferenças

| Aspecto | LOCAL (`-local`) | DOCKER (sem `-local`) |
|---------|------------------|----------------------|
| PostgreSQL | Porta 5433 (sistema) | Porta 5432 (Docker) |
| Keycloak | Porta 8081 (standalone) | Porta 8081 (standalone) |
| Database | `keycloak` em PostgreSQL local | `keycloak` em PostgreSQL Docker |
| Vantagem | Mais leve, não precisa Docker | Isolado, fácil limpar |
| Desvantagem | Compartilha PostgreSQL local | Precisa Docker rodando |

## 🔍 Verificações

### Keycloak está rodando?

```bash
curl -sf http://localhost:8081/health/ready && echo "✓ OK" || echo "✗ Problema"
```

### Qual PostgreSQL está sendo usado?

**LOCAL (5433)**:
```bash
PGPASSWORD=keycloak123 psql -h localhost -p 5433 -U keycloak -d keycloak -c '\conninfo'
# Deve conectar se Keycloak está usando LOCAL
```

**DOCKER (5432)**:
```bash
PGPASSWORD=dev123 psql -h localhost -p 5432 -U postgres -l | grep keycloak
# Lista databases no PostgreSQL Docker
```

### Ver logs:

**LOCAL**:
```bash
tail -f /tmp/keycloak-local.log
```

**DOCKER (antigo)**:
```bash
tail -f /tmp/keycloak-postgres.log
```

## 🛑 Parar Tudo

```bash
# Parar Keycloak
pkill -f "keycloak.*8081"

# Parar Docker (se estiver usando)
docker stop jetski-postgres jetski-redis jetski-opa
```

## 🎯 Recomendação

Para desenvolvimento LOCAL (sua configuração atual):

✅ **SEMPRE use os scripts `-local`**
❌ **NÃO use os scripts sem `-local` (eles usam Docker!)**

## 📝 Credenciais

Independente do ambiente:

**Admin Console**:
- URL: http://localhost:8081/admin
- User: `admin` / `admin`

**Usuários de teste** (após importar realm):
- `admin@acme.com` / `admin123`
- `operador@acme.com` / `operador123`
