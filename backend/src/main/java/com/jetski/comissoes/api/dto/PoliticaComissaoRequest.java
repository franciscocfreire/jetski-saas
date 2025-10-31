package com.jetski.comissoes.api.dto;

import com.jetski.comissoes.domain.NivelPolitica;
import com.jetski.comissoes.domain.TipoComissao;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for creating/updating PoliticaComissao (Commission Policy)
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoliticaComissaoRequest {

    @NotBlank(message = "Nome da política é obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    @NotNull(message = "Nível da política é obrigatório")
    private NivelPolitica nivel;

    @NotNull(message = "Tipo de comissão é obrigatório")
    private TipoComissao tipo;

    // Condicionais por nível
    private UUID vendedorId;        // Required if nivel = VENDEDOR
    private UUID modeloId;          // Required if nivel = MODELO
    private String codigoCampanha;  // Required if nivel = CAMPANHA

    // Duração (para DURACAO ou condições em outras políticas)
    @Min(value = 0, message = "Duração mínima deve ser >= 0")
    private Integer duracaoMinMinutos;

    @Min(value = 0, message = "Duração máxima deve ser >= 0")
    private Integer duracaoMaxMinutos;

    // Valores por tipo
    @DecimalMin(value = "0.0", inclusive = false, message = "Percentual deve ser > 0")
    @DecimalMax(value = "100.0", message = "Percentual deve ser <= 100")
    private BigDecimal percentualComissao; // Required if tipo = PERCENTUAL or ESCALONADO

    @DecimalMin(value = "0.0", inclusive = false, message = "Percentual extra deve ser > 0")
    @DecimalMax(value = "100.0", message = "Percentual extra deve ser <= 100")
    private BigDecimal percentualExtra; // Required if tipo = ESCALONADO

    @DecimalMin(value = "0.01", message = "Valor fixo deve ser > 0")
    private BigDecimal valorFixo; // Required if tipo = VALOR_FIXO

    // Vigência (para CAMPANHA)
    private Instant vigenciaInicio;
    private Instant vigenciaFim;

    @Builder.Default
    private Boolean ativa = true;

    private String descricao;
}
