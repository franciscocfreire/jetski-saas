package com.jetski.tenant.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Capitania (catálogo de plataforma, V047)
 *
 * Capitania/Delegacia/Agência da Marinha. Tabela SEM tenant_id (leitura
 * global; escrita só pelo super admin). O vínculo de emissão delegada exige
 * operadora e EAMA emissora na MESMA capitania (validação por capitania_id,
 * nunca pelo e-mail — ver EMISSAO_DELEGADA_SPEC §3.1).
 *
 * <p>{@code emailOficial} apenas pré-preenche o {@code tenant.marinha_email};
 * o tenant emissor pode sobrescrever o destino (decisão §8.E).
 *
 * @author Jetski Team
 */
@Entity
@Table(name = "capitania")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Capitania {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Código público da OM, ex.: "CPSP", "CPRJ". */
    @Column(unique = true, nullable = false, length = 12)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 2)
    private String uf;

    @Column(name = "email_oficial", length = 255)
    private String emailOficial;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativa = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (ativa == null) {
            ativa = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
