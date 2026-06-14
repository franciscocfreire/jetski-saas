package com.jetski.pagamentos.api.dto;

import com.jetski.locacoes.domain.TipoChavePix;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO: Pendencias Pagamento Response
 *
 * Response containing pending payment summary for a seller.
 * Includes both pending commissions (approved) and unpaid daily allowances.
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendenciasPagamentoResponse {

    private UUID vendedorId;
    private String vendedorNome;
    private String vendedorEmail;

    // PIX Info
    private String chavePix;
    private TipoChavePix tipoChavePix;
    private boolean temPixCadastrado;

    // Commissions (APROVADA status)
    private BigDecimal valorComissoes;
    private int qtdComissoes;

    // Daily Allowances (not paid)
    private BigDecimal valorDiarias;
    private int qtdDiarias;

    // Bonuses (APROVADO status)
    private BigDecimal valorBonus;
    private int qtdBonus;

    // Total (commissions + diarias + bonus)
    private BigDecimal valorTotal;
    private int qtdTotal;
}
