package com.jetski.locacoes.api.dto;

import java.time.Instant;

/** Resumo de um anexo presente no cliente (sem os bytes). */
public record AnexoResumo(String tipo, String contentType, Instant atualizadoEm) {}
