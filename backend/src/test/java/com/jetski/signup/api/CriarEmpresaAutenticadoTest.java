package com.jetski.signup.api;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.security.UserProvisioningService;
import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.domain.UsuarioIdentityProvider;
import com.jetski.usuarios.internal.repository.UsuarioIdentityProviderRepository;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastro de empresa por quem JÁ tem conta (POST /v1/tenants/create).
 *
 * O signup público recusa e-mail que já é identidade (409, de propósito: é anônimo).
 * Esta é a rota que sobra para essa pessoa — e ela precisa funcionar SEM
 * {@code X-Tenant-Id}, porque quem a chama tipicamente não é membro de empresa
 * nenhuma. Antes disso, o TenantFilter exigia o header e devolvia 400: o backoffice
 * mandava a pessoa "criar a empresa pelo dashboard" e o dashboard não tinha como.
 *
 * Cobertura: 201 sem header + vínculo ADMIN_TENANT criado, empresa nasce
 * PENDENTE_APROVACAO, ação fora do OPA (não há papel a consultar) e 401 sem token.
 */
@AutoConfigureMockMvc
@DisplayName("Cadastro de empresa por usuário autenticado")
class CriarEmpresaAutenticadoTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioIdentityProviderRepository identityProviderRepository;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private OPAAuthorizationService opaAuthorizationService;
    @MockBean private EmailService emailService;
    @MockBean private UserProvisioningService userProvisioningService;

    // Seed exclusivo desta classe (e-mail próprio evita colisão entre classes)
    private static final String EMAIL = "sem-empresa@criarempresatest.com";
    private static final String KC_SUB = "kc-sub-criar-empresa-test";

    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        Usuario usuario = usuarioRepository.findByEmail(EMAIL).orElseGet(() ->
            usuarioRepository.save(Usuario.builder()
                .email(EMAIL)
                .nome("Pessoa Sem Empresa")
                .ativo(true)
                .emailVerified(true)
                .build()));
        usuarioId = usuario.getId();

        if (identityProviderRepository.findByUsuarioIdAndProvider(usuarioId, "keycloak").isEmpty()) {
            identityProviderRepository.save(
                UsuarioIdentityProvider.link(usuario, "keycloak", KC_SUB));
        }
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
        return jwt().jwt(j -> j.subject(KC_SUB).claim("email", EMAIL));
    }

    private String corpo(String slug) {
        return """
            {"razaoSocial": "Locadora Sem Empresa Ltda", "slug": "%s", "cnpj": "12.345.678/0001-99"}
            """.formatted(slug);
    }

    @Test
    @DisplayName("cria a empresa SEM X-Tenant-Id, com o solicitante como ADMIN_TENANT")
    void criaEmpresaSemHeaderDeTenant() throws Exception {
        String slug = "sem-empresa-" + UUID.randomUUID().toString().substring(0, 8);

        String json = mockMvc.perform(post("/v1/tenants/create")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(slug)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value(slug))
            .andReturn().getResponse().getContentAsString();

        assertThat(json).contains("tenantId");

        UUID tenantId = jdbc.queryForObject(
            "SELECT id FROM tenant WHERE slug = ?", UUID.class, slug);

        // Nasce pendente: o gate de verdade é a aprovação da plataforma, não a rota.
        assertThat(jdbc.queryForObject(
            "SELECT status FROM tenant WHERE id = ?", String.class, tenantId))
            .isEqualTo("PENDENTE_APROVACAO");

        // O vínculo é explícito e sai do próprio JWT — nada de coincidência de e-mail.
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM membro WHERE tenant_id = ? AND usuario_id = ? AND 'ADMIN_TENANT' = ANY(papeis)",
            Integer.class, tenantId, usuarioId))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant_access WHERE tenant_id = ? AND usuario_id = ?",
            Integer.class, tenantId, usuarioId))
            .isEqualTo(1);

        // Sem papel a consultar: quem pede ainda não é membro de empresa nenhuma.
        verify(opaAuthorizationService, never()).authorize(any(OPAInput.class));

        // O vínculo no banco não basta: o @PreAuthorize dos controllers lê a role do
        // token, então o provedor precisa receber ADMIN_TENANT para a pessoa operar.
        verify(userProvisioningService).garantirRealmRole(KC_SUB, "ADMIN_TENANT");
    }

    @Test
    @DisplayName("slug já usado é recusado com 409 e não cria empresa órfã")
    void slugDuplicadoRecusado() throws Exception {
        String slug = "dup-empresa-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/v1/tenants/create")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(slug)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/tenants/create")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(slug)))
            .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant WHERE slug = ?", Integer.class, slug))
            .isEqualTo(1);
    }

    @Test
    @DisplayName("signup público recusa e-mail com conta e devolve o código que leva ao caminho autenticado")
    void signupPublicoDevolveCodigoDeContaExistente() throws Exception {
        String corpo = """
            {"razaoSocial": "Locadora Repetida Ltda", "slug": "repetida-%s",
             "adminEmail": "%s", "adminNome": "Pessoa Sem Empresa"}
            """.formatted(UUID.randomUUID().toString().substring(0, 8), EMAIL);

        // Sem o code, o frontend teria de casar a mensagem por string para saber que
        // pode oferecer "entrar e cadastrar" em vez de só mostrar o erro.
        mockMvc.perform(post("/v1/signup/tenant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.details.code").value("EMAIL_JA_CADASTRADO"));
    }

    @Test
    @DisplayName("sem token não passa — a rota é isenta de tenant, não de autenticação")
    void semTokenNaoPassa() throws Exception {
        mockMvc.perform(post("/v1/tenants/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo("anonimo-nao-cria")))
            .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant WHERE slug = 'anonimo-nao-cria'", Integer.class))
            .isZero();
    }
}
