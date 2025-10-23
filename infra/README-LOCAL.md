# 🏠 Ambiente de Desenvolvimento LOCAL (Sem Docker)

Configuração 100% local para desenvolvimento do Jetski SaaS, sem depender de containers Docker.

## 📦 Pré-requisitos

Serviços instalados localmente via `apt`:

- ✅ **PostgreSQL 16** (porta 5433) - `sudo apt install postgresql-16`
- ✅ **Redis** (porta 6379) - `sudo apt install redis-server`
- ✅ **Java 21 JDK** - `sudo apt install openjdk-21-jdk`
- ✅ **Maven 3.8+** - `sudo apt install maven`

Aplicações standalone:

- ✅ **Keycloak 26.4.1** em `/home/franciscocfreire/apps/keycloak-26.4.1`
- ✅ **OPA 0.70.0** em `/home/franciscocfreire/apps/opa` (baixado automaticamente)

## 🏗️ Arquitetura Local

```
┌─────────────────────────────────────────────────────────┐
│              TUDO LOCAL (SEM DOCKER)                    │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  PostgreSQL 16 (systemd)    Redis (systemd)             │
│  └─ localhost:5433          └─ localhost:6379           │
│                                                         │
│  Keycloak Standalone        OPA Standalone              │
│  └─ localhost:8081          └─ localhost:8181           │
│                                                         │
│  Backend Spring Boot (Maven)                            │
│  └─ localhost:8090                                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## 🚀 Início Rápido

### Opção 1: Script Automatizado (Recomendado)

```bash
# Iniciar tudo
/home/franciscocfreire/repos/jetski/infra/start-local.sh

# Parar tudo
/home/franciscocfreire/repos/jetski/infra/stop-local.sh
```

### Opção 2: Manual (Passo a Passo)

#### 1. Verificar Serviços do Sistema

```bash
# PostgreSQL
sudo systemctl status postgresql
PGPASSWORD=dev123 psql -h localhost -p 5433 -U postgres -l

# Redis
sudo systemctl status redis-server
redis-cli ping  # Deve retornar PONG
```

#### 2. Iniciar Keycloak (Porta 8081)

```bash
/home/franciscocfreire/repos/jetski/infra/keycloak-setup/start-keycloak-postgres.sh

# Verificar
curl http://localhost:8081/health/ready

# Logs
tail -f /tmp/keycloak-postgres.log
```

#### 3. Importar Realm no Keycloak (Apenas primeira vez)

```bash
# Aguardar Keycloak estar pronto (step 2)
sleep 10

# Importar realm
/home/franciscocfreire/repos/jetski/infra/keycloak-setup/setup-keycloak-local.sh
```

#### 4. Iniciar OPA (Porta 8181)

```bash
/home/franciscocfreire/repos/jetski/infra/start-opa-local.sh

# Verificar
curl http://localhost:8181/health

# Testar política
curl http://localhost:8181/v1/data/jetski/rbac/allow \
  -d '{"input": {"roles": ["GERENTE"], "action": "modelo:list"}}'

# Logs
tail -f /tmp/opa-local.log
```

#### 5. Iniciar Backend (Porta 8090)

```bash
cd /home/franciscocfreire/repos/jetski/backend

# Opção A: Foreground (vê logs diretamente)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# Opção B: Background
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run > /tmp/backend.log 2>&1 &

# Verificar
curl http://localhost:8090/api/actuator/health

# Logs (se background)
tail -f /tmp/backend.log
```

## 🔍 Verificar Status

```bash
# Status rápido de todos os serviços
echo "=== Status Serviços Locais ==="

# PostgreSQL
echo -n "PostgreSQL (5433): "
PGPASSWORD=dev123 psql -h localhost -p 5433 -U postgres -c '\q' && echo "✓" || echo "✗"

# Redis
echo -n "Redis (6379): "
redis-cli ping > /dev/null && echo "✓" || echo "✗"

# Keycloak
echo -n "Keycloak (8081): "
curl -sf http://localhost:8081/health/ready > /dev/null && echo "✓" || echo "✗"

# OPA
echo -n "OPA (8181): "
curl -sf http://localhost:8181/health > /dev/null && echo "✓" || echo "✗"

# Backend
echo -n "Backend (8090): "
curl -sf http://localhost:8090/api/actuator/health > /dev/null && echo "✓" || echo "✗"
```

## 🛑 Parar Serviços

```bash
# Parar apenas aplicações (PostgreSQL e Redis ficam rodando)
/home/franciscocfreire/repos/jetski/infra/stop-local.sh

# Ou manualmente:
pkill -f "spring-boot:run"         # Backend
pkill -f "opa run.*8181"            # OPA
pkill -f "keycloak.*8081"           # Keycloak

# Parar serviços do sistema (opcional)
sudo systemctl stop postgresql
sudo systemctl stop redis-server
```

## 📊 Portas e Serviços

| Serviço    | Porta | Tipo     | Comando Iniciar                                |
|------------|-------|----------|------------------------------------------------|
| PostgreSQL | 5433  | systemd  | `sudo systemctl start postgresql`              |
| Redis      | 6379  | systemd  | `sudo systemctl start redis-server`            |
| Keycloak   | 8081  | Manual   | `infra/keycloak-setup/start-keycloak-postgres.sh` |
| OPA        | 8181  | Manual   | `infra/start-opa-local.sh`                     |
| Backend    | 8090  | Maven    | `SPRING_PROFILES_ACTIVE=local mvn spring-boot:run` |

## 🔑 Credenciais

### Keycloak Admin Console
- **URL**: http://localhost:8081/admin
- **Usuário**: `admin`
- **Senha**: `admin`

### Usuários do Realm (jetski-saas)
- **Admin/Gerente**: `admin@acme.com` / `admin123`
  - Roles: ADMIN_TENANT, GERENTE
  - Tenant: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
- **Operador**: `operador@acme.com` / `operador123`
  - Role: OPERADOR
  - Tenant: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11

### PostgreSQL
- **Host**: `localhost:5433`
- **Databases**:
  - `jetski_local` (aplicação) - User: `jetski` / Password: `dev123`
  - `keycloak` (Keycloak) - User: `keycloak` / Password: `keycloak123`

## 🧪 Testar com Postman

1. **Importar Collection**:
   - `/backend/postman/Jetski-SaaS-API.postman_collection.json`

2. **Importar Environment**:
   - `/backend/postman/environments/Local.postman_environment.json`

3. **Configuração do Environment** (já está correto):
   ```json
   {
     "keycloak_url": "http://localhost:8081",
     "api_url": "http://localhost:8090/api",
     "username": "admin@acme.com",
     "password": "admin123",
     "tenant_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
   }
   ```

4. **Executar Requests**:
   - **Auth** → `Get Access Token` (preenche automaticamente)
   - **User** → `List User Tenants`
   - **ABAC/OPA** → Testes de autorização

## 🔧 Troubleshooting

### PostgreSQL - Porta 5433

O PostgreSQL local está configurado para porta **5433** (não 5432 para não conflitar com Docker).

**Verificar configuração**:
```bash
cat /etc/postgresql/16/main/postgresql.conf | grep ^port
# Deve retornar: port = 5433
```

**Conectar**:
```bash
PGPASSWORD=dev123 psql -h localhost -p 5433 -U postgres
```

### Keycloak não inicia

```bash
# Ver logs
tail -100 /tmp/keycloak-postgres.log

# Verificar porta livre
lsof -i :8081

# Matar processos anteriores
pkill -f "keycloak.*8081"

# Tentar novamente
/home/franciscocfreire/repos/jetski/infra/keycloak-setup/start-keycloak-postgres.sh
```

### OPA não responde

```bash
# Ver logs
tail -50 /tmp/opa-local.log

# Verificar processo
ps aux | grep "opa run"

# Testar health
curl -v http://localhost:8181/health

# Reiniciar
pkill -f "opa run.*8181"
/home/franciscocfreire/repos/jetski/infra/start-opa-local.sh
```

### Backend - Erro de Conexão

**Keycloak (401/403)**:
```bash
# Verificar issuer-uri
cat backend/src/main/resources/application-local.yml | grep issuer-uri
# Deve ser: http://localhost:8081/realms/jetski-saas

# Testar endpoint
curl http://localhost:8081/realms/jetski-saas/.well-known/openid-configuration
```

**OPA (Connection Refused)**:
```bash
# Verificar OPA rodando
curl http://localhost:8181/health

# Backend deve usar: http://localhost:8181
```

**PostgreSQL (Connection Refused)**:
```bash
# Verificar porta 5433
PGPASSWORD=dev123 psql -h localhost -p 5433 -U jetski -d jetski_local

# application-local.yml deve ter:
# url: jdbc:postgresql://localhost:5433/jetski_local
```

## 📁 Estrutura de Arquivos

```
jetski/
├── backend/
│   ├── src/main/resources/
│   │   └── application-local.yml  ← Configuração LOCAL
│   └── postman/
│       └── environments/
│           └── Local.postman_environment.json
└── infra/
    ├── start-local.sh              ← Inicia tudo
    ├── stop-local.sh               ← Para tudo
    ├── start-opa-local.sh          ← Inicia OPA
    └── keycloak-setup/
        ├── start-keycloak-postgres.sh  ← Inicia Keycloak
        └── setup-keycloak-local.sh     ← Importa realm
```

## 🌐 URLs Úteis

- **Backend API**: http://localhost:8090/api
- **Swagger UI**: http://localhost:8090/api/swagger-ui/index.html
- **Actuator Health**: http://localhost:8090/api/actuator/health
- **Keycloak Admin**: http://localhost:8081/admin
- **Keycloak Realm**: http://localhost:8081/realms/jetski-saas
- **OPA Health**: http://localhost:8181/health
- **OPA Data**: http://localhost:8181/v1/data

## 📚 Próximos Passos

1. ✅ Ambiente local configurado
2. 🔜 Criar dados de teste no database
3. 🔜 Testar fluxo completo no Postman
4. 🔜 Desenvolver novos endpoints

## ❓ Ajuda

- **Logs**:
  - Keycloak: `/tmp/keycloak-postgres.log`
  - OPA: `/tmp/opa-local.log`
  - Backend: `/tmp/backend.log` (se rodando em background)

- **Documentação**:
  - [QUICK_START.md](../backend/postman/QUICK_START.md)
  - [TESTES-OAUTH2-OPA.md](../backend/TESTES-OAUTH2-OPA.md)
  - [CLAUDE.md](../CLAUDE.md)
