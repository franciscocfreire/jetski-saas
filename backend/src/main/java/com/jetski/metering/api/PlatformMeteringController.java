package com.jetski.metering.api;

import com.jetski.metering.api.dto.PlatformEmissaoTenantDTO;
import com.jetski.metering.internal.PlatformMeteringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Visão cross-tenant de emissões para o super admin.
 *
 * <p>Autorização: {@code /v1/platform/**} → ação {@code platform:*} no OPA,
 * liberada apenas para {@code unrestricted_access} (mesmo modelo do
 * {@link com.jetski.tenant.api.PlatformTenantController}).
 */
@RestController
@RequestMapping("/v1/platform/metering")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Platform Metering", description = "Uso de emissões por empresa (super admin)")
public class PlatformMeteringController {

    private final PlatformMeteringService platformMeteringService;

    @GetMapping("/emissoes")
    @Operation(summary = "Emissões por empresa na competência (YYYY-MM; default mês atual)")
    public List<PlatformEmissaoTenantDTO> emissoes(
            @RequestParam(required = false) String competencia) {
        YearMonth ym = (competencia == null || competencia.isBlank())
            ? YearMonth.now(ZoneId.of("America/Sao_Paulo"))
            : YearMonth.parse(competencia);
        log.info("GET /v1/platform/metering/emissoes?competencia={}", ym);
        return platformMeteringService.emissoesPorTenant(ym);
    }
}
