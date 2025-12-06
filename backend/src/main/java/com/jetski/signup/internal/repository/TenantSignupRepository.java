package com.jetski.signup.internal.repository;

import com.jetski.signup.domain.SignupStatus;
import com.jetski.signup.domain.TenantSignup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TenantSignup entity.
 */
@Repository
public interface TenantSignupRepository extends JpaRepository<TenantSignup, UUID> {

    /**
     * Find a signup by its activation token.
     *
     * @param token The activation token
     * @return Optional containing the signup if found
     */
    Optional<TenantSignup> findByToken(String token);

    /**
     * Find a pending signup by email.
     *
     * @param email The email address
     * @param status The signup status
     * @return Optional containing the signup if found
     */
    Optional<TenantSignup> findByEmailAndStatus(String email, SignupStatus status);

    /**
     * Check if a pending signup exists for the given email.
     *
     * @param email The email address
     * @param status The signup status
     * @return true if a signup exists with the given email and status
     */
    boolean existsByEmailAndStatus(String email, SignupStatus status);

    /**
     * Find a pending signup by tenant ID.
     *
     * @param tenantId The tenant ID
     * @param status The signup status
     * @return Optional containing the signup if found
     */
    Optional<TenantSignup> findByTenantIdAndStatus(UUID tenantId, SignupStatus status);

    /**
     * Count pending signups for a given email (for rate limiting).
     *
     * @param email The email address
     * @return Count of pending signups
     */
    @Query("SELECT COUNT(ts) FROM TenantSignup ts WHERE ts.email = :email AND ts.status = 'PENDING'")
    long countPendingByEmail(String email);
}
