package com.jetski.tenant.api.dto;

/**
 * Atualização do white-label: cores + conteúdo da vitrine pública. Nulos/vazios
 * ⇒ cores voltam ao padrão Meu Jet e a seção correspondente some da vitrine.
 * O logo é gerenciado pelos endpoints dedicados de upload/remoção.
 */
public record BrandingRequest(
        String corPrimaria,
        String corSecundaria,
        String vitrineDescricao,
        String vitrineEndereco,
        String vitrinePraia,
        String vitrineHorario,
        String vitrineInstagram,
        String vitrineSite
) {}
