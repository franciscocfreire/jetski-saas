package com.jetski.locacoes.internal;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.ClienteClaimToken;
import com.jetski.locacoes.internal.repository.ClienteClaimTokenRepository;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.security.UserProvisioningService;
import com.jetski.usuarios.api.PessoaProvisioningService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Identidade única — F0 (IDENTIDADE_UNICA_SPEC §5): a pessoa nasce como
 * {@code usuario} (raiz única) nos fluxos de consumidor, com dupla escrita do
 * vínculo ({@code cliente.usuario_id} ao lado de cliente_identity_provider).
 */
@AutoConfigureMockMvc
@DisplayName("Identidade única F0 — provisionamento da pessoa + dupla escrita")
class IdentidadeUnicaF0IntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("f0000000-0000-0000-0000-0000000000aa");

    @Autowired private PessoaProvisioningService pessoaService;
    @Autowired private ClaimService claimService;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ClienteClaimTokenRepository tokenRepository;
    @Autowired private JdbcTemplate jdbc;

    // Mesma combinação de mocks do CustomerPortalIntegrationTest (contexto cacheado)
    @MockBean OPAAuthorizationService opaAuthorizationService;
    @MockBean UserProvisioningService userProvisioningService;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, 'f0-teste', 'F0 Teste Ltda', 'ATIVO') ON CONFLICT DO NOTHING", TENANT);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM cliente_identity_provider WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM cliente_claim_token WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM cliente WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM auditoria WHERE acao = 'PESSOA_PROVISIONADA' "
            + "AND usuario_id IN (SELECT id FROM usuario WHERE email LIKE '%@f0.test')");
        jdbc.update("DELETE FROM usuario_identity_provider WHERE provider_user_id LIKE 'sub-f0-%'");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@f0.test'");
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    /** Trilha é AFTER_COMMIT + @Async — espera curta com polling. */
    private long aguardarAuditoria(UUID usuarioId) throws Exception {
        for (int i = 0; i < 50; i++) {
            long n = count("SELECT count(*) FROM auditoria "
                + "WHERE acao = 'PESSOA_PROVISIONADA' AND usuario_id = ?", usuarioId);
            if (n > 0) {
                return n;
            }
            Thread.sleep(100);
        }
        return 0;
    }

    @Test
    @DisplayName("provisionarPessoa cria usuario+vínculo uma vez e é idempotente")
    void provisionarPessoaCriaEReusa() throws Exception {
        UUID id1 = pessoaService.provisionarPessoa("sub-f0-novo", "novo@f0.test", "F0 Novo", "TESTE");

        assertThat(count("SELECT count(*) FROM usuario WHERE email = 'novo@f0.test'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM usuario_identity_provider "
            + "WHERE provider = 'keycloak' AND provider_user_id = 'sub-f0-novo'")).isEqualTo(1);
        assertThat(aguardarAuditoria(id1))
            .as("PESSOA_PROVISIONADA deve pousar na trilha global")
            .isPositive();

        UUID id2 = pessoaService.provisionarPessoa("sub-f0-novo", "novo@f0.test", "F0 Novo", "TESTE");
        assertThat(id2).isEqualTo(id1);
        assertThat(count("SELECT count(*) FROM usuario WHERE email = 'novo@f0.test'")).isEqualTo(1);
    }

    @Test
    @DisplayName("staff com o mesmo e-mail ACUMULA o papel — nenhuma pessoa duplicada")
    void staffAcumulaPapelDeCliente() {
        UUID staffId = UUID.fromString("f0000000-0000-0000-0000-000000000001");
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) "
            + "VALUES (?, 'staff@f0.test', 'Staff F0', true) ON CONFLICT DO NOTHING", staffId);

        UUID pessoa = pessoaService.provisionarPessoa(
            "sub-f0-staff", "staff@f0.test", "Staff F0", "TESTE");

        assertThat(pessoa).isEqualTo(staffId); // mesma pessoa, papel novo
        assertThat(count("SELECT count(*) FROM usuario WHERE email = 'staff@f0.test'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM usuario_identity_provider "
            + "WHERE usuario_id = ? AND provider_user_id = 'sub-f0-staff'", staffId)).isEqualTo(1);
    }

    @Test
    @DisplayName("ativação de claim: dupla escrita — cliente.usuario_id + vínculo legado")
    void claimAtivacaoPreencheUsuarioId() {
        Cliente cliente = clienteRepository.save(Cliente.builder()
            .tenantId(TENANT)
            .nome("Cliente F0")
            .email("cliente@f0.test")
            .documento("444.555.666-77")
            .origem(Cliente.Origem.BALCAO)
            .statusConta(Cliente.StatusConta.CONVIDADA)
            .ativo(true)
            .build());
        ClienteClaimToken token = ClienteClaimToken.builder()
            .tenantId(TENANT)
            .clienteId(cliente.getId())
            .token("TOKEN-F0-CLAIM-0001")
            .canais("email")
            .expiraEm(java.time.Instant.now().plusSeconds(3600))
            .ativo(true)
            .build();
        token.setTemporaryPassword("senha-f0-123");
        tokenRepository.save(token);
        when(userProvisioningService.provisionOrReuseCliente(
                any(), anyString(), anyString(), any(), anyString()))
            .thenReturn(new UserProvisioningService.ClienteProvisionResult("sub-f0-claim", false));

        claimService.validar("TOKEN-F0-CLAIM-0001", "senha-f0-123");

        UUID usuarioId = jdbc.queryForObject(
            "SELECT usuario_id FROM cliente WHERE id = ?", UUID.class, cliente.getId());
        assertThat(usuarioId).as("dupla escrita: ficha aponta para a pessoa").isNotNull();
        assertThat(count("SELECT count(*) FROM usuario WHERE id = ? AND email = 'cliente@f0.test'",
            usuarioId)).isEqualTo(1);
        // Caminho legado segue vivo até a F4
        assertThat(count("SELECT count(*) FROM cliente_identity_provider "
            + "WHERE cliente_id = ? AND provider_user_id = 'sub-f0-claim'", cliente.getId()))
            .isEqualTo(1);
    }
}
