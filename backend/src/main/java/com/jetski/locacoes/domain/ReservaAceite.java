package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: aceite/assinatura presencial de uma reserva (trilha de evidências).
 * Append-only; o aceite "atual" é o mais recente. A imagem da assinatura é
 * arquivada no storage (assinatura_s3_key) com hash de integridade.
 */
@Entity
@Table(name = "reserva_aceite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaAceite {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Column(name = "operador_id")
    private UUID operadorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Metodo metodo;

    @Column(name = "assinatura_s3_key")
    private String assinaturaS3Key;

    @Column(name = "hash_sha256")
    private String hashSha256;

    @Column
    private String ip;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String origem = "BALCAO";

    @Column(name = "aceito_em", nullable = false)
    private Instant aceitoEm;

    // OTP: evidência de posse do canal (e-mail/WhatsApp) confirmada no aceite.
    @Column(name = "otp_verificado")
    private Boolean otpVerificado;

    @Column(name = "otp_canal", length = 20)
    private String otpCanal;

    @Column(name = "otp_destino", length = 160)
    private String otpDestino;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (aceitoEm == null) {
            aceitoEm = Instant.now();
        }
        createdAt = Instant.now();
    }

    public enum Metodo {
        SIGNATURE_PAD,
        PAPEL
    }
}
