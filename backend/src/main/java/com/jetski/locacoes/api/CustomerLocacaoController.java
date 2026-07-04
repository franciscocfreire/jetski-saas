package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.CustomerLocacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Histórico de locações do cliente final + avaliação (P4 do portal).
 * Escopo /v1/customers/** (role CLIENTE, posse via vínculos, sem X-Tenant-Id).
 */
@RestController
@RequestMapping("/v1/customers/locacoes")
@RequiredArgsConstructor
@Tag(name = "Portal do Cliente — Locações", description = "Histórico, fotos e avaliação")
public class CustomerLocacaoController {

    private final CustomerLocacaoService customerLocacaoService;

    public record AvaliarRequest(
        @NotNull @Min(1) @Max(5) Integer nota,
        @Size(max = 1000) String comentario
    ) {}

    @GetMapping
    @Operation(summary = "Minhas locações (todas as lojas vinculadas)")
    public ResponseEntity<List<CustomerLocacaoService.LocacaoCliente>> minhas(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(customerLocacaoService.minhasLocacoes(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe da locação (valores + fotos de check-in/out)")
    public ResponseEntity<CustomerLocacaoService.LocacaoClienteDetalhe> detalhe(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerLocacaoService.detalhe(jwt.getSubject(), id));
    }

    @GetMapping("/{id}/recibo")
    @Operation(summary = "Recibo da locação finalizada (PDF)")
    public ResponseEntity<byte[]> recibo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        byte[] pdf = customerLocacaoService.recibo(jwt.getSubject(), id);
        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/pdf")
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=recibo-" + id.toString().substring(0, 8) + ".pdf")
            .body(pdf);
    }

    @PostMapping("/{id}/avaliacao")
    @Operation(summary = "Avalia a locação finalizada (nota 1-5 + comentário; única)")
    public ResponseEntity<CustomerLocacaoService.LocacaoCliente> avaliar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody AvaliarRequest request) {
        return ResponseEntity.ok(customerLocacaoService.avaliar(
            jwt.getSubject(), id, request.nota(), request.comentario()));
    }
}
