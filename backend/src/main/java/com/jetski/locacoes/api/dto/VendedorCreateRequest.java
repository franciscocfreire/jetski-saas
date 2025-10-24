package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.VendedorTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Create Vendedor Request
 *
 * Request to register a new seller or partner.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendedorCreateRequest {

    @NotBlank(message = "Nome do vendedor é obrigatório")
    @Size(min = 2, max = 255, message = "Nome deve ter entre 2 e 255 caracteres")
    private String nome;

    @Size(max = 20, message = "Documento deve ter no máximo 20 caracteres")
    private String documento;

    @NotNull(message = "Tipo do vendedor é obrigatório (INTERNO ou PARCEIRO)")
    private VendedorTipo tipo;

    private String regraComissaoJson;
}
