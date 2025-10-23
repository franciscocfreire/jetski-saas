package com.jetski.usuarios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Convite (User Invitation)
 *
 * Represents an invitation sent by ADMIN_TENANT to invite new users to the tenant.
 * Invitation contains an activation token (JWT) valid for 48 hours.
 * After activation, user account is created in both PostgreSQL and Keycloak.
 *
 * Status flow: PENDING → ACTIVATED (or EXPIRED/CANCELLED)
 *
 * @author Jetski Team
 * @since 0.3.0
 */
@Entity
@Table(name = "convite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Convite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, columnDefinition = "TEXT[]")
    private String[] papeis;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConviteStatus status = ConviteStatus.PENDING;

    // Email tracking fields
    @Column(name = "password_reset_link", columnDefinition = "TEXT")
    private String passwordResetLink;  // Actually stores login link (legacy column name)

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @Column(name = "email_sent_count", nullable = false)
    @Builder.Default
    private Integer emailSentCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Temporary password hash for Option 2 flow (temporary password + Keycloak UPDATE_PASSWORD)
    @Column(name = "temporary_password_hash")
    private String temporaryPasswordHash;

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
     * Checks if the invitation is still valid (PENDING and not expired).
     */
    public boolean isValid() {
        return status == ConviteStatus.PENDING && Instant.now().isBefore(expiresAt);
    }

    /**
     * Checks if the invitation has expired.
     */
    public boolean isExpired() {
        return status == ConviteStatus.PENDING && Instant.now().isAfter(expiresAt);
    }

    /**
     * Marks invitation as activated.
     */
    public void activate(UUID usuarioId) {
        this.status = ConviteStatus.ACTIVATED;
        this.activatedAt = Instant.now();
        this.usuarioId = usuarioId;
    }

    /**
     * Marks invitation as cancelled.
     */
    public void cancel() {
        this.status = ConviteStatus.CANCELLED;
    }

    /**
     * Marks invitation as expired.
     */
    public void expire() {
        this.status = ConviteStatus.EXPIRED;
    }

    /**
     * Records that an activation email was sent.
     *
     * @param loginLink The full login link sent in the email
     */
    public void recordEmailSent(String loginLink) {
        this.passwordResetLink = loginLink;  // Stores login link (legacy field name)
        this.emailSentAt = Instant.now();
        this.emailSentCount = (this.emailSentCount == null ? 0 : this.emailSentCount) + 1;
    }

    /**
     * Sets the temporary password by hashing it with BCrypt.
     *
     * Option 2 Flow: Backend generates random temporary password, hashes it,
     * stores hash, sends plain password in email. User uses it to activate account,
     * then Keycloak forces password change on first login via UPDATE_PASSWORD.
     *
     * @param rawPassword The plain-text temporary password to hash and store
     */
    public void setTemporaryPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Temporary password cannot be null or blank");
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        this.temporaryPasswordHash = encoder.encode(rawPassword);
    }

    /**
     * Validates that the provided temporary password matches the stored hash.
     *
     * Used during activation to verify user has the correct temporary password
     * before creating Keycloak account with UPDATE_PASSWORD required action.
     *
     * @param rawPassword The plain-text password provided by user
     * @return true if password matches hash, false otherwise
     */
    public boolean validateTemporaryPassword(String rawPassword) {
        if (this.temporaryPasswordHash == null || rawPassword == null) {
            return false;
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.matches(rawPassword, this.temporaryPasswordHash);
    }

    /**
     * Invitation status enum.
     */
    public enum ConviteStatus {
        PENDING,    // Invitation sent, waiting for activation
        ACTIVATED,  // User activated the account
        EXPIRED,    // Token expired (48h)
        CANCELLED   // Admin cancelled the invitation
    }
}
