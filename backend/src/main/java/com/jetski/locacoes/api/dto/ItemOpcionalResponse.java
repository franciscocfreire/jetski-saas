package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: ItemOpcionalResponse
 *
 * Response containing optional add-on item details.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemOpcionalResponse {

    private UUID id;
    private UUID tenantId;
    private String nome;
    private String descricao;
    private BigDecimal precoBase;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}
