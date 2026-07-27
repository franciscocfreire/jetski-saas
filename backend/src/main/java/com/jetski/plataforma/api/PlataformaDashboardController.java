package com.jetski.plataforma.api;

import com.jetski.plataforma.internal.PlataformaMetricasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Dashboard da plataforma (F4): lê o read model com UM select, sem varrer empresa a empresa.
 *
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/platform/dashboard")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlataformaDashboardController {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbc;
    private final PlataformaMetricasService metricasService;

    /**
     * @param dias         janela consultada
     * @param atualizadoEm quando o read model foi calculado (null = job nunca rodou)
     */
    public record DashboardResponse(
        int dias,
        java.time.Instant atualizadoEm,
        Map<String, Object> totais,
        List<Map<String, Object>> serie,
        List<Map<String, Object>> topEmpresas) {}

    @GetMapping
    @Operation(summary = "Indicadores consolidados da plataforma",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<DashboardResponse> dashboard(
            @RequestParam(defaultValue = "30") int dias) {
        int janela = Math.min(Math.max(dias, 1), 180);
        LocalDate ate = LocalDate.now(FUSO);
        LocalDate de = ate.minusDays(janela - 1L);

        Map<String, Object> totais = jdbc.queryForMap("""
            SELECT COALESCE(SUM(locacoes),0) AS locacoes,
                   COALESCE(SUM(reservas),0) AS reservas,
                   COALESCE(SUM(no_shows),0) AS no_shows,
                   COALESCE(SUM(receita_bruta),0) AS receita_bruta,
                   COALESCE(SUM(receita_comissionavel),0) AS receita_comissionavel,
                   COALESCE(SUM(emissoes_documento + emissoes_gru),0) AS emissoes_cobraveis,
                   COALESCE(SUM(emissoes_previa),0) AS emissoes_previa,
                   COALESCE(SUM(creditos_consumidos),0) AS creditos_consumidos
              FROM plataforma_metrica_diaria WHERE dia BETWEEN ? AND ?
            """, de, ate);

        // MRR e faturas: estado, não soma da janela — vale a linha MAIS RECENTE de cada
        // empresa (ver comentário da coluna faturas_abertas na V056).
        Map<String, Object> estado = jdbc.queryForMap("""
            SELECT COALESCE(SUM(m.mrr),0) AS mrr,
                   COALESCE(SUM(m.faturas_abertas),0) AS faturas_abertas,
                   COALESCE(SUM(m.valor_em_aberto),0) AS valor_em_aberto,
                   MAX(m.atualizado_em) AS atualizado_em
              FROM plataforma_metrica_diaria m
              JOIN (SELECT tenant_id, MAX(dia) AS dia
                      FROM plataforma_metrica_diaria GROUP BY tenant_id) u
                ON u.tenant_id = m.tenant_id AND u.dia = m.dia
            """);
        totais.putAll(estado);

        List<Map<String, Object>> serie = jdbc.queryForList("""
            SELECT dia,
                   SUM(locacoes) AS locacoes,
                   SUM(receita_bruta) AS receita_bruta,
                   SUM(emissoes_documento + emissoes_gru) AS emissoes
              FROM plataforma_metrica_diaria WHERE dia BETWEEN ? AND ?
             GROUP BY dia ORDER BY dia
            """, de, ate);

        List<Map<String, Object>> top = jdbc.queryForList("""
            SELECT t.id, t.slug, t.razao_social,
                   SUM(m.locacoes) AS locacoes,
                   SUM(m.receita_bruta) AS receita_bruta,
                   SUM(m.emissoes_documento + m.emissoes_gru) AS emissoes
              FROM plataforma_metrica_diaria m JOIN tenant t ON t.id = m.tenant_id
             WHERE m.dia BETWEEN ? AND ?
             GROUP BY t.id, t.slug, t.razao_social
             HAVING SUM(m.receita_bruta) > 0 OR SUM(m.locacoes) > 0
             ORDER BY SUM(m.receita_bruta) DESC LIMIT 10
            """, de, ate);

        Object atualizado = estado.get("atualizado_em");
        return ResponseEntity.ok(new DashboardResponse(
            janela,
            atualizado instanceof java.sql.Timestamp ts ? ts.toInstant() : null,
            totais, serie, top));
    }

    /**
     * Recálculo manual — para backfill e para conferir divergência sem esperar a madrugada.
     */
    @PostMapping("/recalcular")
    @Operation(summary = "Recalcular o read model no intervalo informado",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<PlataformaMetricasService.Resultado> recalcular(
            @RequestParam(required = false) String de,
            @RequestParam(required = false) String ate) {
        LocalDate fim = ate != null ? LocalDate.parse(ate) : LocalDate.now(FUSO);
        LocalDate inicio = de != null ? LocalDate.parse(de) : fim.minusDays(6);
        return ResponseEntity.ok(metricasService.recalcular(inicio, fim));
    }
}
