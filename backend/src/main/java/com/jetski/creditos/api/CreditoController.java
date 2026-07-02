package com.jetski.creditos.api;

import com.jetski.creditos.CreditoService;
import com.jetski.creditos.api.dto.CompraResponse;
import com.jetski.creditos.api.dto.LancamentoResponse;
import com.jetski.creditos.api.dto.SaldoResponse;
import com.jetski.creditos.api.dto.SolicitarCompraRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consulta de créditos de emissão do tenant.
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/creditos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Créditos", description = "Créditos de emissão do tenant")
public class CreditoController {

    private final CreditoService creditoService;

    @GetMapping("/saldo")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Saldo atual de créditos de emissão")
    public ResponseEntity<SaldoResponse> saldo(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(new SaldoResponse(creditoService.saldo(tenantId)));
    }

    @GetMapping("/extrato")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Extrato dos últimos lançamentos de créditos")
    public ResponseEntity<List<LancamentoResponse>> extrato(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(creditoService.extrato(tenantId, limit).stream()
            .map(LancamentoResponse::from)
            .toList());
    }

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Configuração de compra (chave PIX fixa + preço do crédito)")
    public ResponseEntity<Map<String, Object>> config(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(Map.of(
            "pixChave", creditoService.pixChave(),
            "precoUnitario", creditoService.precoUnitario()));
    }

    @PostMapping("/compras")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Solicitar compra por VALOR (créditos = valor / preço vigente; aguarda aprovação)")
    public ResponseEntity<CompraResponse> solicitarCompra(
            @PathVariable UUID tenantId,
            @RequestBody SolicitarCompraRequest request) {
        log.info("POST /v1/tenants/{}/creditos/compras valor={}", tenantId, request.valor());
        return ResponseEntity.ok(CompraResponse.from(
            creditoService.solicitarCompra(tenantId, request.valor(), request.pixTxid())));
    }

    @GetMapping("/compras")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Solicitações de compra do tenant (com status)")
    public ResponseEntity<List<CompraResponse>> compras(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(creditoService.comprasDoTenant(tenantId, limit).stream()
            .map(CompraResponse::from)
            .toList());
    }
}
