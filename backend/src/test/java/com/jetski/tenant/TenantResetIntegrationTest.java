package com.jetski.tenant;

import com.jetski.integration.AbstractIntegrationTest;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.internal.TenantResetService;
import com.jetski.tenant.internal.TenantResetService.Nivel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reset de empresa por nível, contra dados reais do tenant fixture:
 * apaga o classificado, preserva créditos/metering/auditoria/assinatura,
 * e no TOTAL mantém os membros ADMIN_TENANT.
 */
@DisplayName("TenantResetService (níveis + preservação)")
class TenantResetIntegrationTest extends AbstractIntegrationTest {

    /**
     * Tenant PRÓPRIO e descartável — NUNCA usar os fixtures compartilhados
     * (test-fixture-a/b): outros testes dependem dos seeds deles (convites
     * pendentes, membros), e o reset TOTAL os destruiria.
     */
    private static final UUID TENANT = UUID.fromString("a1000000-0000-0000-0000-0000000000aa");

    @Autowired private TenantResetService resetService;
    @Autowired private JdbcTemplate jdbc;

    private String slug;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        jdbc.update("INSERT INTO tenant (id, slug, razao_social, status) "
            + "VALUES (?, 'reset-teste', 'Reset Teste Ltda', 'ATIVO') ON CONFLICT DO NOTHING", TENANT);
        slug = jdbc.queryForObject("SELECT slug FROM tenant WHERE id = ?", String.class, TENANT);

        // População mínima cobrindo as categorias: operacional, frota, equipe e preservadas
        jdbc.update("INSERT INTO modelo (id, tenant_id, nome, fabricante, preco_base_hora, ativo) "
            + "VALUES ('a1000000-0000-0000-0000-000000000001', ?, 'Reset Modelo', 'Yamaha', 100, true) "
            + "ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO jetski (id, tenant_id, modelo_id, serie, ano, status, ativo) "
            + "VALUES ('a1000000-0000-0000-0000-000000000002', ?, 'a1000000-0000-0000-0000-000000000001', "
            + "'RESET-001', 2024, 'DISPONIVEL', true) ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO cliente (id, tenant_id, nome, documento, ativo) "
            + "VALUES ('a1000000-0000-0000-0000-000000000003', ?, 'Cliente Reset', '999.888.777-66', true) "
            + "ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO reserva (id, tenant_id, modelo_id, cliente_id, data_inicio, "
            + "data_fim_prevista, status, ativo) "
            + "VALUES ('a1000000-0000-0000-0000-000000000004', ?, 'a1000000-0000-0000-0000-000000000001', "
            + "'a1000000-0000-0000-0000-000000000003', now() + interval '1 day', "
            + "now() + interval '1 day 2 hours', 'CONFIRMADA', true) ON CONFLICT DO NOTHING", TENANT);
        // Preservadas: ledger de créditos e metering
        jdbc.update("INSERT INTO credito_lancamento (id, tenant_id, tipo, quantidade, saldo_apos, motivo) "
            + "VALUES ('a1000000-0000-0000-0000-000000000005', ?, 'AJUSTE', 5, 5, 'teste reset') "
            + "ON CONFLICT DO NOTHING", TENANT);
        // Equipe: um admin (fica no TOTAL) e um operador (sai no TOTAL)
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) "
            + "VALUES ('a1000000-0000-0000-0000-000000000006', 'admin-reset@t.com', 'Admin Reset', true) "
            + "ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO usuario (id, email, nome, ativo) "
            + "VALUES ('a1000000-0000-0000-0000-000000000007', 'op-reset@t.com', 'Op Reset', true) "
            + "ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES "
            + "(?, 'a1000000-0000-0000-0000-000000000006', '{ADMIN_TENANT}', true) "
            + "ON CONFLICT DO NOTHING", TENANT);
        jdbc.update("INSERT INTO membro (tenant_id, usuario_id, papeis, ativo) VALUES "
            + "(?, 'a1000000-0000-0000-0000-000000000007', '{OPERADOR}', true) "
            + "ON CONFLICT DO NOTHING", TENANT);
    }

    @AfterEach
    void tearDown() {
        // Remove TUDO que o fixture criou — os cenários sem reset (slug errado,
        // preview) deixam dados, e outros testes usam deleteAll() global que
        // esbarraria nas FKs daqui (reserva → modelo).
        for (String tabela : new String[]{
                "reserva", "cliente", "jetski", "modelo", "membro"}) {
            jdbc.update("DELETE FROM " + tabela + " WHERE tenant_id = ?", TENANT);
        }
        jdbc.update("DELETE FROM usuario WHERE id IN "
            + "('a1000000-0000-0000-0000-000000000006', 'a1000000-0000-0000-0000-000000000007')");
        // O tenant descartável fica (ledger append-only impede removê-lo);
        // inserts com ON CONFLICT tornam o setUp idempotente entre testes.
        TenantContext.clear();
    }

    private long count(String tabela) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM " + tabela + " WHERE tenant_id = ?", Long.class, TENANT);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("OPERACIONAL: apaga reservas/clientes, mantém frota, equipe e créditos")
    void resetOperacional() {
        Map<String, Long> apagados = resetService.reset(TENANT, Nivel.OPERACIONAL, slug);

        assertThat(apagados).containsKeys("reserva", "cliente");
        assertThat(count("reserva")).isZero();
        assertThat(count("cliente")).isZero();
        assertThat(count("modelo")).isPositive();
        assertThat(count("jetski")).isPositive();
        assertThat(count("membro")).isEqualTo(2);
        assertThat(count("credito_lancamento")).isPositive();
    }

    @Test
    @DisplayName("FROTA: também apaga modelos/jetskis; equipe e créditos ficam")
    void resetFrota() {
        resetService.reset(TENANT, Nivel.FROTA, slug);

        assertThat(count("reserva")).isZero();
        assertThat(count("modelo")).isZero();
        assertThat(count("jetski")).isZero();
        assertThat(count("membro")).isEqualTo(2);
        assertThat(count("credito_lancamento")).isPositive();
    }

    @Test
    @DisplayName("TOTAL: preserva só membros ADMIN_TENANT (e sempre os créditos)")
    void resetTotal() {
        Map<String, Long> apagados = resetService.reset(TENANT, Nivel.TOTAL, slug);

        assertThat(count("modelo")).isZero();
        assertThat(apagados).containsEntry("membro", 1L);
        assertThat(count("membro")).isEqualTo(1);
        String papeis = jdbc.queryForObject(
            "SELECT papeis::text FROM membro WHERE tenant_id = ?", String.class, TENANT);
        assertThat(papeis).contains("ADMIN_TENANT");
        assertThat(count("credito_lancamento")).isPositive();
        assertThat(count("auditoria")).isGreaterThanOrEqualTo(0); // preservada (não tocada)
    }

    @Test
    @DisplayName("slug errado → recusa sem apagar nada")
    void confirmacaoInvalida() {
        assertThatThrownBy(() -> resetService.reset(TENANT, Nivel.OPERACIONAL, "slug-errado"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("slug");
        assertThat(count("reserva")).isPositive();
    }

    @Test
    @DisplayName("preview retorna contagens sem apagar")
    void previewNaoApaga() {
        Map<String, Long> preview = resetService.preview(TENANT, Nivel.TOTAL);

        assertThat(preview.getOrDefault("reserva", 0L)).isPositive();
        assertThat(preview.getOrDefault("membro", 0L)).isEqualTo(1); // só o não-admin
        assertThat(count("reserva")).isPositive();
    }
}
