package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.Reserva.ReservaPrioridade;
import com.jetski.locacoes.domain.Reserva.ReservaStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO: Reserva Response
 *
 * Response containing reservation details.
 *
 * Includes:
 * - Reservation metadata (ID, tenant, status, dates)
 * - Related entities (modelo, jetski, cliente, vendedor)
 * - Notes and audit timestamps
 *
 * @author Jetski Team
 * @since 0.2.0
 * @version 0.3.0 - Added modeloId for modelo-based booking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaResponse {

    private UUID id;
    private UUID tenantId;
    private UUID modeloId;
    private UUID jetskiId;
    private UUID clienteId;
    private UUID vendedorId;
    private LocalDateTime dataInicio;
    private LocalDateTime dataFimPrevista;
    private LocalDateTime expiraEm;
    private ReservaStatus status;
    private ReservaPrioridade prioridade;
    private Boolean sinalPago;
    private BigDecimal valorSinal;
    private Instant sinalPagoEm;
    private String observacoes;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}
