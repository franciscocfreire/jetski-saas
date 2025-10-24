package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: Update Cliente Request
 *
 * Request to update an existing rental customer.
 * All fields are optional - only provided fields will be updated.
 * Phone numbers must follow E.164 international format: +5511987654321
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteUpdateRequest {

    @Size(min = 2, max = 255, message = "Nome deve ter entre 2 e 255 caracteres")
    private String nome;

    @Size(max = 20, message = "Documento deve ter no máximo 20 caracteres")
    private String documento;

    private java.time.LocalDate dataNascimento;

    private String genero;

    @Email(message = "Email deve ser válido")
    private String email;

    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$",
             message = "Telefone deve seguir formato E.164 internacional (+5511987654321)")
    private String telefone;

    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$",
             message = "WhatsApp deve seguir formato E.164 internacional (+5511987654321)")
    private String whatsapp;

    private String enderecoJson;

    private Boolean termoAceite;
}
