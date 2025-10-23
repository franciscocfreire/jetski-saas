# Jetski SaaS - Backend API

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-1.1.3-green.svg)](https://spring.io/projects/spring-modulith)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Tests](https://img.shields.io/badge/tests-60%20passing-brightgreen.svg)]()
[![Coverage - Lines](https://img.shields.io/badge/coverage--lines-80.5%25-brightgreen.svg)]()
[![Coverage - Branches](https://img.shields.io/badge/coverage--branches-56.6%25-green.svg)]()

API REST multi-tenant para gestão de locações de jetski, implementada como **Monolito Modular** usando Spring Modulith.

## 📊 Evolução Arquitetural

### AS IS - Monolito Tradicional (até v0.1.0)

```mermaid
graph TB
    subgraph "Aplicação Monolítica"
        Controller[Controllers]
        Service[Services]
        Repo[Repositories]
        Domain[Domain Entities]
        Config[Configuration]
        Security[Security]

        Controller --> Service
        Controller --> Security
        Service --> Repo
        Service --> Domain
        Service --> Security
        Security --> Repo
        Config --> Service
        Config --> Security

        style Controller fill:#ff9999
        style Service fill:#ff9999
        style Repo fill:#ff9999
        style Domain fill:#ff9999
        style Config fill:#ff9999
        style Security fill:#ff9999
    end

    Client[Client] --> Controller

    DB[(PostgreSQL<br/>RLS)]
    Repo --> DB

    KC[Keycloak<br/>OIDC]
    Security --> KC
```

**Problemas:**
- ❌ Acoplamento alto entre componentes
- ❌ Dependências circulares (shared ↔ usuarios)
- ❌ Difícil entender limites de contexto
- ❌ Testes validam apenas funcionalidade, não arquitetura
- ❌ Preparação para microserviços complexa

### TO BE - Monolito Modular Completo (v1.0+ Planejado)

```mermaid
graph TB
    subgraph "✅ PRONTO: shared"
        direction LR
        Shared[shared<br/>───────────<br/>security API<br/>authorization OPA<br/>exception handling<br/>config Redis/Cache<br/>internal filters]

        style Shared fill:#90EE90,stroke:#2d5016,stroke-width:3px
    end

    subgraph "✅ PRONTO: usuarios"
        direction LR
        Usuarios[usuarios<br/>───────────<br/>api: UserTenantsController<br/>domain: Usuario, Membro, GlobalRoles<br/>internal: TenantAccessService]

        style Usuarios fill:#90EE90,stroke:#2d5016,stroke-width:3px
    end

    subgraph "🚧 EM PROGRESSO: locacoes"
        direction LR
        Locacoes[locacoes<br/>───────────<br/>api: Modelos, Jetskis, Reservas, Locacoes<br/>domain: Modelo, Jetski, Vendedor, Cliente, Reserva, Locacao<br/>internal: Services, Repositories<br/>events: ReservaCreated, LocacaoConcluida]

        style Locacoes fill:#FFD700,stroke:#b8860b,stroke-width:3px
    end

    subgraph "📋 PLANEJADO: combustivel"
        direction LR
        Combustivel[combustivel<br/>───────────<br/>domain: Abastecimento, FuelPolicy<br/>events: AbastecimentoRegistrado]

        style Combustivel fill:#E8E8E8,stroke:#999,stroke-width:2px,stroke-dasharray: 5 5
    end

    subgraph "📋 PLANEJADO: manutencao"
        direction LR
        Manutencao[manutencao<br/>───────────<br/>domain: OS_Manutencao<br/>events: ManutencaoIniciada/Concluida]

        style Manutencao fill:#E8E8E8,stroke:#999,stroke-width:2px,stroke-dasharray: 5 5
    end

    subgraph "📋 PLANEJADO: financeiro"
        direction LR
        Financeiro[financeiro<br/>───────────<br/>domain: FechamentoDiario, FechamentoMensal<br/>CommissionPolicy, Comissao<br/>events: FechamentoCriado, ComissaoCalculada]

        style Financeiro fill:#E8E8E8,stroke:#999,stroke-width:2px,stroke-dasharray: 5 5
    end

    subgraph "📋 PLANEJADO: fotos"
        direction LR
        Fotos[fotos<br/>───────────<br/>domain: Foto, FotoMetadata<br/>S3 integration<br/>events: FotoUploadada]

        style Fotos fill:#E8E8E8,stroke:#999,stroke-width:2px,stroke-dasharray: 5 5
    end

    Client[Cliente<br/>Web/Mobile] --> Usuarios
    Client --> Locacoes
    Client -.futuro.-> Combustivel
    Client -.futuro.-> Manutencao
    Client -.futuro.-> Financeiro
    Client -.futuro.-> Fotos

    Usuarios --> Shared
    Locacoes --> Shared
    Combustivel -.futuro.-> Shared
    Manutencao -.futuro.-> Shared
    Financeiro -.futuro.-> Shared
    Fotos -.futuro.-> Shared

    Events{Spring<br/>Application<br/>Events}
    Usuarios -.publish.-> Events
    Locacoes -.pub/sub.-> Events
    Combustivel -.subscribe.-> Events
    Manutencao -.pub/sub.-> Events
    Financeiro -.subscribe.-> Events
    Fotos -.subscribe.-> Events

    DB[(PostgreSQL 16<br/>Multi-tenant RLS)]
    Usuarios --> DB
    Locacoes --> DB
    Combustivel -.futuro.-> DB
    Manutencao -.futuro.-> DB
    Financeiro -.futuro.-> DB
    Fotos -.futuro.-> DB

    S3[(AWS S3<br/>Foto Storage)]
    Fotos -.futuro.-> S3

    KC[Keycloak 26<br/>OIDC]
    Shared --> KC

    OPA[OPA<br/>RBAC + Alçada]
    Shared --> OPA

    Redis[(Redis 7<br/>Cache)]
    Shared --> Redis
```

**Legenda de Status:**
- ✅ **Verde sólido** (#90EE90) - Módulo PRONTO e em produção
- 🚧 **Amarelo sólido** (#FFD700) - Módulo EM PROGRESSO (próximo)
- 📋 **Cinza tracejado** (#E8E8E8) - Módulo PLANEJADO (futuro)

**Módulos Planejados:**
1. ✅ **shared** - Infraestrutura compartilhada (security, auth, config)
2. ✅ **usuarios** - Gestão de usuários e membros multi-tenant
3. 🚧 **locacoes** - Core business: Modelos, Jetskis, Reservas, Locações (70% completo)
4. 📋 **combustivel** - Gestão de abastecimento e políticas de cobrança
5. 📋 **manutencao** - Ordens de serviço e controle de disponibilidade
6. 📋 **financeiro** - Fechamentos diário/mensal e comissões
7. 📋 **fotos** - Upload S3, metadata, integração com locações

**Benefícios Alcançados:**
- ✅ Módulos com limites claros e acoplamento baixo
- ✅ Inversão de dependência (shared define interface, usuarios implementa)
- ✅ Testes automatizados de arquitetura (Spring Modulith)
- ✅ Documentação automática de módulos (PlantUML)
- ✅ Preparado para evolução gradual para microserviços
- ✅ Comunicação via eventos entre módulos (próximo)

**Ver arquitetura completa:** [docs/ARCHITECTURE_COMPLETE.md](./docs/ARCHITECTURE_COMPLETE.md)

## 🏗️ Arquitetura de Módulos

### Estrutura Atual

```
com.jetski/
├── shared/                        # Módulo: Shared Infrastructure
│   ├── security/                  # ✅ Named Interface (API pública)
│   │   ├── TenantAccessValidator  # Interface
│   │   ├── TenantAccessInfo       # DTO
│   │   ├── TenantContext          # ThreadLocal
│   │   └── SecurityConfig         # Security configuration
│   ├── authorization/             # OPA integration
│   │   ├── OPAAuthorizationService
│   │   └── dto/
│   ├── exception/                 # Exception handling
│   ├── config/                    # Shared configuration
│   └── internal/                  # 🔒 Implementação privada
│       ├── TenantFilter
│       ├── JwtAuthenticationConverter
│       └── FilterChainExceptionFilter
│
├── usuarios/                      # Módulo: Users and Members
│   ├── api/                       # ✅ API pública
│   │   ├── UserTenantsController
│   │   ├── UserInvitationController    # ✨ NEW (v0.5.0)
│   │   ├── AccountActivationController # ✨ NEW (v0.5.0)
│   │   ├── TenantMemberController      # ✨ NEW (v0.5.0)
│   │   └── dto/
│   ├── domain/                    # Entidades de domínio
│   │   ├── Usuario
│   │   ├── Membro
│   │   ├── Convite                     # ✨ NEW (v0.5.0)
│   │   └── event/
│   │       └── UserAccountActivatedEvent # ✨ NEW (v0.5.0)
│   └── internal/                  # 🔒 Implementação privada
│       ├── TenantAccessService    # Implementa TenantAccessValidator
│       ├── UserInvitationService        # ✨ NEW (v0.5.0)
│       ├── MemberManagementService      # ✨ NEW (v0.5.0)
│       ├── UserActivationEmailListener  # ✨ NEW (v0.5.0)
│       ├── UsuarioGlobalRoles
│       └── repository/
│           ├── MembroRepository
│           ├── ConviteRepository        # ✨ NEW (v0.5.0)
│           └── UsuarioGlobalRolesRepository
│
└── locacoes/                      # Módulo: Rentals (futuro)
    ├── api/
    ├── domain/
    └── internal/
```

### Regras de Dependência

```mermaid
graph LR
    usuarios[usuarios] -->|allowed| shared_sec[shared::security]
    locacoes[locacoes] -->|allowed| shared_sec
    locacoes -.->|events| usuarios

    shared_internal[shared::internal] -.->|usa| shared_sec

    style shared_sec fill:#90EE90
    style usuarios fill:#87CEEB
    style locacoes fill:#87CEEB
```

**Princípios:**
1. Módulos de domínio (`usuarios`, `locacoes`) podem depender de `shared::security`
2. Nenhum módulo pode acessar `internal/` de outros módulos
3. Comunicação entre módulos de domínio via eventos (loose coupling)
4. Shared não pode depender de módulos de domínio (Dependency Inversion)

## 🚀 Tecnologias

### Core
- **Java 21** - LTS release com Virtual Threads
- **Spring Boot 3.3** - Framework principal
- **Spring Modulith 1.1.3** - Validação de arquitetura modular
- **Maven 3.9+** - Build tool

### Persistência
- **PostgreSQL 16** - Database com Row Level Security (RLS)
- **Flyway 10** - Database migrations
- **Spring Data JPA** - ORM
- **HikariCP** - Connection pooling

### Segurança
- **Keycloak 26 (OSS)** - Identity Provider (OIDC)
- **Spring Security 6** - Authentication & Authorization
- **OAuth2 Resource Server** - JWT validation
- **OPA (Open Policy Agent)** - Policy-based authorization (RBAC + Alçada)

### Cache & Messaging
- **Redis 7** - Distributed cache (tenant access, sessions)
- **Spring Application Events** - Comunicação entre módulos

### Observabilidade
- **Spring Boot Actuator** - Health checks, metrics
- **Logback** - Structured logging
- **JaCoCo** - Code coverage

### Testes
- **JUnit 5** - Unit tests framework
- **Mockito** - Mocking framework
- **AssertJ** - Fluent assertions
- **Testcontainers** - Integration tests com PostgreSQL
- **MockMvc** - API testing
- **ArchUnit** (via Spring Modulith) - Architecture tests

## 📋 Pré-requisitos

- Java 21+ (OpenJDK ou Oracle JDK)
- Maven 3.9+
- Docker & Docker Compose (para serviços)
- Git

## 🔧 Setup

### 1. Clone do repositório

```bash
git clone <repository-url>
cd jetski/backend
```

### 2. Subir serviços de infraestrutura

```bash
# Da raiz do projeto
cd ..
make up

# Ou manualmente
docker-compose up -d postgres redis keycloak opa
```

Serviços disponíveis:
- PostgreSQL: `localhost:5432` (dev) / `localhost:5433` (local)
- Redis: `localhost:6379`
- Keycloak: `http://localhost:8080` (dev) / `http://localhost:8081` (local)
- OPA: `http://localhost:8181`

### 3. Configurar Keycloak

```bash
# Script automatizado de setup
./infra/keycloak-setup/setup-keycloak.sh

# Ou manualmente via Admin Console:
# http://localhost:8080/admin (admin/admin)
```

### 4. Build e testes

```bash
# Build completo com testes
mvn clean install

# Build sem testes (mais rápido)
mvn clean install -DskipTests

# Apenas compilar
mvn clean compile
```

### 5. Rodar aplicação

#### Perfil LOCAL (desenvolvimento local)
```bash
# Usando Maven
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# Ou via JAR
java -jar target/jetski-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

#### Perfil DEV (Docker Compose)
```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

A API estará disponível em: **http://localhost:8090/api**

## 🧪 Testes

### Execução

```bash
# Todos os testes (89 testes)
mvn test

# Apenas testes unitários
mvn test -Dtest=*Test

# Apenas testes de integração
mvn test -Dtest=*IntegrationTest

# Testes de verificação modular
mvn test -Dtest=ModuleStructureTest

# Com relatório de cobertura
mvn clean verify
```

### Cobertura

```bash
# Gerar relatório JaCoCo
mvn jacoco:report

# Visualizar relatório
open target/site/jacoco/index.html
```

**Métricas atuais:**
- **60 testes de integração passando** (100%)
- **80.5% line coverage** (target: 60%) ✅
- **56.6% branch coverage** (target: 50%) ✅

**Breakdown por suite:**
- `UserInvitationIntegrationTest`: 18 testes ✅
- `AccountActivationIntegrationTest`: 21 testes ✅
- `UserActivationEventFlowIntegrationTest`: 11 testes ✅
- `MemberManagementIntegrationTest`: 10 testes ✅ (NEW)

### Tipos de Testes

1. **Unit Tests** - Testes unitários com mocks
   - `TenantAccessServiceTest`
   - `TenantContextTest`
   - `OPAAuthorizationServiceTest`

2. **Integration Tests** - Testes com Spring context + Testcontainers
   - `AuthTestControllerIntegrationTest`
   - `ApplicationContextIntegrationTest`
   - `FlywayMigrationIntegrationTest`

3. **Architecture Tests** - Validação de estrutura modular
   - `ModuleStructureTest` (Spring Modulith)

## 🏛️ Multi-tenancy

### Estratégia de Isolamento

**Row Level Security (RLS)** do PostgreSQL:
- Todas as tabelas operacionais incluem coluna `tenant_id`
- Políticas RLS garantem isolamento automático
- Filtro aplicado transparentemente em todas as queries

### Fluxo de Requisição

```mermaid
sequenceDiagram
    participant C as Client
    participant F as TenantFilter
    participant TC as TenantContext
    participant S as TenantAccessService
    participant DB as PostgreSQL

    C->>F: HTTP Request<br/>Header: X-Tenant-Id
    F->>F: Extract tenant_id from header
    F->>F: Validate against JWT claim
    F->>S: validateAccess(usuario_id, tenant_id)
    S->>DB: Check membro table
    DB-->>S: User has access
    S-->>F: TenantAccessInfo
    F->>TC: setTenantId(tenant_id)
    F->>DB: SET app.tenant_id = 'uuid'
    F->>C: Continue request
    Note over DB: RLS filters all queries<br/>by app.tenant_id
    F->>TC: clear() (finally)
```

### TenantContext

ThreadLocal que armazena o `tenant_id` durante a requisição:

```java
// Configurado automaticamente pelo TenantFilter
UUID tenantId = TenantContext.getTenantId();

// Uso em queries (automático via RLS)
@Query("SELECT m FROM Modelo m WHERE m.tenantId = current_setting('app.tenant_id')::uuid")
List<Modelo> findAllByTenant();
```

### TenantAccessService

Serviço que valida acesso de usuários a tenants:

```java
public interface TenantAccessValidator {
    TenantAccessInfo validateAccess(UUID usuarioId, UUID tenantId);
}

// Implementação em usuarios::internal
@Service
public class TenantAccessService implements TenantAccessValidator {
    // 1. Check unrestricted access (platform admin)
    // 2. Check membership via membro table
    // 3. Return access info with roles
}
```

**Tipos de acesso:**
- **Unrestricted** - Platform admin (acesso a qualquer tenant)
- **Member** - Usuário membro do tenant (roles específicos)
- **Denied** - Sem acesso ao tenant

### Exemplo de Requisição

```bash
# Listar modelos de um tenant específico
curl http://localhost:8090/api/v1/user/tenants \
  -H "Authorization: Bearer <jwt-token>" \
  -H "X-Tenant-Id: a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
```

## 📚 Endpoints

### Públicos (sem autenticação)

- `GET /api/actuator/health` - Health check
- `GET /api/actuator/info` - Application info
- `GET /api/swagger-ui.html` - Swagger UI
- `GET /api/v3/api-docs` - OpenAPI spec
- `GET /api/v1/auth-test/public` - Endpoint público de teste

### Protegidos (requerem JWT + X-Tenant-Id)

**Usuários e Membros:**
- `GET /api/v1/user/tenants` - Listar tenants do usuário
- `GET /api/v1/user/tenants/count` - Contar tenants do usuário
- `POST /api/v1/tenants/{tenantId}/users/invite` - Convidar novo usuário (ADMIN_TENANT) ✨ NEW
- `POST /api/v1/auth/activate` - Ativar conta com token (PÚBLICO) ✨ NEW
- `GET /api/v1/tenants/{tenantId}/members?includeInactive={bool}` - Listar membros do tenant ✨ NEW
- `DELETE /api/v1/tenants/{tenantId}/members/{usuarioId}` - Desativar membro ✨ NEW

**AuthTest (endpoints de teste):**
- `GET /api/v1/auth-test/me` - Info do usuário autenticado
- `GET /api/v1/auth-test/operador-only` - Requer role OPERADOR
- `GET /api/v1/auth-test/manager-only` - Requer role GERENTE ou ADMIN_TENANT
- `GET /api/v1/auth-test/opa/rbac` - Teste de autorização RBAC via OPA
- `GET /api/v1/auth-test/opa/alcada` - Teste de alçada de aprovação via OPA

## 🔧 Configuração

### Perfis de Ambiente

**local** - Desenvolvimento local (padrão)
```yaml
server:
  port: 8090

spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/jetski_local
    username: jetski
    password: local123

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8081/realms/jetski-saas

opa:
  base-url: http://localhost:8181
```

**dev** - Docker Compose
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/jetski_dev

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/jetski-saas
```

**test** - Testes (Testcontainers)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:<random>/jetski_test
```

### Variáveis de Ambiente

```bash
# Database
DB_URL=jdbc:postgresql://localhost:5432/jetski_dev
DB_USERNAME=jetski
DB_PASSWORD=dev123

# Keycloak
KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/jetski-saas

# OPA
OPA_BASE_URL=http://localhost:8181

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Spring Profile
SPRING_PROFILES_ACTIVE=local
```

## 🧬 Migrations (Flyway)

### Estrutura

```
src/main/resources/db/migration/
├── V001__create_tenant_tables.sql      # Tabelas multi-tenant base
├── V002__create_operational_tables.sql  # Modelo, Jetski, Cliente, etc.
├── V003__create_support_tables.sql      # Tabelas de apoio
├── V004__create_composite_indexes.sql   # Índices compostos
├── V999__seed_data_dev.sql              # Seed data (apenas DEV)
├── V1000__create_global_roles_table.sql # Global roles
├── V1001__create_tenant_access_tables.sql
├── V1002__tenant_access_indexes.sql
├── V1003__seed_platform_admin.sql       # Platform admin user
├── V1004__create_user_invitation_tables.sql # ✨ NEW (v0.5.0)
├── V1005__add_email_tracking_to_convite.sql # ✨ NEW (v0.5.0)
├── V1006__fix_convite_unique_constraint.sql # ✨ NEW (v0.5.0)
├── V1007__remove_restrictive_email_format_constraint.sql # ✨ NEW (v0.5.0)
└── V9999__test_data.sql                 # Test data para integration tests
```

### Comandos

```bash
# Executar migrations
mvn flyway:migrate

# Ver status de migrations
mvn flyway:info

# Validar migrations
mvn flyway:validate

# Baseline (primeira vez em DB existente)
mvn flyway:baseline

# Limpar database (⚠️ cuidado!)
mvn flyway:clean
```

## 🏗️ Desenvolvimento

### Hot Reload

Spring Boot DevTools habilitado - mudanças em código disparam reload automático:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

### Lombok

Certifique-se de habilitar annotation processing na sua IDE:

**IntelliJ IDEA:**
1. Settings → Build, Execution, Deployment → Compiler → Annotation Processors
2. Enable annotation processing ✅

**VS Code:**
1. Instalar extensão "Lombok Annotations Support"

### Spring Modulith

Verificar estrutura modular:

```bash
# Rodar testes de arquitetura
mvn test -Dtest=ModuleStructureTest

# Gerar documentação de módulos
mvn test -Dtest=ModuleStructureTest#shouldGenerateModuleDocumentation

# Visualizar documentação
open target/spring-modulith-docs/modules.puml
```

Documentação gerada:
- `modules.md` - Documentação em Markdown
- `modules.puml` - Diagrama PlantUML de todos os módulos
- `<module>.puml` - Diagrama individual de cada módulo

### Adicionar Novo Módulo

1. Criar package `com.jetski.<nome-modulo>`
2. Criar `package-info.java` com anotação `@ApplicationModule`
3. Organizar estrutura: `api/`, `domain/`, `internal/`
4. Rodar `ModuleStructureTest` para validar

Exemplo:

```java
/**
 * Módulo de Locações
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Rentals",
    allowedDependencies = {"shared::security", "usuarios::api"}
)
package com.jetski.locacoes;
```

## 📊 Status do Projeto

### ✅ Implementado

- [x] Multi-tenant foundation (TenantContext, TenantFilter)
- [x] OAuth2/JWT authentication (Keycloak integration)
- [x] Multi-tenant access control (TenantAccessService)
- [x] OPA authorization (RBAC + Alçada)
- [x] Database migrations (Flyway)
- [x] Row Level Security (RLS)
- [x] Global exception handling
- [x] Redis caching
- [x] Integration tests (Testcontainers)
- [x] **Modular architecture (Spring Modulith)**
- [x] **Architecture tests**
- [x] **Module documentation generation**
- [x] **User Invitation flow (OIDC)** ✨ NEW
- [x] **Account Activation** ✨ NEW
- [x] **Member Management (list/deactivate)** ✨ NEW
- [x] **Event-Driven Architecture (Spring Events)** ✨ NEW
- [x] **80.5% test coverage** ✨ NEW

### 🚧 Em Progresso

- [ ] Módulo `locacoes` (Reserva, Locação, Modelo, Jetski)
- [ ] Comunicação via mensageria distribuída (Kafka)
- [ ] API endpoints de domínio core business

### 📋 Backlog

- [ ] Módulo `manutencao` (OS Manutenção)
- [ ] Módulo `financeiro` (Fechamentos, Comissões)
- [ ] Módulo `fotos` (Upload S3, metadata)
- [ ] Migração gradual para microserviços (quando necessário)

## 🔗 Links Úteis

- [Especificação Inicial](../../inicial.md) - Documento completo de especificação
- [CLAUDE.md](../../CLAUDE.md) - Instruções para Claude Code
- [Spring Modulith Docs](https://docs.spring.io/spring-modulith/reference/)
- [Keycloak Setup](../infra/keycloak-setup/)
- [OPA Policies](../infra/opa/policies/)

## 📝 Licença

Proprietary - Jetski SaaS Project

---

**Versão:** 0.5.0-SNAPSHOT
**Última atualização:** 2025-10-21
**Testes:** 60 integration tests passing ✅
**Coverage:** 80.5% lines | 56.6% branches ✅
