# Setup do Ambiente Jetski

## 📋 Arquitetura de Ambientes

### Local (WSL) - Desenvolvimento Rápido
- **PostgreSQL**: Nativo no WSL, porta **5433**
- **Database**: `jetski_local`
- **Spring Boot**: Roda diretamente no WSL com hot reload
- **Profile**: `local`

### Dev/HML/PRD - Docker Compose
- **PostgreSQL**: Container Docker, porta **5432**
- **Database**: `jetski_dev`
- **Redis**: Container Docker, porta **6379**
- **Keycloak**: Container Docker, porta **8080**
- **Profile**: `dev`

---

## 🚀 Comandos Rápidos

### Ambiente Local (Desenvolvimento Diário)

```bash
# 1. Iniciar PostgreSQL local
sudo service postgresql start

# 2. Verificar status
sudo service postgresql status

# 3. Iniciar Spring Boot
cd /home/ubuntu/repos/jetski/backend
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# 4. Acessar health check
curl http://localhost:8090/api/actuator/health
```

### Ambiente Docker (Dev/HML/PRD)

```bash
# 1. Iniciar containers
cd /home/ubuntu/repos/jetski
docker-compose up -d

# 2. Verificar status
docker-compose ps

# 3. Ver logs
docker logs jetski-postgres
docker logs jetski-keycloak
docker logs jetski-redis

# 4. Parar containers
docker-compose down

# 5. Recriar tudo (limpar volumes)
docker-compose down -v
docker-compose up -d
```

---

## 🗄️ Configuração de Banco de Dados

### PostgreSQL Local (WSL)
- **Host**: localhost
- **Porta**: 5433
- **Database**: jetski_local
- **Usuário**: jetski
- **Senha**: dev123

```bash
# Conectar via psql
PGPASSWORD=dev123 psql -h localhost -p 5433 -U jetski -d jetski_local

# Listar tabelas
\dt

# Ver seed data
SELECT COUNT(*) FROM cliente;
SELECT * FROM tenant;
```

### PostgreSQL Docker (Dev)
- **Host**: localhost (do Windows) ou container name (entre containers)
- **Porta**: 5432
- **Database**: jetski_dev
- **Usuário**: jetski
- **Senha**: dev123

```bash
# Conectar via container
docker exec -it jetski-postgres psql -U jetski -d jetski_dev

# Rodar migrations manualmente
docker exec -i jetski-postgres psql -U jetski -d jetski_dev < backend/src/main/resources/db/migration/V001__create_multi_tenant_tables.sql
```

---

## 📁 Estrutura de Profiles

### application.yml (Base)
Configurações compartilhadas entre todos os ambientes.

### application-local.yml
- PostgreSQL local (porta 5433)
- Flyway **habilitado** (roda migrations automaticamente)
- Redis localhost
- Keycloak localhost

### application-dev.yml
- PostgreSQL Docker (porta 5432 via IP do container)
- Flyway **desabilitado** (migrations rodadas manualmente)
- Redis container
- Keycloak container

---

## ⚠️ Problema WSL2 Resolvido

### Issue
WSL2 não consegue acessar redes bridge personalizadas do Docker:
- WSL network: `172.30.x.x`
- Docker network: `172.20.0.x`
- Resultado: "Destination Host Unreachable"

### Solução Implementada
**Opção A (Local)**: PostgreSQL nativo no WSL
- ✅ Hot reload funciona perfeitamente
- ✅ Sem problemas de rede
- ✅ Desenvolvimento rápido

**Opção B (Docker)**: Full containerizado
- ✅ Ambiente idêntico a HML/PRD
- ✅ Isolamento completo
- ⚠️ Spring Boot precisa rodar em container também (não implementado ainda)

---

## 🗂️ Banco de Dados - Schema

### Tabelas Criadas (20 + 1 Flyway)

**Multi-tenant Base:**
- `tenant` - Empresas/clientes do SaaS
- `plano` - Planos de assinatura (Basic/Pro/Enterprise)
- `assinatura` - Assinaturas ativas dos tenants
- `usuario` - Usuários do sistema
- `membro` - Relação usuário-tenant com papéis

**Operacionais:**
- `modelo` - Modelos de jetski (Sea-Doo, Yamaha, etc.)
- `jetski` - Unidades individuais
- `vendedor` - Vendedores/parceiros
- `cliente` - Clientes que alugam

**Fluxo de Locação:**
- `reserva` - Reservas/agendamentos
- `locacao` - Locações ativas
- `foto` - Fotos de check-in/out/incidentes
- `abastecimento` - Logs de combustível
- `os_manutencao` - Ordens de manutenção

**Políticas e Regras:**
- `commission_policy` - Políticas de comissão
- `fuel_policy` - Políticas de combustível
- `fuel_price_day` - Preços diários de combustível

**Fechamentos:**
- `fechamento_diario` - Fechamentos diários
- `fechamento_mensal` - Fechamentos mensais
- `auditoria` - Trilha de auditoria

**Flyway:**
- `flyway_schema_history` - Controle de migrations

### Migrations Executadas

1. **V001** - Multi-tenant tables (tenant, plano, usuario, membro)
2. **V002** - Operational tables (modelo, jetski, vendedor, cliente)
3. **V003** - Support tables (reserva, locacao, policies, closures)
4. **V004** - Composite indexes (63 índices tenant-scoped)
5. **V999** - Seed data (dados de teste)

### Seed Data Carregado

- 1 Tenant (Praia do Sol Jet Ski Ltda)
- 3 Planos (Basic, Pro, Enterprise)
- 6 Usuários (Admin, Gerente, Operador, Vendedor, Mecânico, Financeiro)
- 6 Membros (com roles)
- 3 Modelos (Sea-Doo, Yamaha, Kawasaki)
- 5 Jetskis (1 manutenção, 1 alugado, 3 disponíveis)
- 5 Clientes
- 3 Vendedores
- 2 Fuel Policies
- 8 Fuel Prices (últimos 8 dias)
- 6 Commission Policies
- 1 Reserva
- 1 Locação ativa
- 1 OS Manutenção

---

## 🔧 Troubleshooting

### PostgreSQL local não inicia
```bash
sudo service postgresql start
sudo service postgresql status

# Ver logs
sudo tail -f /var/log/postgresql/postgresql-12-main.log
```

### Spring Boot não conecta no banco
```bash
# Verificar se PostgreSQL está rodando
sudo service postgresql status  # Local
docker ps | grep postgres       # Docker

# Verificar porta
netstat -tlnp | grep 5433  # Local
netstat -tlnp | grep 5432  # Docker

# Testar conexão
PGPASSWORD=dev123 psql -h localhost -p 5433 -U jetski -d jetski_local
```

### Migrations não rodaram
```bash
# Verificar histórico
PGPASSWORD=dev123 psql -h localhost -p 5433 -U jetski -d jetski_local -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"

# Rodar Flyway manualmente
cd backend
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5433/jetski_local -Dflyway.user=jetski -Dflyway.password=dev123
```

### Containers Docker não comunicam
```bash
# Verificar network
docker network ls
docker network inspect jetski_jetski-network

# Ver IPs dos containers
docker network inspect jetski_jetski-network --format '{{range .Containers}}{{.Name}}: {{.IPv4Address}}{{"\n"}}{{end}}'

# Testar conectividade entre containers
docker exec jetski-redis redis-cli ping
docker exec jetski-postgres psql -U jetski -d jetski_dev -c "SELECT 1;"
```

---

## 📝 Notas Importantes

1. **Hot Reload**: Funciona apenas no ambiente local (WSL)
2. **Porta PostgreSQL**: 5433 (local) vs 5432 (Docker)
3. **Flyway**: Habilitado no local, desabilitado no dev
4. **WSL2 Networking**: Containers Docker não são alcançáveis diretamente do WSL
5. **DBeaver**: Conecta via Windows usando localhost:5432 (Docker)
6. **Seed Data**: Apenas em ambiente local e dev (não carregar em PRD)

---

## 🎯 Próximos Passos

1. Criar entidades JPA (@Entity) para as tabelas
2. Implementar repositories (JpaRepository)
3. Criar DTOs e mappers (MapStruct)
4. Implementar services com regras de negócio
5. Criar controllers REST
6. Configurar segurança OAuth2 com Keycloak
7. Implementar testes (JUnit + Testcontainers)
8. Configurar CI/CD

---

## 📚 Referências

- **Especificação**: `inicial.md`
- **Architecture**: `CLAUDE.md`
- **Migrations**: `backend/src/main/resources/db/migration/`
- **Docker Compose**: `docker-compose.yml`
- **Keycloak Config**: `infra/keycloak-realm.json`

---

**Última atualização**: 2025-10-14
**Ambiente**: WSL2 Ubuntu 20.04 + Docker Desktop
