package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: vínculo entre um cliente e sua identidade no provedor (Keycloak).
 *
 * <p>Criado ao validar o claim-token (F2.7). Diferente de staff, o cliente
 * <strong>não</strong> tem {@code Usuario}/{@code Membro}: o login é mapeado
 * exclusivamente por aqui ({@code provider_user_id} = sub do Keycloak).
 * Tabela {@code cliente_identity_provider} (V004).
 */
@Entity
@Table(name = "cliente_identity_provider")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteIdentityProvider {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (linkedAt == null) linkedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
