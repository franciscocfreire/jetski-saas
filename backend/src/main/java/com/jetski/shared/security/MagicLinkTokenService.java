package com.jetski.shared.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Service for generating and validating Magic Link JWT tokens.
 *
 * <p>Magic Link tokens contain encrypted invitation data (token + temporary password)
 * allowing users to activate their accounts by simply clicking a link, without
 * manually entering credentials.
 *
 * <p><strong>Security features:</strong>
 * <ul>
 *   <li>HMAC-SHA256 signature for token integrity</li>
 *   <li>48-hour expiration (same as invitation)</li>
 *   <li>Temporary password encrypted in JWT (not stored in DB)</li>
 *   <li>Token cannot be forged or modified</li>
 * </ul>
 *
 * <p><strong>JWT Claims:</strong>
 * <ul>
 *   <li>sub: invitation token (string, 40 chars alphanumeric)</li>
 *   <li>pwd: temporary password (plain text, encrypted in JWT)</li>
 *   <li>exp: expiration timestamp</li>
 *   <li>iat: issued at timestamp</li>
 *   <li>jti: unique JWT ID</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.5.0
 */
@Slf4j
@Service
public class MagicLinkTokenService {

    private static final String CLAIM_TEMPORARY_PASSWORD = "pwd";
    private static final long EXPIRATION_HOURS = 48; // Same as invitation expiration

    private final SecretKey signingKey;

    public MagicLinkTokenService(@Value("${jwt.magic-link.secret}") String secret) {
        // Generate signing key from secret
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("MagicLinkTokenService initialized with HMAC-SHA256 signing");
    }

    /**
     * Generates a signed JWT for magic link activation.
     *
     * <p>The JWT contains the invitation token and temporary password encrypted.
     * It is valid for 48 hours.
     *
     * @param invitationToken String token from convite table (40 chars alphanumeric)
     * @param temporaryPassword Plain text temporary password
     * @return Signed JWT string
     */
    public String generateMagicToken(String invitationToken, String temporaryPassword) {
        Instant now = Instant.now();
        Instant expiration = now.plus(EXPIRATION_HOURS, ChronoUnit.HOURS);

        String jwt = Jwts.builder()
            .subject(invitationToken)  // Invitation token as subject
            .claim(CLAIM_TEMPORARY_PASSWORD, temporaryPassword)  // Temporary password
            .id(UUID.randomUUID().toString())  // Unique JWT ID
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();

        log.debug("Generated magic link token: invitationToken={}, exp={}", invitationToken, expiration);
        return jwt;
    }

    /**
     * Validates and parses a magic link JWT.
     *
     * <p>Validates signature, expiration, and extracts claims.
     *
     * @param magicToken JWT string from URL
     * @return MagicTokenClaims with extracted data
     * @throws InvalidMagicTokenException if JWT is invalid, expired, or tampered
     */
    public MagicTokenClaims validateAndParse(String magicToken) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(magicToken)
                .getPayload();

            String invitationToken = claims.getSubject();
            String temporaryPassword = claims.get(CLAIM_TEMPORARY_PASSWORD, String.class);

            if (invitationToken == null || invitationToken.isBlank()) {
                throw new InvalidMagicTokenException("Missing invitation token in JWT");
            }

            if (temporaryPassword == null || temporaryPassword.isBlank()) {
                throw new InvalidMagicTokenException("Missing temporary password in JWT");
            }

            log.debug("Magic token validated successfully: invitationToken={}", invitationToken);

            return new MagicTokenClaims(invitationToken, temporaryPassword);

        } catch (ExpiredJwtException e) {
            log.warn("Magic token expired: {}", e.getMessage());
            throw new InvalidMagicTokenException("Magic link expired", e);
        } catch (SignatureException e) {
            log.error("Invalid magic token signature: {}", e.getMessage());
            throw new InvalidMagicTokenException("Invalid magic link signature", e);
        } catch (MalformedJwtException e) {
            log.error("Malformed magic token: {}", e.getMessage());
            throw new InvalidMagicTokenException("Malformed magic link token", e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid magic token format: {}", e.getMessage());
            throw new InvalidMagicTokenException("Invalid magic link format", e);
        } catch (JwtException e) {
            log.error("JWT validation error: {}", e.getMessage());
            throw new InvalidMagicTokenException("Invalid magic link", e);
        }
    }

    /**
     * Parsed claims from a magic link JWT.
     *
     * @param invitationToken Invitation token string from convite table (40 chars alphanumeric)
     * @param temporaryPassword Plain text temporary password
     */
    public record MagicTokenClaims(
        String invitationToken,
        String temporaryPassword
    ) {}

    /**
     * Exception thrown when magic token validation fails.
     */
    public static class InvalidMagicTokenException extends RuntimeException {
        public InvalidMagicTokenException(String message) {
            super(message);
        }

        public InvalidMagicTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
