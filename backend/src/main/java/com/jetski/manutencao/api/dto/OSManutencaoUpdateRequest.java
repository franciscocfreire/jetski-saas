package com.jetski.manutencao.api.dto;

import com.jetski.manutencao.domain.OSManutencaoPrioridade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO Request: Atualizar OS Manutenção
 *
 * <p>Todos os campos são opcionais. Apenas os campos fornecidos serão atualizados.
 * Status changes should use dedicated endpoints (start, finish, cancel, etc.).
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requisição para atualizar uma ordem de serviço de manutenção")
public class OSManutencaoUpdateRequest {

    @Schema(description = "UUID do mecânico responsável", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private UUID mecanicoId;

    @Schema(description = "Prioridade", example = "URGENTE")
    private OSManutencaoPrioridade prioridade;

    @Schema(description = "Data prevista de início", example = "2025-01-16T08:00:00Z")
    private Instant dtPrevistaInicio;

    @Schema(description = "Data prevista de conclusão", example = "2025-01-16T18:00:00Z")
    private Instant dtPrevistaFim;

    @Schema(description = "Descrição do problema", example = "Motor falhando em altas rotações e superaquecendo")
    private String descricaoProblema;

    @Schema(description = "Diagnóstico técnico", example = "Vela desgastada, filtro de combustível sujo, bomba d'água com defeito")
    private String diagnostico;

    @Schema(description = "Solução aplicada", example = "Substituição de vela, limpeza do sistema de combustível, reparo bomba d'água")
    private String solucao;

    @Schema(description = "Peças utilizadas (JSON)", example = "[{\"nome\":\"Vela\",\"qtd\":2,\"valor\":45.00},{\"nome\":\"Bomba d'água\",\"qtd\":1,\"valor\":180.00}]")
    private String pecasJson;

    @PositiveOrZero(message = "Valor das peças deve ser maior ou igual a zero")
    @Schema(description = "Valor total das peças", example = "225.00")
    private BigDecimal valorPecas;

    @PositiveOrZero(message = "Valor da mão de obra deve ser maior ou igual a zero")
    @Schema(description = "Valor da mão de obra", example = "300.00")
    private BigDecimal valorMaoObra;

    @Schema(description = "Observações adicionais", example = "Necessário aguardar chegada de peças")
    private String observacoes;
}
