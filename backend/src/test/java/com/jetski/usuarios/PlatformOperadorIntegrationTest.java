package com.jetski.usuarios;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.internal.PlatformOperadorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gestão de operadores da plataforma (F2).
 *
 * <p>O que estes testes travam é o cenário caro: uma revogação que deixa a plataforma
 * sem nenhum administrador. O conserto seria SQL manual em produção — exatamente o que
 * esta tela veio eliminar.
 */
@DisplayName("Operadores da plataforma (F2)")
class PlatformOperadorIntegrationTest extends AbstractIntegrationTest {

    private static final UUID ADMIN_A = UUID.fromString("9f000000-0000-0000-0000-00000000f2a1");
    private static final UUID ADMIN_B = UUID.fromString("9f000000-0000-0000-0000-00000000f2b2");
    private static final UUID COMUM = UUID.fromString("9f000000-0000-0000-0000-00000000f2c3");

    @Autowired PlatformOperadorService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        limpar();
        criarUsuario(ADMIN_A, "f2-admin-a@teste.local", "Admin A");
        criarUsuario(ADMIN_B, "f2-admin-b@teste.local", "Admin B");
        criarUsuario(COMUM, "f2-comum@teste.local", "Sem acesso");
        conceder(ADMIN_A, "PLATFORM_ADMIN");
        // Actor padrão dos testes: ADMIN_A (quem executa as mudanças)
        TenantContext.setUsuarioId(ADMIN_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        limpar();
    }

    private void limpar() {
        jdbc.update("DELETE FROM usuario_global_roles WHERE usuario_id IN (?,?,?)",
            ADMIN_A, ADMIN_B, COMUM);
        jdbc.update("DELETE FROM usuario_identity_provider WHERE usuario_id IN (?,?,?)",
            ADMIN_A, ADMIN_B, COMUM);
        jdbc.update("DELETE FROM usuario WHERE id IN (?,?,?)", ADMIN_A, ADMIN_B, COMUM);
    }

    private void criarUsuario(UUID id, String email, String nome) {
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) VALUES (?,?,?,TRUE)",
            id, email, nome);
    }

    private void conceder(UUID id, String papel) {
        jdbc.update("INSERT INTO usuario_global_roles "
            + "(usuario_id, roles, unrestricted_access, created_at, updated_at) "
            + "VALUES (?, ARRAY[?]::text[], TRUE, now(), now()) "
            + "ON CONFLICT (usuario_id) DO UPDATE SET roles = EXCLUDED.roles", id, papel);
    }

    @Test
    @DisplayName("Conceder exige conta existente — não cria usuário a partir de e-mail digitado")
    void concederExigeContaExistente() {
        assertThatThrownBy(() ->
            service.conceder("nao-existe@teste.local", List.of("PLATFORM_SUPORTE")))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("cadastrar");
    }

    @Test
    @DisplayName("Papel desconhecido é erro de negócio, não silêncio")
    void papelDesconhecidoFalha() {
        assertThatThrownBy(() ->
            service.conceder("f2-comum@teste.local", List.of("PLATFORM_ROOT")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("desconhecido");
    }

    @Test
    @DisplayName("Conceder papel liga o ALCANCE junto (sem unrestricted o TenantFilter barraria)")
    void concederLigaUnrestricted() {
        service.conceder("f2-comum@teste.local", List.of("PLATFORM_FINANCEIRO"));

        Boolean irrestrito = jdbc.queryForObject(
            "SELECT unrestricted_access FROM usuario_global_roles WHERE usuario_id = ?",
            Boolean.class, COMUM);
        assertThat(irrestrito).isTrue();
        assertThat(service.listar())
            .anyMatch(o -> o.usuarioId().equals(COMUM)
                && o.papeis().equals(List.of("PLATFORM_FINANCEIRO")));
    }

    @Test
    @DisplayName("Revogar tira o acesso e some da lista de operadores")
    void revogarRemoveAcesso() {
        service.conceder("f2-comum@teste.local", List.of("PLATFORM_LEITURA"));
        service.revogar(COMUM);

        assertThat(service.listar()).noneMatch(o -> o.usuarioId().equals(COMUM));
        Integer linhas = jdbc.queryForObject(
            "SELECT count(*) FROM usuario_global_roles WHERE usuario_id = ?",
            Integer.class, COMUM);
        assertThat(linhas).isZero();
    }

    @Test
    @DisplayName("Último PLATFORM_ADMIN não pode ser revogado (evita plataforma sem dono)")
    void naoRevogaUltimoAdmin() {
        // A trava é GLOBAL por natureza: conta admins da plataforma inteira. Outras
        // classes de teste semeiam operadores próprios no MESMO banco (Testcontainers é
        // compartilhado), então "ser o último" precisa ser construído aqui e desfeito no
        // fim — senão o teste passa/falha conforme a ordem de execução da suíte.
        List<UUID> rebaixados = rebaixarOutrosAdmins();
        try {
            TenantContext.setUsuarioId(ADMIN_B);   // outro operador tentando revogar

            assertThatThrownBy(() -> service.revogar(ADMIN_A))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("último administrador");
        } finally {
            restaurarAdmins(rebaixados);
        }
    }

    /** Tira PLATFORM_ADMIN de todo mundo menos ADMIN_A; devolve quem foi rebaixado. */
    private List<UUID> rebaixarOutrosAdmins() {
        List<UUID> outros = jdbc.queryForList(
            "SELECT usuario_id FROM usuario_global_roles "
            + "WHERE 'PLATFORM_ADMIN' = ANY(roles) AND usuario_id <> ?",
            UUID.class, ADMIN_A);
        for (UUID id : outros) {
            jdbc.update("UPDATE usuario_global_roles "
                + "SET roles = array_remove(roles, 'PLATFORM_ADMIN') WHERE usuario_id = ?", id);
        }
        return outros;
    }

    private void restaurarAdmins(List<UUID> ids) {
        for (UUID id : ids) {
            jdbc.update("UPDATE usuario_global_roles "
                + "SET roles = array_append(roles, 'PLATFORM_ADMIN') "
                + "WHERE usuario_id = ? AND NOT ('PLATFORM_ADMIN' = ANY(roles))", id);
        }
    }

    @Test
    @DisplayName("Com dois admins, revogar um é permitido")
    void revogaAdminQuandoHaOutro() {
        service.conceder("f2-admin-b@teste.local", List.of("PLATFORM_ADMIN"));
        TenantContext.setUsuarioId(ADMIN_B);   // ADMIN_B revoga ADMIN_A

        service.revogar(ADMIN_A);

        assertThat(service.listar()).noneMatch(o -> o.usuarioId().equals(ADMIN_A));
        // e ADMIN_B continua de pé — a plataforma nunca ficou sem dono
        assertThat(service.listar())
            .anyMatch(o -> o.usuarioId().equals(ADMIN_B)
                && o.papeis().contains("PLATFORM_ADMIN"));
    }

    @Test
    @DisplayName("Ninguém remove o próprio acesso de admin, mesmo havendo outro")
    void naoRemoveOProprioAdmin() {
        service.conceder("f2-admin-b@teste.local", List.of("PLATFORM_ADMIN"));
        TenantContext.setUsuarioId(ADMIN_A);   // tentando se auto-rebaixar

        assertThatThrownBy(() -> service.atualizar(ADMIN_A, List.of("PLATFORM_LEITURA")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("seu próprio acesso");
    }

    @Test
    @DisplayName("Papéis não-plataforma da linha são preservados na edição")
    void preservaPapeisNaoPlataforma() {
        jdbc.update("INSERT INTO usuario_global_roles "
            + "(usuario_id, roles, unrestricted_access, created_at, updated_at) "
            + "VALUES (?, ARRAY['AUDITOR']::text[], FALSE, now(), now())", COMUM);

        service.atualizar(COMUM, List.of("PLATFORM_LEITURA"));

        String[] roles = jdbc.queryForObject(
            "SELECT roles FROM usuario_global_roles WHERE usuario_id = ?",
            (rs, n) -> (String[]) rs.getArray("roles").getArray(), COMUM);
        assertThat(roles).contains("AUDITOR", "PLATFORM_LEITURA");
    }
}
