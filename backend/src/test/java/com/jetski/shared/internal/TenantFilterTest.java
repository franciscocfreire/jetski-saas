package com.jetski.shared.internal;

import com.jetski.shared.exception.InvalidTenantException;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.internal.TenantAccessService;
import com.jetski.shared.security.TenantAccessInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantFilter
 *
 * @author Jetski Team
 */
@ExtendWith(MockitoExtension.class)
class TenantFilterTest {

    @Mock
    private TenantAccessService tenantAccessService;

    @InjectMocks
    private TenantFilter tenantFilter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExtractTenantIdFromHeader() throws ServletException, IOException {
        // Given
        UUID tenantId = UUID.randomUUID();
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.setRequestURI("/api/v1/modelos");

        // When
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        // Note: TenantContext is cleared after filter, so we can't assert here
    }

    @Test
    void shouldThrowExceptionWhenTenantIdMissing() {
        // Given
        request.setRequestURI("/api/v1/modelos");
        // No X-Tenant-Id header

        // When / Then
        assertThatThrownBy(() ->
                tenantFilter.doFilterInternal(request, response, filterChain)
        )
                .isInstanceOf(InvalidTenantException.class)
                .hasMessageContaining("Tenant ID not found");

        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldThrowExceptionWhenTenantIdInvalid() {
        // Given
        request.addHeader("X-Tenant-Id", "invalid-uuid");
        request.setRequestURI("/api/v1/modelos");

        // When / Then
        assertThatThrownBy(() ->
                tenantFilter.doFilterInternal(request, response, filterChain)
        )
                .isInstanceOf(InvalidTenantException.class)
                .hasMessageContaining("Invalid tenant ID format");

        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldAllowPublicEndpoints() throws ServletException, IOException {
        // Given
        request.setRequestURI("/api/actuator/health");
        // No X-Tenant-Id header

        // When
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void shouldAllowSwaggerEndpoints() throws ServletException, IOException {
        // Given
        request.setRequestURI("/api/swagger-ui/index.html");

        // When
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAllowApiDocsEndpoints() throws ServletException, IOException {
        // Given
        request.setRequestURI("/api/v3/api-docs");

        // When
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldClearTenantContextAfterRequest() throws ServletException, IOException {
        // Given
        UUID tenantId = UUID.randomUUID();
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.setRequestURI("/api/v1/modelos");

        // When
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then
        // TenantContext should be cleared after filter execution
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void shouldClearTenantContextEvenOnException() throws Exception {
        // Given
        UUID tenantId = UUID.randomUUID();
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.setRequestURI("/api/v1/modelos");

        // Mock filterChain to throw exception
        doThrow(new RuntimeException("Test exception"))
                .when(filterChain)
                .doFilter(request, response);

        // When
        try {
            tenantFilter.doFilterInternal(request, response, filterChain);
        } catch (Exception e) {
            // Expected
        }

        // Then
        // TenantContext should be cleared even when exception occurs
        assertThat(TenantContext.getTenantId()).isNull();
    }

    // ========== Database Access Validation Tests ==========

    @Test
    void shouldAllowRequestWhenUserHasAccessToTenant() throws ServletException, IOException {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.setRequestURI("/api/v1/modelos");

        // Mock authenticated JWT
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(usuarioId.toString());

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // Mock database validation - user HAS access
        TenantAccessInfo accessInfo = TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("GERENTE", "OPERADOR"))
                .unrestricted(false)
                .build();
        when(tenantAccessService.validateAccess(usuarioId, tenantId)).thenReturn(accessInfo);

        // When
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(tenantAccessService).validateAccess(usuarioId, tenantId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldDenyAccessWhenUserHasNoAccessToTenant() {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.setRequestURI("/api/v1/modelos");

        // Mock authenticated JWT
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(usuarioId.toString());

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // Mock database validation - user DOES NOT have access
        TenantAccessInfo accessInfo = TenantAccessInfo.builder()
                .hasAccess(false)
                .reason("User is not a member of this tenant")
                .build();
        when(tenantAccessService.validateAccess(usuarioId, tenantId)).thenReturn(accessInfo);

        // When / Then
        assertThatThrownBy(() ->
                tenantFilter.doFilterInternal(request, response, filterChain)
        )
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No access to tenant");

        verify(tenantAccessService).validateAccess(usuarioId, tenantId);
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldAllowRequestWhenUserHasUnrestrictedAccess() throws ServletException, IOException {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.setRequestURI("/api/v1/modelos");

        // Mock authenticated JWT (platform admin)
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(usuarioId.toString());

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // Mock database validation - user has UNRESTRICTED access (platform admin)
        TenantAccessInfo accessInfo = TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(List.of("PLATFORM_ADMIN"))
                .unrestricted(true)
                .build();
        when(tenantAccessService.validateAccess(usuarioId, tenantId)).thenReturn(accessInfo);

        // When
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(tenantAccessService).validateAccess(usuarioId, tenantId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSkipJwtValidationWhenNotAuthenticated() throws ServletException, IOException {
        // Given
        UUID tenantId = UUID.randomUUID();
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.setRequestURI("/api/v1/modelos");

        // Mock unauthenticated request
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // When
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        // SecurityContextHolder cleanup
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipJwtValidationWhenAnonymousUser() throws ServletException, IOException {
        // Given
        UUID tenantId = UUID.randomUUID();
        request.addHeader("X-Tenant-Id", tenantId.toString());
        request.setRequestURI("/api/v1/modelos");

        // Mock anonymous user
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // When
        tenantFilter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        // SecurityContextHolder cleanup
        SecurityContextHolder.clearContext();
    }
}
