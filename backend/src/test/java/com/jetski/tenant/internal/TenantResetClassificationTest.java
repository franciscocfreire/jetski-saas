package com.jetski.tenant.internal;

import com.jetski.integration.AbstractIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda de cobertura do reset de empresa (mesmo espírito do 02-verify-rls.sql):
 * TODA tabela multi-tenant (coluna tenant_id) precisa estar classificada em
 * EXATAMENTE um grupo do {@link TenantResetService} — apagável em algum nível,
 * tratamento especial de equipe ou preservada-com-justificativa.
 *
 * <p>Se este teste falhou na sua migration nova: decida o destino da tabela e
 * adicione-a à lista certa no TenantResetService (com o porquê, se preservada).
 */
@DisplayName("Reset de empresa — classificação obrigatória das tabelas multi-tenant")
class TenantResetClassificationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("toda tabela com tenant_id está classificada em exatamente um grupo")
    void todasAsTabelasClassificadas() {
        Set<String> noBanco = new HashSet<>(jdbcTemplate.queryForList(
            "SELECT DISTINCT table_name FROM information_schema.columns "
            + "WHERE table_schema = 'public' AND column_name = 'tenant_id'", String.class));

        List<String> todasClassificadas = new ArrayList<>();
        todasClassificadas.addAll(TenantResetService.TABELAS_OPERACIONAL);
        todasClassificadas.addAll(TenantResetService.TABELAS_FROTA);
        todasClassificadas.addAll(TenantResetService.TABELAS_TOTAL);
        todasClassificadas.addAll(TenantResetService.TABELAS_EQUIPE_ESPECIAL);
        todasClassificadas.addAll(TenantResetService.TABELAS_PRESERVADAS);

        // Sem duplicata entre grupos (grupo é decisão única)
        Set<String> unicas = new HashSet<>(todasClassificadas);
        assertThat(todasClassificadas)
            .as("tabela classificada em MAIS de um grupo do TenantResetService")
            .hasSameSizeAs(unicas);

        Set<String> naoClassificadas = new HashSet<>(noBanco);
        naoClassificadas.removeAll(unicas);
        assertThat(naoClassificadas)
            .as("tabelas multi-tenant NOVAS sem destino no reset — classifique no "
                + "TenantResetService (apagável por nível ou preservada com justificativa)")
            .isEmpty();

        Set<String> fantasmas = new HashSet<>(unicas);
        fantasmas.removeAll(noBanco);
        assertThat(fantasmas)
            .as("tabelas classificadas no TenantResetService que não existem mais no schema")
            .isEmpty();
    }

    /**
     * Guarda da ORDEM de deleção: FK NO ACTION/RESTRICT entre tabelas apagáveis
     * exige o filho ANTES do pai na lista — quatro pares estavam invertidos e
     * só explodiam quando o padrão de dados existia (locacao→reserva estourou
     * em produção em 28/jul, no primeiro tenant com locação ligada a reserva).
     * CASCADE/SET NULL ficam de fora: qualquer ordem funciona para elas.
     */
    @Test
    @DisplayName("ordem de deleção: filho com FK restritiva vem antes do pai")
    void ordemDeDelecaoRespeitaFks() {
        List<String> ordem = new ArrayList<>();
        ordem.addAll(TenantResetService.TABELAS_OPERACIONAL);
        ordem.addAll(TenantResetService.TABELAS_FROTA);
        ordem.addAll(TenantResetService.TABELAS_TOTAL);
        // equipe (membro/tenant_access) roda DEPOIS de tudo, mas é Set sem
        // ordem interna — coberta pelo assert de independência logo abaixo
        ordem.addAll(TenantResetService.TABELAS_EQUIPE_ESPECIAL);

        String csv = String.join(",", ordem);
        List<String> violacoes = jdbcTemplate.query(
            "SELECT src.relname AS filho, tgt.relname AS pai "
            + "FROM pg_constraint c "
            + "JOIN pg_class src ON src.oid = c.conrelid "
            + "JOIN pg_class tgt ON tgt.oid = c.confrelid "
            + "WHERE c.contype = 'f' AND c.confdeltype IN ('a', 'r') "
            + "AND src.relname = ANY(string_to_array(?, ',')) "
            + "AND tgt.relname = ANY(string_to_array(?, ',')) "
            + "AND src.relname <> tgt.relname",
            (rs, i) -> rs.getString("filho") + " → " + rs.getString("pai"),
            csv, csv).stream()
            .filter(par -> {
                String[] p = par.split(" → ");
                return ordem.indexOf(p[0]) > ordem.indexOf(p[1]);
            })
            .toList();
        assertThat(violacoes)
            .as("FK restritiva com o PAI sendo apagado antes do filho — reordene as "
                + "listas do TenantResetService (filho primeiro)")
            .isEmpty();

        // As duas tabelas de equipe não podem depender uma da outra (o Set não
        // tem ordem); se uma migration criar essa FK, escolha uma ordem explícita.
        List<String> entreEquipe = jdbcTemplate.queryForList(
            "SELECT c.conname FROM pg_constraint c "
            + "JOIN pg_class src ON src.oid = c.conrelid "
            + "JOIN pg_class tgt ON tgt.oid = c.confrelid "
            + "WHERE c.contype = 'f' AND c.confdeltype IN ('a', 'r') "
            + "AND src.relname IN ('membro', 'tenant_access') "
            + "AND tgt.relname IN ('membro', 'tenant_access') "
            + "AND src.relname <> tgt.relname", String.class);
        assertThat(entreEquipe)
            .as("FK restritiva entre membro e tenant_access — o Set de equipe não tem ordem")
            .isEmpty();
    }
}
