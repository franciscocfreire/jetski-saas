package com.jetski.signup;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.signup.internal.PlatformSignupService;
import com.jetski.signup.internal.PlatformSignupService.Situacao;
import com.jetski.signup.internal.PlatformSignupService.SolicitacaoCadastro;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quem pediu o cadastro da empresa, no console.
 *
 * <p>Caso real que motivou: pedido com e-mail digitado errado fica PENDING para sempre,
 * a empresa aparece sem nenhum usuário e o dono só existe em {@code tenant_signup}.
 */
@DisplayName("Console: quem pediu o cadastro")
class PlatformSignupIntegrationTest extends AbstractIntegrationTest {

    private static final UUID PENDENTE = UUID.fromString("9f000000-0000-0000-0000-0000000e5a01");
    private static final UUID ATIVA = UUID.fromString("9f000000-0000-0000-0000-0000000e5a02");
    private static final UUID SEM_PEDIDO = UUID.fromString("9f000000-0000-0000-0000-0000000e5a03");

    @Autowired PlatformSignupService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        limpar();
        criarTenant(PENDENTE, "signup-pendente");
        criarTenant(ATIVA, "signup-ativa");
        criarTenant(SEM_PEDIDO, "signup-sem-pedido");

        Instant agora = Instant.now();
        // primeiro pedido: link vencido, nunca ativado (status continua PENDING no banco)
        pedido(PENDENTE, "dono@htmail.test", "PENDING",
            agora.minus(Duration.ofDays(3)), agora.minus(Duration.ofDays(1)), null);
        // pedido refeito depois, ainda dentro da validade
        pedido(PENDENTE, "dono@hotmail.test", "PENDING",
            agora.minus(Duration.ofHours(1)), agora.plus(Duration.ofHours(47)), null);
        pedido(ATIVA, "dona@ativa.test", "ACTIVATED",
            agora.minus(Duration.ofDays(2)), agora, agora.minus(Duration.ofDays(2)));
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    private void limpar() {
        jdbc.update("DELETE FROM tenant_signup WHERE tenant_id IN (?,?,?)", PENDENTE, ATIVA, SEM_PEDIDO);
    }

    private void criarTenant(UUID id, String slug) {
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, ?, ?, 'PENDENTE_APROVACAO') ON CONFLICT (id) DO NOTHING",
            id, slug, slug + " Ltda");
    }

    private void pedido(UUID tenant, String email, String status,
                        Instant criado, Instant expira, Instant ativado) {
        jdbc.update("INSERT INTO tenant_signup "
                + "(tenant_id, email, nome, token, temporary_password, expires_at, status, created_at, activated_at) "
                + "VALUES (?, ?, 'Dono Teste', ?, 'hash-secreto', ?, ?, ?, ?)",
            tenant, email, "tok-" + UUID.randomUUID(), Timestamp.from(expira), status,
            Timestamp.from(criado), ativado == null ? null : Timestamp.from(ativado));
    }

    @Test
    @DisplayName("Mostra o dono de empresa sem usuários, mais recente primeiro e só da empresa pedida")
    void pedidosDaEmpresa() {
        List<SolicitacaoCadastro> pedidos = service.listar(PENDENTE);

        assertThat(pedidos).extracting(SolicitacaoCadastro::email)
            .containsExactly("dono@hotmail.test", "dono@htmail.test");
    }

    @Test
    @DisplayName("Link vencido é LINK_EXPIRADO mesmo com status PENDING no banco")
    void situacaoDerivada() {
        List<SolicitacaoCadastro> pedidos = service.listar(PENDENTE);

        assertThat(pedidos.get(0).situacao()).isEqualTo(Situacao.AGUARDANDO_ATIVACAO);
        assertThat(pedidos.get(1).situacao()).isEqualTo(Situacao.LINK_EXPIRADO);

        SolicitacaoCadastro ativado = service.listar(ATIVA).get(0);
        assertThat(ativado.situacao()).isEqualTo(Situacao.ATIVADO);
        assertThat(ativado.ativadoEm()).isNotNull();
    }

    @Test
    @DisplayName("Empresa criada por usuário já cadastrado não tem pedido: lista vazia")
    void semPedido() {
        assertThat(service.listar(SEM_PEDIDO)).isEmpty();
    }

    @Test
    @DisplayName("Token e senha temporária nunca saem para o console")
    void naoExpoeSegredos() {
        assertThat(Arrays.stream(SolicitacaoCadastro.class.getRecordComponents())
                .map(c -> c.getName().toLowerCase()))
            .noneMatch(n -> n.contains("token") || n.contains("senha") || n.contains("password"));
    }
}
