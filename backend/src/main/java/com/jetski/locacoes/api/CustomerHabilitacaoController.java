package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.CustomerHabilitacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Habilitações temporárias (CHA-MTA-E) do cliente autenticado — todas as
 * lojas vinculadas. O nº da GRU é a referência para consultar o estado da
 * habilitação junto à Marinha; a validade é de 30 dias a partir da emissão.
 *
 * Escopo /v1/customers/** (role CLIENTE, sem X-Tenant-Id).
 */
@RestController
@RequestMapping("/v1/customers/habilitacoes")
@RequiredArgsConstructor
@Tag(name = "Portal do Cliente — Habilitações",
     description = "Habilitações temporárias emitidas (validade 30 dias)")
public class CustomerHabilitacaoController {

    private final CustomerHabilitacaoService customerHabilitacaoService;

    @GetMapping
    @Operation(summary = "Minhas habilitações temporárias (todas as lojas vinculadas)")
    public ResponseEntity<List<CustomerHabilitacaoService.HabilitacaoTemporaria>> minhas(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(customerHabilitacaoService.minhasTemporarias(jwt.getSubject()));
    }

    @GetMapping("/{reservaId}/documento")
    @Operation(summary = "Baixar a confirmação da Marinha (PDF) desta habilitação")
    public ResponseEntity<byte[]> documento(
            @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID reservaId) {
        byte[] pdf = customerHabilitacaoService.documentoConfirmado(jwt.getSubject(), reservaId);
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"cha-mtae-confirmada.pdf\"")
            .body(pdf);
    }
}
