package com.jetski.locacoes.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class InstrutorResponse {
    private UUID id;
    private UUID tenantId;
    private String nome;
    private String rg;
    private String orgaoEmissor;
    private String cpf;
    private String cha;
    private java.time.LocalDate dataEmissao;
    private Boolean temAssinatura;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}
