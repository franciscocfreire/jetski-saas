package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO: registrar aceite/assinatura presencial.
 * metodo = "SIGNATURE_PAD" (assinatura capturada) ou "PAPEL" (digitalizado à parte).
 * assinaturaBase64 = PNG em base64 (dataURL ou puro); obrigatório p/ SIGNATURE_PAD.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AceiteRequest {

    @NotBlank(message = "metodo é obrigatório (SIGNATURE_PAD ou PAPEL)")
    private String metodo;

    private String assinaturaBase64;
}
