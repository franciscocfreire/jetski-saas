package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.VendedorTipo;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Update Vendedor Request
 *
 * Request to update an existing seller or partner.
 * All fields are optional - only provided fields will be updated.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendedorUpdateRequest {

    @Size(min = 2, max = 255, message = "Nome deve ter entre 2 e 255 caracteres")
    private String nome;

    @Size(max = 20, message = "Documento deve ter no máximo 20 caracteres")
    private String documento;

    private VendedorTipo tipo;

    private String regraComissaoJson;
}
