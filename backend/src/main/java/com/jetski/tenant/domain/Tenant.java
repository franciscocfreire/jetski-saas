package com.jetski.tenant.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Tenant (Multi-tenant Organization)
 *
 * Represents a tenant organization in the SaaS multi-tenant architecture.
 * Each tenant is an independent customer (company) with isolated data.
 *
 * Examples:
 * - ACME Locações Ltda (slug: acme)
 * - Beach Rentals (slug: beach-rentals)
 * - Premium JetSki (slug: premium)
 *
 * @author Jetski Team
 * @since 0.1.0
 */
@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Unique slug for URL-friendly identification
     * Examples: "acme", "beach-rentals", "premium"
     * Used in: subdomain (acme.jetski.app) or path (/t/acme)
     */
    @Column(unique = true, nullable = false, length = 50)
    private String slug;

    /**
     * Legal business name (Razão Social)
     * Example: "ACME Locações de Jet Ski Ltda"
     */
    @Column(name = "razao_social", nullable = false, length = 200)
    private String razaoSocial;

    /**
     * Brazilian CNPJ (company registration number)
     * Format: XX.XXX.XXX/XXXX-XX
     */
    @Column(length = 18)
    private String cnpj;

    /**
     * Tenant status
     * ATIVO: Active and operational
     * SUSPENSO: Suspended (payment issues, violations)
     * INATIVO: Deactivated (closed account)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status;

    /**
     * Timezone for date/time operations
     * Examples: "America/Sao_Paulo", "America/Fortaleza"
     */
    @Column(nullable = false, length = 50)
    private String timezone;

    /**
     * Currency code (ISO 4217)
     * Example: "BRL" (Brazilian Real)
     */
    @Column(nullable = false, length = 3)
    private String moeda;

    /**
     * Creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Last update timestamp
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ========== CAMPOS DE CONTATO E LOCALIZAÇÃO ==========

    /**
     * WhatsApp para contato no marketplace
     * Formato: 5548999999999 (código país + DDD + número)
     */
    @Column(length = 20)
    private String whatsapp;

    /**
     * Cidade para exibição no marketplace
     * Exemplo: "Florianópolis"
     */
    @Column(length = 100)
    private String cidade;

    /**
     * UF (Unidade Federativa) para exibição no marketplace
     * Exemplo: "SC", "SP", "RJ"
     */
    @Column(length = 2)
    private String uf;

    // ========== CAMPOS DE MARKETPLACE ==========

    /**
     * Controla se o tenant aparece no marketplace público
     * Se false, nenhum modelo deste tenant será listado
     */
    @Column(name = "exibir_no_marketplace", nullable = false)
    @Builder.Default
    private Boolean exibirNoMarketplace = true;

    /**
     * Prioridade para ordenação no marketplace (0-100)
     * Quanto maior o valor, mais destaque na listagem
     * Usado para sistema de anúncios pagos/premium
     */
    @Column(name = "prioridade_marketplace", nullable = false)
    @Builder.Default
    private Integer prioridadeMarketplace = 0;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = TenantStatus.ATIVO;
        }
        if (timezone == null) {
            timezone = "America/Sao_Paulo";
        }
        if (moeda == null) {
            moeda = "BRL";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
