package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.LocacaoStatus;
import com.jetski.locacoes.domain.ModalidadePreco;
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

    // Denormalized fields for display
    private String jetskiSerie;
    private String jetskiModeloNome;
    private String clienteNome;

    // Check-in data
    private LocalDateTime dataCheckIn;
    private BigDecimal horimetroInicio;
    private Integer duracaoPrevista;

    // Check-out data
    private LocalDateTime dataCheckOut;
    private BigDecimal horimetroFim;
    private Integer minutosUsados;
    private Integer minutosFaturaveis;

    // Negotiated price (set at check-in if price was negotiated)
    private BigDecimal valorNegociado;
    private String motivoDesconto;

    // Pricing mode
    private ModalidadePreco modalidadePreco;

    // Values
    private BigDecimal valorBase;
    private BigDecimal valorItensOpcionais;
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
