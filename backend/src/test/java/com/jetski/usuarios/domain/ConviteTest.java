package com.jetski.usuarios.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para Convite domain class.
 *
 * Foco em cobertura de branches nos métodos de regras de negócio:
 * - isValid(), isExpired()
 * - activate(), cancel(), expire()
 * - recordEmailSent()
 * - setTemporaryPassword(), validateTemporaryPassword()
 */
@DisplayName("Convite Domain Unit Tests")
class ConviteTest {

    // ==================== isValid() Tests ====================

    @Test
    @DisplayName("isValid should return true when status=PENDING and not expired")
    void testIsValid_True_WhenPendingAndNotExpired() {
        // Given: Convite PENDING that expires in the future
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.PENDING)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        // When/Then
        assertThat(convite.isValid()).isTrue();
    }

    @Test
    @DisplayName("isValid should return false when status is not PENDING")
    void testIsValid_False_WhenActivated() {
        // Given: Convite ACTIVATED (even if not expired)
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.ACTIVATED)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        // When/Then
        assertThat(convite.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid should return false when status=PENDING but expired")
    void testIsValid_False_WhenPendingButExpired() {
        // Given: Convite PENDING but already expired
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        // When/Then
        assertThat(convite.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid should return false when status=CANCELLED")
    void testIsValid_False_WhenCancelled() {
        // Given
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.CANCELLED)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        // When/Then
        assertThat(convite.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid should return false when status=EXPIRED")
    void testIsValid_False_WhenExpiredStatus() {
        // Given
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.EXPIRED)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        // When/Then
        assertThat(convite.isValid()).isFalse();
    }

    // ==================== isExpired() Tests ====================

    @Test
    @DisplayName("isExpired should return true when status=PENDING and past expiration")
    void testIsExpired_True_WhenPendingAndPastExpiration() {
        // Given
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        // When/Then
        assertThat(convite.isExpired()).isTrue();
    }

    @Test
    @DisplayName("isExpired should return false when status=PENDING but not yet expired")
    void testIsExpired_False_WhenPendingNotYetExpired() {
        // Given
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.PENDING)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        // When/Then
        assertThat(convite.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isExpired should return false when status is not PENDING")
    void testIsExpired_False_WhenNotPending() {
        // Given: ACTIVATED status (not PENDING)
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.ACTIVATED)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        // When/Then: Should return false because status != PENDING
        assertThat(convite.isExpired()).isFalse();
    }

    // ==================== activate() Tests ====================

    @Test
    @DisplayName("activate should set status=ACTIVATED and record usuarioId")
    void testActivate_Success() {
        // Given
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.PENDING)
                .build();
        UUID usuarioId = UUID.randomUUID();

        // When
        convite.activate(usuarioId);

        // Then
        assertThat(convite.getStatus()).isEqualTo(Convite.ConviteStatus.ACTIVATED);
        assertThat(convite.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(convite.getActivatedAt()).isNotNull();
        assertThat(convite.getActivatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    // ==================== cancel() Tests ====================

    @Test
    @DisplayName("cancel should set status=CANCELLED")
    void testCancel_Success() {
        // Given
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.PENDING)
                .build();

        // When
        convite.cancel();

        // Then
        assertThat(convite.getStatus()).isEqualTo(Convite.ConviteStatus.CANCELLED);
    }

    // ==================== expire() Tests ====================

    @Test
    @DisplayName("expire should set status=EXPIRED")
    void testExpire_Success() {
        // Given
        Convite convite = Convite.builder()
                .status(Convite.ConviteStatus.PENDING)
                .build();

        // When
        convite.expire();

        // Then
        assertThat(convite.getStatus()).isEqualTo(Convite.ConviteStatus.EXPIRED);
    }

    // ==================== recordEmailSent() Tests ====================

    @Test
    @DisplayName("recordEmailSent should increment count when emailSentCount is null")
    void testRecordEmailSent_IncrementFromNull() {
        // Given: emailSentCount is null
        Convite convite = Convite.builder()
                .emailSentCount(null)
                .build();
        String loginLink = "https://example.com/login?token=abc123";

        // When
        convite.recordEmailSent(loginLink);

        // Then
        assertThat(convite.getPasswordResetLink()).isEqualTo(loginLink);
        assertThat(convite.getEmailSentAt()).isNotNull();
        assertThat(convite.getEmailSentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordEmailSent should increment count when emailSentCount is 0")
    void testRecordEmailSent_IncrementFromZero() {
        // Given
        Convite convite = Convite.builder()
                .emailSentCount(0)
                .build();
        String loginLink = "https://example.com/login?token=xyz789";

        // When
        convite.recordEmailSent(loginLink);

        // Then
        assertThat(convite.getPasswordResetLink()).isEqualTo(loginLink);
        assertThat(convite.getEmailSentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordEmailSent should increment count when emailSentCount is 3")
    void testRecordEmailSent_IncrementFromThree() {
        // Given
        Convite convite = Convite.builder()
                .emailSentCount(3)
                .build();
        String loginLink = "https://example.com/login?token=def456";

        // When
        convite.recordEmailSent(loginLink);

        // Then
        assertThat(convite.getEmailSentCount()).isEqualTo(4);
    }

    // ==================== setTemporaryPassword() Tests ====================

    @Test
    @DisplayName("setTemporaryPassword should hash the password successfully")
    void testSetTemporaryPassword_Success() {
        // Given
        Convite convite = new Convite();
        String rawPassword = "TempPass123!";

        // When
        convite.setTemporaryPassword(rawPassword);

        // Then
        assertThat(convite.getTemporaryPasswordHash()).isNotNull();
        assertThat(convite.getTemporaryPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(convite.getTemporaryPasswordHash()).startsWith("$2a$"); // BCrypt prefix
    }

    @Test
    @DisplayName("setTemporaryPassword should throw exception when password is null")
    void testSetTemporaryPassword_ThrowsException_WhenNull() {
        // Given
        Convite convite = new Convite();

        // When/Then
        assertThatThrownBy(() -> convite.setTemporaryPassword(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    @Test
    @DisplayName("setTemporaryPassword should throw exception when password is blank")
    void testSetTemporaryPassword_ThrowsException_WhenBlank() {
        // Given
        Convite convite = new Convite();

        // When/Then
        assertThatThrownBy(() -> convite.setTemporaryPassword("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    @Test
    @DisplayName("setTemporaryPassword should throw exception when password is empty")
    void testSetTemporaryPassword_ThrowsException_WhenEmpty() {
        // Given
        Convite convite = new Convite();

        // When/Then
        assertThatThrownBy(() -> convite.setTemporaryPassword(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    // ==================== validateTemporaryPassword() Tests ====================

    @Test
    @DisplayName("validateTemporaryPassword should return true when password matches")
    void testValidateTemporaryPassword_True_WhenMatches() {
        // Given
        Convite convite = new Convite();
        String rawPassword = "TempPass123!";
        convite.setTemporaryPassword(rawPassword);

        // When
        boolean result = convite.validateTemporaryPassword(rawPassword);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("validateTemporaryPassword should return false when password does not match")
    void testValidateTemporaryPassword_False_WhenDoesNotMatch() {
        // Given
        Convite convite = new Convite();
        convite.setTemporaryPassword("CorrectPassword123!");

        // When
        boolean result = convite.validateTemporaryPassword("WrongPassword456!");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("validateTemporaryPassword should return false when hash is null")
    void testValidateTemporaryPassword_False_WhenHashIsNull() {
        // Given: Convite without temporary password hash
        Convite convite = Convite.builder()
                .temporaryPasswordHash(null)
                .build();

        // When
        boolean result = convite.validateTemporaryPassword("AnyPassword123!");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("validateTemporaryPassword should return false when raw password is null")
    void testValidateTemporaryPassword_False_WhenRawPasswordIsNull() {
        // Given
        Convite convite = new Convite();
        convite.setTemporaryPassword("TempPass123!");

        // When
        boolean result = convite.validateTemporaryPassword(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("validateTemporaryPassword should return false when both are null")
    void testValidateTemporaryPassword_False_WhenBothNull() {
        // Given
        Convite convite = Convite.builder()
                .temporaryPasswordHash(null)
                .build();

        // When
        boolean result = convite.validateTemporaryPassword(null);

        // Then
        assertThat(result).isFalse();
    }

    // ==================== Builder Defaults Tests ====================

    @Test
    @DisplayName("Should have correct default values when built with builder")
    void testBuilderDefaults() {
        // When
        Convite convite = Convite.builder().build();

        // Then
        assertThat(convite.getStatus()).isEqualTo(Convite.ConviteStatus.PENDING);
        assertThat(convite.getEmailSentCount()).isEqualTo(0);
    }
}
