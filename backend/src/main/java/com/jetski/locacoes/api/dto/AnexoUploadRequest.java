package com.jetski.locacoes.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Upload de anexo do cliente: imagem em dataURL/base64. */
public record AnexoUploadRequest(@NotBlank String conteudoBase64) {}
