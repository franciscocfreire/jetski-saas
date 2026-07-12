package com.jetski.tenant.api.dto;

/**
 * Branding do tenant para a UI. Não expõe a chave interna de storage do logo:
 * o logo trafega como data URL pronto para {@code <img src>}.
 */
public record BrandingResponse(
        String corPrimaria,
        String corSecundaria,
        String logoDataUrl,
        String vitrineDescricao,
        String vitrineEndereco,
        String vitrinePraia,
        String vitrineHorario,
        String vitrineInstagram,
        String vitrineSite
) {}
