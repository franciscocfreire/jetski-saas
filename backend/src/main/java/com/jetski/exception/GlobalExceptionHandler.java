package com.jetski.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler for REST API.
 *
 * Catches exceptions thrown by controllers and filters, converting them
 * into standardized ErrorResponse objects with appropriate HTTP status codes.
 *
 * Exception Mappings:
 * - InvalidTenantException → 400 Bad Request
 * - AccessDeniedException → 403 Forbidden
 * - AuthenticationException → 401 Unauthorized
 * - Other exceptions → 500 Internal Server Error
 *
 * @author Jetski Team
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handle InvalidTenantException (tenant validation failures)
     * HTTP Status: 400 Bad Request
     */
    @ExceptionHandler(InvalidTenantException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTenantException(
            InvalidTenantException ex,
            HttpServletRequest request) {

        log.warn("Invalid tenant request: path={}, message={}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    /**
     * Handle AccessDeniedException (authorization failures)
     * HTTP Status: 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied: path={}, message={}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(error);
    }

    /**
     * Handle AuthenticationException (authentication failures)
     * HTTP Status: 401 Unauthorized
     */
    @ExceptionHandler({AuthenticationException.class, InsufficientAuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            Exception ex,
            HttpServletRequest request) {

        log.warn("Authentication failed: path={}, message={}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message("Authentication required")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(error);
    }

    /**
     * Handle generic exceptions
     * HTTP Status: 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error: path={}, error={}", request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("An unexpected error occurred. Please contact support if the problem persists.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}
