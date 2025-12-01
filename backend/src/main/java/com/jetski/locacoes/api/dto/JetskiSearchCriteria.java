package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.JetskiStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO: Jetski Search Criteria
 *
 * Advanced search and filter criteria for fleet management.
 * All fields are optional - empty criteria returns all jetskis.
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Advanced search criteria for jetski fleet filtering")
public class JetskiSearchCriteria {

    /**
     * Filter by one or more status values
     */
    @Schema(description = "Filter by status (DISPONIVEL, LOCADO, MANUTENCAO, INDISPONIVEL)", example = "[\"DISPONIVEL\", \"LOCADO\"]")
    private List<JetskiStatus> status;

    /**
     * Filter by modelo ID
     */
    @Schema(description = "Filter by modelo UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID modeloId;

    /**
     * Search by serial number (partial match, case-insensitive)
     */
    @Schema(description = "Search by serial number (partial match)", example = "JSK")
    private String serie;

    /**
     * Minimum hourmeter reading
     */
    @Schema(description = "Minimum hourmeter reading", example = "0")
    private BigDecimal horimetroMin;

    /**
     * Maximum hourmeter reading
     */
    @Schema(description = "Maximum hourmeter reading", example = "200")
    private BigDecimal horimetroMax;

    /**
     * Filter by active status (default: true = only active jetskis)
     */
    @Schema(description = "Show only active jetskis", example = "true")
    @Builder.Default
    private Boolean ativo = true;

    /**
     * Sort field
     */
    @Schema(description = "Sort by field (serie, horimetroAtual, status)", example = "serie")
    @Builder.Default
    private String sortBy = "serie";

    /**
     * Sort direction
     */
    @Schema(description = "Sort direction (asc, desc)", example = "asc")
    @Builder.Default
    private String sortDirection = "asc";

    /**
     * Check if any filters are applied
     */
    public boolean hasFilters() {
        return (status != null && !status.isEmpty())
                || modeloId != null
                || (serie != null && !serie.isBlank())
                || horimetroMin != null
                || horimetroMax != null;
    }
}
