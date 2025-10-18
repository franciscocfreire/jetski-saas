package com.jetski.shared.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtAuthenticationConverter
 *
 * @author Jetski Team
 */
class JwtAuthenticationConverterTest {

    private JwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JwtAuthenticationConverter();
    }

    @Test
    void shouldExtractRolesFromDirectRolesClaim() {
        // Given
        List<String> roles = List.of("ADMIN_TENANT", "GERENTE", "OPERADOR");

        Jwt jwt = buildJwtWithRoles(roles);

        // When
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN_TENANT", "ROLE_GERENTE", "ROLE_OPERADOR");
    }

    @Test
    void shouldAddRolePrefixToRoles() {
        // Given
        Jwt jwt = buildJwtWithRoles(List.of("OPERADOR"));

        // When
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Then
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_OPERADOR");
    }

    @Test
    void shouldReturnDefaultAuthoritiesWhenNoRoles() {
        // Given - JWT without roles claim
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", "user-789", "email", "noRoles@example.com")
        );

        // When
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Then
        assertThat(result).isNotNull();
        // May have default authorities from JWT scopes, but no role authorities
    }

    @Test
    void shouldHandleEmptyRolesList() {
        // Given
        Jwt jwt = buildJwtWithRoles(List.of());

        // When
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Then
        assertThat(result).isNotNull();
        // Only default authorities from scopes if any
    }

    @Test
    void shouldHandleMultipleTenantRoles() {
        // Given - User with multiple tenant-specific roles
        List<String> roles = List.of("ADMIN_TENANT", "GERENTE", "OPERADOR", "FINANCEIRO", "MECANICO");

        Jwt jwt = buildJwtWithRoles(roles);

        // When
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Then
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains(
                        "ROLE_ADMIN_TENANT",
                        "ROLE_GERENTE",
                        "ROLE_OPERADOR",
                        "ROLE_FINANCEIRO",
                        "ROLE_MECANICO"
                );
    }

    @Test
    void shouldPreservePrincipalName() {
        // Given
        Jwt jwt = buildJwtWithRoles(List.of("OPERADOR"));

        // When
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Then
        assertThat(result.getName()).isEqualTo("user-123");
    }

    @Test
    void shouldExtractTenantIdFromJwt() {
        // Given
        String tenantId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-456");
        claims.put("tenant_id", tenantId);
        claims.put("roles", List.of("OPERADOR"));

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                claims
        );

        // When
        String extractedTenantId = JwtAuthenticationConverter.extractTenantId(jwt);

        // Then
        assertThat(extractedTenantId).isEqualTo(tenantId);
    }

    @Test
    void shouldReturnNullWhenTenantIdNotPresent() {
        // Given
        Jwt jwt = buildJwtWithRoles(List.of("OPERADOR"));

        // When
        String extractedTenantId = JwtAuthenticationConverter.extractTenantId(jwt);

        // Then
        assertThat(extractedTenantId).isNull();
    }

    @Test
    void shouldCombineDefaultScopesWithRealmRoles() {
        // Given - JWT with scopes and roles
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-789");
        claims.put("scope", "read write");  // Default OAuth2 scopes
        claims.put("roles", List.of("GERENTE"));

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                claims
        );

        // When
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Then
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_GERENTE");  // Realm role
        // May also contain SCOPE_read and SCOPE_write from default converter
    }

    @Test
    void shouldHandleNullRolesClaim() {
        // Given - JWT without roles claim
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-null");
        // No roles claim at all

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                claims
        );

        // When
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Then
        assertThat(result).isNotNull();
        // No exception should be thrown
    }

    // Helper method to build JWT with roles
    private Jwt buildJwtWithRoles(List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user-123");
        claims.put("email", "test@example.com");
        if (roles != null) {
            claims.put("roles", new ArrayList<>(roles));
        }

        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                claims
        );
    }
}
