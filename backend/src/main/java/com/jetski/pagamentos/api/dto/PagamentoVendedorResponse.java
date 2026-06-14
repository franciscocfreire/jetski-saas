package com.jetski.pagamentos.api.dto;

import com.jetski.locacoes.domain.TipoChavePix;
import com.jetski.pagamentos.domain.TipoPagamento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO: Pagamento Vendedor Response
 *
 * Response containing payment record details.
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoVendedorResponse {

    private UUID id;
    private UUID tenantId;
    private UUID vendedorId;
    private String vendedorNome;

    // Payment Type
    private TipoPagamento tipoPagamento;

    // Values
    private BigDecimal valorComissoes;
    private BigDecimal valorDiarias;
    private BigDecimal valorBonus;
    private BigDecimal valorTotal;

    // PIX Snapshot
    private String chavePix;
    private TipoChavePix tipoChavePix;

    // Payment Reference
    private String referenciaPagamento;
    private String comprovanteUrl;

    // Quantities
    private Integer qtdComissoes;
    private Integer qtdDiarias;
    private Integer qtdBonus;

    // Period
    private LocalDate periodoInicio;
    private LocalDate periodoFim;

    // Audit
    private UUID pagoPor;
    private String observacoes;
    private Instant createdAt;
}
