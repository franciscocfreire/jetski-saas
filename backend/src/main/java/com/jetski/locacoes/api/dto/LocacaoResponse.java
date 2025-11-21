package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.LocacaoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO: LocacaoResponse
 *
 * Response containing rental operation details.
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocacaoResponse {

    private UUID id;
    private UUID tenantId;
    private UUID reservaId;
    private UUID jetskiId;
    private UUID clienteId;
    private UUID vendedorId;

    // Check-in data
    private LocalDateTime dataCheckIn;
    private BigDecimal horimetroInicio;
    private Integer duracaoPrevista;

    // Check-out data
    private LocalDateTime dataCheckOut;
    private BigDecimal horimetroFim;
    private Integer minutosUsados;
    private Integer minutosFaturaveis;

    // Values
    private BigDecimal valorBase;
    private BigDecimal valorTotal;

    // Status
    private LocacaoStatus status;
    private String observacoes;

    // Checklist (RN05)
    private String checklistSaidaJson;
    private String checklistEntradaJson;

    // Audit
    private Instant createdAt;
    private Instant updatedAt;
}
