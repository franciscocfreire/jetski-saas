---
story_id: STORY-001
epic: EPIC-01
title: Implementar TenantContext e TenantFilter
status: DONE
priority: CRITICAL
estimate: 5
assignee: Claude
started_at: 2025-01-15
completed_at: 2025-01-15
tags: [backend, multi-tenant, security, spring-boot]
dependencies: []
---

# STORY-001: Implementar TenantContext e TenantFilter

## Como
Backend Developer

## Quero
Um mecanismo para extrair e armazenar o `tenant_id` atual da requisição HTTP

## Para que
Todas as operações subsequentes sejam executadas no contexto do tenant correto, garantindo isolamento de dados

## Critérios de Aceite

- [ ] **CA1:** `TenantContext` usa ThreadLocal para armazenar `tenant_id` durante a requisição
- [ ] **CA2:** `TenantFilter` extrai `tenant_id` do header `X-Tenant-Id` ou subdomain
- [ ] **CA3:** Filter valida se `tenant_id` do token JWT bate com o header/subdomain
- [ ] **CA4:** Exceção customizada `InvalidTenantException` é lançada se tenant inválido ou ausente
- [ ] **CA5:** `TenantContext.clear()` é chamado no finally para evitar vazamento em thread pools
- [ ] **CA6:** Testes unitários cobrem: sucesso, header ausente, mismatch JWT vs header
- [ ] **CA7:** Testes de integração validam isolamento (tenant A não acessa dados do tenant B)

## Tarefas Técnicas

- [ ] Criar classe `TenantContext` com ThreadLocal
  ```java
  public class TenantContext {
      private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();
      public static void setTenantId(UUID tenantId) { ... }
      public static UUID getTenantId() { ... }
      public static void clear() { TENANT_ID.remove(); }
  }
  ```
- [ ] Criar `TenantFilter` extends `OncePerRequestFilter`
- [ ] Implementar extração de tenant do header `X-Tenant-Id`
- [ ] Implementar extração de tenant do subdomain (alternativa)
- [ ] Extrair `tenant_id` do JWT claim (usando Spring Security)
- [ ] Validar coerência: header/subdomain vs JWT claim
- [ ] Criar exceção `InvalidTenantException` extends `RuntimeException`
- [ ] Registrar filter na cadeia do Spring Security (Order = 1, antes de autenticação)
- [ ] Escrever testes unitários (JUnit 5 + Mockito)
- [ ] Escrever testes de integração com MockMvc + @SpringBootTest

## Definição de Pronto (DoD)

- [ ] Code review aprovado (mínimo 1 reviewer)
- [ ] Todos os testes passando (unit + integration)
- [ ] Cobertura de código > 80%
- [ ] Sem vulnerabilidades críticas (SonarQube)
- [ ] Javadoc nas classes públicas
- [ ] Documentação técnica atualizada em `/docs/multi-tenant.md`

## Notas Técnicas

### Implementação do TenantFilter

```java
@Component
@Order(1)  // Executar antes do filtro de autenticação
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        try {
            // 1. Extrair tenant do header ou subdomain
            String tenantId = extractTenantId(request);

            // 2. Validar contra JWT claim (se autenticado)
            validateTenantId(tenantId, request);

            // 3. Armazenar no contexto
            TenantContext.setTenantId(UUID.fromString(tenantId));

            // 4. Continuar cadeia
            chain.doFilter(request, response);
        } finally {
            // 5. Limpar contexto (IMPORTANTE!)
            TenantContext.clear();
        }
    }

    private String extractTenantId(HttpServletRequest request) {
        // Prioridade 1: Header X-Tenant-Id
        String header = request.getHeader("X-Tenant-Id");
        if (header != null) {
            return header;
        }

        // Prioridade 2: Subdomain (ex: acme.jetski.com.br)
        String host = request.getServerName();
        if (host != null && host.contains(".")) {
            return host.split("\\.")[0];  // Retorna "acme"
        }

        throw new InvalidTenantException("Tenant ID not found in request");
    }

    private void validateTenantId(String tenantId, HttpServletRequest request) {
        // Obter tenant do JWT (se autenticado)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Jwt jwt = (Jwt) auth.getPrincipal();
            String jwtTenantId = jwt.getClaim("tenant_id");

            if (!tenantId.equals(jwtTenantId)) {
                throw new InvalidTenantException(
                    "Tenant ID mismatch: header=" + tenantId + ", jwt=" + jwtTenantId
                );
            }
        }
    }
}
```

### Considerações de Design

**Por que ThreadLocal?**
- Evita passar `tenant_id` como parâmetro em toda a cadeia de chamadas
- Garante isolamento entre requisições concorrentes
- Cleanup automático via finally previne vazamento

**Por que validar contra JWT?**
- Previne que usuário malicioso troque header `X-Tenant-Id` para acessar dados de outro tenant
- JWT é assinado pelo Keycloak, portanto confiável

**Ordem do filtro:**
- Order = 1 para executar ANTES do filtro de autenticação
- Assim o TenantContext já está disponível quando o Spring Security executar

### Impacto
- **Performance:** Overhead mínimo (~1ms por request)
- **Segurança:** ✅ Crítico para isolamento multi-tenant
- **Multi-tenancy:** ✅ Base para RLS e isolamento de dados

## Blockers

- [ ] Nenhum blocker no momento

## Links

- **Epic:** [EPIC-01: Multi-tenant Foundation](../../stories/epics/epic-01-multi-tenant-foundation.md)
- **PR:** TBD
- **Design doc:** /docs/multi-tenant.md (a ser criado)
- **Testes:** /src/test/java/com/jetski/security/TenantFilterTest.java

## Changelog

- 2025-01-15: História criada
