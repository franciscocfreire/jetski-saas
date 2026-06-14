package com.jetski.manutencao.api.dto;

import com.jetski.despesas.domain.StatusDespesa;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for DespesaManutencao
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DespesaManutencaoResponse {

    private UUID id;
    private UUID tenantId;
    private UUID osManutencaoId;
    private String osNumero;  // Numero da OS para exibicao
    private LocalDate dtVencimento;
    private Integer numeroParcela;
    private Integer totalParcelas;
    private String descricaoParcela;  // "Parcela 1/3" ou "Pagamento unico"
    private BigDecimal valor;
    private StatusDespesa status;
    private Integer aprovadoPor;
    private Instant aprovadoEm;
    private Integer pagoPor;
    private Instant pagoEm;
    private String referenciaPagamento;
    private String observacoes;
    private Instant createdAt;
    private Instant updatedAt;

    // Dados da OS para contexto
    private String jetskiNome;
    private String descricaoProblema;
    private BigDecimal valorTotalOS;  // Valor total da OS (todas as parcelas)
}
