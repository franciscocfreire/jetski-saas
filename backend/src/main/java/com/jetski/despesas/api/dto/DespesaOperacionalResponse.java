package com.jetski.despesas.api.dto;

import com.jetski.despesas.domain.CategoriaDespesa;
import com.jetski.despesas.domain.StatusDespesa;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for DespesaOperacional
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DespesaOperacionalResponse {

    private UUID id;
    private UUID tenantId;
    private LocalDate dtReferencia;
    private CategoriaDespesa categoria;
    private String descricao;
    private BigDecimal valor;
    private UUID responsavelId;
    private String responsavelNome;  // Preenchido pelo controller se disponivel
    private StatusDespesa status;
    private UUID aprovadoPor;
    private Instant aprovadoEm;
    private UUID pagoPor;
    private Instant pagoEm;
    private String referenciaPagamento;
    private String observacoes;
    private Instant createdAt;
    private Instant updatedAt;
}
