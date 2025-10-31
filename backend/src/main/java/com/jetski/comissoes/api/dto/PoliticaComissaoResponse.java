package com.jetski.comissoes.api.dto;

import com.jetski.comissoes.domain.NivelPolitica;
import com.jetski.comissoes.domain.TipoComissao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for PoliticaComissao (Commission Policy)
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoliticaComissaoResponse {

    private UUID id;
    private String nome;
    private NivelPolitica nivel;
    private TipoComissao tipo;

    // Condicionais por nível
    private UUID vendedorId;
    private UUID modeloId;
    private String codigoCampanha;

    // Duração
    private Integer duracaoMinMinutos;
    private Integer duracaoMaxMinutos;

    // Valores por tipo
    private BigDecimal percentualComissao;
    private BigDecimal percentualExtra;
    private BigDecimal valorFixo;

    // Vigência
    private Instant vigenciaInicio;
    private Instant vigenciaFim;

    private Boolean ativa;
    private String descricao;

    // Auditoria
    private Instant createdAt;
    private Instant updatedAt;
}
