package com.jetski.tenant.api;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração dos endpoints de branding (white-label) do tenant:
 * GET/PUT /v1/tenants/{id}/config/branding e POST/DELETE .../branding/logo.
 *
 * Estabelece o padrão de teste para os endpoints de config do tenant
 * (assinatura/documento/comissao não têm teste dedicado ainda).
 */
@AutoConfigureMockMvc
@DisplayName("TenantConfig Branding Tests")
class TenantConfigBrandingControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** PNG 1x1 válido. */
    private static final byte[] PNG_1PX = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        try {
            jdbcTemplate.execute("INSERT INTO usuario_identity_provider (usuario_id, provider, provider_user_id, linked_at) " +
                                 "VALUES ('11111111-1111-1111-1111-111111111111', 'keycloak', '11111111-1111-1111-1111-111111111111', NOW())");
        } catch (Exception ignored) {
            // já existe
        }

        when(opaAuthorizationService.authorize(any(OPAInput.class)))
            .thenReturn(OPADecision.builder().allow(true).tenantIsValid(true).build());

        // estado conhecido: só a cor primária do seed, sem logo
        jdbcTemplate.update(
            "UPDATE tenant SET branding = '{\"cor_primaria\": \"#0066CC\"}'::jsonb WHERE id = ?", TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
    @DisplayName("GET branding retorna cores do seed e logo nulo")
    void testGetBranding_Default() throws Exception {
        mockMvc.perform(get("/v1/tenants/{tenantId}/config/branding", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.corPrimaria").value("#0066CC"))
            .andExpect(jsonPath("$.logoDataUrl").isEmpty());
    }

    @Test
    @DisplayName("PUT branding atualiza e normaliza as cores")
    void testUpdateBranding_RoundTrip() throws Exception {
        mockMvc.perform(put("/v1/tenants/{tenantId}/config/branding", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"corPrimaria\": \"#1e4266\", \"corSecundaria\": \"#c9a24b\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.corPrimaria").value("#1E4266"))
            .andExpect(jsonPath("$.corSecundaria").value("#C9A24B"));

        mockMvc.perform(get("/v1/tenants/{tenantId}/config/branding", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.corPrimaria").value("#1E4266"));
    }

    @Test
    @DisplayName("PUT branding com nulos volta ao padrão Meu Jet")
    void testUpdateBranding_ResetToDefault() throws Exception {
        mockMvc.perform(put("/v1/tenants/{tenantId}/config/branding", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"corPrimaria\": null, \"corSecundaria\": null}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.corPrimaria").isEmpty())
            .andExpect(jsonPath("$.corSecundaria").isEmpty());
    }

    @Test
    @DisplayName("PUT branding rejeita hex inválido")
    void testUpdateBranding_InvalidHex() throws Exception {
        mockMvc.perform(put("/v1/tenants/{tenantId}/config/branding", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"corPrimaria\": \"azul\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT branding rejeita cor primária clara demais (contraste)")
    void testUpdateBranding_LowContrastRejected() throws Exception {
        mockMvc.perform(put("/v1/tenants/{tenantId}/config/branding", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"corPrimaria\": \"#FFEE99\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Upload de logo PNG retorna logoDataUrl; DELETE remove")
    void testLogoUploadAndDelete() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", PNG_1PX);

        mockMvc.perform(multipart("/v1/tenants/{tenantId}/config/branding/logo", TENANT_ID)
                .file(file)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logoDataUrl").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));

        mockMvc.perform(delete("/v1/tenants/{tenantId}/config/branding/logo", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logoDataUrl").isEmpty());
    }

    @Test
    @DisplayName("Upload de logo rejeita content-type não suportado")
    void testLogoUpload_UnsupportedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logo.svg", "image/svg+xml", "<svg/>".getBytes());

        mockMvc.perform(multipart("/v1/tenants/{tenantId}/config/branding/logo", TENANT_ID)
                .file(file)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("VENDEDOR não pode alterar branding (403)")
    void testUpdateBranding_ForbiddenForVendedor() throws Exception {
        mockMvc.perform(put("/v1/tenants/{tenantId}/config/branding", TENANT_ID)
                .header("X-Tenant-Id", TENANT_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"corPrimaria\": \"#1E4266\"}")
                .with(vendedor()))
            .andExpect(status().isForbidden());
    }
}
