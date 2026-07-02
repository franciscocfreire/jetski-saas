package com.jetski.tenant.api.dto;

/**
 * Atualização das cores do branding. Nulos/vazios ⇒ volta ao padrão Meu Jet.
 * O logo é gerenciado pelos endpoints dedicados de upload/remoção.
 */
public record BrandingRequest(
        String corPrimaria,
        String corSecundaria
) {}
