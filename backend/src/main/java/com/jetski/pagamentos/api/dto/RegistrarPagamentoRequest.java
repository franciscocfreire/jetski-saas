package com.jetski.pagamentos.api.dto;

import com.jetski.pagamentos.domain.TipoPagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO: Registrar Pagamento Request
 *
 * Request to register a payment for a seller.
 * Supports both full payment (all pending items) and partial payment (selected items).
 *
 * If no IDs are provided (comissaoIds, presencaIds, bonusIds all empty/null),
 * all pending items will be paid.
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarPagamentoRequest {

    /**
     * Payment type (PIX or DINHEIRO) - required
     */
    @NotNull(message = "Tipo de pagamento é obrigatório")
    private TipoPagamento tipoPagamento;

    /**
     * Reference of the payment (PIX transaction ID, E2E, etc.)
     * Optional for DINHEIRO payments
     */
    @Size(max = 100, message = "Referência deve ter no máximo 100 caracteres")
    private String referenciaPagamento;

    /**
     * Optional notes about the payment
     */
    @Size(max = 500, message = "Observações devem ter no máximo 500 caracteres")
    private String observacoes;

    // ========== Partial Payment (optional) ==========
    // If all lists are null/empty, pays everything

    /**
     * List of commission IDs to pay (for partial payment)
     */
    private List<UUID> comissaoIds;

    /**
     * List of presenca (daily allowance) IDs to pay (for partial payment)
     */
    private List<UUID> presencaIds;

    /**
     * List of bonus IDs to pay (for partial payment)
     */
    private List<UUID> bonusIds;

    /**
     * Check if this is a partial payment (some IDs specified)
     */
    public boolean isPagamentoParcial() {
        return (comissaoIds != null && !comissaoIds.isEmpty())
            || (presencaIds != null && !presencaIds.isEmpty())
            || (bonusIds != null && !bonusIds.isEmpty());
    }
}
