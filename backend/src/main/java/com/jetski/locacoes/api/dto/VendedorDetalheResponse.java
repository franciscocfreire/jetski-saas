package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.VendedorTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: Vendedor Detail Response
 *
 * Full response containing seller details with commission totals and bonus status.
 * Used in detail view.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendedorDetalheResponse {

    private UUID id;
    private UUID tenantId;
    private String nome;
    private String documento;
    private String email;
    private String telefone;
    private VendedorTipo tipo;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;

    // Resumo de comissões
    private BigDecimal totalPendentes;
    private BigDecimal totalAprovadas;
    private BigDecimal totalPagas;
    private Long qtdLocacoes;
    private Long qtdAcimaPrecoBase;

    // Status do bonus
    private BonusStatusResponse bonusStatus;

    /**
     * Nested DTO for bonus status
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BonusStatusResponse {
        private Boolean elegivel;
        private Long metaAtual;
        private Integer metaNecessaria;
        private BigDecimal valorBonus;
        private Long vendasFaltando;
    }
}
