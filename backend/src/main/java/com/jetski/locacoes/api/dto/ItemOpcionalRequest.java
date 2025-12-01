package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: ItemOpcionalRequest
 *
 * Request to create or update an optional add-on item.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemOpcionalRequest {

    /**
     * Item name (e.g., "Gravação Drone", "Action Cam")
     */
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    /**
     * Item description
     */
    @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
    private String descricao;

    /**
     * Base price for this optional item
     */
    @NotNull(message = "Preço base é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço base deve ser maior que zero")
    private BigDecimal precoBase;

    /**
     * Whether this item is active
     */
    private Boolean ativo;
}
