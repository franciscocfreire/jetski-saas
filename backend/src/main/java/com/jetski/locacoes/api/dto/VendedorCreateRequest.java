package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.TipoChavePix;
import com.jetski.locacoes.domain.VendedorTipo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
     * Required when chavePix is set.
     */
    private TipoChavePix tipoChavePix;

    /**
     * Seller type - defaults to INTERNO if not provided
     */
    @Builder.Default
    private VendedorTipo tipo = VendedorTipo.INTERNO;

    /**
     * Default commission percentage.
     * Will be converted to regraComissaoJson internally.
     */
    private BigDecimal comissaoPercentual;

    /**
     * Advanced: Full commission rules JSON.
     * If provided, takes precedence over comissaoPercentual.
     */
    private String regraComissaoJson;

    /**
     * Valor base da diária do vendedor.
     * Usado no controle de presença diária.
     */
    private BigDecimal diariaBase;
}
