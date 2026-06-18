package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.InstrutorRequest;
import com.jetski.locacoes.api.dto.InstrutorResponse;
import com.jetski.locacoes.domain.Instrutor;
import com.jetski.locacoes.internal.InstrutorService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD de instrutores (EAMA) — usados no Atestado de Demonstração (Anexo 5-B-1).
 * {@code /v1/tenants/{tenantId}/instrutores}
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/instrutores")
@Tag(name = "Instrutores", description = "Cadastro de instrutores (EAMA) para CHA-MTA-E")
@RequiredArgsConstructor
@Slf4j
public class InstrutorController {

    private final InstrutorService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Listar instrutores")
    public ResponseEntity<List<InstrutorResponse>> list(
        @PathVariable UUID tenantId,
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        validateTenant(tenantId);
        return ResponseEntity.ok(service.listar(includeInactive).stream()
            .map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Buscar instrutor por ID")
    public ResponseEntity<InstrutorResponse> getById(@PathVariable UUID tenantId, @PathVariable UUID id) {
        validateTenant(tenantId);
        return ResponseEntity.ok(toResponse(service.buscar(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Cadastrar instrutor")
    public ResponseEntity<InstrutorResponse> create(
        @PathVariable UUID tenantId, @Valid @RequestBody InstrutorRequest request
    ) {
        validateTenant(tenantId);
        Instrutor saved = service.criar(toEntity(request), request.getAssinaturaBase64());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Atualizar instrutor")
    public ResponseEntity<InstrutorResponse> update(
        @PathVariable UUID tenantId, @PathVariable UUID id, @Valid @RequestBody InstrutorRequest request
    ) {
        validateTenant(tenantId);
        return ResponseEntity.ok(toResponse(service.atualizar(id, toEntity(request), request.getAssinaturaBase64())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Desativar instrutor")
    public ResponseEntity<InstrutorResponse> deactivate(@PathVariable UUID tenantId, @PathVariable UUID id) {
        validateTenant(tenantId);
        return ResponseEntity.ok(toResponse(service.definirAtivo(id, false)));
    }

    @PostMapping("/{id}/reativar")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Reativar instrutor")
    public ResponseEntity<InstrutorResponse> reactivate(@PathVariable UUID tenantId, @PathVariable UUID id) {
        validateTenant(tenantId);
        return ResponseEntity.ok(toResponse(service.definirAtivo(id, true)));
    }

    private void validateTenant(UUID tenantId) {
        if (!tenantId.equals(TenantContext.getTenantId())) {
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
    }

    private Instrutor toEntity(InstrutorRequest r) {
        return Instrutor.builder()
            .nome(r.getNome()).rg(r.getRg()).orgaoEmissor(r.getOrgaoEmissor())
            .cpf(r.getCpf()).cha(r.getCha()).dataEmissao(r.getDataEmissao()).build();
    }

    private InstrutorResponse toResponse(Instrutor i) {
        return InstrutorResponse.builder()
            .id(i.getId()).tenantId(i.getTenantId()).nome(i.getNome())
            .rg(i.getRg()).orgaoEmissor(i.getOrgaoEmissor()).cpf(i.getCpf()).cha(i.getCha())
            .dataEmissao(i.getDataEmissao())
            .temAssinatura(i.getAssinaturaS3Key() != null)
            .ativo(i.getAtivo()).createdAt(i.getCreatedAt()).updatedAt(i.getUpdatedAt())
            .build();
    }
}
