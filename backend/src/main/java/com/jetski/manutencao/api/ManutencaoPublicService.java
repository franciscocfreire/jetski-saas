package com.jetski.manutencao.api;

import com.jetski.manutencao.domain.OSManutencao;
import com.jetski.manutencao.internal.OSManutencaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Public API Service for Manutencao Module
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
public class ManutencaoPublicService {

    private final OSManutencaoService osManutencaoService;

    /**
     * Create a new maintenance order
     *
     * This is a public API method that can be called by other modules
     * (e.g., frota module for preventive maintenance scheduling)
     *
     * @param os The maintenance order to create
     * @return The created maintenance order with generated ID
     */
    public OSManutencao createOrder(OSManutencao os) {
        return osManutencaoService.createOrder(os);
    }
}
