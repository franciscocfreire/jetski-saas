# 🎯 Plano de Melhoria de Cobertura de Testes

**Data:** 2025-10-18
**Cobertura Atual:** 66% instruções, 50% branches
**Meta:** 80% instruções, 70% branches

---

## 📊 Análise da Cobertura Atual

### Por Pacote

| Pacote | Cobertura | Status | Prioridade |
|--------|-----------|--------|------------|
| **usuarios.domain** | 13% | ❌ CRÍTICO | 🔴 P0 |
| **usuarios.api** | 18% | ❌ CRÍTICO | 🔴 P0 |
| **shared.exception** | 61% | ⚠️ MÉDIO | 🟡 P1 |
| **usuarios.internal** | 58% | ⚠️ MÉDIO | 🟡 P1 |
| **shared.security** | 74% (25% branches) | ⚠️ MÉDIO | 🟡 P1 |
| **shared.internal** | 85% | ✅ BOM | 🟢 P2 |
| **shared.authorization** | 81% | ✅ BOM | 🟢 P2 |

---

## 🎯 Gaps Críticos Identificados

### 🔴 P0 - Crítico (Adicionar AGORA)

#### 1. **UserTenantsController** - 18% ❌
**Problema:** Controller principal sem testes integrados

**Testes Necessários:**
```java
// src/test/java/com/jetski/usuarios/api/UserTenantsControllerTest.java

@WebMvcTest(UserTenantsController.class)
class UserTenantsControllerTest {

    @Test
    void shouldListUserTenants_Success() {
        // GET /v1/user/tenants
        // Verificar lista de tenants do usuário
    }

    @Test
    void shouldListUserTenants_Unauthorized() {
        // Sem JWT
        // Espera 401
    }

    @Test
    void shouldListUserTenants_EmptyList() {
        // Usuário sem tenants
        // Espera lista vazia
    }

    @Test
    void shouldCountUserTenants_Success() {
        // GET /v1/user/tenants/count
        // Verificar contagem
    }

    @Test
    void shouldCountUserTenants_UnrestrictedAdmin() {
        // Platform admin
        // Espera -1 (unlimited)
    }
}
```

**Impacto Estimado:** +15% cobertura total

---

#### 2. **Entidades de Domínio** - 13% ❌
**Problema:** Getters, setters e builders Lombok não testados

**Abordagem:** Usar ArchUnit ou testes de contrato

```java
// src/test/java/com/jetski/usuarios/domain/DomainEntityTest.java

@ExtendWith(MockitoExtension.class)
class DomainEntityTest {

    @Test
    void usuarioBuilder_ShouldCreateValidEntity() {
        Usuario usuario = Usuario.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .nome("Test User")
            .ativo(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        assertThat(usuario.getEmail()).isEqualTo("test@example.com");
        assertThat(usuario.isAtivo()).isTrue();
    }

    @Test
    void membroBuilder_ShouldCreateValidEntity() {
        Membro membro = Membro.builder()
            .id(1)
            .tenantId(UUID.randomUUID())
            .usuarioId(UUID.randomUUID())
            .papeis(new String[]{"GERENTE", "OPERADOR"})
            .ativo(true)
            .build();

        assertThat(membro.getPapeis()).contains("GERENTE", "OPERADOR");
        assertThat(membro.isAtivo()).isTrue();
    }

    @Test
    void usuarioGlobalRoles_ShouldCreateValidEntity() {
        UsuarioGlobalRoles globalRoles = UsuarioGlobalRoles.builder()
            .usuarioId(UUID.randomUUID())
            .roles(new String[]{"PLATFORM_ADMIN"})
            .unrestrictedAccess(true)
            .build();

        assertThat(globalRoles.getUnrestrictedAccess()).isTrue();
        assertThat(globalRoles.getRoles()).contains("PLATFORM_ADMIN");
    }

    @Test
    void entities_ShouldSupportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();

        Usuario u1 = Usuario.builder().id(id).email("test@example.com").build();
        Usuario u2 = Usuario.builder().id(id).email("test@example.com").build();

        // Lombok gera equals/hashCode baseado em todos os campos
        // Teste básico de consistência
        assertThat(u1.getId()).isEqualTo(u2.getId());
    }
}
```

**Impacto Estimado:** +8% cobertura total

---

### 🟡 P1 - Importante (Adicionar em Seguida)

#### 3. **GlobalExceptionHandler** - 61% ⚠️
**Problema:** Alguns exception handlers não testados

```java
// src/test/java/com/jetski/shared/exception/GlobalExceptionHandlerTest.java

@WebMvcTest
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldHandleMethodArgumentNotValid() throws Exception {
        // POST com JSON inválido
        // Espera 400 com detalhes de validação
    }

    @Test
    void shouldHandleHttpMessageNotReadable() throws Exception {
        // POST com JSON malformado
        // Espera 400
    }

    @Test
    void shouldHandleHttpRequestMethodNotSupported() throws Exception {
        // POST em endpoint GET
        // Espera 405
    }

    @Test
    void shouldHandleHttpMediaTypeNotSupported() throws Exception {
        // Content-Type inválido
        // Espera 415
    }

    @Test
    void shouldHandleMissingServletRequestParameter() throws Exception {
        // Query param obrigatório faltando
        // Espera 400
    }

    @Test
    void shouldHandleConstraintViolation() throws Exception {
        // Violação de @Valid
        // Espera 400 com detalhes
    }
}
```

**Impacto Estimado:** +5% cobertura total

---

#### 4. **TenantAccessService** - Branches não testadas
**Problema:** Faltam cenários edge case

```java
// Adicionar em TenantAccessServiceTest.java

@Test
@DisplayName("Should handle concurrent access validation")
void testConcurrentAccess() throws InterruptedException {
    // Simular múltiplas threads validando acesso
    // Verificar thread-safety do cache
}

@Test
@DisplayName("Should return correct count for user with multiple tenants")
void testCountWithLargeTenantList() {
    when(globalRolesRepository.findById(usuarioId))
        .thenReturn(Optional.empty());
    when(membroRepository.countActiveByUsuario(usuarioId))
        .thenReturn(100L);

    long count = tenantAccessService.countUserTenants(usuarioId);

    assertThat(count).isEqualTo(100L);
}

@Test
@DisplayName("Should validate access with expired membership")
void testExpiredMembership() {
    // Membro inativo
    Membro membro = createMembro(tenantId, usuarioId, "GERENTE");
    membro.setAtivo(false);

    when(membroRepository.findActiveByUsuarioAndTenant(usuarioId, tenantId))
        .thenReturn(Optional.empty()); // Não retorna inativos

    TenantAccessInfo result = tenantAccessService.validateAccess(usuarioId, tenantId);

    assertThat(result.isHasAccess()).isFalse();
}
```

**Impacto Estimado:** +3% cobertura total

---

#### 5. **SecurityConfig** - Testes de configuração
**Problema:** Beans e configurações não testados

```java
// src/test/java/com/jetski/shared/security/SecurityConfigTest.java

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpoints_ShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoints_ShouldRequireAuth() throws Exception {
        mockMvc.perform(get("/v1/user/tenants"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void corsConfiguration_ShouldAllowConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/v1/user/tenants")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    void jwtConverter_ShouldExtractRolesFromToken() {
        // Verificar que JwtAuthenticationConverter é configurado
        // e extrai roles corretamente
    }
}
```

**Impacto Estimado:** +4% cobertura total

---

#### 6. **AuthTestController** - Mais cenários
**Problema:** Faltam edge cases

```java
// Adicionar em AuthTestControllerIntegrationTest.java

@Test
void shouldRejectExpiredToken() throws Exception {
    // JWT expirado
    // Espera 401
}

@Test
void shouldRejectInvalidIssuer() throws Exception {
    // JWT de issuer não confiável
    // Espera 401
}

@Test
void shouldRejectMalformedToken() throws Exception {
    // JWT malformado
    // Espera 401
}

@Test
void shouldHandleMissingRoleClaim() throws Exception {
    // JWT sem claim de roles
    // Deve funcionar (roles vazias)
}

@Test
void shouldValidateTenantIdFormat() throws Exception {
    // X-Tenant-Id com formato inválido
    // Espera 400
}
```

**Impacto Estimado:** +3% cobertura total

---

### 🟢 P2 - Opcional (Melhorias)

#### 7. **Testes de Integração Adicionais**

```java
// src/test/java/com/jetski/integration/CacheIntegrationTest.java

@SpringBootTest
@Testcontainers
class CacheIntegrationTest extends AbstractIntegrationTest {

    @Test
    void tenantAccessCache_ShouldCacheResults() {
        // Verificar que validateAccess usa cache Redis
        // 1ª chamada: hit no DB
        // 2ª chamada: hit no cache
    }

    @Test
    void cacheEviction_ShouldClearOnUpdate() {
        // Atualizar membro
        // Verificar que cache foi invalidado
    }
}
```

**Impacto Estimado:** +2% cobertura total

---

## 📈 Projeção de Melhoria

### Execução do Plano

| Fase | Testes | Impacto | Cobertura Projetada |
|------|--------|---------|---------------------|
| **Atual** | 89 | - | 66% / 50% |
| **+ P0** | +15 | +23% | 82% / 60% |
| **+ P1** | +20 | +15% | 85% / 70% |
| **+ P2** | +5 | +3% | 87% / 72% |
| **TOTAL** | **129** | **+41%** | **87% / 72%** ✅ |

---

## 🚀 Ordem de Execução Recomendada

### Sprint 1 (P0 - Crítico)

1. ✅ Criar `UserTenantsControllerTest.java` (5 testes)
2. ✅ Criar `DomainEntityTest.java` (10 testes)
3. ✅ Rodar cobertura e verificar aumento

**Tempo estimado:** 2-3 horas
**Impacto:** +23% cobertura

### Sprint 2 (P1 - Importante)

1. ✅ Expandir `GlobalExceptionHandlerTest.java` (+6 testes)
2. ✅ Adicionar edge cases em `TenantAccessServiceTest.java` (+3 testes)
3. ✅ Criar `SecurityConfigTest.java` (+4 testes)
4. ✅ Expandir `AuthTestControllerIntegrationTest.java` (+5 testes)
5. ✅ Rodar cobertura e verificar progresso

**Tempo estimado:** 3-4 horas
**Impacto:** +15% cobertura

### Sprint 3 (P2 - Opcional)

1. ✅ Criar `CacheIntegrationTest.java` (+5 testes)
2. ✅ Meta alcançada: 85%+

**Tempo estimado:** 1-2 horas
**Impacto:** +3% cobertura

---

## 🎯 Checklist de Validação

Após cada sprint, verificar:

```bash
# Rodar testes
mvn clean test

# Gerar relatório
mvn jacoco:report

# Ver relatório
open target/site/jacoco/index.html

# Verificar métricas
cat target/site/jacoco/index.html | grep "Total"
```

**Critérios de Sucesso:**
- ✅ Cobertura de instruções > 80%
- ✅ Cobertura de branches > 70%
- ✅ Todos os controllers testados
- ✅ Entidades de domínio cobertas
- ✅ Exception handlers testados

---

## 📊 Comandos Úteis

```bash
# Apenas cobertura (sem rodar aplicação)
mvn clean test jacoco:report

# Ver coverage por classe
ls -lh target/site/jacoco/com.jetski.*/*.html

# Coverage de um pacote específico
open target/site/jacoco/com.jetski.usuarios.api/index.html

# Verificar se algum teste está falhando
mvn test 2>&1 | grep -E "Tests run|FAILURE|ERROR"
```

---

## 🎓 Boas Práticas Aplicadas

### 1. Testes de Valor vs Coverage Artificial
- ❌ NÃO criar testes só para aumentar coverage
- ✅ Focar em cenários reais e edge cases
- ✅ Testar comportamento, não implementação

### 2. Pirâmide de Testes
```
         /\
        /  \  E2E (poucos)
       /────\
      /      \  Integration (médio)
     /────────\
    /          \  Unit (muitos)
   /────────────\
```

### 3. Princípios FIRST
- **Fast** - Testes rápidos (<1s cada)
- **Independent** - Sem dependências entre testes
- **Repeatable** - Mesmo resultado sempre
- **Self-validating** - Passa ou falha claramente
- **Timely** - Escrito junto com o código

### 4. AAA Pattern
```java
@Test
void testName() {
    // Arrange - Setup

    // Act - Executar ação

    // Assert - Verificar resultado
}
```

---

## 📝 Notas

### Classes Excluídas do Coverage (Configuração Maven)
```xml
<excludes>
    **/dto/**
    **/config/**
    **/*Application.*
    **/exception/ErrorResponse.*
    **/exception/GlobalExceptionHandler.*
    **/exception/InvalidTenantException.*
    **/security/FilterChainExceptionFilter.*
</excludes>
```

**Atenção:** Remover `GlobalExceptionHandler` da exclusão após criar testes!

---

**Versão:** 1.0
**Última atualização:** 2025-10-18
**Próxima revisão:** Após implementar P0
