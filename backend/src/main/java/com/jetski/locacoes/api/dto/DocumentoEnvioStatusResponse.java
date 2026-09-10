package com.jetski.locacoes.api.dto;

import com.jetski.locacoes.domain.EnvioStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Estado do envio por e-mail de um documento emitido — o que a tela do balcão
 * consulta enquanto os e-mails saem fora do request.
 *
 * <p>{@code concluido} é derivado no servidor de propósito: a condição de parada do
 * polling não deve virar uma lista de strings duplicada no frontend.
 */
public record DocumentoEnvioStatusResponse(
    UUID documentoId,
    Destino marinha,
    Destino cliente,
    boolean concluido,
    Instant atualizadoEm
) {
    public record Destino(EnvioStatus status, Instant em, String erro) {}
}
