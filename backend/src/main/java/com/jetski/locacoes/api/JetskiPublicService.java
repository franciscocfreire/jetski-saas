package com.jetski.locacoes.api;

import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.locacoes.internal.JetskiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Public API Service for Jetski operations
 *
 * This service exposes safe, public methods that other modules can use
 * without directly accessing internal services.
 *
 * Architecture: This follows the modular architecture pattern where:
 * - `api` package = public API (safe to use from other modules)
 * - `internal` package = private implementation (not exposed)
 * - `domain` package = shared domain models (safe to use)
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Service
@RequiredArgsConstructor
public class JetskiPublicService {

    private final JetskiService jetskiService;

    /**
     * Find jetski by ID
     *
     * This is a public API method that can be called by other modules
     * (e.g., manutencao module for validating jetski exists)
     *
     * @param jetskiId The jetski ID
     * @return The jetski entity
     * @throws jakarta.persistence.EntityNotFoundException if jetski not found
     */
    public Jetski findById(UUID jetskiId) {
        return jetskiService.findById(jetskiId);
    }

    /**
     * Update jetski status
     *
     * This is a public API method that can be called by other modules
     * (e.g., manutencao module for blocking/unblocking jetskis)
     *
     * @param jetskiId The jetski ID
     * @param status The new status
     * @return The updated jetski entity
     */
    public Jetski updateStatus(UUID jetskiId, JetskiStatus status) {
        return jetskiService.updateStatus(jetskiId, status);
    }
}
