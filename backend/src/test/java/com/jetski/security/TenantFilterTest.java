package com.jetski.security;

import com.jetski.exception.InvalidTenantException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
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
        ReflectionTestUtils.setField(tenantFilter, "tenantHeaderName", "X-Tenant-Id");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
}
