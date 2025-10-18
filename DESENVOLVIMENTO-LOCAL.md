# Guia de Desenvolvimento Local - Jetski SaaS

## 🎯 Escolha Seu Cenário

### Cenário 1: Docker Infra + Spring Boot Local (RECOMENDADO)
- Infraestrutura no Docker
- Spring Boot na sua máquina
- Hot reload rápido

### Cenário 2: 100% Local
- Tudo rodando na sua máquina
- Sem Docker

---

## 🐳 CENÁRIO 1: Docker Infra + Spring Boot Local

### 1. Subir Infraestrutura Docker

```bash
cd /home/ubuntu/repos/jetski

# Subir PostgreSQL, Redis, Keycloak, OPA
docker-compose up -d

# Verificar status
docker-compose ps

# Aguardar (~30s)
sleep 30
```

### 2. Verificar Serviços Docker

```bash
# PostgreSQL (porta 5432)
PGPASSWORD=dev123 psql -h localhost -p 5432 -U jetski -d jetski_dev -c "SELECT version();"

# Redis (porta 6379)
redis-cli -h localhost -p 6379 ping

# Keycloak (porta 8080)
curl -s http://localhost:8080/realms/jetski-saas | jq -r '.realm'

# OPA (porta 8181)
curl -s http://localhost:8181/health
```

### 3. Rodar Spring Boot Localmente

```bash
cd /home/ubuntu/repos/jetski/backend

# Primeira vez: rodar migrations
mvn flyway:migrate

# Rodar aplicação (porta 8090)
mvn spring-boot:run

# OU com debug remoto (porta 5005)
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

### 4. Testar API Local

```bash
# Health check
curl http://localhost:8090/api/actuator/health | jq

# Endpoint público
curl http://localhost:8090/api/v1/auth-test/public

# Swagger UI (navegador)
open http://localhost:8090/api/swagger-ui.html
```

### 5. Parar Ambiente

```bash
# Parar Spring Boot: Ctrl+C no terminal

# Parar Docker
docker-compose down

# Parar e limpar volumes (perde dados!)
docker-compose down -v
```

---

## 💻 CENÁRIO 2: 100% Local (Sem Docker)

### Pré-requisitos

```bash
# Verificar instalações
psql --version        # PostgreSQL 16+
redis-cli --version   # Redis 7+
java -version         # Java 21
mvn -version          # Maven 3.8+
```

### 1. Configurar PostgreSQL Local

```bash
# Criar database
sudo -u postgres psql -c "CREATE USER jetski WITH PASSWORD 'dev123';"
sudo -u postgres psql -c "CREATE DATABASE jetski_local OWNER jetski;"

# Testar
PGPASSWORD=dev123 psql -h localhost -U jetski -d jetski_local -c "SELECT version();"
```

### 2. Iniciar Redis Local

```bash
# Verificar se está rodando
redis-cli ping

# Se não estiver, iniciar
redis-server &
# OU
sudo systemctl start redis
```

### 3. Keycloak Local (Opcional - via Docker)

```bash
# Rodar Keycloak na porta 8081
docker run -d --name keycloak-local \
  -p 8081:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:26.0 start-dev
```

### 4. Rodar Migrations

```bash
cd /home/ubuntu/repos/jetski/backend

mvn flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/jetski_local \
  -Dflyway.user=jetski \
  -Dflyway.password=dev123
```

### 5. Rodar Spring Boot (Profile Local)

```bash
cd /home/ubuntu/repos/jetski/backend

# Com profile local (application-local.yml)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# OU com configuração customizada
mvn spring-boot:run \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/jetski_local \
  -Dspring.datasource.username=jetski \
  -Dspring.datasource.password=dev123
```

---

## 🔍 Troubleshooting

### Porta já em uso

```bash
# Descobrir o que está usando a porta
lsof -ti:8090    # Spring Boot
lsof -ti:5432    # PostgreSQL
lsof -ti:6379    # Redis
lsof -ti:8080    # Keycloak

# Matar processo
lsof -ti:8090 | xargs kill
```

### PostgreSQL não conecta

```bash
# Ver status
sudo systemctl status postgresql

# Reiniciar
sudo systemctl restart postgresql

# Ver logs
sudo journalctl -u postgresql -n 50
```

### Redis não conecta

```bash
# Ver status
sudo systemctl status redis

# Reiniciar
sudo systemctl restart redis
```

### Docker não sobe

```bash
# Ver logs
docker-compose logs postgres
docker-compose logs keycloak

# Reiniciar serviço específico
docker-compose restart postgres
```

---

## 📊 Portas Utilizadas

| Serviço | Cenário 1 (Docker) | Cenário 2 (Local) |
|---------|-------------------|-------------------|
| PostgreSQL | 5432 | 5432 |
| Redis | 6379 | 6379 |
| Keycloak | 8080 | 8081 (Docker) |
| OPA | 8181 | - |
| Spring Boot | 8090 | 8090 |
| Debug | 5005 | 5005 |

---

## 🎯 Comandos Rápidos

### Status Geral

```bash
# Docker
docker-compose ps

# Processos Java
jps -l | grep jetski

# Todas as portas
lsof -i :5432,:6379,:8080,:8090
```

### Logs

```bash
# Docker
docker-compose logs -f

# Spring Boot
tail -f backend/logs/application.log
```

### Reset Completo

```bash
# Parar tudo
docker-compose down -v
pkill -f "spring-boot:run"

# Subir novamente
docker-compose up -d
cd backend
mvn flyway:migrate
mvn spring-boot:run
```

---

## 📚 Links Úteis

- **Swagger UI:** http://localhost:8090/api/swagger-ui.html
- **Actuator Health:** http://localhost:8090/api/actuator/health
- **Keycloak Admin:** http://localhost:8080/admin (admin/admin)
- **API Docs:** http://localhost:8090/api/v3/api-docs

---

**Última atualização:** 2025-10-16
