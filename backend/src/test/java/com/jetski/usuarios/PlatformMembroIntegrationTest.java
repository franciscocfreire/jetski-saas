package com.jetski.usuarios;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.usuarios.internal.PlatformMembroService;
import com.jetski.usuarios.internal.PlatformMembroService.MembroEmpresa;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Usuários da empresa no console da plataforma.
 *
 * <p>O que importa travar: a lista é da empresa do PATH e só dela. {@code membro} não tem
 * RLS — o filtro por tenant na consulta é o único escopo, e os testes rodam como
 * superuser, então um vazamento entre empresas só aparece se for testado assim.
 */
@DisplayName("Console: usuários da empresa")
class PlatformMembroIntegrationTest extends AbstractIntegrationTest {

    private static final UUID EAMA = UUID.fromString("9f000000-0000-0000-0000-0000000e4a01");
    private static final UUID OUTRA = UUID.fromString("9f000000-0000-0000-0000-0000000e4a02");

    private static final UUID ADMIN = UUID.fromString("9f000000-0000-0000-0000-0000000e4b01");
    private static final UUID OPERADOR = UUID.fromString("9f000000-0000-0000-0000-0000000e4b02");
    private static final UUID EX_FUNC = UUID.fromString("9f000000-0000-0000-0000-0000000e4b03");
    private static final UUID DE_FORA = UUID.fromString("9f000000-0000-0000-0000-0000000e4b04");

    @Autowired PlatformMembroService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        limpar();
        criarTenant(EAMA, "membros-eama");
        criarTenant(OUTRA, "membros-outra");

        criarUsuario(ADMIN, "e4-admin@teste.local", "Ana Admin", true, true);
        criarUsuario(OPERADOR, "e4-operador@teste.local", "Bruno Operador", false, false);
        criarUsuario(EX_FUNC, "e4-ex@teste.local", "Carla Ex", true, true);
        criarUsuario(DE_FORA, "e4-fora@teste.local", "Davi de Fora", true, true);

        vincular(EAMA, ADMIN, "{ADMIN_TENANT,GERENTE}", true);
        vincular(EAMA, OPERADOR, "{OPERADOR}", true);
        vincular(EAMA, EX_FUNC, "{VENDEDOR}", false);
        vincular(OUTRA, DE_FORA, "{ADMIN_TENANT}", true);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    private void limpar() {
        jdbc.update("DELETE FROM membro WHERE tenant_id IN (?,?)", EAMA, OUTRA);
        jdbc.update("DELETE FROM usuario WHERE id IN (?,?,?,?)", ADMIN, OPERADOR, EX_FUNC, DE_FORA);
    }

    private void criarTenant(UUID id, String slug) {
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, ?, ?, 'ATIVO') ON CONFLICT (id) DO NOTHING", id, slug, slug + " Ltda");
    }

    private void criarUsuario(UUID id, String email, String nome, boolean ativo, boolean verificado) {
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo, email_verified) VALUES (?,?,?,?,?)",
            id, email, nome, ativo, verificado);
    }

    private void vincular(UUID tenant, UUID usuario, String papeis, boolean ativo) {
        jdbc.update("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) "
            + "VALUES (?, ?, ?::text[], ?)", tenant, usuario, papeis, ativo);
    }

    @Test
    @DisplayName("Lista só a empresa do path — usuário de outra empresa não aparece")
    void escopoPorEmpresa() {
        List<MembroEmpresa> membros = service.listar(EAMA);

        assertThat(membros).extracting(MembroEmpresa::usuarioId)
            .containsExactlyInAnyOrder(ADMIN, OPERADOR, EX_FUNC)
            .doesNotContain(DE_FORA);
    }

    @Test
    @DisplayName("Inclui inativos, com ativos primeiro e ordem por nome")
    void inativosPorUltimo() {
        assertThat(service.listar(EAMA)).extracting(MembroEmpresa::nome)
            .containsExactly("Ana Admin", "Bruno Operador", "Carla Ex");
    }

    @Test
    @DisplayName("Papéis na empresa e situação da conta vêm separados do vínculo")
    void papeisESituacao() {
        List<MembroEmpresa> membros = service.listar(EAMA);

        MembroEmpresa admin = membros.stream().filter(m -> m.usuarioId().equals(ADMIN)).findFirst().orElseThrow();
        assertThat(admin.papeis()).containsExactly("ADMIN_TENANT", "GERENTE");
        assertThat(admin.ativo()).isTrue();
        assertThat(admin.desde()).isNotNull();

        // vínculo ativo, mas conta global bloqueada e e-mail nunca verificado
        MembroEmpresa operador = membros.stream().filter(m -> m.usuarioId().equals(OPERADOR)).findFirst().orElseThrow();
        assertThat(operador.ativo()).isTrue();
        assertThat(operador.contaAtiva()).isFalse();
        assertThat(operador.emailVerificado()).isFalse();

        MembroEmpresa ex = membros.stream().filter(m -> m.usuarioId().equals(EX_FUNC)).findFirst().orElseThrow();
        assertThat(ex.ativo()).isFalse();
    }

    @Test
    @DisplayName("Empresa sem usuários devolve lista vazia, não erro")
    void empresaVazia() {
        assertThat(service.listar(UUID.fromString("9f000000-0000-0000-0000-0000000e4aff"))).isEmpty();
    }
}
