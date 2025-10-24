package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.VendedorTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO: Vendedor Response
 *
 * Response containing seller/partner details.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendedorResponse {

    private UUID id;
    private UUID tenantId;
    private String nome;
    private String documento;
    private VendedorTipo tipo;
    private String regraComissaoJson;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}
