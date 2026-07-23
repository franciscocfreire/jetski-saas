package com.jetski.tenant.api;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração de GET /v1/tenants/{id}/config/permissions-matrix
 * (matriz papel × permissões read-only, documento role_permissions via OPA).
 */
@AutoConfigureMockMvc
@DisplayName("TenantConfig Permissions Matrix Tests")
class TenantConfigPermissionsMatrixControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        try {
            jdbcTemplate.execute("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) " +
                                 "VALUES ('11111111-1111-1111-1111-111111111111', 'keycloak', '11111111-1111-1111-1111-111111111111', NOW())");
        } catch (Exception ignored) {
            // já existe
        }

        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());
        when(opaAuthorizationService.getRolePermissionsMatrix())
            .thenReturn(Map.of(
                "ADMIN_TENANT", List.of("*"),
                "GERENTE", List.of("config:*", "reserva:*"),
                "OPERADOR", List.of("locacao:list")));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN_TENANT"));
    }

    private RequestPostProcessor vendedor() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_VENDEDOR"));
    }

    @Test
    @DisplayName("GET permissions-matrix retorna o mapa papel → permissões")
    void testGetMatrix_Admin() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/config/permissions-matrix", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles.ADMIN_TENANT[0]").value("*"))
            .andExpect(jsonPath("$.roles.GERENTE").isArray())
            .andExpect(jsonPath("$.roles.OPERADOR[0]").value("locacao:list"));
    }

    @Test
    @DisplayName("VENDEDOR não acessa a matriz (403 via @PreAuthorize)")
    void testGetMatrix_ForbiddenForVendedor() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/config/permissions-matrix", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(vendedor()))
            .andExpect(status().isForbidden());
    }
}
