package com.jetski.locacoes.api;

import com.jetski.locacoes.domain.CustomerProfile;
import com.jetski.locacoes.internal.CustomerAccountService;
import com.jetski.locacoes.internal.CustomerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Perfil "self" do cliente final autenticado (portal).
 *
 * Escopo /v1/customers/**: exige role CLIENTE (SecurityConfig) e ação OPA
 * customer:* — sem X-Tenant-Id (o cliente é multi-loja; vínculos por tenant
 * são resolvidos internamente via cliente_identity_provider).
 */
@RestController
@RequestMapping("/v1/customers/self")
@RequiredArgsConstructor
@Tag(name = "Portal do Cliente — Conta", description = "Perfil e vínculos do cliente autenticado")
public class CustomerSelfController {

    private final CustomerAccountService customerAccountService;
    private final CustomerProfileService customerProfileService;
    private final com.jetski.locacoes.internal.CustomerAnexoService customerAnexoService;

    public record AtualizarPerfilRequest(
        @NotBlank @Size(min = 3, max = 120) String nome,
        // Identidade global (CPF define-only; endereço/telefone são por loja)
        @Size(max = 20) String cpf,
        @Size(max = 30) String rg,
        @Size(max = 20) String orgaoEmissor,
        @Size(max = 60) String nacionalidade,
        @Size(max = 80) String naturalidade,
        Boolean estrangeiro,
        java.time.LocalDate dataNascimento
    ) {}

    public record Identidade(
        String cpf, String rg, String orgaoEmissor,
        String nacionalidade, String naturalidade,
        Boolean estrangeiro, java.time.LocalDate dataNascimento
    ) {
        static Identidade of(CustomerProfile p) {
            return new Identidade(p.getCpf(), p.getRg(), p.getOrgaoEmissor(),
                p.getNacionalidade(), p.getNaturalidade(), p.getEstrangeiro(),
                p.getDataNascimento());
        }
    }

    @Value
    @Builder
    public static class SelfResponse {
        String nome;
        String email;
        boolean emailVerified;
        Identidade identidade;
        List<CustomerAccountService.VinculoLoja> lojas;
    }

    @GetMapping
    @Operation(summary = "Perfil do cliente autenticado + lojas vinculadas")
    public ResponseEntity<SelfResponse> self(@AuthenticationPrincipal Jwt jwt) {
        Boolean verified = jwt.getClaimAsBoolean("email_verified");
        CustomerProfile profile = customerProfileService.obter(
            jwt.getSubject(), jwt.getClaimAsString("name"));
        return ResponseEntity.ok(SelfResponse.builder()
            .nome(jwt.getClaimAsString("name"))
            .email(jwt.getClaimAsString("email"))
            .emailVerified(Boolean.TRUE.equals(verified))
            .identidade(Identidade.of(profile))
            .lojas(customerAccountService.vinculosComContato(jwt.getSubject()))
            .build());
    }

    public record ContatoLojaRequest(
        @Size(max = 30) String telefone,
        @Size(max = 30) String whatsapp
    ) {}

    @PutMapping("/lojas/{tenantId}/contato")
    @Operation(summary = "Atualiza telefone/WhatsApp do cliente NESTA loja (contato é por loja)")
    public ResponseEntity<CustomerAccountService.VinculoLoja> atualizarContato(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable java.util.UUID tenantId,
            @Valid @RequestBody ContatoLojaRequest request) {
        return ResponseEntity.ok(customerAccountService.atualizarContato(
            jwt.getSubject(), tenantId, request.telefone(), request.whatsapp()));
    }

    // ==================== Documentos (anexos) POR LOJA ====================

    public record UploadAnexoRequest(
        @NotBlank String tipo,
        @NotBlank String conteudoBase64
    ) {}

    @GetMapping("/lojas/{tenantId}/anexos")
    @Operation(summary = "Tipos de documento já anexados NESTA loja")
    public ResponseEntity<List<String>> anexos(
            @AuthenticationPrincipal Jwt jwt, @PathVariable java.util.UUID tenantId) {
        return ResponseEntity.ok(customerAnexoService.listar(jwt.getSubject(), tenantId));
    }

    @GetMapping("/lojas/{tenantId}/anexos/{tipo}")
    @Operation(summary = "Imagem do documento anexado (preview do próprio cliente)")
    public ResponseEntity<byte[]> anexoImagem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable java.util.UUID tenantId, @PathVariable String tipo) {
        var img = customerAnexoService.ler(jwt.getSubject(), tenantId, tipo);
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(
                img.contentType() != null ? img.contentType() : "image/jpeg"))
            .body(img.bytes());
    }

    @PostMapping("/lojas/{tenantId}/anexos")
    @Operation(summary = "Anexa/substitui documento NESTA loja (IDENTIDADE | SELFIE | COMPROVANTE_RESIDENCIA)")
    public ResponseEntity<List<String>> uploadAnexo(
            @AuthenticationPrincipal Jwt jwt, @PathVariable java.util.UUID tenantId,
            @Valid @RequestBody UploadAnexoRequest request) {
        return ResponseEntity.ok(customerAnexoService.upload(
            jwt.getSubject(), tenantId, request.tipo(), request.conteudoBase64()));
    }

    @PutMapping
    @Operation(summary = "Atualiza nome e identidade global (CPF define-only)")
    public ResponseEntity<Identidade> atualizar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AtualizarPerfilRequest request) {
        customerAccountService.atualizarNome(jwt.getSubject(), request.nome());
        CustomerProfile atualizado = customerProfileService.atualizar(
            jwt.getSubject(), request.nome(),
            new CustomerProfileService.AtualizarCmd(
                request.cpf(), request.rg(), request.orgaoEmissor(),
                request.nacionalidade(), request.naturalidade(),
                request.estrangeiro(), request.dataNascimento()));
        return ResponseEntity.ok(Identidade.of(atualizado));
    }
}
