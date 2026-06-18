package com.jetski.locacoes.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/** Item da consulta de documentos emitidos (por cliente/reserva). */
@Value
@Builder
public class DocumentoConsultaResponse {
    UUID id;
    UUID reservaId;
    UUID clienteId;
    String clienteNome;
    Instant emitidoEm;
    String hashSha256;
    /** URL presigned de download (TTL curto). */
    String downloadUrl;
}
