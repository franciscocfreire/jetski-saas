package com.jetski.shared.authorization;

import com.jetski.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regressão de segurança: as rotas /v1/platform/** têm barreira em Java, além do OPA.
 *
 * <p>Antes deste interceptor, o único gate era a política OPA — uma regra .rego errada
 * abriria reset/exclusão de empresa para qualquer usuário autenticado.
 */
@DisplayName("PlatformScopeInterceptor")
class PlatformScopeInterceptorTest {

    private PlatformScopeInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new PlatformScopeInterceptor();
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/platform/tenants/"
            + "123e4567-e89b-12d3-a456-426614174000/reset");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String... papeis) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(papeis)
            .map(p -> new SimpleGrantedAuthority("ROLE_" + p))
            .toList();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("usuario-teste", "n/a", authorities));
    }

    @Test
    @DisplayName("Operador de plataforma passa")
    void permiteOperadorDePlataforma() {
        autenticar("PLATFORM_ADMIN");
        TenantContext.setUserRoles(List.of("PLATFORM_ADMIN"));
        TenantContext.setUnrestricted(true);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("Papel de plataforma NÃO-admin também passa a barreira — quem decide a ação é o OPA")
    void permiteOperadorNaoAdmin() {
        autenticar("PLATFORM_LEITURA");
        TenantContext.setUserRoles(List.of("PLATFORM_LEITURA"));
        TenantContext.setUnrestricted(true);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("unrestricted_access sem papel de plataforma é negado (alcance != poder)")
    void negaAlcanceSemPapel() {
        // Estado possível numa base pré-F2 que não rodou a V054: acesso irrestrito
        // herdado, sem papel explícito. Sem papel não há poder.
        autenticar("ADMIN_TENANT");
        TenantContext.setUserRoles(List.of("ADMIN_TENANT"));
        TenantContext.setUnrestricted(true);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ADMIN_TENANT é negado — admin de empresa não opera a plataforma")
    void negaAdminDeEmpresa() {
        autenticar("ADMIN_TENANT");
        TenantContext.setUnrestricted(false);
        TenantContext.setUserRoles(List.of("ADMIN_TENANT"));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("operadores de plataforma");
    }

    @Test
    @DisplayName("Usuário autenticado sem contexto de plataforma é negado")
    void negaContextoVazio() {
        autenticar("OPERADOR");
        // TenantContext sem unrestricted — é o estado de quem não é operador de plataforma

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Request anônimo passa adiante — 401 é responsabilidade do SecurityConfig")
    void delegaAnonimoParaSecurityConfig() {
        SecurityContextHolder.getContext().setAuthentication(
            new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("Sem autenticação nenhuma passa adiante — 401 é do SecurityConfig")
    void delegaSemAutenticacao() {
        SecurityContextHolder.clearContext();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}
