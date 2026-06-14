package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.TipoChavePix;
import com.jetski.locacoes.domain.VendedorTipo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    @Email(message = "Email inválido")
    private String email;

    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    private String telefone;

    /**
     * PIX key for payment transfers.
     */
    @Size(max = 100, message = "Chave PIX deve ter no máximo 100 caracteres")
    private String chavePix;

    /**
     * Type of PIX key (CPF, CNPJ, EMAIL, TELEFONE, ALEATORIA).
     */
    private TipoChavePix tipoChavePix;

    private VendedorTipo tipo;

    /**
     * Default commission percentage.
     * Will be converted to regraComissaoJson internally.
     */
    private BigDecimal comissaoPercentual;

    private String regraComissaoJson;

    /**
     * Valor base da diária do vendedor.
     * Usado no controle de presença diária.
     */
    private BigDecimal diariaBase;
}
