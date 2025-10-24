package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO: Update Modelo Request
 *
 * Request to update an existing jetski model.
 * All fields are optional - only provided fields will be updated.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeloUpdateRequest {

    @Size(min = 2, max = 255, message = "Nome deve ter entre 2 e 255 caracteres")
    private String nome;

    @Size(max = 255, message = "Fabricante deve ter no máximo 255 caracteres")
    private String fabricante;

    @Min(value = 1, message = "Potência deve ser maior que zero")
    private Integer potenciaHp;

    @Min(value = 1, message = "Capacidade deve ser pelo menos 1 pessoa")
    private Integer capacidadePessoas;

    @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
    private BigDecimal precoBaseHora;

    @Min(value = 0, message = "Tolerância não pode ser negativa")
    private Integer toleranciaMin;

    @DecimalMin(value = "0.00", message = "Taxa hora extra não pode ser negativa")
    private BigDecimal taxaHoraExtra;

    private Boolean incluiCombustivel;

    @DecimalMin(value = "0.00", message = "Caução não pode ser negativa")
    private BigDecimal caucao;

    private String fotoReferenciaUrl;

    private String pacotesJson;
}
