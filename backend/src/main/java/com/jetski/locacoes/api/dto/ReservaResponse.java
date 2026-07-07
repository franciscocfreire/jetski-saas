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
    // Pagamento (sinal/total) — F2.1
    private String pagamentoTipo;
    private String pagamentoStatus;
    private String pagamentoMotivoRecusa;
    private BigDecimal valorTotal;
    private Instant documentoEmitidoEm;

    /** Canal de criação: BALCAO (staff) ou PORTAL (cliente online). */
    private String canal;
    private String observacoes;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;

    public static ReservaResponse from(com.jetski.locacoes.domain.Reserva reserva) {
        return ReservaResponse.builder()
            .id(reserva.getId())
            .tenantId(reserva.getTenantId())
            .modeloId(reserva.getModeloId())
            .jetskiId(reserva.getJetskiId())
            .clienteId(reserva.getClienteId())
            .vendedorId(reserva.getVendedorId())
            .dataInicio(reserva.getDataInicio())
            .dataFimPrevista(reserva.getDataFimPrevista())
            .expiraEm(reserva.getExpiraEm())
            .status(reserva.getStatus())
            .prioridade(reserva.getPrioridade())
            .sinalPago(reserva.getSinalPago())
            .valorSinal(reserva.getValorSinal())
            .sinalPagoEm(reserva.getSinalPagoEm())
            .pagamentoTipo(reserva.getPagamentoTipo() != null ? reserva.getPagamentoTipo().name() : null)
            .pagamentoStatus(reserva.getPagamentoStatus() != null ? reserva.getPagamentoStatus().name() : null)
            .pagamentoMotivoRecusa(reserva.getPagamentoMotivoRecusa())
            .valorTotal(reserva.getValorTotal())
            .documentoEmitidoEm(reserva.getDocumentoEmitidoEm())
            .canal(reserva.getCanal() != null ? reserva.getCanal().name() : null)
            .observacoes(reserva.getObservacoes())
            .ativo(reserva.getAtivo())
            .createdAt(reserva.getCreatedAt())
            .updatedAt(reserva.getUpdatedAt())
            .build();
    }
}
