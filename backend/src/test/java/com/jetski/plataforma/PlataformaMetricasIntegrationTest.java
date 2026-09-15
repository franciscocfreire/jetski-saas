package com.jetski.plataforma;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.security.TenantContext;
import com.jetski.plataforma.internal.PlataformaMetricasService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read model da plataforma (F4).
 *
 * <p>O que estes testes travam: o agregado tem que bater com a SOMA REAL por empresa
 * (um read model que mente é pior que não ter read model), o recálculo tem que ser
 * idempotente, e o job NÃO pode vazar o {@code TenantContext} — ele roda na thread do
 * scheduler, e um ThreadLocal vazado ali contamina o datasource de todos os jobs
 * seguintes (já aconteceu neste projeto).
 */
@DisplayName("Read model da plataforma (F4)")
class PlataformaMetricasIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

    @Autowired PlataformaMetricasService service;
    @Autowired JdbcTemplate jdbc;

    private LocalDate hoje;

    @BeforeEach
    void setUp() {
        hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        jdbc.update("DELETE FROM plataforma_metrica_diaria WHERE dia >= ?", hoje.minusDays(10));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Agregado bate com a soma real de locações e receita do dia")
    void agregadoBateComAFonte() {
        service.recalcular(hoje, hoje);

        TenantContext.setTenantId(TENANT);
        Integer locacoesReais = jdbc.queryForObject("""
            SELECT count(*) FROM locacao WHERE tenant_id = ?
              AND (data_check_in AT TIME ZONE 'America/Sao_Paulo')::date = ?
            """, Integer.class, TENANT, hoje);
        BigDecimal receitaReal = jdbc.queryForObject("""
            SELECT COALESCE(SUM(COALESCE(valor_total,0)),0) FROM locacao WHERE tenant_id = ?
              AND (data_check_in AT TIME ZONE 'America/Sao_Paulo')::date = ?
            """, BigDecimal.class, TENANT, hoje);
        TenantContext.clear();

        var linha = jdbc.queryForMap(
            "SELECT locacoes, receita_bruta FROM plataforma_metrica_diaria "
            + "WHERE tenant_id = ? AND dia = ?", TENANT, hoje);

        assertThat(((Number) linha.get("locacoes")).intValue()).isEqualTo(locacoesReais);
        assertThat((BigDecimal) linha.get("receita_bruta")).isEqualByComparingTo(receitaReal);
    }

    @Test
    @DisplayName("Recalcular é idempotente — rodar duas vezes não duplica nem muda o número")
    void recalculoIdempotente() {
        service.recalcular(hoje, hoje);
        var antes = jdbc.queryForMap(
            "SELECT locacoes, receita_bruta FROM plataforma_metrica_diaria "
            + "WHERE tenant_id = ? AND dia = ?", TENANT, hoje);

        service.recalcular(hoje, hoje);

        Integer linhas = jdbc.queryForObject(
            "SELECT count(*) FROM plataforma_metrica_diaria WHERE tenant_id = ? AND dia = ?",
            Integer.class, TENANT, hoje);
        var depois = jdbc.queryForMap(
            "SELECT locacoes, receita_bruta FROM plataforma_metrica_diaria "
            + "WHERE tenant_id = ? AND dia = ?", TENANT, hoje);

        assertThat(linhas).isEqualTo(1);
        assertThat(((Number) depois.get("locacoes")).intValue())
            .isEqualTo(((Number) antes.get("locacoes")).intValue());
        assertThat((BigDecimal) depois.get("receita_bruta"))
            .isEqualByComparingTo((BigDecimal) antes.get("receita_bruta"));
    }

    @Test
    @DisplayName("Não vaza TenantContext — o job roda na thread do scheduler")
    void naoVazaTenantContext() {
        TenantContext.clear();

        service.recalcular(hoje.minusDays(2), hoje);

        assertThat(TenantContext.getTenantId())
            .as("ThreadLocal vazado contamina o TenantAwareDataSource dos jobs seguintes")
            .isNull();
    }

    @Test
    @DisplayName("Preserva o tenant de quem chamou (recálculo manual dentro de um request)")
    void preservaTenantDoChamador() {
        TenantContext.setTenantId(TENANT);

        service.recalcular(hoje, hoje);

        assertThat(TenantContext.getTenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("Cobre TODAS as empresas e a janela pedida")
    void cobreTodasAsEmpresasEDias() {
        Integer empresas = jdbc.queryForObject(
            "SELECT count(*) FROM tenant WHERE excluido_em IS NULL", Integer.class);

        var r = service.recalcular(hoje.minusDays(2), hoje);

        assertThat(r.empresas()).isEqualTo(empresas);
        assertThat(r.dias()).isEqualTo(3);
        assertThat(r.linhas()).isEqualTo(empresas * 3);
    }

    // ---------------------------------------------------------------- MRR e receita

    private static final UUID EMPRESA_MRR = UUID.fromString("a5000000-0000-0000-0000-0000000000e1");

    private void seedEmpresaPro(String status) {
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, 'metrica-mrr', 'Métrica MRR Ltda', ?) "
            + "ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status", EMPRESA_MRR, status);
        jdbc.update("DELETE FROM fatura WHERE tenant_id = ?", EMPRESA_MRR);
        jdbc.update("DELETE FROM assinatura WHERE tenant_id = ?", EMPRESA_MRR);
        jdbc.update("INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) "
            + "SELECT ?, id, 'mensal', CURRENT_DATE - 30, 'ativa' FROM plano WHERE nome = 'Pro'",
            EMPRESA_MRR);
    }

    private BigDecimal coluna(String coluna) {
        return jdbc.queryForObject("SELECT " + coluna + " FROM plataforma_metrica_diaria "
            + "WHERE tenant_id = ? AND dia = ?", BigDecimal.class, EMPRESA_MRR, hoje);
    }

    @Test
    @DisplayName("MRR conta o plano da empresa ativa")
    void mrrDeEmpresaAtiva() {
        seedEmpresaPro("ATIVO");

        service.recalcular(hoje, hoje);

        assertThat(coluna("mrr")).isEqualByComparingTo("299.00");
    }

    @Test
    @DisplayName("MRR ignora empresa suspensa — plano sem vencimento não é receita recorrente")
    void mrrIgnoraSuspensa() {
        // Antes: "soma dos planos vigentes" contava suspensas com o preço cheio.
        seedEmpresaPro("SUSPENSO");

        service.recalcular(hoje, hoje);

        assertThat(coluna("mrr")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT plano_nome FROM plataforma_metrica_diaria "
            + "WHERE tenant_id = ? AND dia = ?", String.class, EMPRESA_MRR, hoje)).isEqualTo("Pro");
    }

    @Test
    @DisplayName("Troca de plano no dia: vale a assinatura nova, não a que expirou hoje")
    void trocaDePlanoNoDiaUsaANova() {
        seedEmpresaPro("ATIVO");
        jdbc.update("UPDATE assinatura SET status = 'expirada', dt_inicio = CURRENT_DATE, "
            + "dt_fim = CURRENT_DATE WHERE tenant_id = ?", EMPRESA_MRR);
        jdbc.update("INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, status) "
            + "SELECT ?, id, 'mensal', CURRENT_DATE, 'ativa' FROM plano WHERE nome = 'Basic'",
            EMPRESA_MRR);

        service.recalcular(hoje, hoje);

        assertThat(coluna("mrr")).isEqualByComparingTo("99.00");
    }

    @Test
    @DisplayName("Receita da plataforma = fatura PAGA no dia; aberta não conta")
    void receitaDaPlataformaContaFaturaPaga() {
        seedEmpresaPro("ATIVO");
        jdbc.update("INSERT INTO fatura (tenant_id, competencia, plano_nome, valor, status, "
            + "vencimento, pago_em) VALUES (?, date_trunc('month', CURRENT_DATE), 'Pro', 299, "
            + "'PAGA', CURRENT_DATE, now())", EMPRESA_MRR);
        jdbc.update("INSERT INTO fatura (tenant_id, competencia, plano_nome, valor, status, "
            + "vencimento) VALUES (?, date_trunc('month', CURRENT_DATE) - interval '1 month', "
            + "'Pro', 299, 'ABERTA', CURRENT_DATE)", EMPRESA_MRR);

        service.recalcular(hoje, hoje);

        assertThat(coluna("receita_faturas")).isEqualByComparingTo("299.00");
        assertThat(coluna("valor_em_aberto")).isEqualByComparingTo("299.00");
    }

    @Test
    @DisplayName("Empresa sem movimento vira linha zerada, não linha ausente")
    void empresaSemMovimentoTemLinhaZerada() {
        // Sem isto o dashboard teria de distinguir "não calculado" de "não vendeu",
        // e um LEFT JOIN ausente viraria buraco no gráfico.
        service.recalcular(hoje, hoje);

        Integer semLinha = jdbc.queryForObject("""
            SELECT count(*) FROM tenant t
             WHERE t.excluido_em IS NULL
               AND NOT EXISTS (SELECT 1 FROM plataforma_metrica_diaria m
                                WHERE m.tenant_id = t.id AND m.dia = ?)
            """, Integer.class, hoje);

        assertThat(semLinha).isZero();
    }
}
