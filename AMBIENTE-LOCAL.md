# 🏠 Ambiente Local - Jetski SaaS (100% Local, Sem Docker)

## 📊 Status Atual

✅ **PostgreSQL 16**: localhost:5433 (usuário: postgres/postgres, jetski/dev123)
✅ **Redis**: localhost:6379 (sistema)
✅ **OPA**: localhost:8181 (rodando!)
⚠️ **Keycloak**: localhost:8081 (parado - precisa iniciar)
⚠️ **Backend**: localhost:8090 (parado - precisa iniciar)

## 🚀 Como Iniciar o Ambiente

### 1. Iniciar Keycloak (Porta 8081)

```bash
/home/franciscocfreire/repos/jetski/infra/keycloak-setup/start-keycloak-postgres.sh
```

**O que faz:**
- Conecta no PostgreSQL local (5433)
- Cria database `keycloak` se não existir
- Inicia Keycloak standalone na porta 8081
- Aguarda até estar pronto

**Verificar:**
```bash
curl http://localhost:8081/health/ready
tail -f /tmp/keycloak-postgres.log
```

### 2. Importar Realm (Apenas primeira vez)

```bash
# Aguardar Keycloak estar pronto
sleep 10

# Importar realm com usuários de teste
/home/franciscocfreire/repos/jetski/infra/keycloak-setup/setup-keycloak-local.sh
```

**Usuários criados:**
- `admin@acme.com` / `admin123` (ADMIN_TENANT, GERENTE)
- `operador@acme.com` / `operador123` (OPERADOR)
- Tenant: `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`

### 3. Iniciar Backend (Porta 8090)

```bash
cd /home/franciscocfreire/repos/jetski/backend

# Foreground (vê logs diretamente)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run 2>&1 | tee /tmp/spring-boot-local.log

# OU Background
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run > /tmp/backend.log 2>&1 &
```

**Verificar:**
```bash
curl http://localhost:8090/api/actuator/health
tail -f /tmp/backend.log
```

## 🔍 Verificar Status

```bash
# Status de todos os serviços
echo "=== Status Ambiente Local ==="
echo -n "PostgreSQL (5433): "; PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres -c '\q' 2>/dev/null && echo "✓" || echo "✗"
echo -n "Redis (6379): "; redis-cli ping 2>/dev/null | grep -q PONG && echo "✓" || echo "✗"
echo -n "Keycloak (8081): "; curl -sf http://localhost:8081/health/ready >/dev/null && echo "✓" || echo "✗"
echo -n "OPA (8181): "; curl -sf http://localhost:8181/health >/dev/null && echo "✓" || echo "✗"
echo -n "Backend (8090): "; curl -sf http://localhost:8090/api/actuator/health >/dev/null && echo "✓" || echo "✗"
```

## 🛑 Parar Tudo

```bash
# Parar aplicações
pkill -f "spring-boot:run"    # Backend
pkill -f "opa run.*8181"       # OPA
pkill -f "keycloak.*8081"      # Keycloak

# PostgreSQL e Redis continuam rodando (serviços do sistema)
```

## 📊 Configuração dos Serviços

### PostgreSQL (Porta 5433)

```yaml
# /backend/src/main/resources/application-local.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/jetski_local
    username: jetski
    password: dev123
```

**Credenciais:**
- Admin: `postgres` / `postgres`
- App: `jetski` / `dev123`
- Keycloak: `keycloak` / `keycloak123`

**Databases:**
- `jetski_local` - Aplicação (24 tabelas via Flyway)
- `keycloak` - Keycloak

**Conectar:**
```bash
PGPASSWORD=dev123 psql -h localhost -p 5433 -U jetski -d jetski_local
```

### Redis (Porta 6379)

```yaml
# /backend/src/main/resources/application-local.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

**Testar:**
```bash
redis-cli ping  # Deve retornar PONG
```

### Keycloak (Porta 8081)

```yaml
# /backend/src/main/resources/application-local.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8081/realms/jetski-saas
```

**Admin Console:**
- URL: http://localhost:8081/admin
- Usuário: `admin` / `admin`

**Realm:** `jetski-saas`

### OPA (Porta 8181)

```java
// Configurado em OPAConfig.java com default
@Value("${jetski.opa.base-url:http://localhost:8181}")
```

**Health:** http://localhost:8181/health

**Testar Política:**
```bash
curl http://localhost:8181/v1/data/jetski/rbac/allow \
  -d '{"input": {"roles": ["GERENTE"], "action": "modelo:list"}}'
```

**Políticas:** `/home/franciscocfreire/repos/jetski/policies/*.rego`

### Backend (Porta 8090)

**URLs:**
- API: http://localhost:8090/api
- Swagger: http://localhost:8090/api/swagger-ui/index.html
- Health: http://localhost:8090/api/actuator/health

## 🧪 Testar com Postman

### 1. Importar Collection

```
/backend/postman/Jetski-SaaS-API.postman_collection.json
```

### 2. Importar Environment

```
/backend/postman/environments/Local.postman_environment.json
```

### 3. Configuração (já está correta)

```json
{
  "keycloak_url": "http://localhost:8081",
  "api_url": "http://localhost:8090/api",
  "username": "admin@acme.com",
  "password": "admin123",
  "tenant_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
}
```

### 4. Executar Requests

1. **Auth** → `Get Access Token` (preenche token automaticamente)
2. **User** → `List User Tenants`
3. **ABAC/OPA** → `RBAC Test`, `Alçada Test`

## 🔧 Troubleshooting

### Keycloak não inicia

```bash
# Ver logs
tail -100 /tmp/keycloak-postgres.log

# Verificar PostgreSQL
PGPASSWORD=postgres psql -h localhost -p 5433 -U postgres -l

# Matar processo anterior
pkill -f "keycloak.*8081"

# Verificar porta livre
lsof -i :8081
```

### Backend - Erro de Conexão

**PostgreSQL:**
```bash
# Testar conexão
PGPASSWORD=dev123 psql -h localhost -p 5433 -U jetski -d jetski_local

# Verificar application-local.yml
grep -A3 "datasource:" backend/src/main/resources/application-local.yml
```

**Keycloak:**
```bash
# Testar well-known
curl http://localhost:8081/realms/jetski-saas/.well-known/openid-configuration

# Ver issuer-uri no application-local.yml
grep issuer-uri backend/src/main/resources/application-local.yml
```

**OPA:**
```bash
# Testar health
curl http://localhost:8181/health

# Ver logs
tail -f /tmp/opa-local.log

# Reiniciar
pkill -f "opa run.*8181"
/home/franciscocfreire/repos/jetski/infra/start-opa-local.sh
```

## 📁 Scripts Úteis

| Script | Descrição |
|--------|-----------|
| `/infra/start-opa-local.sh` | Inicia OPA local |
| `/infra/keycloak-setup/start-keycloak-postgres.sh` | Inicia Keycloak |
| `/infra/keycloak-setup/setup-keycloak-local.sh` | Importa realm (primeira vez) |
| `/infra/stop-local.sh` | Para tudo (Keycloak, OPA, Backend) |

## 📝 Logs

| Serviço | Log |
|---------|-----|
| Keycloak | `/tmp/keycloak-postgres.log` |
| OPA | `/tmp/opa-local.log` |
| Backend | `/tmp/backend.log` (se rodando em background) |
| PostgreSQL | `journalctl -u postgresql -f` |
| Redis | `journalctl -u redis-server -f` |

## ✅ Checklist Final

- [x] PostgreSQL local (5433) com senhas configuradas
- [x] Redis local (6379) rodando
- [x] OPA local (8181) rodando
- [ ] Keycloak (8081) - **Execute**: `infra/keycloak-setup/start-keycloak-postgres.sh`
- [ ] Backend (8090) - **Execute**: `cd backend && SPRING_PROFILES_ACTIVE=local mvn spring-boot:run`
- [ ] Realm importado - **Execute** (primeira vez): `infra/keycloak-setup/setup-keycloak-local.sh`
- [ ] Testado no Postman

## 🎯 Próximos Passos

1. Iniciar Keycloak
2. Importar realm (se ainda não foi feito)
3. Iniciar Backend
4. Testar no Postman
5. Desenvolver! 🚀
