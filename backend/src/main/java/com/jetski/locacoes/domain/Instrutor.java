package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: instrutor (EAMA) que ministra a demonstração prática e assina o
 * Atestado de Demonstração (Anexo 5-B-1, NORMAM-212). Dados reutilizáveis
 * entre locações; tenant-scoped (RLS).
 */
@Entity
@Table(name = "instrutor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instrutor {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 30)
    private String rg;

    @Column(name = "orgao_emissor", length = 30)
    private String orgaoEmissor;

    @Column(length = 20)
    private String cpf;

    /** Número da CHA do instrutor. */
    @Column(length = 60)
    private String cha;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
