package com.jetski.locacoes.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Cliente (Customer)
 *
 * Represents a rental customer who can make reservations and rent jetskis.
 * Customers must accept liability terms before first rental.
 *
 * Examples:
 * - Maria Santos (CPF 123.456.789-00) - phone: (11) 98765-4321, email: maria@example.com
 * - Empresa ABC Ltda (CNPJ 12.345.678/0001-90) - corporate customer
 *
 * Business Rules:
 * - RF03.4: Customer must sign liability term (termoAceite) before check-in
 * - Contact information (email, telefone, whatsapp) optional but recommended for notifications
 * - LGPD: Customer data retention governed by tenant policy
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    /**
     * CPF (individual) or CNPJ (legal entity)
     * Optional but recommended for liability tracking
     */
    @Column
    private String documento;

    /**
     * Birth date (optional)
     * Useful for age validation (e.g., minimum age for rental) and demographics
     */
    @Column(name = "data_nascimento")
    private java.time.LocalDate dataNascimento;

    /**
     * Gender (optional)
     * Possible values: MASCULINO, FEMININO, OUTRO, NAO_INFORMADO
     * Used for demographics and statistics
     */
    @Column(name = "genero")
    private String genero;

    /**
     * Email address (optional)
     * Validated with @Email annotation in DTOs
     */
    @Column(name = "email")
    private String email;

    /**
     * Phone number in E.164 international format (optional)
     * Example: +5511987654321 (Brazil), +14155552671 (USA)
     * Validated with @Pattern in DTOs
     */
    @Column(name = "telefone")
    private String telefone;

    /**
     * WhatsApp number in E.164 international format (optional)
     * Example: +5511987654321
     * Validated with @Pattern in DTOs
     */
    @Column(name = "whatsapp")
    private String whatsapp;

    /**
     * Address information (JSONB) - flexible structure for different countries
     *
     * Expected format (Brazil example):
     * {
     *   "cep": "01310-100",
     *   "logradouro": "Av. Paulista",
     *   "numero": "1000",
     *   "complemento": "10º andar",
     *   "bairro": "Bela Vista",
     *   "cidade": "São Paulo",
     *   "estado": "SP",
     *   "pais": "Brasil"
     * }
     */
    @Type(JsonBinaryType.class)
    @Column(name = "endereco", columnDefinition = "jsonb")
    private String enderecoJson;

    /**
     * Indicates if customer has accepted liability terms
     * Business rule RF03.4: Must be true before allowing check-in
     *
     * Note: Actual term signature is stored in Locacao entity
     * This flag indicates customer has accepted general terms
     */
    @Column(name = "termo_aceite")
    @Builder.Default
    private Boolean termoAceite = false;

    /**
     * Soft delete flag - inactive customers cannot make new rentals
     * but historical rentals are preserved (LGPD compliance)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if customer can rent (active and terms accepted)
     * Business rule RF03.4
     */
    public boolean canRent() {
        return ativo && Boolean.TRUE.equals(termoAceite);
    }
}
