package com.jetski.locacoes.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: documento consolidado (PDF) emitido para uma reserva.
 * Registra a key no storage, o hash de integridade e os destinos do envio.
 */
@Entity
@Table(name = "documento_emitido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentoEmitido {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "hash_sha256", nullable = false)
    private String hashSha256;

    /** Resumo dos destinos (JSON): ex. {"marinha":"x@y","cliente":"a@b"}. */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private String destinos;

    @Column(name = "emitido_em", nullable = false)
    private Instant emitidoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (emitidoEm == null) {
            emitidoEm = Instant.now();
        }
        createdAt = Instant.now();
    }
}
