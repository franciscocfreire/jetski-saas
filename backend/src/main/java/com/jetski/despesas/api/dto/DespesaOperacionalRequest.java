package com.jetski.despesas.api.dto;

import com.jetski.despesas.domain.CategoriaDespesa;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating/updating DespesaOperacional
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DespesaOperacionalRequest {

    @NotNull(message = "Data de referencia e obrigatoria")
    private LocalDate dtReferencia;

    @NotNull(message = "Categoria e obrigatoria")
    private CategoriaDespesa categoria;

    @Size(max = 255, message = "Descricao deve ter no maximo 255 caracteres")
    private String descricao;

    @NotNull(message = "Valor e obrigatorio")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal valor;

    private UUID responsavelId;

    private String observacoes;
}
