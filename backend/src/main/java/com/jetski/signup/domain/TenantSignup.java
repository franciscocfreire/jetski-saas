package com.jetski.signup.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Tenant Signup
 *
 * Represents a pending tenant signup request.
 * Similar to Convite but for self-service tenant creation.
 *
 * Flow:
 * 1. User fills signup form
 * 2. TenantSignup is created with PENDING status
 * 3. Email with activation link + temp password is sent
 * 4. User clicks link, validates temp password
 * 5. Usuario + Membro + Keycloak user are created
 * 6. Status changes to ACTIVATED
 *
 * @author Jetski Team
 * @since 0.5.0
 */
@Entity
@Table(name = "tenant_signup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSignup {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * The tenant created during signup.
     * Tenant is created immediately but admin user is only created after activation.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Email of the admin user being created.
     */
    @Column(nullable = false, length = 255)
    private String email;

    /**
     * Name of the admin user being created.
     */
    @Column(nullable = false, length = 200)
    private String nome;

    /**
     * Unique activation token (40 chars alphanumeric).
     */
    @Column(nullable = false, unique = true, length = 255)
    private String token;

    /**
     * BCrypt hash of the temporary password.
     * Plain password is sent in activation email.
     */
    @Column(name = "temporary_password", length = 255)
    private String temporaryPasswordHash;

    /**
     * When the activation token expires (default: 48h after creation).
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Current status of the signup request.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignupStatus status;

    /**
     * When the signup request was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * When the signup was activated (null if pending/expired).
     */
    @Column(name = "activated_at")
    private Instant activatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = SignupStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Sets the temporary password by hashing it with BCrypt.
     *
     * @param plainPassword The plain text password to hash and store
     */
    public void setTemporaryPassword(String plainPassword) {
        this.temporaryPasswordHash = PASSWORD_ENCODER.encode(plainPassword);
    }

    /**
     * Validates a temporary password against the stored hash.
     *
     * @param plainPassword The plain text password to validate
     * @return true if the password matches, false otherwise
     */
    public boolean validateTemporaryPassword(String plainPassword) {
        if (temporaryPasswordHash == null || plainPassword == null) {
            return false;
        }
        return PASSWORD_ENCODER.matches(plainPassword, temporaryPasswordHash);
    }

    /**
     * Checks if the activation token has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Marks the signup as activated.
     */
    public void activate() {
        this.status = SignupStatus.ACTIVATED;
        this.activatedAt = Instant.now();
    }

    /**
     * Marks the signup as expired.
     */
    public void expire() {
        this.status = SignupStatus.EXPIRED;
    }
}
