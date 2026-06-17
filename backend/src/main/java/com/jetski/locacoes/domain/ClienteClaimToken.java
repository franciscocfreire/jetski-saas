package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: claim-token de ativação de conta do cliente (balcão F2.7).
 *
 * <p>O staff gera o token + senha temporária; o cliente valida (endpoint
 * público) provisionando o usuário no Keycloak (role CLIENTE, SEM Membro)
 * vinculado via {@link ClienteIdentityProvider}. A senha temporária é
 * armazenada apenas como hash BCrypt (espelha o fluxo de convite de staff).
 * Reenvio desativa o token anterior ({@code ativo=false}) e emite outro.
 */
@Entity
@Table(name = "cliente_claim_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteClaimToken {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "temporary_password_hash", nullable = false)
    private String temporaryPasswordHash;

    @Column(length = 100)
    private String canais;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "criado_por")
    private UUID criadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Define a senha temporária, armazenando apenas o hash BCrypt. */
    public void setTemporaryPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Senha temporária não pode ser vazia");
        }
        this.temporaryPasswordHash = new BCryptPasswordEncoder().encode(rawPassword);
    }

    /** Valida a senha temporária contra o hash armazenado. */
    public boolean validateTemporaryPassword(String rawPassword) {
        if (rawPassword == null || temporaryPasswordHash == null) {
            return false;
        }
        return new BCryptPasswordEncoder().matches(rawPassword, temporaryPasswordHash);
    }

    public boolean isExpired() {
        return expiraEm != null && Instant.now().isAfter(expiraEm);
    }

    public boolean isUsado() {
        return usadoEm != null;
    }
}
