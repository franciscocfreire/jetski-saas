package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.Fatura;
import com.jetski.tenant.internal.FaturaService;
import com.jetski.tenant.internal.PlatformFaturaService;
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
 * Billing manual assistido (v2 item 1): geração idempotente por competência
 * (só plano pago), fluxo informar→conferir→paga, suspensão por inadimplência
 * e troca de plano. Tenant descartável próprio (nunca fixtures compartilhados).
 */
@DisplayName("Faturamento (billing manual assistido)")
class FaturamentoIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a3000000-0000-0000-0000-0000000000cc");

    @Autowired private PlatformFaturaService platformService;
    @Autowired private FaturaService faturaService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        jdbc.update("DELETE FROM fatura WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM assinatura WHERE tenant_id = ?", TENANT);
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, 'fatura-teste', 'Fatura Teste Ltda', 'ATIVO') "
            + "ON CONFLICT (id) DO UPDATE SET status = 'ATIVO', exclusao_agendada_em = NULL, "
            + "excluido_em = NULL, slug = 'fatura-teste'", TENANT);
        // Assinatura ativa do plano Pro (pago) — o cenário faturável
        jdbc.update("INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) "
            + "SELECT ?, id, 'mensal', CURRENT_DATE, 'ativa' FROM plano WHERE nome = 'Pro'", TENANT);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE tenant SET status = 'ATIVO' WHERE id = ?", TENANT);
        TenantContext.clear();
    }

    private Fatura unica() {
        List<Fatura> fs = faturaService.minhas(TENANT);
        assertThat(fs).hasSize(1);
        return fs.get(0);
    }

    @Test
    @DisplayName("gera fatura do plano pago com PIX, idempotente por competência")
    void geraFaturaIdempotente() {
        int criadas = platformService.gerarFaturasDoMes();
        assertThat(criadas).isGreaterThanOrEqualTo(1);

        Fatura f = unica();
        assertThat(f.getPlanoNome()).isEqualTo("Pro");
        assertThat(f.getValor()).isEqualByComparingTo("299.00");
        assertThat(f.getStatus()).isEqualTo(Fatura.Status.ABERTA);
        assertThat(f.getPixCopiaECola()).startsWith("000201").contains("br.gov.bcb.pix");

        // Segunda rodada não duplica
        platformService.gerarFaturasDoMes();
        assertThat(faturaService.minhas(TENANT)).hasSize(1);
    }

    @Test
    @DisplayName("Trial (preço 0) não fatura")
    void trialNaoFatura() {
        jdbc.update("UPDATE assinatura SET status = 'expirada' WHERE tenant_id = ?", TENANT);
        jdbc.update("INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) "
            + "SELECT ?, id, 'mensal', CURRENT_DATE, 'ativa' FROM plano WHERE nome = 'Trial'", TENANT);

        platformService.gerarFaturasDoMes();

        assertThat(faturaService.minhas(TENANT)).isEmpty();
    }

    @Test
    @DisplayName("informar pagamento → EM_CONFERENCIA → fila → confirmar → PAGA")
    void fluxoConferencia() {
        platformService.gerarFaturasDoMes();
        Fatura f = unica();

        faturaService.informarPagamento(TENANT, f.getId(), "E12345678901234567890");
        assertThat(unica().getStatus()).isEqualTo(Fatura.Status.EM_CONFERENCIA);

        var fila = platformService.pendentesConferencia();
        assertThat(fila).anyMatch(p -> p.fatura().getId().equals(f.getId())
            && p.slug().equals("fatura-teste"));

        Fatura paga = platformService.confirmar(TENANT, f.getId());
        assertThat(paga.getStatus()).isEqualTo(Fatura.Status.PAGA);
        assertThat(paga.getPagoEm()).isNotNull();
    }

    @Test
    @DisplayName("inadimplência: ABERTA vencida além da carência suspende o tenant")
    void inadimplenciaSuspende() {
        platformService.gerarFaturasDoMes();
        jdbc.update("UPDATE fatura SET vencimento = CURRENT_DATE - 10 WHERE tenant_id = ?", TENANT);

        int suspensos = platformService.suspenderInadimplentes();

        assertThat(suspensos).isGreaterThanOrEqualTo(1);
        String status = jdbc.queryForObject(
            "SELECT status FROM tenant WHERE id = ?", String.class, TENANT);
        assertThat(status).isEqualTo("SUSPENSO");
    }

    @Test
    @DisplayName("EM_CONFERENCIA não suspende (aguardando conferência humana)")
    void emConferenciaNaoSuspende() {
        platformService.gerarFaturasDoMes();
        Fatura f = unica();
        faturaService.informarPagamento(TENANT, f.getId(), "E999");
        jdbc.update("UPDATE fatura SET vencimento = CURRENT_DATE - 10 WHERE tenant_id = ?", TENANT);

        platformService.suspenderInadimplentes();

        String status = jdbc.queryForObject(
            "SELECT status FROM tenant WHERE id = ?", String.class, TENANT);
        assertThat(status).isEqualTo("ATIVO");
    }

    @Test
    @DisplayName("mudar plano expira a assinatura ativa e a próxima fatura usa o novo")
    void mudarPlano() {
        Integer basicId = jdbc.queryForObject(
            "SELECT id FROM plano WHERE nome = 'Basic'", Integer.class);

        platformService.mudarPlano(TENANT, basicId);

        Long ativas = jdbc.queryForObject("SELECT count(*) FROM assinatura "
            + "WHERE tenant_id = ? AND status = 'ativa'", Long.class, TENANT);
        assertThat(ativas).isEqualTo(1);
        platformService.gerarFaturasDoMes();
        Fatura f = unica();
        assertThat(f.getPlanoNome()).isEqualTo("Basic");
        assertThat(f.getValor()).isEqualByComparingTo("99.00");
    }

    @Test
    @DisplayName("cancelar exige observação; fatura paga não cancela")
    void validacoesCancelamento() {
        platformService.gerarFaturasDoMes();
        Fatura f = unica();

        assertThatThrownBy(() -> platformService.cancelar(TENANT, f.getId(), "  "))
            .isInstanceOf(BusinessException.class);

        platformService.confirmar(TENANT, f.getId());
        assertThatThrownBy(() -> platformService.cancelar(TENANT, f.getId(), "teste"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("paga");
    }
}
