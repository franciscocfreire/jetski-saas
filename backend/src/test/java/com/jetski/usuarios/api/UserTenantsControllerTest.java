package com.jetski.usuarios.api;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.usuarios.api.dto.UserTenantsResponse;
import com.jetski.usuarios.domain.Membro;
import com.jetski.usuarios.internal.TenantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UserTenantsController
 *
 * Tests:
 * - List tenants for normal user
 * - List tenants for platform admin (unrestricted)
 * - Empty tenant list
 * - Unauthorized access
 * - Multiple tenants with roles
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@AutoConfigureMockMvc
@DisplayName("UserTenantsController Tests")
class UserTenantsControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantAccessService tenantAccessService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID_1 = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID TENANT_ID_2 = UUID.fromString("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22");

    // ========================================================================
    // Happy Path Tests
    // ========================================================================

    @Test
    @DisplayName("Should list tenants for normal user with multiple memberships")
    void testListUserTenants_NormalUser_MultipleTenants() throws Exception {
        // Given: User with 2 tenant memberships
        List<Membro> membros = List.of(
            createMembro(TENANT_ID_1, USER_ID, "GERENTE", "OPERADOR"),
            createMembro(TENANT_ID_2, USER_ID, "ADMIN_TENANT")
        );

        when(tenantAccessService.countUserTenants(USER_ID)).thenReturn(2L);
        when(tenantAccessService.listUserTenants(USER_ID)).thenReturn(membros);

        // When/Then: GET /v1/user/tenants
        mockMvc.perform(get("/v1/user/tenants")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessType").value("LIMITED"))
            .andExpect(jsonPath("$.totalTenants").value(2))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.tenants").isArray())
            .andExpect(jsonPath("$.tenants.length()").value(2))
            .andExpect(jsonPath("$.tenants[0].tenantId").value(TENANT_ID_1.toString()))
            .andExpect(jsonPath("$.tenants[0].roles").isArray())
            .andExpect(jsonPath("$.tenants[0].roles[0]").value("GERENTE"))
            .andExpect(jsonPath("$.tenants[0].roles[1]").value("OPERADOR"))
            .andExpect(jsonPath("$.tenants[1].tenantId").value(TENANT_ID_2.toString()))
            .andExpect(jsonPath("$.tenants[1].roles[0]").value("ADMIN_TENANT"));
    }

    @Test
    @DisplayName("Should return empty list for user with no tenant memberships")
    void testListUserTenants_EmptyList() throws Exception {
        // Given: User with no tenants
        when(tenantAccessService.countUserTenants(USER_ID)).thenReturn(0L);
        when(tenantAccessService.listUserTenants(USER_ID)).thenReturn(List.of());

        // When/Then: GET /v1/user/tenants
        mockMvc.perform(get("/v1/user/tenants")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessType").value("LIMITED"))
            .andExpect(jsonPath("$.totalTenants").value(0))
            .andExpect(jsonPath("$.tenants").isEmpty());
    }

    @Test
    @DisplayName("Should return unrestricted response for platform admin")
    void testListUserTenants_PlatformAdmin_Unrestricted() throws Exception {
        // Given: Platform admin with unrestricted access
        when(tenantAccessService.countUserTenants(USER_ID)).thenReturn(-1L);

        // When/Then: GET /v1/user/tenants
        mockMvc.perform(get("/v1/user/tenants")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessType").value("UNRESTRICTED"))
            .andExpect(jsonPath("$.totalTenants").value(-1))
            .andExpect(jsonPath("$.message").value("Full platform access - use tenant search"))
            .andExpect(jsonPath("$.tenants").isEmpty());
    }

    @Test
    @DisplayName("Should list single tenant for user with one membership")
    void testListUserTenants_SingleTenant() throws Exception {
        // Given: User with 1 tenant
        List<Membro> membros = List.of(
            createMembro(TENANT_ID_1, USER_ID, "OPERADOR")
        );

        when(tenantAccessService.countUserTenants(USER_ID)).thenReturn(1L);
        when(tenantAccessService.listUserTenants(USER_ID)).thenReturn(membros);

        // When/Then: GET /v1/user/tenants
        mockMvc.perform(get("/v1/user/tenants")
                .with(jwt().jwt(jwt -> jwt.subject(USER_ID.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessType").value("LIMITED"))
            .andExpect(jsonPath("$.totalTenants").value(1))
            .andExpect(jsonPath("$.tenants.length()").value(1))
            .andExpect(jsonPath("$.tenants[0].tenantId").value(TENANT_ID_1.toString()))
            .andExpect(jsonPath("$.tenants[0].roles[0]").value("OPERADOR"));
    }

    // ========================================================================
    // Error Cases
    // ========================================================================

    @Test
    @DisplayName("Should return 401 for unauthenticated request")
    void testListUserTenants_Unauthorized() throws Exception {
        // When/Then: GET /v1/user/tenants without JWT
        mockMvc.perform(get("/v1/user/tenants"))
            .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private Membro createMembro(UUID tenantId, UUID usuarioId, String... papeis) {
        Membro membro = new Membro();
        membro.setId(1);
        membro.setTenantId(tenantId);
        membro.setUsuarioId(usuarioId);
        membro.setPapeis(papeis);
        membro.setAtivo(true);
        membro.setCreatedAt(Instant.now());
        membro.setUpdatedAt(Instant.now());
        return membro;
    }
}
