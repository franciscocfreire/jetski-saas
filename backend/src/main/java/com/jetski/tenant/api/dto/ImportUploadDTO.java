package com.jetski.tenant.api.dto;

/**
 * Resultado do upload de um zip de export externo: a chave onde ele foi
 * gravado no prefixo da plataforma — o import continua sempre por key.
 */
public record ImportUploadDTO(String key, long bytes) {}
