package com.jetski.locacoes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO: Cliente Response
 *
 * Response containing rental customer details.
 * Phone numbers in E.164 international format: +5511987654321
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteResponse {

    private UUID id;
    private UUID tenantId;
    private String nome;
    private String documento;
    private java.time.LocalDate dataNascimento;
    private String genero;
    private String email;
    private String telefone;
    private String whatsapp;
    private String enderecoJson;
    private Boolean termoAceite;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}
