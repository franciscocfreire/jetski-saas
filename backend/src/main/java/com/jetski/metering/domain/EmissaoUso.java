package com.jetski.metering.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Um fato de uso contabilizado (linha imutável). Idempotência garantida pelo
 * índice único (tipo, referencia_id, ocorrido_em): reprocesso do mesmo evento
 * não duplica; regeneração legítima (novo ocorrido_em) conta de novo.
 */
@Entity
@Table(name = "emissao_uso")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissaoUso {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoEmissao tipo;

    @Column(name = "referencia_id", nullable = false)
    private UUID referenciaId;

    /** DOCUMENTO: "marinha,cliente" | GRU: PIX/BOLETO | PREVIA: destino da prévia. */
    @Column(name = "destinos", length = 60)
    private String destinos;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
