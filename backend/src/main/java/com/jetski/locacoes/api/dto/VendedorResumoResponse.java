package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.VendedorTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO: Vendedor Resume Response
 *
 * Response containing seller summary with commission totals.
 * Used in list views.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendedorResumoResponse {

    private UUID id;
    private String nome;
    private String email;
    private VendedorTipo tipo;
    private Boolean ativo;
    private BigDecimal diariaBase;

    // Resumo de comissões
    private BigDecimal totalPendentes;
    private BigDecimal totalAprovadas;
    private BigDecimal totalPagas;
    private Long qtdLocacoes;
}
