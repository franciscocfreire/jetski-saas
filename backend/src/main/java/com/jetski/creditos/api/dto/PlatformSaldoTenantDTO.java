package com.jetski.creditos.api.dto;

import java.util.UUID;

/** Saldo de créditos de um tenant (visão do super admin). */
public record PlatformSaldoTenantDTO(
        UUID tenantId,
        String slug,
        String razaoSocial,
        int saldo
) {}
