package com.jetski.combustivel.api;

import com.jetski.combustivel.api.dto.FuelPriceDayCreateRequest;
import com.jetski.combustivel.api.dto.FuelPriceDayResponse;
import com.jetski.combustivel.domain.FuelPriceDay;
import com.jetski.combustivel.internal.FuelPriceDayService;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: FuelPriceDayController
 *
 * REST API for daily fuel price management.
 *
 * Endpoints:
 * - POST /v1/tenants/{tenantId}/fuel-prices - Create/override daily price
 * - GET /v1/tenants/{tenantId}/fuel-prices - List daily prices
 * - GET /v1/tenants/{tenantId}/fuel-prices/{data} - Get price by date
 * - GET /v1/tenants/{tenantId}/fuel-prices/average - Get average price for date
 *
 * Authorization:
 * - GERENTE: Full access
 * - ADMIN_TENANT: Full access
 * - OPERADOR: Read-only
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/tenants/{tenantId}/fuel-prices")
@RequiredArgsConstructor
@Tag(name = "Combustivel", description = "Fuel management API - Refills, Policies, Pricing")
public class FuelPriceDayController {

    private final FuelPriceDayService fuelPriceDayService;

    /**
     * POST /v1/tenants/{tenantId}/fuel-prices
     *
     * Create or override daily fuel price (admin function).
     * Normally prices are calculated automatically from refills.
     *
     * @param tenantId Tenant ID from path
     * @param request Price data
     * @return 201 Created with FuelPriceDayResponse
     */
    @PostMapping
    @Operation(summary = "Create/override daily fuel price",
               description = "Manually set daily fuel price (admin override)")
    public ResponseEntity<FuelPriceDayResponse> criar(
        @PathVariable UUID tenantId,
        @Valid @RequestBody FuelPriceDayCreateRequest request
    ) {
        log.info("POST /v1/tenants/{}/fuel-prices - data={}, preco={}",
                 tenantId, request.getData(), request.getPrecoMedioLitro());

        validateTenantContext(tenantId);

        FuelPriceDay priceDay = mapToEntity(request);
        priceDay.setTenantId(tenantId);

        FuelPriceDay saved = fuelPriceDayService.salvar(priceDay);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(mapToResponse(saved));
    }

    /**
     * GET /v1/tenants/{tenantId}/fuel-prices
     *
     * List daily fuel prices with optional date range filter.
     *
     * @param tenantId Tenant ID
     * @param dataInicio Optional: start date
     * @param dataFim Optional: end date
     * @return 200 OK with list of FuelPriceDayResponse
     */
    @GetMapping
    @Operation(summary = "List daily fuel prices",
               description = "List daily fuel prices with optional date range")
    public ResponseEntity<List<FuelPriceDayResponse>> listar(
        @PathVariable UUID tenantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        log.info("GET /v1/tenants/{}/fuel-prices - periodo={} a {}",
                 tenantId, dataInicio, dataFim);

        validateTenantContext(tenantId);

        List<FuelPriceDay> prices = fuelPriceDayService.listar(tenantId, dataInicio, dataFim);

        List<FuelPriceDayResponse> responses = prices.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * GET /v1/tenants/{tenantId}/fuel-prices/{data}
     *
     * Get fuel price for specific date.
     *
     * @param tenantId Tenant ID
     * @param data Date
     * @return 200 OK with FuelPriceDayResponse
     */
    @GetMapping("/{data}")
    @Operation(summary = "Get price by date")
    public ResponseEntity<FuelPriceDayResponse> buscarPorData(
        @PathVariable UUID tenantId,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        log.info("GET /v1/tenants/{}/fuel-prices/{}", tenantId, data);

        validateTenantContext(tenantId);

        FuelPriceDay priceDay = fuelPriceDayService.buscarPorData(tenantId, data)
            .orElseThrow(() -> new NotFoundException("Preço não encontrado para data: " + data));

        return ResponseEntity.ok(mapToResponse(priceDay));
    }

    /**
     * GET /v1/tenants/{tenantId}/fuel-prices/average
     *
     * Get average fuel price for date (with intelligent fallback).
     * Uses:
     * 1. Exact date if exists
     * 2. Average of last 7 days
     * 3. Default R$ 6.00
     *
     * @param tenantId Tenant ID
     * @param data Date
     * @return 200 OK with price value
     */
    @GetMapping("/average")
    @Operation(summary = "Get average price for date",
               description = "Get price with intelligent fallback: exact → 7-day avg → R$ 6.00")
    public ResponseEntity<BigDecimal> obterPrecoMedio(
        @PathVariable UUID tenantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        log.info("GET /v1/tenants/{}/fuel-prices/average?data={}", tenantId, data);

        validateTenantContext(tenantId);

        BigDecimal preco = fuelPriceDayService.obterPrecoMedioDia(tenantId, data);

        return ResponseEntity.ok(preco);
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private void validateTenantContext(UUID tenantId) {
        if (!TenantContext.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }

    private FuelPriceDay mapToEntity(FuelPriceDayCreateRequest request) {
        return FuelPriceDay.builder()
            .data(request.getData())
            .precoMedioLitro(request.getPrecoMedioLitro())
            .totalLitrosAbastecidos(request.getTotalLitrosAbastecidos())
            .totalCusto(request.getTotalCusto())
            .qtdAbastecimentos(request.getQtdAbastecimentos())
            .build();
    }

    private FuelPriceDayResponse mapToResponse(FuelPriceDay entity) {
        return FuelPriceDayResponse.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .data(entity.getData())
            .precoMedioLitro(entity.getPrecoMedioLitro())
            .totalLitrosAbastecidos(entity.getTotalLitrosAbastecidos())
            .totalCusto(entity.getTotalCusto())
            .qtdAbastecimentos(entity.getQtdAbastecimentos())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
