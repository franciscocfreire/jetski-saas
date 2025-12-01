package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.ModalidadePreco;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO: CheckInWalkInRequest
 *
 * Request to perform walk-in check-in (without prior reservation).
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInWalkInRequest {

    /**
     * Jetski ID to rent
     */
    @NotNull(message = "Jetski ID é obrigatório")
    private UUID jetskiId;

    /**
     * Cliente ID (customer) - opcional para check-in rápido
     * Pode ser associado posteriormente via PATCH
     */
    private UUID clienteId;

    /**
     * Optional Vendedor ID (seller/partner)
     */
    private UUID vendedorId;

    /**
     * Hourmeter reading at check-in
     */
    @NotNull(message = "Horímetro inicial é obrigatório")
    @DecimalMin(value = "0.0", message = "Horímetro deve ser positivo")
    private BigDecimal horimetroInicio;

    /**
     * Expected rental duration in minutes
     */
    @NotNull(message = "Duração prevista é obrigatória")
    @Min(value = 15, message = "Duração mínima é 15 minutos")
    private Integer duracaoPrevista;

    /**
     * Optional notes
     */
    private String observacoes;

    /**
     * Check-in checklist (JSON array of verification items)
     * Example: ["motor_ok", "casco_ok", "gasolina_ok", "equipamentos_ok"]
     * RN05: Mandatory for check-out validation
     */
    private String checklistSaidaJson;

    // ===================================================================
    // Negotiated Price (Optional)
    // ===================================================================

    /**
     * Negotiated price for this rental (optional).
     * If set, this value will be used as valorBase instead of calculating
     * from the model's hourly rate.
     * Use case: Operator negotiates a discount or special price with customer.
     */
    @DecimalMin(value = "0.01", message = "Valor negociado deve ser maior que zero")
    private BigDecimal valorNegociado;

    /**
     * Reason for the negotiated price/discount (optional, for audit trail).
     * Examples: "Cliente frequente", "Promoção verão", "Desconto grupo"
     */
    private String motivoDesconto;

    // ===================================================================
    // Pricing Mode (Optional)
    // ===================================================================

    /**
     * Pricing mode for this rental (optional, default: PRECO_FECHADO).
     * - PRECO_FECHADO: Fixed/negotiated price or calculated from hourly rate
     * - DIARIA: Full day rental price
     * - MEIA_DIARIA: Half day rental price
     */
    @Builder.Default
    private ModalidadePreco modalidadePreco = ModalidadePreco.PRECO_FECHADO;
}
