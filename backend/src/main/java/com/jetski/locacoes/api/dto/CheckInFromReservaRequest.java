package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.ModalidadePreco;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO: CheckInFromReservaRequest
 *
 * Request to perform check-in from an existing reservation.
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInFromReservaRequest {

    /**
     * Reservation ID to convert to rental
     */
    @NotNull(message = "Reserva ID é obrigatório")
    private UUID reservaId;

    /**
     * Hourmeter reading at check-in (e.g., 100.5 hours)
     */
    @NotNull(message = "Horímetro inicial é obrigatório")
    @DecimalMin(value = "0.0", message = "Horímetro deve ser positivo")
    private BigDecimal horimetroInicio;

    /**
     * Optional notes for check-in
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
