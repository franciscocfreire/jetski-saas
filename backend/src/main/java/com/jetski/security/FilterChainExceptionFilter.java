package com.jetski.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.exception.ErrorResponse;
import com.jetski.exception.InvalidTenantException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to catch exceptions thrown by other filters in the chain.
 *
 * This filter wraps the entire filter chain and catches exceptions that
 * would otherwise propagate to the servlet container. It converts these
 * exceptions into proper HTTP error responses using ErrorResponse format.
 *
 * This is necessary because @ControllerAdvice only handles exceptions
 * from controllers, not from filters.
 *
 * Order: This filter should be registered FIRST in the filter chain
 * (Order = Ordered.HIGHEST_PRECEDENCE) to catch all downstream exceptions.
 *
 * @author Jetski Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilterChainExceptionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Continue with the filter chain
            filterChain.doFilter(request, response);
        } catch (InvalidTenantException ex) {
            // Handle tenant validation errors (400 Bad Request)
            handleInvalidTenantException(ex, request, response);
        } catch (AccessDeniedException ex) {
            // Handle access denied errors (403 Forbidden)
            handleAccessDeniedException(ex, request, response);
        } catch (Exception ex) {
            // Handle unexpected errors (500 Internal Server Error)
            handleGenericException(ex, request, response);
        }
    }

    private void handleInvalidTenantException(
            InvalidTenantException ex,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        log.warn("Invalid tenant request: path={}, message={}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        writeErrorResponse(response, HttpStatus.BAD_REQUEST, error);
    }

    private void handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        log.warn("Access denied: path={}, message={}", request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        writeErrorResponse(response, HttpStatus.FORBIDDEN, error);
    }

    private void handleGenericException(
            Exception ex,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        log.error("Unexpected error in filter chain: path={}, error={}", request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("An unexpected error occurred")
                .path(request.getRequestURI())
                .build();

        writeErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, error);
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            HttpStatus status,
            ErrorResponse error) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), error);
    }
}
