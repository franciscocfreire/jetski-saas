package com.jetski.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Conversor customizado de JWT para Authentication do Spring Security.
 *
 * Extrai:
 * - tenant_id (custom claim) → usado para isolamento multi-tenant
 * - roles (realm roles do Keycloak) → mapeadas para GrantedAuthority
 *
 * @author Jetski Team
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    /**
     * Converte um JWT token em um AbstractAuthenticationToken com authorities customizadas.
     *
     * @param jwt o token JWT validado
     * @return JwtAuthenticationToken com authorities incluindo roles do Keycloak
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    /**
     * Extrai authorities do JWT combinando:
     * 1. Default authorities (scopes) do Spring
     * 2. Realm roles do Keycloak (claim "roles")
     *
     * As roles são prefixadas com "ROLE_" para compatibilidade com @PreAuthorize("hasRole('...')")
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Authorities padrão do Spring (scopes)
        Collection<GrantedAuthority> defaultAuthorities = defaultGrantedAuthoritiesConverter.convert(jwt);

        // Realm roles do Keycloak (custom claim "roles")
        Collection<GrantedAuthority> keycloakRoles = extractKeycloakRealmRoles(jwt);

        // Combinar ambas
        return Stream.concat(
            defaultAuthorities != null ? defaultAuthorities.stream() : Stream.empty(),
            keycloakRoles.stream()
        ).collect(Collectors.toSet());
    }

    /**
     * Extrai realm roles do claim "roles" e converte para GrantedAuthority.
     * Prefixo "ROLE_" é adicionado para compatibilidade com Spring Security.
     *
     * Exemplo de JWT:
     * {
     *   "sub": "user123",
     *   "tenant_id": "abc-def-ghi",
     *   "roles": ["OPERADOR", "VENDEDOR"],
     *   ...
     * }
     *
     * Resultado: [ROLE_OPERADOR, ROLE_VENDEDOR]
     */
    private Collection<GrantedAuthority> extractKeycloakRealmRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");

        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toList());
    }

    /**
     * Extrai o tenant_id do JWT token.
     * Este método é utilizado pelo TenantFilter para validação.
     *
     * @param jwt o token JWT
     * @return o UUID do tenant ou null se não presente
     */
    public static String extractTenantId(Jwt jwt) {
        return jwt.getClaimAsString("tenant_id");
    }
}
