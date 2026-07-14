package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.EmissaoDelegada;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha do painel "Emissões em meu nome" da EAMA emissora (V048).
 *
 * @author Jetski Team
 */
public record EmissaoDelegadaResponse(
    UUID id,
    UUID operadoraTenantId,
    String operadoraNome,
    String condutorNome,
    String condutorCpf,
    String instrutorNome,
    String gruNumero,
    String documentoHash,
    Instant emitidoEm,
    Instant reenviadoEm,
    String reenviadoPara
) {
    public static EmissaoDelegadaResponse of(EmissaoDelegada e) {
        return new EmissaoDelegadaResponse(
            e.getId(), e.getOperadoraTenantId(), e.getOperadoraNome(),
            e.getCondutorNome(), e.getCondutorCpf(), e.getInstrutorNome(),
            e.getGruNumero(), e.getDocumentoHash(), e.getEmitidoEm(),
            e.getReenviadoEm(), e.getReenviadoPara());
    }
}
