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
}
