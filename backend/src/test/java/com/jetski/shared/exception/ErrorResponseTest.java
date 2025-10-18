package com.jetski.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ErrorResponse
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@DisplayName("ErrorResponse Tests")
class ErrorResponseTest {

    // ========================================================================
    // Builder Tests
    // ========================================================================

    @Test
    @DisplayName("Should build ErrorResponse with all fields")
    void shouldBuildWithAllFields() {
        // Given
        Instant now = Instant.now();
        Map<String, Object> details = Map.of(
            "field", "tenantId",
            "rejectedValue", "invalid-uuid",
            "reason", "Must be a valid UUID"
        );

        // When
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(now)
            .status(400)
            .error("Bad Request")
            .message("Invalid tenant ID")
            .path("/v1/user/tenants")
            .details(details)
            .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTimestamp()).isEqualTo(now);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("Bad Request");
        assertThat(response.getMessage()).isEqualTo("Invalid tenant ID");
        assertThat(response.getPath()).isEqualTo("/v1/user/tenants");
        assertThat(response.getDetails()).isEqualTo(details);
    }

    @Test
    @DisplayName("Should build ErrorResponse with minimal fields")
    void shouldBuildWithMinimalFields() {
        // When
        ErrorResponse response = ErrorResponse.builder()
            .status(500)
            .error("Internal Server Error")
            .message("An unexpected error occurred")
            .path("/v1/jetskis")
            .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTimestamp()).isNotNull(); // Auto-generated
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getError()).isEqualTo("Internal Server Error");
        assertThat(response.getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getPath()).isEqualTo("/v1/jetskis");
        assertThat(response.getDetails()).isNull(); // Not set
    }

    @Test
    @DisplayName("Should use default timestamp when not specified")
    void shouldUseDefaultTimestamp() {
        // Given
        Instant before = Instant.now();

        // When
        ErrorResponse response = ErrorResponse.builder()
            .status(404)
            .error("Not Found")
            .message("Resource not found")
            .path("/v1/jetskis/999")
            .build();

        // Then
        Instant after = Instant.now();
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("Should allow explicit timestamp override")
    void shouldAllowExplicitTimestamp() {
        // Given: Specific timestamp (e.g., from request context)
        Instant specificTime = Instant.parse("2024-01-15T10:30:00Z");

        // When
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(specificTime)
            .status(403)
            .error("Forbidden")
            .message("Access denied")
            .path("/v1/admin/settings")
            .build();

        // Then
        assertThat(response.getTimestamp()).isEqualTo(specificTime);
    }

    @Test
    @DisplayName("Should include validation details for bad request")
    void shouldIncludeValidationDetails() {
        // Given
        Map<String, Object> validationDetails = Map.of(
            "errors", Map.of(
                "email", "Email is required",
                "password", "Password must be at least 8 characters"
            )
        );

        // When
        ErrorResponse response = ErrorResponse.builder()
            .status(400)
            .error("Bad Request")
            .message("Validation failed")
            .path("/v1/auth/register")
            .details(validationDetails)
            .build();

        // Then
        assertThat(response.getDetails()).isNotNull();
        assertThat(response.getDetails()).containsKey("errors");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getDetails().get("errors");
        assertThat(errors).containsEntry("email", "Email is required");
        assertThat(errors).containsEntry("password", "Password must be at least 8 characters");
    }

    // ========================================================================
    // toString() Tests
    // ========================================================================

    @Test
    @DisplayName("Should generate string representation")
    void shouldGenerateToString() {
        // When
        ErrorResponse response = ErrorResponse.builder()
            .status(401)
            .error("Unauthorized")
            .message("JWT token expired")
            .path("/v1/auth-test/me")
            .build();

        // Then
        String str = response.toString();
        assertThat(str).isNotNull();
        assertThat(str).contains("ErrorResponse");
        assertThat(str).contains("status=401");
        assertThat(str).contains("error=Unauthorized");
        assertThat(str).contains("message=JWT token expired");
        assertThat(str).contains("path=/v1/auth-test/me");
    }

    @Test
    @DisplayName("Should include details in toString when present")
    void shouldIncludeDetailsInToString() {
        // Given
        Map<String, Object> details = Map.of("reason", "Token expired at 2024-01-15T10:00:00Z");

        // When
        ErrorResponse response = ErrorResponse.builder()
            .status(401)
            .error("Unauthorized")
            .message("Authentication failed")
            .path("/v1/secured")
            .details(details)
            .build();

        // Then
        String str = response.toString();
        assertThat(str).contains("details=");
        assertThat(str).contains("reason");
    }

    // ========================================================================
    // Equals/HashCode Tests (Lombok @Data)
    // ========================================================================

    @Test
    @DisplayName("Should consider two ErrorResponses equal with same content")
    void shouldConsiderEqualWithSameContent() {
        // Given
        Instant timestamp = Instant.now();

        ErrorResponse response1 = ErrorResponse.builder()
            .timestamp(timestamp)
            .status(404)
            .error("Not Found")
            .message("Jetski not found")
            .path("/v1/jetskis/123")
            .build();

        ErrorResponse response2 = ErrorResponse.builder()
            .timestamp(timestamp)
            .status(404)
            .error("Not Found")
            .message("Jetski not found")
            .path("/v1/jetskis/123")
            .build();

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("Should consider two ErrorResponses different with different content")
    void shouldConsiderDifferentWithDifferentContent() {
        // Given
        ErrorResponse response1 = ErrorResponse.builder()
            .status(400)
            .error("Bad Request")
            .message("Invalid input")
            .path("/v1/jetskis")
            .build();

        ErrorResponse response2 = ErrorResponse.builder()
            .status(500)
            .error("Internal Server Error")
            .message("System error")
            .path("/v1/jetskis")
            .build();

        // Then
        assertThat(response1).isNotEqualTo(response2);
    }

    // ========================================================================
    // Real-world Scenario Tests
    // ========================================================================

    @Test
    @DisplayName("Should represent 400 Bad Request scenario")
    void shouldRepresentBadRequestScenario() {
        // When: Invalid tenant ID format
        ErrorResponse response = ErrorResponse.builder()
            .status(400)
            .error("Bad Request")
            .message("Invalid tenant identifier")
            .path("/v1/user/tenants")
            .details(Map.of("tenantId", "not-a-uuid"))
            .build();

        // Then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getError()).isEqualTo("Bad Request");
        assertThat(response.getDetails()).containsEntry("tenantId", "not-a-uuid");
    }

    @Test
    @DisplayName("Should represent 403 Forbidden scenario")
    void shouldRepresentForbiddenScenario() {
        // When: User doesn't have required role
        ErrorResponse response = ErrorResponse.builder()
            .status(403)
            .error("Forbidden")
            .message("Insufficient permissions")
            .path("/v1/admin/users")
            .details(Map.of(
                "requiredRole", "ADMIN_TENANT",
                "userRoles", "OPERADOR, VENDEDOR"
            ))
            .build();

        // Then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getMessage()).contains("permissions");
        assertThat(response.getDetails()).containsKey("requiredRole");
    }

    @Test
    @DisplayName("Should represent 401 Unauthorized scenario")
    void shouldRepresentUnauthorizedScenario() {
        // When: Missing or invalid JWT
        ErrorResponse response = ErrorResponse.builder()
            .status(401)
            .error("Unauthorized")
            .message("Authentication required")
            .path("/v1/jetskis")
            .build();

        // Then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getError()).isEqualTo("Unauthorized");
        assertThat(response.getMessage()).contains("Authentication");
    }

    @Test
    @DisplayName("Should represent 500 Internal Server Error scenario")
    void shouldRepresentInternalServerErrorScenario() {
        // When: Unexpected system error
        ErrorResponse response = ErrorResponse.builder()
            .status(500)
            .error("Internal Server Error")
            .message("An unexpected error occurred")
            .path("/v1/reservas")
            .details(Map.of("errorId", "ERR-2024-001"))
            .build();

        // Then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getError()).isEqualTo("Internal Server Error");
        assertThat(response.getDetails()).containsEntry("errorId", "ERR-2024-001");
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    @DisplayName("Should handle empty details map")
    void shouldHandleEmptyDetails() {
        // When
        ErrorResponse response = ErrorResponse.builder()
            .status(400)
            .error("Bad Request")
            .message("Validation failed")
            .path("/v1/jetskis")
            .details(Map.of())
            .build();

        // Then
        assertThat(response.getDetails()).isNotNull();
        assertThat(response.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null details gracefully")
    void shouldHandleNullDetails() {
        // When
        ErrorResponse response = ErrorResponse.builder()
            .status(404)
            .error("Not Found")
            .message("Resource not found")
            .path("/v1/jetskis/999")
            .details(null)
            .build();

        // Then
        assertThat(response.getDetails()).isNull();
    }
}
