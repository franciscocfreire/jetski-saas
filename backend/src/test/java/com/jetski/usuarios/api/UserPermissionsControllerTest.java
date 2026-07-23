package com.jetski.usuarios.api;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantAccessInfo;
import com.jetski.usuarios.internal.TenantAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração de GET /v1/user/permissions (permissões efetivas no
 * tenant do X-Tenant-Id, fonte rbac.rego via OPA).
 *
 * A ação user:permissions é pulada no ABACAuthorizationInterceptor (o
 * TenantFilter valida o vínculo e resolve os roles); o OPA entra apenas via
 * getUserPermissions, mockado aqui como nos demais testes de integração.
 */
@AutoConfigureMockMvc
@DisplayName("UserPermissionsController Tests")
class UserPermissionsControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    @MockBean
    private TenantAccessService tenantAccessService;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private RequestPostProcessor gerente() {
        return jwt().jwt(j -> j.subject(USER_ID.toString()).claim("tenant_id", TENANT_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_GERENTE"));
    }

    private void mockAccess(List<String> roles, boolean unrestricted) {
        when(tenantAccessService.validateAccess(any(String.class), any(String.class), any(UUID.class)))
            .thenReturn(TenantAccessInfo.builder()
                .hasAccess(true)
                .roles(roles)
                .unrestricted(unrestricted)
                .build());
    }

    @Test
    @DisplayName("200 com roles do membro e permissões cruas do OPA; ABAC não é chamado")
    void testGetPermissions_Member() throws Exception {
        mockAccess(List.of("GERENTE"), false);
        when(opaAuthorizationService.getUserPermissions(List.of("GERENTE")))
            .thenReturn(List.of("config:*", "reserva:*", "member:list"));

        mockMvc.perform(get("/v1/user/permissions")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(gerente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
            .andExpect(jsonPath("$.roles[0]").value("GERENTE"))
            .andExpect(jsonPath("$.permissions").isArray())
            .andExpect(jsonPath("$.permissions[0]").value("config:*"))
            .andExpect(jsonPath("$.unrestricted").value(false));

        // user:permissions é público para o ABAC — decisão completa nunca é avaliada
        verify(opaAuthorizationService, never()).authorize(any(OPAInput.class));
    }

    @Test
    @DisplayName("Super admin (unrestricted) recebe [\"*\"] sem consultar o OPA")
    void testGetPermissions_Unrestricted() throws Exception {
        mockAccess(List.of(), true);

        mockMvc.perform(get("/v1/user/permissions")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(gerente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[0]").value("*"))
            .andExpect(jsonPath("$.unrestricted").value(true));

        verify(opaAuthorizationService, never()).getUserPermissions(any());
    }

    @Test
    @DisplayName("Sem X-Tenant-Id → 4xx (tenant é obrigatório fora de /v1/user/me)")
    void testGetPermissions_MissingTenant() throws Exception {
        mockMvc.perform(get("/v1/user/permissions").with(gerente()))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("OPA indisponível → 200 com lista vazia (fail-safe, nunca 500)")
    void testGetPermissions_OpaDown() throws Exception {
        mockAccess(List.of("OPERADOR"), false);
        when(opaAuthorizationService.getUserPermissions(any())).thenReturn(List.of());

        mockMvc.perform(get("/v1/user/permissions")
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(gerente()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions").isEmpty());
    }
}
