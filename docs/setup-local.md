# Setup do Ambiente de Desenvolvimento Local

Este guia detalha como configurar o ambiente de desenvolvimento local para o projeto Jetski SaaS.

## Pré-requisitos

### Obrigatórios

- **Docker Desktop** 20.10+ ou Docker Engine + Docker Compose
- **Git** 2.30+

### Para desenvolvimento Backend

- **Java 21** (OpenJDK ou Oracle JDK)
- **Maven** 3.8+
- IDE recomendada: IntelliJ IDEA ou VS Code com Extension Pack for Java

### Para desenvolvimento Frontend

- **Node.js** 18+ (recomendado: 20 LTS)
- **npm** ou **yarn**
- IDE recomendada: VS Code com extensões React/TypeScript

### Para desenvolvimento Mobile

- **Android Studio** (para Android)
- **Xcode** (para iOS, apenas macOS)
- **Kotlin Multiplatform Mobile plugin**

## Passo a Passo

### 1. Clonar o repositório

```bash
git clone <repo-url>
cd jetski
```

### 2. Subir serviços Docker

```bash
# Usando Make (recomendado)
make up

# Ou manualmente
docker-compose up -d
```

Isso vai subir:
- **PostgreSQL 16** na porta 5432
- **Redis 7** na porta 6379
- **Keycloak 26** na porta 8080

**Tempo esperado:** 1-2 minutos para todos os serviços ficarem prontos.

### 3. Verificar health dos serviços

```bash
# Testar todos
make test-all

# Ou individualmente
make test-db        # PostgreSQL
make test-redis     # Redis
make test-keycloak  # Keycloak
```

Saída esperada:
```
✅ PostgreSQL OK
✅ Redis OK
✅ Keycloak OK
```

### 4. Acessar Keycloak Admin Console

1. Abrir http://localhost:8080/admin
2. Login: `admin` / `admin`
3. Selecionar realm: **jetski-saas**

Você deve ver:
- 3 clients configurados: `jetski-api`, `jetski-web`, `jetski-mobile`
- 6 roles: ADMIN_TENANT, GERENTE, OPERADOR, VENDEDOR, MECANICO, FINANCEIRO
- 2 usuários de teste

### 5. Configurar Backend (Spring Boot)

```bash
cd backend

# Build
mvn clean install

# Rodar aplicação
mvn spring-boot:run

# Ou via Make (da raiz do projeto)
make backend-run
```

A API deve subir em http://localhost:8090 (porta configurável).

### 6. Configurar Frontend (Next.js)

```bash
cd frontend

# Instalar dependências
npm install

# Rodar em desenvolvimento
npm run dev
```

O frontend deve subir em http://localhost:3000.

### 7. Testar autenticação

#### Via Frontend
1. Acessar http://localhost:3000
2. Clicar em "Login"
3. Será redirecionado para Keycloak
4. Login com: `admin@acme.com` / `admin123`
5. Após login, deve voltar para o dashboard

#### Via API (Postman/curl)

```bash
# 1. Obter token do Keycloak
curl -X POST http://localhost:8080/realms/jetski-saas/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=jetski-api" \
  -d "username=admin@acme.com" \
  -d "password=admin123" \
  -d "scope=openid profile"

# 2. Copiar o access_token da resposta

# 3. Chamar API com token
curl http://localhost:8090/api/v1/modelos \
  -H "Authorization: Bearer <access_token>" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
```

## Estrutura do Banco de Dados

### Conexão PostgreSQL

```bash
# Via Make
make shell-postgres

# Ou manualmente
docker-compose exec postgres psql -U jetski -d jetski_dev
```

### Schemas

- **public:** Tabelas da aplicação
- **keycloak:** Tabelas do Keycloak (isolado)

### Tabelas principais (após migrations)

```sql
-- Listar tabelas
\dt

-- Ver estrutura de uma tabela
\d tenant

-- Query de exemplo
SELECT * FROM tenant;
```

## Troubleshooting

### Problema: "Port 5432 already in use"

Solução: PostgreSQL local está rodando. Pare-o:

```bash
# macOS
brew services stop postgresql

# Linux (systemd)
sudo systemctl stop postgresql

# Ou mude a porta no docker-compose.yml
ports:
  - "15432:5432"
```

### Problema: "Keycloak não fica pronto"

Verificar logs:

```bash
make logs-keycloak

# Ou
docker-compose logs -f keycloak
```

Causas comuns:
- PostgreSQL não está pronto (aguarde mais tempo)
- Falta memória (Keycloak precisa ~1GB RAM)
- Porta 8080 em uso

### Problema: "Realm jetski-saas não existe"

Importar manualmente:

```bash
make setup-keycloak

# Ou manualmente
bash infra/setup-keycloak.sh
```

### Problema: "Teste de RLS falha"

Verificar se migrations foram executadas:

```bash
make shell-postgres

# No psql:
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

# Deve mostrar todas as migrations aplicadas
```

## Resetar ambiente (limpar tudo)

**ATENÇÃO:** Isso vai apagar TODOS os dados!

```bash
make clean

# Confirmar com 'y'
# Depois subir novamente:
make up
```

## Próximos Passos

Após setup concluído:

1. ✅ Serviços rodando localmente
2. ✅ Keycloak configurado
3. ✅ Banco de dados criado
4. 📝 [Executar migrations Flyway](../backend/README.md) (STORY-005)
5. 📝 [Implementar TenantFilter](../backend/stories/story-001-tenant-context-filter.md) (STORY-001)
6. 📝 [Habilitar RLS](../backend/stories/story-002-rls-implementation.md) (STORY-002)

## Referências

- [Docker Compose Reference](https://docs.docker.com/compose/)
- [Keycloak Documentation](https://www.keycloak.org/docs/latest/)
- [PostgreSQL Row Level Security](https://www.postgresql.org/docs/16/ddl-rowsecurity.html)
- [Spring Boot with Keycloak](https://www.baeldung.com/spring-boot-keycloak)

---

**Última atualização:** 2025-01-15
