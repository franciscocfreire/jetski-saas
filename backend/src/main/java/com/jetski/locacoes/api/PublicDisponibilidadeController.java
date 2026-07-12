package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.DisponibilidadeResponse;
import com.jetski.locacoes.internal.CustomerReservaService;
import com.jetski.locacoes.internal.ReservaService;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Disponibilidade PÚBLICA por loja (portal do cliente, P1).
 *
 * O endpoint tenant-scoped exige contexto autenticado; aqui a loja é resolvida
 * pelo slug e o tenant é fixado transaction-local antes da consulta (RLS
 * continua valendo). Resposta agregada — não expõe detalhes da frota.
 */
@RestController
@RequestMapping("/v1/public/lojas")
@RequiredArgsConstructor
@Tag(name = "Marketplace Público", description = "Disponibilidade por loja (sem autenticação)")
public class PublicDisponibilidadeController {

    private final ReservaService reservaService;
    private final CustomerReservaService customerReservaService;
    private final com.jetski.tenant.PlanoLimiteService planoLimiteService;
    private final EntityManager entityManager;

    @GetMapping("/{slug}/disponibilidade")
    @Transactional(readOnly = true)
    @Operation(summary = "Disponibilidade agregada de um modelo da loja no período")
    public ResponseEntity<DisponibilidadeResponse> disponibilidade(
            @PathVariable String slug,
            @RequestParam UUID modeloId,
            @RequestParam LocalDateTime dataInicio,
            @RequestParam LocalDateTime dataFimPrevista) {

        UUID tenantId = tenantIdBySlug(slug);
        // Gate por plano (V046): sem o módulo Loja online, a vitrine some e a
        // disponibilidade pública responde como loja inexistente.
        if (!planoLimiteService.moduloHabilitado(tenantId, com.jetski.tenant.ModuloPlano.LOJA_ONLINE)) {
            throw new com.jetski.shared.exception.NotFoundException("Loja não encontrada: " + slug);
        }
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        TenantContext.setTenantId(tenantId);

        ReservaService.DisponibilidadeDetalhada d =
            reservaService.verificarDisponibilidadeDetalhada(modeloId, dataInicio, dataFimPrevista);

        return ResponseEntity.ok(DisponibilidadeResponse.builder()
            .modeloId(d.getModeloId())
            .modeloNome(d.getModeloNome())
            .dataInicio(d.getDataInicio())
            .dataFimPrevista(d.getDataFimPrevista())
            .totalJetskis(d.getTotalJetskis())
            .reservasGarantidas(d.getReservasGarantidas())
            .totalReservas(d.getTotalReservas())
            .maximoReservas(d.getMaximoReservas())
            .aceitaComSinal(d.isAceitaComSinal())
            .aceitaSemSinal(d.isAceitaSemSinal())
            .vagasGarantidas(d.getVagasGarantidas())
            .vagasRegulares(d.getVagasRegulares())
            .build());
    }

    private UUID tenantIdBySlug(String slug) {
        return customerReservaService.lojaPublica(slug)
            .map(CustomerReservaService.Loja::tenantId)
            .orElseThrow(() -> new NotFoundException("Loja não encontrada: " + slug));
    }
}
