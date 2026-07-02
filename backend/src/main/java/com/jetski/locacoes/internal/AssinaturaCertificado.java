package com.jetski.locacoes.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Certificado auto-assinado da plataforma para assinatura digital PAdES (Fase C2).
 * Linha única global; a chave privada é armazenada cifrada ({@code key_pem_enc}).
 */
@Entity
@Table(name = "assinatura_certificado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssinaturaCertificado {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String subject;

    /** Certificado X.509 em Base64 (DER). */
    @Column(name = "cert_pem", nullable = false, columnDefinition = "TEXT")
    private String certPem;

    /** Chave privada PKCS#8 (Base64) cifrada com SecretCipher. */
    @Column(name = "key_pem_enc", nullable = false, columnDefinition = "TEXT")
    private String keyPemEnc;

    @Column(nullable = false, length = 40)
    private String algoritmo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
