package com.jetski.locacoes.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO: EditFinalizadaLocacaoRequest
 *
 * Request to edit a finalized rental (FINALIZADA status).
 * Only allowed when FechamentoDiario for the rental date is NOT bloqueado.
 * Requires GERENTE or ADMIN_TENANT role.
 *
 * All fields except motivoEdicao are optional - only provided fields will be updated.
 *
 * @author Jetski Team
 * @since 0.11.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditFinalizadaLocacaoRequest {

    // === Check-in data ===

    /**
     * New check-in date/time
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dataCheckIn;

    /**
     * Engine hour reading at check-in
     */
    @DecimalMin(value = "0.0", message = "Horímetro deve ser positivo")
    private BigDecimal horimetroInicio;

    // === Check-out data ===

    /**
     * New check-out date/time
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dataCheckOut;

    /**
     * Engine hour reading at check-out
     */
    @DecimalMin(value = "0.0", message = "Horímetro deve ser positivo")
    private BigDecimal horimetroFim;

    // === Time calculations (allows manual override) ===

    /**
     * Total minutes used (can be manually overridden)
     */
    private Integer minutosUsados;

    /**
     * Billable minutes after tolerance and rounding (can be manually overridden)
     */
    private Integer minutosFaturaveis;

    // === Value fields ===

    /**
     * Base rental value
     */
    @DecimalMin(value = "0.0", message = "Valor deve ser positivo")
    private BigDecimal valorBase;

    /**
     * Negotiated price override (if applicable)
     */
    @DecimalMin(value = "0.0", message = "Valor deve ser positivo")
    private BigDecimal valorNegociado;

    /**
     * Final total value (base + fuel + optional items)
     */
    @DecimalMin(value = "0.0", message = "Valor deve ser positivo")
    private BigDecimal valorTotal;

    /**
     * Fuel cost
     */
    @DecimalMin(value = "0.0", message = "Custo de combustível deve ser positivo")
    private BigDecimal combustivelCusto;

    // === Associations ===

    /**
     * Seller/partner ID (can be changed to correct commission attribution)
     */
    private UUID vendedorId;

    // === Other fields ===

    /**
     * Reason for discount (if any)
     */
    private String motivoDesconto;

    /**
     * Additional observations
     */
    private String observacoes;

    /**
     * Reason for the edit (mandatory for audit trail)
     */
    @NotBlank(message = "Motivo da edição é obrigatório")
    private String motivoEdicao;
}
