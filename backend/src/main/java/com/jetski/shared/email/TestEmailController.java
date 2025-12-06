package com.jetski.shared.email;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for E2E testing purposes.
 *
 * Provides endpoints to retrieve email data that would normally
 * require actual email access (magic links, temporary passwords, etc).
 *
 * IMPORTANT: Only available in local/test profiles!
 * DO NOT enable in production!
 *
 * @author Jetski Team
 * @since 0.6.0
 */
@RestController
@RequestMapping("/v1/test")
@Profile({"local", "test", "dev"})
@Slf4j
@Tag(name = "Test Utilities", description = "Test endpoints for E2E testing - NOT FOR PRODUCTION")
public class TestEmailController {

    /**
     * GET /v1/test/last-email
     *
     * Retrieves the last email sent by DevEmailService.
     * Useful for E2E tests to get activation tokens without actual email access.
     *
     * @return Last email data including magic token and temporary password
     */
    @GetMapping("/last-email")
    @Operation(
        summary = "Get last sent email data",
        description = "Retrieves magic token and temporary password from last sent email. " +
            "TEST ONLY - not available in production!"
    )
    public ResponseEntity<Map<String, Object>> getLastEmail() {
        log.info("📧 E2E Test: Fetching last email data");

        DevEmailService.LastEmailData lastEmail = DevEmailService.getLastEmail();

        Map<String, Object> response = new HashMap<>();

        if (lastEmail == null) {
            response.put("success", false);
            response.put("message", "No email has been sent yet");
            log.warn("📧 E2E Test: No email data available");
            return ResponseEntity.ok(response);
        }

        response.put("success", true);
        response.put("to", lastEmail.getTo());
        response.put("name", lastEmail.getName());
        response.put("subject", lastEmail.getSubject());
        response.put("magicToken", lastEmail.getMagicToken());
        response.put("temporaryPassword", lastEmail.getTemporaryPassword());
        response.put("activationLink", lastEmail.getActivationLink());
        response.put("sentAt", lastEmail.getSentAt().toString());

        log.info("📧 E2E Test: Returning email data for: {}", lastEmail.getTo());
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /v1/test/last-email
     *
     * Clears the last email data.
     * Useful for test isolation between test cases.
     *
     * @return Success message
     */
    @DeleteMapping("/last-email")
    @Operation(
        summary = "Clear last email data",
        description = "Clears stored email data for test isolation. " +
            "TEST ONLY - not available in production!"
    )
    public ResponseEntity<Map<String, Object>> clearLastEmail() {
        log.info("📧 E2E Test: Clearing last email data");

        DevEmailService.clearLastEmail();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Last email data cleared");

        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/test/health
     *
     * Simple health check for test endpoints.
     *
     * @return Health status
     */
    @GetMapping("/health")
    @Operation(
        summary = "Test endpoints health check",
        description = "Verifies test endpoints are available"
    )
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("profile", "local/test");
        response.put("message", "Test endpoints available");
        return ResponseEntity.ok(response);
    }
}
