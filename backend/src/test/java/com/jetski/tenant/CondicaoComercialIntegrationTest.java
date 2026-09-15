package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.internal.CondicaoComercialService;
import com.jetski.tenant.internal.CondicaoComercialService.Condicao;
import com.jetski.tenant.internal.CondicaoComercialService.Forma;
import com.jetski.tenant.internal.CondicaoComercialService.NovaCondicao;
import com.jetski.tenant.internal.CondicaoComercialService.Tipo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Condição comercial da mensalidade (V076): regras de concessão, vigência e encerramento.
 * Os efeitos no faturamento ficam no {@code FaturamentoIntegrationTest}; no read model, no
 * {@code PlataformaMetricasIntegrationTest}.
 */
@DisplayName("Condição comercial (isenção/desconto da mensalidade)")
class CondicaoComercialIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a6000000-0000-0000-0000-0000000000c1");

    @Autowired private CondicaoComercialService service;
    @Autowired private JdbcTemplate jdbc;

    private LocalDate hoje;

    @BeforeEach
    void setUp() {
        hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, 'condicao-teste', 'Condição Teste Ltda', 'ATIVO') "
            + "ON CONFLICT (id) DO UPDATE SET status = 'ATIVO'", TENANT);
        jdbc.update("DELETE FROM condicao_comercial WHERE tenant_id = ?", TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NovaCondicao piloto(LocalDate inicio, LocalDate fim) {
        return new NovaCondicao(Tipo.PILOTO, Forma.ISENCAO, null, inicio, fim, "piloto de verão");
    }

    @Test
    @DisplayName("isenção concedida hoje fica vigente, com situação e histórico")
    void concedeIsencaoVigente() {
        Condicao c = service.conceder(TENANT, piloto(null, hoje.plusMonths(3)));

        assertThat(c.situacao()).isEqualTo("VIGENTE");
        assertThat(c.inicio()).isEqualTo(hoje);
        assertThat(c.valor()).isNull();
        assertThat(service.vigenteHoje(TENANT)).map(Condicao::id).contains(c.id());
        assertThat(service.historico(TENANT)).hasSize(1);
    }

    @Test
    @DisplayName("piloto sem término é recusado")
    void pilotoExigeFim() {
        assertThatThrownBy(() -> service.conceder(TENANT, piloto(null, null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("término");
    }

    @Test
    @DisplayName("início retroativo é recusado (fatura emitida se trata em Faturamento)")
    void inicioRetroativoRecusado() {
        assertThatThrownBy(() -> service.conceder(TENANT, piloto(hoje.minusDays(1), hoje.plusDays(10))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("retroativo");
    }

    @Test
    @DisplayName("motivo é obrigatório")
    void motivoObrigatorio() {
        assertThatThrownBy(() -> service.conceder(TENANT,
                new NovaCondicao(Tipo.CORTESIA, Forma.ISENCAO, null, null, null, "  ")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("desconto fora de (0, 100] é recusado")
    void percentualInvalido() {
        assertThatThrownBy(() -> service.conceder(TENANT,
                new NovaCondicao(Tipo.NEGOCIADO, Forma.PERCENTUAL, BigDecimal.ZERO, null, null, "x")))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.conceder(TENANT,
                new NovaCondicao(Tipo.NEGOCIADO, Forma.PERCENTUAL, new BigDecimal("101"), null, null, "x")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("período sobreposto a outra condição é recusado — uma condição por vez")
    void sobreposicaoRecusada() {
        service.conceder(TENANT, piloto(null, hoje.plusMonths(3)));

        assertThatThrownBy(() -> service.conceder(TENANT,
                new NovaCondicao(Tipo.NEGOCIADO, Forma.PERCENTUAL, new BigDecimal("30"),
                    hoje.plusMonths(1), null, "contrato")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Encerre");
    }

    @Test
    @DisplayName("condição após o término da atual é aceita e fica agendada")
    void agendadaDepoisDoTermino() {
        service.conceder(TENANT, piloto(null, hoje.plusDays(30)));

        Condicao contrato = service.conceder(TENANT, new NovaCondicao(Tipo.NEGOCIADO,
            Forma.VALOR_FIXO, new BigDecimal("199"), hoje.plusDays(31), null, "contrato pós-piloto"));

        assertThat(contrato.situacao()).isEqualTo("AGENDADA");
        assertThat(service.vigenteHoje(TENANT)).map(Condicao::tipo).contains(Tipo.PILOTO);
    }

    @Test
    @DisplayName("encerrar tira a vigência hoje e mantém o histórico")
    void encerrarMantemHistorico() {
        Condicao c = service.conceder(TENANT, piloto(null, hoje.plusMonths(3)));

        Condicao encerrada = service.encerrar(TENANT, c.id(), "virou contrato");

        assertThat(encerrada.situacao()).isEqualTo("ENCERRADA");
        assertThat(encerrada.motivoEncerramento()).isEqualTo("virou contrato");
        assertThat(service.vigenteHoje(TENANT)).isEmpty();
        assertThat(service.historico(TENANT)).hasSize(1);
        assertThatThrownBy(() -> service.encerrar(TENANT, c.id(), "de novo"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("valor efetivo: isenção 0, percentual arredonda, valor fixo nunca encarece")
    void valorEfetivo() {
        BigDecimal pro = new BigDecimal("299.00");
        Condicao isencao = condicao(Forma.ISENCAO, null);
        Condicao terco = condicao(Forma.PERCENTUAL, "33.33");
        Condicao fixoAlto = condicao(Forma.VALOR_FIXO, "500");
        Condicao fixo = condicao(Forma.VALOR_FIXO, "150");

        assertThat(CondicaoComercialService.valorEfetivo(pro, null)).isEqualByComparingTo("299.00");
        assertThat(CondicaoComercialService.valorEfetivo(pro, isencao)).isEqualByComparingTo("0");
        assertThat(CondicaoComercialService.valorEfetivo(pro, terco)).isEqualByComparingTo("199.34");
        assertThat(CondicaoComercialService.valorEfetivo(pro, fixoAlto)).isEqualByComparingTo("299.00");
        assertThat(CondicaoComercialService.valorEfetivo(pro, fixo)).isEqualByComparingTo("150");
    }

    private Condicao condicao(Forma forma, String valor) {
        return new Condicao(UUID.randomUUID(), Tipo.NEGOCIADO, forma,
            valor == null ? null : new BigDecimal(valor), hoje, null, "x", null, null,
            null, null, null, "VIGENTE");
    }
}
