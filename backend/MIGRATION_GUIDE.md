# Guia de Migração: Monolito Tradicional → Monolito Modular

Este guia documenta o processo de migração para arquitetura modular com Spring Modulith.

## 📋 Checklist de Migração

### ✅ Fase 1: Preparação (Concluída)

- [x] Estudar conceitos de monolito modular
- [x] Adicionar Spring Modulith ao `pom.xml`
- [x] Verificar compatibilidade (compilação bem-sucedida)
- [x] Criar plano de módulos
- [x] Definir boundaries de contexto

### ✅ Fase 2: Criação da Estrutura (Concluída)

- [x] Criar módulo `shared` (infraestrutura)
- [x] Criar módulo `usuarios` (primeiro domínio)
- [x] Adicionar `package-info.java` em cada módulo
- [x] Definir named interfaces públicas

### ✅ Fase 3: Migração de Código (Concluída)

- [x] Mover código de segurança para `shared`
- [x] Mover código de usuários para `usuarios`
- [x] Aplicar Dependency Inversion Principle
- [x] Quebrar ciclos de dependência
- [x] Atualizar imports

### ✅ Fase 4: Testes e Validação (Concluída)

- [x] Criar `ModuleStructureTest`
- [x] Validar ausência de ciclos
- [x] Executar suite completa de testes (89 passing)
- [x] Gerar documentação de módulos

### ✅ Fase 5: Documentação (Concluída)

- [x] Atualizar README.md com diagramas AS IS/TO BE
- [x] Criar ARCHITECTURE.md detalhado
- [x] Documentar regras de dependência
- [x] Adicionar badges e métricas

### 🚧 Fase 6: Expansão de Módulos (Em Progresso)

- [ ] Criar módulo `locacoes`
- [ ] Implementar comunicação via eventos
- [ ] Migrar código de domínio de locações

### 📋 Fase 7: Módulos Futuros (Backlog)

- [ ] Módulo `combustivel`
- [ ] Módulo `manutencao`
- [ ] Módulo `financeiro`
- [ ] Módulo `fotos`

---

## 🔧 Passo a Passo Detalhado

### 1. Adicionar Spring Modulith

**Arquivo:** `pom.xml`

```xml
<!-- BOM para versões consistentes -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>1.1.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Dependências do Spring Modulith -->
<dependencies>
    <!-- Validação de estrutura modular -->
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>

    <!-- Testes de arquitetura -->
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Geração de documentação -->
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-docs</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Validação:**
```bash
mvn clean compile
# ✅ Deve compilar sem erros
```

---

### 2. Criar Módulo `shared`

**a) Criar package com estrutura:**

```
src/main/java/com/jetski/shared/
├── security/              # Named interface pública
│   ├── TenantAccessValidator.java
│   ├── TenantAccessInfo.java
│   ├── TenantContext.java
│   ├── SecurityConfig.java
│   └── package-info.java
├── authorization/
├── exception/
├── config/
├── internal/              # Implementação privada
│   ├── TenantFilter.java
│   └── JwtAuthenticationConverter.java
└── package-info.java      # Metadados do módulo
```

**b) Criar `package-info.java` do módulo:**

```java
/**
 * Módulo compartilhado (Shared Module)
 *
 * Infraestrutura transversal: segurança, autorização, exceções, configurações.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Shared Infrastructure"
)
package com.jetski.shared;
```

**c) Criar `package-info.java` da named interface:**

```java
/**
 * Security API - Public interfaces and DTOs
 *
 * Named interface of the 'shared' module.
 */
@org.springframework.modulith.NamedInterface("security")
package com.jetski.shared.security;
```

---

### 3. Criar Módulo `usuarios`

**a) Criar estrutura:**

```
src/main/java/com/jetski/usuarios/
├── api/                   # API pública
│   ├── UserTenantsController.java
│   └── dto/
├── domain/                # Entidades
│   ├── Usuario.java
│   └── Membro.java
├── internal/              # Implementação privada
│   ├── TenantAccessService.java
│   ├── UsuarioGlobalRoles.java
│   └── repository/
└── package-info.java
```

**b) Criar `package-info.java`:**

```java
/**
 * Módulo de Usuários e Membros
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Users and Members",
    allowedDependencies = "shared::security"  // Apenas security API
)
package com.jetski.usuarios;
```

---

### 4. Aplicar Dependency Inversion

**Problema:** `shared` depende de `TenantAccessService` em `usuarios` (ciclo!)

**Solução:** Criar interface em `shared`, implementar em `usuarios`

**a) Criar interface em `shared/security/`:**

```java
package com.jetski.shared.security;

public interface TenantAccessValidator {
    TenantAccessInfo validateAccess(UUID usuarioId, UUID tenantId);
}
```

**b) Implementar em `usuarios/internal/`:**

```java
package com.jetski.usuarios.internal;

import com.jetski.shared.security.TenantAccessValidator;
import com.jetski.shared.security.TenantAccessInfo;

@Service
public class TenantAccessService implements TenantAccessValidator {

    @Override
    public TenantAccessInfo validateAccess(UUID usuarioId, UUID tenantId) {
        // Implementação
    }
}
```

**c) Consumir interface em `shared/internal/`:**

```java
package com.jetski.shared.internal;

import com.jetski.shared.security.TenantAccessValidator;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private final TenantAccessValidator validator; // ✅ Interface

    public TenantFilter(TenantAccessValidator validator) {
        this.validator = validator;
    }
}
```

**Resultado:** Ciclo quebrado! ✅

---

### 5. Criar Testes de Arquitetura

**Arquivo:** `src/test/java/com/jetski/modulith/ModuleStructureTest.java`

```java
package com.jetski.modulith;

import com.jetski.JetskiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModuleStructureTest {

    @Test
    void shouldVerifyModularStructure() {
        ApplicationModules modules = ApplicationModules.of(JetskiApplication.class);
        modules.verify();  // ✅ Valida estrutura
    }

    @Test
    void shouldNotHaveCyclicDependencies() {
        ApplicationModules modules = ApplicationModules.of(JetskiApplication.class);
        modules.verify();  // ❌ Falha se houver ciclos
    }

    @Test
    void shouldGenerateModuleDocumentation() {
        ApplicationModules modules = ApplicationModules.of(JetskiApplication.class);

        new Documenter(modules)
            .writeDocumentation()
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
```

**Executar:**
```bash
mvn test -Dtest=ModuleStructureTest
```

---

### 6. Validar Migração

**Checklist de validação:**

- [ ] Compilação sem erros
- [ ] Todos os testes passando
- [ ] Sem dependências circulares (ModuleStructureTest)
- [ ] Documentação gerada em `target/spring-modulith-docs/`
- [ ] Aplicação inicia corretamente
- [ ] Endpoints funcionando

**Comandos:**

```bash
# 1. Compilar
mvn clean compile

# 2. Testes
mvn test

# 3. Verificar testes de arquitetura
mvn test -Dtest=ModuleStructureTest

# 4. Rodar aplicação
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# 5. Testar endpoint
curl http://localhost:8090/api/actuator/health
```

---

## 🎯 Padrões de Migração

### Padrão 1: Migrar Entidade de Domínio

**Antes:**
```
src/main/java/com/jetski/domain/Usuario.java
```

**Depois:**
```
src/main/java/com/jetski/usuarios/domain/Usuario.java
```

**Ações:**
1. Mover arquivo para novo package
2. Atualizar package declaration
3. Atualizar imports em outros arquivos
4. Rodar testes

### Padrão 2: Migrar Serviço

**Antes:**
```
src/main/java/com/jetski/service/UsuarioService.java
```

**Depois:**
```
src/main/java/com/jetski/usuarios/internal/UsuarioService.java
```

**Ações:**
1. Mover para `internal/` (implementação privada)
2. Atualizar package e imports
3. Se for usado por outros módulos, criar interface pública em `api/`

### Padrão 3: Migrar Controller

**Antes:**
```
src/main/java/com/jetski/controller/UserController.java
```

**Depois:**
```
src/main/java/com/jetski/usuarios/api/UserTenantsController.java
```

**Ações:**
1. Mover para `api/` (API pública do módulo)
2. Migrar DTOs para `api/dto/`
3. Atualizar package e imports

### Padrão 4: Migrar Repository

**Antes:**
```
src/main/java/com/jetski/repository/UsuarioRepository.java
```

**Depois:**
```
src/main/java/com/jetski/usuarios/internal/repository/UsuarioRepository.java
```

**Ações:**
1. Mover para `internal/repository/` (sempre privado)
2. Atualizar package e imports

---

## ⚠️ Problemas Comuns

### Problema 1: Dependência Circular Detectada

**Erro:**
```
Cycle detected: Slice shared → Slice usuarios → Slice shared
```

**Solução:**
Aplicar Dependency Inversion Principle (ver seção 4)

### Problema 2: Non-Exposed Type

**Erro:**
```
Module 'shared' depends on non-exposed type TenantAccessInfo
```

**Solução:**
Criar `package-info.java` com `@NamedInterface`:

```java
@org.springframework.modulith.NamedInterface("security")
package com.jetski.shared.security;
```

### Problema 3: Módulo Não Detectado

**Erro:**
Module não aparece em `ApplicationModules`

**Solução:**
1. Verificar que package está direto em `com.jetski.<modulo>`
2. Adicionar `package-info.java` com `@ApplicationModule`
3. Recompilar projeto

### Problema 4: Import Não Resolvido

**Erro:**
```
cannot find symbol: class TenantAccessService
```

**Solução:**
1. Atualizar import para novo package
2. Usar buscar/substituir global:
   ```bash
   sed -i 's/import com.jetski.service.TenantAccessService/import com.jetski.usuarios.internal.TenantAccessService/g' **/*.java
   ```

---

## 📊 Métricas de Sucesso

### Antes da Migração

- ❌ 3 ciclos de dependência
- ❌ 0 testes de arquitetura
- ⚠️ Acoplamento alto

### Depois da Migração

- ✅ 0 ciclos de dependência
- ✅ 6 testes de arquitetura
- ✅ Acoplamento baixo
- ✅ 89 testes passando
- ✅ Documentação automática

---

## 🚀 Próximos Módulos

### Template para Novo Módulo

```bash
# 1. Criar estrutura
mkdir -p src/main/java/com/jetski/{nome-modulo}/{api,domain,internal}

# 2. Criar package-info.java
cat > src/main/java/com/jetski/{nome-modulo}/package-info.java << 'EOF'
/**
 * Módulo de {Nome}
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "{Display Name}",
    allowedDependencies = "shared::security"
)
package com.jetski.{nome-modulo};
EOF

# 3. Validar
mvn test -Dtest=ModuleStructureTest
```

---

## 📚 Referências

- [Documentação Spring Modulith](https://docs.spring.io/spring-modulith/reference/)
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Comparação detalhada AS IS vs TO BE
- [README.md](./README.md) - Documentação principal do projeto

---

**Versão:** 1.0
**Última atualização:** 2025-10-18
