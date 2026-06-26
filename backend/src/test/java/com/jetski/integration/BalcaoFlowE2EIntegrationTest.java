package com.jetski.integration;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.ClienteClaimToken;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.AceiteService;
import com.jetski.locacoes.internal.ClaimService;
import com.jetski.locacoes.internal.ClienteService;
import com.jetski.locacoes.internal.EmissaoService;
import com.jetski.locacoes.internal.HabilitacaoService;
import com.jetski.locacoes.internal.repository.ClienteClaimTokenRepository;
import com.jetski.locacoes.internal.repository.ClienteIdentityProviderRepository;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.security.UserProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2.8 — Integração fim-a-fim da jornada do balcão (atendimento assistido),
 * contra Postgres real (Testcontainers) + Flyway + serviços reais + round-trip
 * de storage/PDF em disco. Keycloak e e-mail são mockados (o e-mail também
 * serve para capturar a senha temporária, que só sai por esse canal).
 *
 * <p>Jornada: pré-conta → habilitação (EMA+GRU) → aceite (assinatura) →
 * emitir-documentos (PDF/Marinha/cliente) → claim (token) → validar (ativação).
 * Invariante: o cliente é ativado SEM Usuario/Membro.
 */
@DisplayName("Integration E2E: jornada do balcão (F2.8)")
class BalcaoFlowE2EIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ClienteService clienteService;
    @Autowired private HabilitacaoService habilitacaoService;
    @Autowired private AceiteService aceiteService;
    @Autowired private EmissaoService emissaoService;
    @Autowired private ClaimService claimService;
    @Autowired private ClienteClaimTokenRepository claimTokenRepository;
    @Autowired private ClienteIdentityProviderRepository identityRepository;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private EmailService emailService;

    private static final UUID TENANT_ID = UUID.fromString("e2e00000-0000-0000-0000-0000000000aa");
    private static final UUID USER_ID = UUID.fromString("e2e00000-0000-0000-0000-0000000000bb");
    private static final UUID MODELO_ID = UUID.fromString("e2e00000-0000-0000-0000-0000000000cc");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setUsuarioId(USER_ID);

        jdbc.update("""
            INSERT INTO tenant (id, slug, razao_social, cnpj, cidade, marinha_email)
            VALUES (?, 'e2e-balcao', 'Jet Save E2E LTDA', '11.222.333/0001-44', 'Angra dos Reis', 'capitania-e2e@example.com')
            ON CONFLICT (id) DO NOTHING
            """, TENANT_ID);

        // Operador que executa o balcão (FK de auditoria.usuario_id)
        jdbc.update("""
            INSERT INTO usuario (id, email, nome, ativo)
            VALUES (?, 'operador.e2e@example.com', 'Operador E2E', TRUE)
            ON CONFLICT (id) DO NOTHING
            """, USER_ID);

        jdbc.update("""
            INSERT INTO modelo (id, tenant_id, nome, fabricante, potencia_hp, capacidade_pessoas,
                                preco_base_hora, tolerancia_min, taxa_hora_extra, caucao,
                                inclui_combustivel, ativo)
            VALUES (?, ?, 'SeaDoo GTI 130', 'Sea-Doo', 130, 2, 150.00, 5, 50.00, 300.00, FALSE, TRUE)
            ON CONFLICT (id) DO NOTHING
            """, MODELO_ID, TENANT_ID);

        when(userProvisioningService.provisionUserWithPassword(
                any(), anyString(), anyString(), any(), anyList(), anyString()))
            .thenReturn("kc-sub-e2e");
    }

    @Test
    @DisplayName("Pré-conta → habilitação → aceite → emissão → claim → ativação (sem Membro)")
    void jornadaCompletaBalcao() {
        // 1) Pré-conta (balcão)
        Cliente cliente = clienteService.criarPreConta(Cliente.builder()
            .tenantId(TENANT_ID)
            .nome("Roberto Lima")
            .documento("987.654.321-00")
            .email("roberto.e2e@example.com")
            .telefone("+5521988887777")
            .build());
        assertThat(cliente.getOrigem()).isEqualTo(Cliente.Origem.BALCAO);
        assertThat(cliente.getStatusConta()).isEqualTo(Cliente.StatusConta.PRE_CONTA);

        // 2) Reserva para o cliente (pré-requisito da habilitação/aceite/emissão)
        UUID reservaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, data_fim_prevista)
            VALUES (?, ?, ?, ?, now() + interval '1 day', now() + interval '1 day' + interval '2 hours')
            """, reservaId, TENANT_ID, MODELO_ID, cliente.getId());

        // 3) Habilitação via EMA + GRU paga → resolvida
        ReservaHabilitacao hab = habilitacaoService.registrar(reservaId, ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA)
            .anexoSaude(true).anexoRegras(true).anexoResidencia(true).instrutorId(UUID.randomUUID())
            .gruNumero("GRU-E2E-001").gruValor(new BigDecimal("23.13")).gruPago(true)
            .build());
        assertThat(hab.getResolvida()).isTrue();

        // 4) Aceite (assinatura no pad) → arquiva PNG no storage (round-trip real)
        ReservaAceite aceite = aceiteService.registrar(
            reservaId, ReservaAceite.Metodo.SIGNATURE_PAD, pngValido(), "127.0.0.1", "JUnit");
        assertThat(aceite.getAssinaturaS3Key()).isNotBlank();

        // 5) Emissão dos documentos (PDF + Marinha + cliente)
        EmissaoService.ResultadoEmissao emissao = emissaoService.emitir(reservaId);
        assertThat(emissao.getHashSha256()).isNotBlank();
        assertThat(emissao.getDownloadUrl()).isNotBlank();
        assertThat(emissao.isEnviadoMarinha()).isTrue();
        assertThat(emissao.isEnviadoCliente()).isTrue();
        assertThat(emissao.getGruNumero()).isEqualTo("GRU-E2E-001");
        // persistiu documento_emitido + marcou a reserva
        Integer docs = jdbc.queryForObject(
            "SELECT count(*) FROM documento_emitido WHERE reserva_id = ?", Integer.class, reservaId);
        assertThat(docs).isEqualTo(1);
        Instant emitidoEm = jdbc.queryForObject(
            "SELECT documento_emitido_em FROM reserva WHERE id = ?", Instant.class, reservaId);
        assertThat(emitidoEm).isNotNull();

        // 6) Claim: gera token + senha temporária (capturada do e-mail mockado)
        ClaimService.ClaimResult claim = claimService.gerar(cliente.getId(), "email,whatsapp");
        assertThat(claim.getToken()).hasSize(40);

        ArgumentCaptor<String> linkCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> senhaCap = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvitationEmail(
            eq("roberto.e2e@example.com"), eq("Roberto Lima"), linkCap.capture(), senhaCap.capture());
        String senhaTemporaria = senhaCap.getValue();
        assertThat(linkCap.getValue()).contains("/cliente/ativar?token=" + claim.getToken());

        // cliente passou a CONVIDADA
        assertThat(reloadCliente(cliente.getId()).getStatusConta())
            .isEqualTo(Cliente.StatusConta.CONVIDADA);

        // 7) Validação pública: ativa a conta (provisiona Keycloak + vincula identidade)
        ClaimService.AtivacaoResult ativacao = claimService.validar(claim.getToken(), senhaTemporaria);
        assertThat(ativacao.getProviderUserId()).isEqualTo("kc-sub-e2e");

        verify(userProvisioningService).provisionUserWithPassword(
            eq(cliente.getId()), eq("roberto.e2e@example.com"), eq("Roberto Lima"),
            eq(TENANT_ID), eq(List.of("CLIENTE")), eq(senhaTemporaria));

        // estado final: ATIVA + identidade vinculada + token consumido
        assertThat(reloadCliente(cliente.getId()).getStatusConta())
            .isEqualTo(Cliente.StatusConta.ATIVA);
        assertThat(identityRepository.findByClienteId(cliente.getId()))
            .get().extracting("provider", "providerUserId")
            .containsExactly("keycloak", "kc-sub-e2e");
        Optional<ClienteClaimToken> tok = claimTokenRepository.findByToken(claim.getToken());
        assertThat(tok).get().extracting(ClienteClaimToken::getAtivo, ClienteClaimToken::isUsado)
            .containsExactly(false, true);

        // INVARIANTE: cliente nunca vira Usuario/Membro
        Integer membros = jdbc.queryForObject(
            "SELECT count(*) FROM membro WHERE tenant_id = ?", Integer.class, TENANT_ID);
        assertThat(membros).isZero();
        Integer usuarios = jdbc.queryForObject(
            "SELECT count(*) FROM usuario WHERE email = ?", Integer.class, "roberto.e2e@example.com");
        assertThat(usuarios).isZero();
    }

    private Cliente reloadCliente(UUID id) {
        return clienteService.findById(id);
    }

    /** PNG 1x1 válido (o OpenPDF precisa parsear o header da imagem). */
    private static byte[] pngValido() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
