package com.jetski.manutencao.api.dto;

import com.jetski.manutencao.domain.OSManutencaoPrioridade;
import com.jetski.manutencao.domain.OSManutencaoTipo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO Request: Criar OS Manutenção
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requisição para criar uma nova ordem de serviço de manutenção")
public class OSManutencaoCreateRequest {

    @NotNull(message = "Jetski ID é obrigatório")
    @Schema(description = "UUID do jetski em manutenção", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7", required = true)
    private UUID jetskiId;

    @Schema(description = "UUID do mecânico responsável", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private UUID mecanicoId;

    @NotNull(message = "Tipo de manutenção é obrigatório")
    @Schema(description = "Tipo de manutenção", example = "PREVENTIVA", required = true)
    private OSManutencaoTipo tipo;

    @Schema(description = "Prioridade", example = "ALTA")
    @Builder.Default
    private OSManutencaoPrioridade prioridade = OSManutencaoPrioridade.MEDIA;

    @Schema(description = "Data prevista de início", example = "2025-01-16T08:00:00Z")
    private Instant dtPrevistaInicio;

    @Schema(description = "Data prevista de conclusão", example = "2025-01-16T18:00:00Z")
    private Instant dtPrevistaFim;

    @NotBlank(message = "Descrição do problema é obrigatória")
    @Schema(description = "Descrição do problema", example = "Motor falhando em altas rotações", required = true)
    private String descricaoProblema;

    @Schema(description = "Diagnóstico técnico", example = "Vela desgastada")
    private String diagnostico;

    @Schema(description = "Solução prevista", example = "Substituição de vela")
    private String solucao;

    @Schema(description = "Peças utilizadas (JSON)", example = "[{\"nome\":\"Vela\",\"qtd\":2,\"valor\":45.00}]")
    private String pecasJson;

    @PositiveOrZero(message = "Valor das peças deve ser maior ou igual a zero")
    @Schema(description = "Valor estimado das peças", example = "150.00")
    @Builder.Default
    private BigDecimal valorPecas = BigDecimal.ZERO;

    @PositiveOrZero(message = "Valor da mão de obra deve ser maior ou igual a zero")
    @Schema(description = "Valor estimado da mão de obra", example = "200.00")
    @Builder.Default
    private BigDecimal valorMaoObra = BigDecimal.ZERO;

    @Schema(description = "Horímetro atual do jetski", example = "125.5")
    private BigDecimal horimetroAbertura;

    @Schema(description = "Observações adicionais", example = "Cliente relatou problema intermitente")
    private String observacoes;
}
