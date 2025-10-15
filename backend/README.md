# Jetski SaaS - Backend API

API REST multi-tenant para gestão de locações de jetski.

## Tecnologias

- **Java 21**
- **Spring Boot 3.3**
- **PostgreSQL 16** (com Row Level Security)
- **Redis 7** (cache)
- **Keycloak 26** (autenticação OIDC)
- **Flyway** (migrations)
- **Maven** (build)

## Pré-requisitos

- Java 21
- Maven 3.8+
- Docker & Docker Compose (para serviços)

## Setup

### 1. Subir serviços

```bash
# Da raiz do projeto
cd ..
make up

# Ou
docker-compose up -d
```

### 2. Build

```bash
cd backend
mvn clean install
```

### 3. Rodar aplicação

```bash
mvn spring-boot:run
```

A API estará disponível em: http://localhost:8090/api

## Testes

```bash
# Todos os testes
mvn test

# Apenas testes unit\u00e1rios
mvn test -Dtest=*Test

# Apenas testes de integração
mvn test -Dtest=*IT

# Com cobertura
mvn clean verify
```

## Estrutura

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/jetski/
│   │   │   ├── config/          # Configurações Spring
│   │   │   ├── domain/          # Entidades JPA
│   │   │   ├── repository/      # Repositories JPA
│   │   │   ├── service/         # Lógica de negócio
│   │   │   ├── controller/      # REST Controllers
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── security/        # Tenant, filters, auth
│   │   │   ├── exception/       # Exceções customizadas
│   │   │   └── JetskiApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/    # Flyway migrations
│   └── test/
│       └── java/com/jetski/
└── pom.xml
```

## Multi-tenancy

O sistema usa **Row Level Security (RLS)** do PostgreSQL para isolamento de dados.

### TenantContext

ThreadLocal que armazena o `tenant_id` durante a requisição:

```java
// Configurado automaticamente pelo TenantFilter
UUID tenantId = TenantContext.getTenantId();
```

### TenantFilter

Filtro que:
1. Extrai `tenant_id` do header `X-Tenant-Id`
2. Valida contra JWT claim (se autenticado)
3. Armazena no `TenantContext`
4. Limpa no `finally`

### Exemplo de requisição

```bash
curl http://localhost:8090/api/v1/modelos \
  -H "Authorization: Bearer <jwt-token>" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
```

## Endpoints

### Públicos

- `GET /api/actuator/health` - Health check
- `GET /api/swagger-ui.html` - Documentação Swagger

### Protegidos (requerem autenticação + tenant)

- `GET /api/v1/modelos` - Listar modelos
- `GET /api/v1/jetskis` - Listar jetskis
- ... (serão implementados nas próximas histórias)

## Configuração

### application.yml

Principais propriedades:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/jetski_dev
    username: jetski
    password: dev123

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/jetski-saas

jetski:
  tenant:
    header-name: X-Tenant-Id
```

## Desenvolvimento

### Hot reload

O `spring-boot-devtools` está habilitado. Qualquer mudança em código dispara reload automático.

### Lombok

Certifique-se de habilitar annotation processing na sua IDE.

### MapStruct

Mappers são gerados automaticamente durante a compilação.

## Migrations (Flyway)

```bash
# Executar migrations
mvn flyway:migrate

# Ver status
mvn flyway:info

# Limpar database (cuidado!)
mvn flyway:clean
```

## Histórias Implementadas

- ✅ **STORY-001**: TenantContext e TenantFilter (5 pts)
  - ThreadLocal para armazenar tenant
  - Filtro para extrair e validar tenant
  - Exceções customizadas
  - Testes unitários (cobertura > 80%)

## Próximas Histórias

- 📋 **STORY-005**: Migrations Flyway Base (5 pts)
- 📋 **STORY-002**: RLS Implementation (8 pts)
- 📋 **STORY-003**: Keycloak Integration (5 pts)

## Links

- [Stories do Backend](./stories/README.md)
- [Project Board](../stories/project-board.md)
- [EPIC-01: Multi-tenant Foundation](../stories/epics/epic-01-multi-tenant-foundation.md)
