package com.jetski.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for InvalidTenantException
 *
 * @author Jetski Team
 */
class InvalidTenantExceptionTest {

    @Test
    void shouldCreateExceptionWithMissingTenantId() {
        // When
        InvalidTenantException exception = InvalidTenantException.missingTenantId();

        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).contains("Tenant ID not found");
    }

    @Test
    void shouldCreateExceptionWithInvalidFormat() {
        // Given
        String invalidTenantId = "not-a-uuid";

        // When
        InvalidTenantException exception = InvalidTenantException.invalidFormat(invalidTenantId);

        // Then
        assertThat(exception.getMessage())
                .contains("Invalid tenant ID format")
                .contains(invalidTenantId);
    }

    @Test
    void shouldCreateExceptionWithMismatch() {
        // Given
        String headerTenantId = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";
        String jwtTenantId = "b1ffcd99-9c0b-4ef8-bb6d-6bb9bd380a22";

        // When
        InvalidTenantException exception = InvalidTenantException.mismatch(headerTenantId, jwtTenantId);

        // Then
        assertThat(exception.getMessage())
                .contains("Tenant ID mismatch")
                .contains(headerTenantId)
                .contains(jwtTenantId);
    }

    @Test
    void shouldBeRuntimeException() {
        // When
        InvalidTenantException exception = InvalidTenantException.missingTenantId();

        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldHaveMessageInAllFactoryMethods() {
        // When
        InvalidTenantException missing = InvalidTenantException.missingTenantId();
        InvalidTenantException invalid = InvalidTenantException.invalidFormat("bad-id");
        InvalidTenantException mismatch = InvalidTenantException.mismatch("id1", "id2");

        // Then
        assertThat(missing.getMessage()).isNotNull().isNotEmpty();
        assertThat(invalid.getMessage()).isNotNull().isNotEmpty();
        assertThat(mismatch.getMessage()).isNotNull().isNotEmpty();
    }

    @Test
    void shouldPreserveStackTrace() {
        // When
        InvalidTenantException exception = InvalidTenantException.missingTenantId();

        // Then
        assertThat(exception.getStackTrace()).isNotEmpty();
    }
}
