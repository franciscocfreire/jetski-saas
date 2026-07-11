package com.jetski.tenant.domain;

/**
 * Enum: TenantStatus
 *
 * Possible status values for a tenant organization.
 *
 * @author Jetski Team
 * @since 0.1.0
 */
public enum TenantStatus {
    /**
     * Pending approval - empresa recém-cadastrada aguardando liberação de um super admin.
     * Não opera até ser aprovada (ver gate em TenantFilter).
     */
    PENDENTE_APROVACAO,

    /**
     * Trial tenant - testing period
     */
    TRIAL,

    /**
     * Active tenant - fully operational
     */
    ATIVO,

    /**
     * Suspended tenant - temporarily disabled
     * Reasons: payment issues, policy violations, maintenance
     */
    SUSPENSO,

    /**
     * Canceled tenant - subscription ended
     */
    CANCELADO,

    /**
     * Inactive tenant - permanently disabled
     * Account closed or migrated
     */
    INATIVO,

    /**
     * Excluída (tombstone): dados e arquivos expurgados; a linha permanece
     * anonimizada para preservar as FKs do ledger de créditos/metering
     * (histórico fiscal da plataforma). Slug renomeado — liberado p/ reuso.
     */
    EXCLUIDO
}
