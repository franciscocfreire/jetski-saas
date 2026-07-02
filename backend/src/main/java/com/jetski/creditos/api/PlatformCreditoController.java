package com.jetski.creditos.api;

import com.jetski.creditos.api.dto.CompraResponse;
import com.jetski.creditos.api.dto.LancamentoResponse;
import com.jetski.creditos.api.dto.LancarCreditoRequest;
import com.jetski.creditos.api.dto.PlatformCompraDTO;
import com.jetski.creditos.api.dto.PlatformSaldoTenantDTO;
import com.jetski.creditos.api.dto.RejeitarCompraRequest;
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

    @GetMapping("/compras")
    @Operation(summary = "Compras de créditos pendentes de aprovação (todas as empresas)")
    public List<PlatformCompraDTO> comprasPendentes() {
        return platformCreditoService.comprasPendentes();
    }

    @PostMapping("/compras/{tenantId}/{compraId}/aprovar")
    @Operation(summary = "Aprovar compra — credita no ledger (auditado) e marca APROVADA")
    public CompraResponse aprovar(@PathVariable UUID tenantId, @PathVariable UUID compraId) {
        log.info("POST /v1/platform/creditos/compras/{}/{}/aprovar", tenantId, compraId);
        return CompraResponse.from(platformCreditoService.aprovarCompra(tenantId, compraId));
    }

    @PostMapping("/compras/{tenantId}/{compraId}/rejeitar")
    @Operation(summary = "Rejeitar compra — motivo obrigatório")
    public CompraResponse rejeitar(
            @PathVariable UUID tenantId,
            @PathVariable UUID compraId,
            @RequestBody RejeitarCompraRequest request) {
        log.info("POST /v1/platform/creditos/compras/{}/{}/rejeitar", tenantId, compraId);
        return CompraResponse.from(
            platformCreditoService.rejeitarCompra(tenantId, compraId, request.observacao()));
    }
}
