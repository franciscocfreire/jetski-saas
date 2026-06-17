package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AceiteResponse {

    private UUID id;
    private UUID reservaId;
    private UUID operadorId;
    private String metodo;
    private String assinaturaS3Key;
    private String hashSha256;
    private String origem;
    private Instant aceitoEm;
}
