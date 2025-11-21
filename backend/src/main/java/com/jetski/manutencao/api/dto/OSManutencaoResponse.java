package com.jetski.manutencao.api.dto;

import com.jetski.manutencao.domain.OSManutencaoPrioridade;
import com.jetski.manutencao.domain.OSManutencaoStatus;
import com.jetski.manutencao.domain.OSManutencaoTipo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO Response: OS Manutenção
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta contendo detalhes de uma ordem de serviço de manutenção")
public class OSManutencaoResponse {

    @Schema(description = "UUID da ordem de serviço", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID id;

    @Schema(description = "UUID do tenant", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID tenantId;

    @Schema(description = "UUID do jetski em manutenção", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    private UUID jetskiId;

    @Schema(description = "UUID do mecânico responsável", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private UUID mecanicoId;

    @Schema(description = "Tipo de manutenção", example = "PREVENTIVA")
    private OSManutencaoTipo tipo;

    @Schema(description = "Prioridade", example = "ALTA")
    private OSManutencaoPrioridade prioridade;

    @Schema(description = "Data de abertura da OS", example = "2025-01-15T10:00:00Z")
    private Instant dtAbertura;

    @Schema(description = "Data prevista de início", example = "2025-01-16T08:00:00Z")
    private Instant dtPrevistaInicio;

    @Schema(description = "Data real de início", example = "2025-01-16T09:00:00Z")
    private Instant dtInicioReal;

    @Schema(description = "Data prevista de conclusão", example = "2025-01-16T18:00:00Z")
    private Instant dtPrevistaFim;

    @Schema(description = "Data de conclusão", example = "2025-01-16T17:30:00Z")
    private Instant dtConclusao;

    @Schema(description = "Descrição do problema", example = "Motor falhando em altas rotações")
    private String descricaoProblema;

    @Schema(description = "Diagnóstico técnico", example = "Vela desgastada, filtro de combustível sujo")
    private String diagnostico;

    @Schema(description = "Solução aplicada", example = "Substituição de vela e limpeza do sistema de combustível")
    private String solucao;

    @Schema(description = "Peças utilizadas (JSON)", example = "[{\"nome\":\"Vela\",\"qtd\":2,\"valor\":45.00}]")
    private String pecasJson;

    @Schema(description = "Valor total das peças", example = "150.00")
    private BigDecimal valorPecas;

    @Schema(description = "Valor da mão de obra", example = "200.00")
    private BigDecimal valorMaoObra;

    @Schema(description = "Valor total da OS", example = "350.00")
    private BigDecimal valorTotal;

    @Schema(description = "Horímetro na abertura da OS", example = "125.5")
    private BigDecimal horimetroAbertura;

    @Schema(description = "Horímetro na conclusão da OS", example = "125.8")
    private BigDecimal horimetroConclusao;

    @Schema(description = "Status da OS", example = "EM_ANDAMENTO")
    private OSManutencaoStatus status;

    @Schema(description = "Observações adicionais", example = "Cliente relatou problema intermitente")
    private String observacoes;

    @Schema(description = "Data de criação do registro", example = "2025-01-15T10:00:00Z")
    private Instant createdAt;

    @Schema(description = "Data da última atualização", example = "2025-01-16T09:15:00Z")
    private Instant updatedAt;
}
