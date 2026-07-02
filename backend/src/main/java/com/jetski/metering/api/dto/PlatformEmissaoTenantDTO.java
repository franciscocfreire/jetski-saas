package com.jetski.metering.api.dto;

import java.util.UUID;

/** Uso de emissões de um tenant numa competência (visão do super admin). */
public record PlatformEmissaoTenantDTO(
        UUID tenantId,
        String slug,
        String razaoSocial,
        long documento,
        long gru,
        long previa,
        long total
) {}
