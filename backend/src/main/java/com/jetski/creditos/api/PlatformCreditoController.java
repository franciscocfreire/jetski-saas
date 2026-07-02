package com.jetski.creditos.api;

import com.jetski.creditos.api.dto.LancamentoResponse;
import com.jetski.creditos.api.dto.LancarCreditoRequest;
import com.jetski.creditos.api.dto.PlatformSaldoTenantDTO;
import com.jetski.creditos.internal.PlatformCreditoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Lançamento e consulta de créditos pelo super admin.
 *
 * <p>Autorização: {@code /v1/platform/**} → ação {@code platform:*} no OPA,
 * liberada apenas para {@code unrestricted_access}. Todo lançamento fica no
 * ledger append-only e gera trilha em auditoria ({@code CREDITO_LANCADO}).
 */
@RestController
@RequestMapping("/v1/platform/creditos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Platform Créditos", description = "Créditos de emissão por empresa (super admin)")
public class PlatformCreditoController {

    private final PlatformCreditoService platformCreditoService;

    @GetMapping
    @Operation(summary = "Saldo de créditos de todas as empresas")
    public List<PlatformSaldoTenantDTO> saldos() {
        return platformCreditoService.saldos();
    }

    @PostMapping("/{tenantId}")
    @Operation(summary = "Lançar créditos (±) para uma empresa — motivo obrigatório, auditado")
    public LancamentoResponse lancar(
            @PathVariable UUID tenantId,
            @RequestBody LancarCreditoRequest request) {
        log.info("POST /v1/platform/creditos/{} quantidade={}", tenantId, request.quantidade());
        return LancamentoResponse.from(
            platformCreditoService.lancar(tenantId, request.quantidade(), request.motivo()));
    }
}
