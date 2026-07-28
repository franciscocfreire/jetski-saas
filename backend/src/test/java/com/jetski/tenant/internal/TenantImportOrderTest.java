package com.jetski.tenant.internal;

import com.jetski.integration.AbstractIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda da ordem de INSERT do import de arquivamento: como nenhuma FK do
 * schema é DEFERRABLE, o {@link TenantImportService} insere em ordem topológica
 * calculada do catálogo. Este teste falha o build se uma migration futura
 * introduzir algo que quebre essa premissa.
 */
@DisplayName("Import de empresa — ordem topológica das tabelas importáveis")
class TenantImportOrderTest extends AbstractIntegrationTest {

    @Autowired
    private TenantImportService importService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Set<String> importaveis() {
        Set<String> tabelas = new HashSet<>(jdbcTemplate.queryForList(
            "SELECT DISTINCT table_name FROM information_schema.columns "
            + "WHERE table_schema = 'public' AND column_name = 'tenant_id'", String.class));
        tabelas.removeAll(TenantResetService.TABELAS_PRESERVADAS);
        return tabelas;
    }

    @Test
    @DisplayName("topo sort cobre todas as importáveis, com pai sempre antes do filho")
    void ordemCompleta() {
        Set<String> tabelas = importaveis();
        List<String> ordem = importService.ordemTopologica(tabelas);

        assertThat(ordem)
            .as("a ordem topológica deve cobrir exatamente as tabelas importáveis "
                + "(ciclo de FKs lança IllegalStateException antes deste assert)")
            .containsExactlyInAnyOrderElementsOf(tabelas);

        String csv = String.join(",", tabelas);
        List<Map<String, Object>> deps = jdbcTemplate.query(
            "SELECT DISTINCT src.relname AS filho, tgt.relname AS pai "
            + "FROM pg_constraint c "
            + "JOIN pg_class src ON src.oid = c.conrelid "
            + "JOIN pg_class tgt ON tgt.oid = c.confrelid "
            + "WHERE c.contype = 'f' AND src.relnamespace = 'public'::regnamespace "
            + "AND src.relname = ANY(string_to_array(?, ',')) "
            + "AND tgt.relname = ANY(string_to_array(?, ',')) "
            + "AND src.relname <> tgt.relname",
            (rs, i) -> Map.of("filho", rs.getString("filho"), "pai", rs.getString("pai")),
            csv, csv);
        for (Map<String, Object> d : deps) {
            String filho = (String) d.get("filho");
            String pai = (String) d.get("pai");
            assertThat(ordem.indexOf(pai))
                .as("FK %s → %s: o pai precisa vir antes do filho na ordem de INSERT", filho, pai)
                .isLessThan(ordem.indexOf(filho));
        }
    }

    @Test
    @DisplayName("nenhuma tabela importável tem FK auto-referente")
    void semAutoReferencia() {
        List<String> autoFk = jdbcTemplate.queryForList(
            "SELECT DISTINCT src.relname FROM pg_constraint c "
            + "JOIN pg_class src ON src.oid = c.conrelid "
            + "WHERE c.contype = 'f' AND c.conrelid = c.confrelid "
            + "AND src.relname = ANY(string_to_array(?, ','))",
            String.class, String.join(",", importaveis()));

        assertThat(autoFk)
            .as("FK auto-referente em tabela importável: o INSERT em lote do import "
                + "não ordena linhas DENTRO da tabela — trate a ordenação intra-tabela "
                + "no TenantImportService antes de seguir")
            .isEmpty();
    }
}
