package com.jetski.usuarios.api;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.security.UserProvisioningService;
import com.jetski.shared.security.UserProvisioningService.PasswordCheck;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests: perfil self-service do staff (/v1/user/me).
 *
 * Cobertura:
 * - GET perfil (flags senhaGerenciavel/idpFederado), 404 sem cadastro, 401 sem auth
 * - PUT nome/telefone (persiste + espelha nome no Keycloak best-effort)
 * - PUT /senha: ok, senha atual errada, conta só-Google, provedor indisponível
 * - Avatar: upload (key global usuarios/{id}/...), formato/tamanho inválido, delete
 * - Authz: NENHUM request envia X-Tenant-Id e o OPA nunca é consultado
 *   (prova o trio TenantFilter/ActionExtractor/ABACAuthorizationInterceptor)
 */
@AutoConfigureMockMvc
@DisplayName("UserProfileController Tests")
class UserProfileControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioIdentityProviderRepository identityProviderRepository;

    @MockBean
    private UserProvisioningService userProvisioningService;

    @MockBean
    private OPAAuthorizationService opaAuthorizationService;

    // Seed exclusivo desta classe (e-mail próprio evita colisão entre classes)
    private static final String EMAIL = "perfil-staff@userprofiletest.com";
    private static final String KC_SUB = "kc-sub-perfil-staff-test";

    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        Usuario usuario = usuarioRepository.findByEmail(EMAIL).orElseGet(() ->
            usuarioRepository.save(Usuario.builder()
                .email(EMAIL)
                .nome("Perfil Original")
                .ativo(true)
                .emailVerified(true)
                .build()));
        // estado base determinístico entre testes
        usuario.setNome("Perfil Original");
        usuario.setTelefone(null);
        usuario.setAvatarKey(null);
        usuario.setAvatarContentType(null);
        usuarioRepository.save(usuario);
        usuarioId = usuario.getId();

        if (identityProviderRepository.findByUsuarioIdAndProvider(usuarioId, "keycloak").isEmpty()) {
            identityProviderRepository.save(
                UsuarioIdentityProvider.link(usuario, "keycloak", KC_SUB));
        }

        when(userProvisioningService.hasPasswordCredential(KC_SUB)).thenReturn(true);
        when(userProvisioningService.findFederatedIdentity(anyString(), anyString())).thenReturn(null);
        when(userProvisioningService.updateUserName(anyString(), anyString())).thenReturn(true);
        when(userProvisioningService.resetPassword(anyString(), anyString())).thenReturn(true);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
        return jwt().jwt(j -> j.subject(KC_SUB).claim("email", EMAIL));
    }

    // ========================================================================
    // GET /v1/user/me
    // ========================================================================

    @Test
    @DisplayName("GET perfil retorna dados + flags — sem X-Tenant-Id e sem consultar OPA")
    void testObterPerfil() throws Exception {
        mockMvc.perform(get("/v1/user/me").with(auth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(usuarioId.toString()))
            .andExpect(jsonPath("$.nome").value("Perfil Original"))
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.senhaGerenciavel").value(true))
            .andExpect(jsonPath("$.idpFederado").value(false))
            .andExpect(jsonPath("$.avatarDataUrl").doesNotExist());

        // Trio de authz: /v1/user/me passa FORA do OPA (escopo = próprio sub)
        verify(opaAuthorizationService, never()).authorize(any(OPAInput.class));
    }

    @Test
    @DisplayName("Conta vinculada ao Google aparece com idpFederado=true")
    void testObterPerfil_IdpFederado() throws Exception {
        when(userProvisioningService.findFederatedIdentity(eq(KC_SUB), eq("google")))
            .thenReturn(new com.jetski.shared.security.FederatedIdentity("google", "g-123", EMAIL));
        when(userProvisioningService.hasPasswordCredential(KC_SUB)).thenReturn(false);

        mockMvc.perform(get("/v1/user/me").with(auth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.senhaGerenciavel").value(false))
            .andExpect(jsonPath("$.idpFederado").value(true));
    }

    @Test
    @DisplayName("JWT válido sem cadastro staff → 404")
    void testObterPerfil_SemCadastro() throws Exception {
        mockMvc.perform(get("/v1/user/me")
                .with(jwt().jwt(j -> j.subject("sub-inexistente")
                    .claim("email", "nao-existe@userprofiletest.com"))))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sem autenticação → 401")
    void testObterPerfil_SemAuth() throws Exception {
        mockMvc.perform(get("/v1/user/me"))
            .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    // PUT /v1/user/me
    // ========================================================================

    @Test
    @DisplayName("PUT atualiza nome/telefone no banco e espelha nome no Keycloak")
    void testAtualizarPerfil() throws Exception {
        mockMvc.perform(put("/v1/user/me").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Novo Nome Completo\",\"telefone\":\"(11) 98888-7777\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Novo Nome Completo"))
            .andExpect(jsonPath("$.telefone").value("(11) 98888-7777"));

        Usuario atualizado = usuarioRepository.findById(usuarioId).orElseThrow();
        assertThat(atualizado.getNome()).isEqualTo("Novo Nome Completo");
        assertThat(atualizado.getTelefone()).isEqualTo("(11) 98888-7777");
        verify(userProvisioningService).updateUserName(KC_SUB, "Novo Nome Completo");
    }

    @Test
    @DisplayName("Keycloak fora do ar no espelho do nome → ainda 200 (best-effort)")
    void testAtualizarPerfil_KeycloakForaDoAr() throws Exception {
        when(userProvisioningService.updateUserName(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(put("/v1/user/me").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Nome Sem Espelho\",\"telefone\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Nome Sem Espelho"));
    }

    @Test
    @DisplayName("Nome curto demais → 400")
    void testAtualizarPerfil_NomeInvalido() throws Exception {
        mockMvc.perform(put("/v1/user/me").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"ab\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Telefone com caracteres inválidos → 400")
    void testAtualizarPerfil_TelefoneInvalido() throws Exception {
        mockMvc.perform(put("/v1/user/me").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Nome Valido\",\"telefone\":\"abc-def\"}"))
            .andExpect(status().isBadRequest());
    }

    // ========================================================================
    // PUT /v1/user/me/senha
    // ========================================================================

    @Test
    @DisplayName("Troca de senha com senha atual válida → 204")
    void testTrocarSenha() throws Exception {
        when(userProvisioningService.validatePassword(EMAIL, "senha-atual"))
            .thenReturn(PasswordCheck.VALID);

        mockMvc.perform(put("/v1/user/me/senha").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"senha-atual\",\"novaSenha\":\"nova-senha-123\"}"))
            .andExpect(status().isNoContent());

        verify(userProvisioningService).resetPassword(KC_SUB, "nova-senha-123");
        verify(opaAuthorizationService, never()).authorize(any(OPAInput.class));
    }

    @Test
    @DisplayName("Senha atual incorreta → 400 e nunca chama resetPassword")
    void testTrocarSenha_AtualIncorreta() throws Exception {
        when(userProvisioningService.validatePassword(anyString(), anyString()))
            .thenReturn(PasswordCheck.INVALID);

        mockMvc.perform(put("/v1/user/me/senha").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"errada\",\"novaSenha\":\"nova-senha-123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Senha atual incorreta"));

        verify(userProvisioningService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("Conta só-Google (sem credencial de senha) → 400 sem validar nem resetar")
    void testTrocarSenha_ContaGoogle() throws Exception {
        when(userProvisioningService.hasPasswordCredential(KC_SUB)).thenReturn(false);

        mockMvc.perform(put("/v1/user/me/senha").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"qualquer\",\"novaSenha\":\"nova-senha-123\"}"))
            .andExpect(status().isBadRequest());

        verify(userProvisioningService, never()).validatePassword(anyString(), anyString());
        verify(userProvisioningService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("Provedor indisponível na validação → 500 (nunca 'senha errada')")
    void testTrocarSenha_ProvedorIndisponivel() throws Exception {
        when(userProvisioningService.validatePassword(anyString(), anyString()))
            .thenReturn(PasswordCheck.UNAVAILABLE);

        mockMvc.perform(put("/v1/user/me/senha").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"senha-atual\",\"novaSenha\":\"nova-senha-123\"}"))
            .andExpect(status().isInternalServerError());

        verify(userProvisioningService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("Nova senha curta demais → 400 (bean validation)")
    void testTrocarSenha_NovaSenhaCurta() throws Exception {
        mockMvc.perform(put("/v1/user/me/senha").with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"senha-atual\",\"novaSenha\":\"curta\"}"))
            .andExpect(status().isBadRequest());

        verify(userProvisioningService, never()).resetPassword(anyString(), anyString());
    }

    // ========================================================================
    // Avatar
    // ========================================================================

    @Test
    @DisplayName("Upload de avatar grava key global usuarios/{id}/... e devolve data URL")
    void testUploadAvatar() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

        mockMvc.perform(multipart("/v1/user/me/avatar")
                .file(new MockMultipartFile("file", "avatar.png", "image/png", png))
                .with(auth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarDataUrl").isNotEmpty());

        Usuario atualizado = usuarioRepository.findById(usuarioId).orElseThrow();
        assertThat(atualizado.getAvatarKey()).isEqualTo("usuarios/" + usuarioId + "/avatar.png");
        assertThat(atualizado.getAvatarContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Avatar com content-type não suportado → 400")
    void testUploadAvatar_FormatoInvalido() throws Exception {
        mockMvc.perform(multipart("/v1/user/me/avatar")
                .file(new MockMultipartFile("file", "avatar.gif", "image/gif", new byte[]{1, 2}))
                .with(auth()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Avatar acima de 512 KB → 400")
    void testUploadAvatar_MuitoGrande() throws Exception {
        byte[] grande = new byte[513 * 1024];

        mockMvc.perform(multipart("/v1/user/me/avatar")
                .file(new MockMultipartFile("file", "avatar.png", "image/png", grande))
                .with(auth()))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE avatar limpa key e data URL")
    void testRemoverAvatar() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 9, 9};
        mockMvc.perform(multipart("/v1/user/me/avatar")
                .file(new MockMultipartFile("file", "avatar.png", "image/png", png))
                .with(auth()))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/user/me/avatar").with(auth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarDataUrl").doesNotExist());

        Usuario atualizado = usuarioRepository.findById(usuarioId).orElseThrow();
        assertThat(atualizado.getAvatarKey()).isNull();
        assertThat(atualizado.getAvatarContentType()).isNull();
    }
}
