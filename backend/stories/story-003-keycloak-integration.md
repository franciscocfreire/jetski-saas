---
story_id: STORY-003
epic: EPIC-01
title: Integrar Spring Security com Keycloak (OIDC + PKCE)
status: TODO
priority: CRITICAL
estimate: 5
assignee: Unassigned
started_at: null
completed_at: null
tags: [backend, security, keycloak, spring-security, oauth]
dependencies: []
---

# STORY-003: Integrar Spring Security com Keycloak (OIDC + PKCE)

## Como
Backend Developer

## Quero
Autenticação e autorização via Keycloak com validação de JWT e extração do claim `tenant_id`

## Para que
Usuários possam fazer login no sistema e ter suas requisições autorizadas com base em roles por tenant

## Critérios de Aceite

- [ ] **CA1:** Spring Security valida JWT emitido pelo Keycloak
- [ ] **CA2:** Claim `tenant_id` é extraído do JWT e disponibilizado
- [ ] **CA3:** Roles do usuário são extraídas e mapeadas para authorities do Spring
- [ ] **CA4:** Endpoints protegidos exigem autenticação (retorna 401 se não autenticado)
- [ ] **CA5:** Endpoints com `@PreAuthorize` validam roles corretamente
- [ ] **CA6:** Testes de integração validam fluxo completo de autenticação

## Tarefas Técnicas

- [ ] Adicionar dependências: `spring-boot-starter-oauth2-resource-server`
- [ ] Configurar `application.yml` com issuer-uri do Keycloak
- [ ] Criar `SecurityConfig` com `SecurityFilterChain`
- [ ] Configurar JWT decoder com validação de assinatura
- [ ] Criar `JwtAuthenticationConverter` para extrair roles
- [ ] Criar `TenantClaimExtractor` para extrair `tenant_id`
- [ ] Proteger endpoints REST: `/api/**` requer autenticação
- [ ] Permitir acesso público a `/actuator/health`
- [ ] Testes: mock JWT com claim `tenant_id`, validar extração

## Definição de Pronto (DoD)

- [ ] Code review aprovado
- [ ] Testes de integração com JWT mockado passando
- [ ] Documentação de autenticação atualizada

## Notas Técnicas

### application.yml

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/jetski-saas
          jwk-set-uri: http://localhost:8080/realms/jetski-saas/protocol/openid-connect/certs
```

### SecurityConfig

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter
                = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
```

## Blockers

- [ ] Keycloak precisa estar rodando (Docker Compose)

## Links

- **Epic:** [EPIC-01](../../stories/epics/epic-01-multi-tenant-foundation.md)

## Changelog

- 2025-01-15: História criada
