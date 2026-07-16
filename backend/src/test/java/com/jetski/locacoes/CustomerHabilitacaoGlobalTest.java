package com.jetski.locacoes;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.CustomerHabilitacao;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.CustomerHabilitacaoService;
import com.jetski.locacoes.internal.CustomerHabilitacaoSyncService;
import com.jetski.locacoes.internal.HabilitacaoService;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.CustomerHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V043 — habilitação temporária como dado GLOBAL do cliente (by design).
 *
 * Cobre o ciclo: sync na emissão/pagamento, cópia do PDF na devolutiva e o
 * cenário-chave — a loja de origem é EXPURGADA e o direito do cliente
 * (reuso + download da devolutiva) sobrevive pela fonte global.
 */
@DisplayName("CustomerHabilitacao global (V043)")
class CustomerHabilitacaoGlobalTest extends AbstractIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final String SUB = "sub-hab-global-teste";
    private static final String GRU = "GRU-GLOBAL-TESTE-1";

    @Autowired private CustomerHabilitacaoSyncService syncService;
    @Autowired private CustomerHabilitacaoService customerHabilitacaoService;
    @Autowired private CustomerHabilitacaoRepository globalRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private ReservaHabilitacaoRepository habilitacaoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ModeloRepository modeloRepository;
    @Autowired private StorageService storageService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Cliente cliente;
    private Reserva reserva;
    private ReservaHabilitacao hab;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        globalRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM cliente_identity_provider WHERE provider_user_id = ?", SUB);
        // Ordem FK (espelha ReservaControllerTest): locacao referencia cliente —
        // testes anteriores da suíte podem ter deixado locações do tenant seed.
        // Dependentes primeiro (ordem de FK): sobras de OUTRAS classes (comissão,
        // fechamento, avaliação, fólio, docs) referenciam locacao/reserva do ACME e
        // quebravam esta limpeza quando a ordem das classes muda (CI 15-16/jul).
        jdbcTemplate.execute("DELETE FROM comissao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM avaliacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_lancamento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao_item_opcional WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM foto WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM abastecimento WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM documento_emitido WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_aceite WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_comprovante WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva_habilitacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM os_manutencao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM reserva WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM locacao WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");
        jdbcTemplate.execute("DELETE FROM cliente WHERE tenant_id = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'");

        Modelo modelo = modeloRepository.save(Modelo.builder()
            .tenantId(TENANT_ID).nome("VX Teste " + UUID.randomUUID()).fabricante("Yamaha")
            .precoBaseHora(new BigDecimal("300.00")).ativo(true).build());

        cliente = clienteRepository.save(Cliente.builder()
            .tenantId(TENANT_ID).nome("Cliente Global")
            .documento("123.456.789-00")
            .dataNascimento(LocalDate.of(1990, 1, 1))
            .genero("MASCULINO").email("global@teste.com")
            .termoAceite(true).ativo(true).build());

        jdbcTemplate.update(
            "INSERT INTO cliente_identity_provider (tenant_id, cliente_id, provider, provider_user_id) "
            + "VALUES (?, ?, 'keycloak', ?)", TENANT_ID, cliente.getId(), SUB);

        LocalDateTime inicio = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        reserva = Reserva.builder()
            .tenantId(TENANT_ID).modeloId(modelo.getId()).clienteId(cliente.getId())
            .dataInicio(inicio).dataFimPrevista(inicio.plusHours(2))
            .status(Reserva.ReservaStatus.CONFIRMADA).ativo(true).build();
        reserva.setDocumentoEmitidoEm(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        reserva = reservaRepository.save(reserva);

        hab = habilitacaoRepository.save(ReservaHabilitacao.builder()
            .tenantId(TENANT_ID).reservaId(reserva.getId())
            .via(ReservaHabilitacao.Via.EMA)
            .gruNumero(GRU).gruPago(true).resolvida(true).build());
    }

    @AfterEach
    void tearDown() {
        globalRepository.deleteAll();
        TenantContext.clear();
    }

    @Test
    @DisplayName("sync cria o registro global com CPF normalizado, validade e sub do vínculo")
    void syncCriaRegistroGlobal() {
        syncService.sync(reserva.getId());

        CustomerHabilitacao g = globalRepository.findByGruNumero(GRU).orElseThrow();
        assertThat(g.getCpf()).isEqualTo("12345678900");
        assertThat(g.getProviderUserId()).isEqualTo(SUB);
        assertThat(g.getTenantOrigem()).isEqualTo(TENANT_ID);
        assertThat(g.getReservaOrigem()).isEqualTo(reserva.getId());
        assertThat(g.getMarinhaConfirmadaEm()).isNull();
        assertThat(g.getValidaAte()).isEqualTo(LocalDate.ofInstant(
            reserva.getDocumentoEmitidoEm().plus(HabilitacaoService.VALIDADE_TEMPORARIA_DIAS,
                ChronoUnit.DAYS),
            ZoneId.of("America/Sao_Paulo")));
        // idempotente: segundo sync não duplica
        syncService.sync(reserva.getId());
        assertThat(globalRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("sync não grava enquanto o direito não está materializado (GRU não paga)")
    void syncNaoGravaSemPagamento() {
        hab.setGruPago(false);
        habilitacaoRepository.save(hab);

        syncService.sync(reserva.getId());

        assertThat(globalRepository.count()).isZero();
    }

    @Test
    @DisplayName("devolutiva confirmada copia o PDF para o prefixo da plataforma")
    void devolutivaCopiaPdfParaPlataforma() {
        byte[] pdf = "PDF-DEVOLUTIVA".getBytes();
        String keyTenant = TENANT_ID + "/reserva/" + reserva.getId() + "/cha-mtae-confirmada.pdf";
        storageService.putObject(keyTenant, pdf, "application/pdf");
        hab.setChaMtaeS3Key(keyTenant);
        hab.setMarinhaConfirmadaEm(Instant.now());
        habilitacaoRepository.save(hab);

        syncService.sync(reserva.getId());

        CustomerHabilitacao g = globalRepository.findByGruNumero(GRU).orElseThrow();
        assertThat(g.getMarinhaConfirmadaEm()).isNotNull();
        assertThat(g.getPdfS3Key()).startsWith("_platform/customers/12345678900/");
        assertThat(storageService.getObject(g.getPdfS3Key())).isEqualTo(pdf);
    }

    @Test
    @DisplayName("loja expurgada: reuso e download sobrevivem pela fonte global")
    void direitoSobreviveAoExpurgoDaLoja() {
        // devolutiva confirmada + PDF copiado (estado completo)
        byte[] pdf = "PDF-DEVOLUTIVA-EXPURGO".getBytes();
        String keyTenant = TENANT_ID + "/reserva/" + reserva.getId() + "/cha-mtae-confirmada.pdf";
        storageService.putObject(keyTenant, pdf, "application/pdf");
        hab.setChaMtaeS3Key(keyTenant);
        hab.setMarinhaConfirmadaEm(Instant.now());
        habilitacaoRepository.save(hab);
        syncService.sync(reserva.getId());

        UUID reservaId = reserva.getId();

        // EXPURGO simulado da loja: some tudo que era tenant-scoped (incl. vínculo)
        jdbcTemplate.update("DELETE FROM reserva_habilitacao WHERE reserva_id = ?", reservaId);
        jdbcTemplate.update("DELETE FROM reserva WHERE id = ?", reservaId);
        jdbcTemplate.update("DELETE FROM cliente_identity_provider WHERE provider_user_id = ?", SUB);
        jdbcTemplate.update("DELETE FROM cliente WHERE id = ?", cliente.getId());

        // Reuso: a temporária continua descoberta (fonte global, elegível p/ reuso)
        var temporarias = customerHabilitacaoService.minhasTemporarias(SUB);
        assertThat(temporarias).hasSize(1);
        assertThat(temporarias.get(0).gruNumero()).isEqualTo(GRU);
        assertThat(temporarias.get(0).vigente()).isTrue();
        assertThat(temporarias.get(0).confirmada()).isTrue();
        assertThat(customerHabilitacaoService.vigenteMaisRecente(SUB)).isPresent();

        // Download: a devolutiva do cliente continua acessível (cópia da plataforma)
        assertThat(customerHabilitacaoService.documentoConfirmado(SUB, reservaId)).isEqualTo(pdf);
    }
}
