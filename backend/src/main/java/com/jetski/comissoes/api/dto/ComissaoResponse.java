package com.jetski.comissoes.api.dto;

import com.jetski.comissoes.domain.NivelPolitica;
import com.jetski.comissoes.domain.StatusComissao;
import com.jetski.comissoes.domain.TipoComissao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for Comissao (Commission)
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComissaoResponse {

    private UUID id;
    private UUID locacaoId;
    private UUID vendedorId;
    private UUID politicaId;

    private StatusComissao status;
    private Instant dataLocacao;

    // Valores
    private BigDecimal valorTotalLocacao;
    private BigDecimal valorCombustivel;
    private BigDecimal valorMultas;
    private BigDecimal valorTaxas;
    private BigDecimal valorComissionavel;
    private BigDecimal valorComissao;

    // Política aplicada
    private TipoComissao tipoComissao;
    private BigDecimal percentualAplicado;
    private String politicaNome;
    private NivelPolitica politicaNivel;

    // Aprovação
    private UUID aprovadoPor;
    private Instant aprovadoEm;

    // Pagamento
    private UUID pagoPor;
    private Instant pagoEm;
    private String referenciaPagamento;

    // Auditoria
    private Instant createdAt;
    private Instant updatedAt;
}
