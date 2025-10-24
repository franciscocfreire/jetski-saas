package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.JetskiStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO: Jetski Response
 *
 * Response containing jetski unit details.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JetskiResponse {

    private UUID id;
    private UUID tenantId;
    private UUID modeloId;
    private String serie;
    private Integer ano;
    private BigDecimal horimetroAtual;
    private JetskiStatus status;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}
