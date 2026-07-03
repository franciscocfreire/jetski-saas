package com.jetski.locacoes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Comprovante de pagamento (PIX) anexado a uma reserva — tabela criada na V003
 * (balcão) e plugada no P1 do portal: o CLIENTE anexa o comprovante e o staff
 * valida (confirmar-sinal / recusar-pagamento).
 */
@Entity
@Table(name = "reserva_comprovante")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaComprovante {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "url")
    private String url;

    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    /** Tipo do comprovante (PIX no v1). */
    @Column(name = "tipo", nullable = false, length = 20)
    @Builder.Default
    private String tipo = "PIX";

    @Column(name = "enviado_em", nullable = false)
    @Builder.Default
    private Instant enviadoEm = Instant.now();

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
