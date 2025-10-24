package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: Modelo Response
 *
 * Response containing jetski model details.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeloResponse {

    private UUID id;
    private UUID tenantId;
    private String nome;
    private String fabricante;
    private Integer potenciaHp;
    private Integer capacidadePessoas;
    private BigDecimal precoBaseHora;
    private Integer toleranciaMin;
    private BigDecimal taxaHoraExtra;
    private Boolean incluiCombustivel;
    private BigDecimal caucao;
    private String fotoReferenciaUrl;
    private String pacotesJson;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}
