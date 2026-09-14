package com.jetski.usuarios.api;

import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import com.jetski.usuarios.api.dto.TenantSummary;
import com.jetski.usuarios.api.dto.UserTenantsResponse;
import com.jetski.usuarios.domain.Membro;
import com.jetski.usuarios.api.IdentityProviderMappingService;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.usuarios.internal.TenantAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller: User Tenants
 *
 * Provides endpoints for users to list their accessible tenants.
 *
 * Use cases:
 * - Mobile app: Show tenant selector on login
 * - Web app: Show tenant dropdown
 * - Super admin: Indicate unrestricted access (use search instead)
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@RestController
@RequestMapping("/v1/user/tenants")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User", description = "User account and tenant management")
public class UserTenantsController {

    private final TenantAccessService tenantAccessService;
    private final IdentityProviderMappingService identityMappingService;
    private final TenantQueryService tenantQueryService;
    private final com.jetski.tenant.PlanoLimiteService planoLimiteService;
    private final com.jetski.tenant.PapelEmissaoService papelEmissaoService;

    /**
     * GET /api/v1/user/tenants
     *
     * Lists all tenants the authenticated user can access.
     *
     * Response types:
     * - LIMITED: Returns list of tenants (max 100)
     * - UNRESTRICTED: User is platform admin, can access any tenant
     *
     * For users with 10k+ tenants, use tenant search API instead.
     *
     * @param jwt Authenticated user JWT
     * @return UserTenantsResponse with access type and tenant list
     */
    @GetMapping
    @Operation(
        summary = "List user's accessible tenants",
        description = "Returns all tenants the user can access. " +
            "For platform admins, returns UNRESTRICTED indicator.",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    public ResponseEntity<UserTenantsResponse> listUserTenants(
            @AuthenticationPrincipal Jwt jwt) {

        // Resolve Keycloak UUID to PostgreSQL UUID using identity provider mapping
        // Try getSubject() first, fallback to "sub" claim directly
        String providerUserId = jwt.getSubject();
        if (providerUserId == null && jwt.hasClaim("sub")) {
            providerUserId = jwt.getClaimAsString("sub");
        }

        // WORKAROUND: se sub não existe, use email para buscar usuário
        // O Keycloak não está incluindo 'sub' no access token mesmo com mapper
        if (providerUserId == null) {
            if (jwt.hasClaim("email")) {
                String email = jwt.getClaimAsString("email");
                log.warn("JWT sem 'sub' claim — usando e-mail como fallback");
                UUID usuarioId;
                try {
                    usuarioId = identityMappingService.resolveUsuarioIdByEmail(email);
                } catch (NotFoundException e) {
                    return ResponseEntity.ok(semVinculos());
                }

                // Count total tenants
                long count = tenantAccessService.countUserTenants(usuarioId);

                // Build tenant summaries from the user's own memberships (também p/ super admin)
                List<Membro> membros = tenantAccessService.listUserTenants(usuarioId);
                List<UUID> tenantIds = membros.stream()
                    .map(Membro::getTenantId)
                    .collect(Collectors.toList());
                Map<UUID, Tenant> tenantsMap = tenantQueryService.findTenantsById(tenantIds);
                List<TenantSummary> tenantSummaries = buildTenantSummaries(membros, tenantsMap);

                if (count == -1) {
                    return ResponseEntity.ok(
                        UserTenantsResponse.unrestricted(tenantSummaries)
                    );
                }

                return ResponseEntity.ok(
                    UserTenantsResponse.limited(tenantSummaries, count)
                );
            } else {
                throw new RuntimeException("JWT sem 'sub' nem 'email' claim. Impossível identificar usuário.");
            }
        }

        UUID usuarioId;
        try {
            usuarioId = identityMappingService.resolveUsuarioId("keycloak", providerUserId);
        } catch (NotFoundException e) {
            // Autenticado mas sem cadastro de STAFF (cliente do portal, conta
            // Google recém-criada, sessão de usuário removido): não é erro — é
            // "zero vínculos". 200 vazio deixa o backoffice mostrar a tela de
            // orientação (NoTenantGate) sem spam de 404/retry.
            log.info("JWT válido sem usuário staff (sub={}) — respondendo sem vínculos", providerUserId);
            return ResponseEntity.ok(semVinculos());
        }

        // Count total tenants
        long count = tenantAccessService.countUserTenants(usuarioId);

        // Build tenant summaries from the user's own memberships (também p/ super admin,
        // para que ele tenha contexto de tenant ao operar o painel de plataforma)
        List<Membro> membros = tenantAccessService.listUserTenants(usuarioId);
        List<UUID> tenantIds = membros.stream()
            .map(Membro::getTenantId)
            .collect(Collectors.toList());
        Map<UUID, Tenant> tenantsMap = tenantQueryService.findTenantsById(tenantIds);
        List<TenantSummary> tenantSummaries = buildTenantSummaries(membros, tenantsMap);

        if (count == -1) {
            // Super admin with unrestricted access (inclui suas próprias associações)
            return ResponseEntity.ok(
                UserTenantsResponse.unrestricted(tenantSummaries)
            );
        }

        return ResponseEntity.ok(
            UserTenantsResponse.limited(tenantSummaries, count)
        );
    }

    /** Resposta para autenticado sem cadastro staff: acesso LIMITED com zero tenants. */
    private static UserTenantsResponse semVinculos() {
        return UserTenantsResponse.limited(List.of(), 0);
    }

    /**
     * Build TenantSummary list from Membro and Tenant data.
     */
    private List<TenantSummary> buildTenantSummaries(List<Membro> membros, Map<UUID, Tenant> tenantsMap) {
        // Papel na emissão (§8.M): o switcher agrupa as delegadas sob a EAMA delas.
        Map<UUID, com.jetski.tenant.PapelEmissaoService.PapelEmissao> papeis = new java.util.HashMap<>();
        for (Membro m : membros) {
            Tenant t = tenantsMap.get(m.getTenantId());
            var papel = papelEmissaoService.papelDe(m.getTenantId(),
                t != null && Boolean.TRUE.equals(t.getEmissoraHabilitada()));
            if (papel != null) {
                papeis.put(m.getTenantId(), papel);
            }
        }
        // A EAMA de uma delegada pode não ser empresa do usuário: busca só o nome dela.
        List<UUID> emissorasFora = papeis.values().stream()
            .map(com.jetski.tenant.PapelEmissaoService.PapelEmissao::emissoraTenantId)
            .filter(java.util.Objects::nonNull)
            .filter(id -> !tenantsMap.containsKey(id))
            .distinct()
            .toList();
        Map<UUID, Tenant> conhecidas = new java.util.HashMap<>(tenantsMap);
        if (!emissorasFora.isEmpty()) {
            Map<UUID, Tenant> fora = tenantQueryService.findTenantsById(emissorasFora);
            if (fora != null) {
                conhecidas.putAll(fora);
            }
        }
        return membros.stream()
            .map(membro -> {
                Tenant tenant = tenantsMap.get(membro.getTenantId());
                var papel = papeis.get(membro.getTenantId());
                Tenant emissora = papel != null && papel.emissoraTenantId() != null
                    ? conhecidas.get(papel.emissoraTenantId()) : null;
                // Módulos do plano (V046): null = todos — o menu do backoffice
                // filtra por isto (sentinela "*" vira null p/ o frontend)
                List<String> modulos = planoLimiteService.modulosDoPlano(membro.getTenantId());
                boolean podeDesativarVideoaula = modulos.contains("*")
                    || modulos.contains(com.jetski.tenant.ModuloPlano.VIDEO_ORIENTACAO.name());
                com.jetski.tenant.domain.DocumentoConfig docCfg =
                    tenant != null && tenant.getDocumentoConfig() != null
                        ? tenant.getDocumentoConfig() : com.jetski.tenant.domain.DocumentoConfig.padrao();
                return TenantSummary.builder()
                    .id(membro.getTenantId())
                    .slug(tenant != null ? tenant.getSlug() : null)
                    .razaoSocial(tenant != null ? tenant.getRazaoSocial() : null)
                    .status(tenant != null && tenant.getStatus() != null ? tenant.getStatus().name() : null)
                    .roles(membro.getPapeis() != null ? List.of(membro.getPapeis()) : List.of())
                    .modulos(modulos.contains("*") ? null : modulos)
                    .videoaulaObrigatoria(docCfg.videoaulaExigida(podeDesativarVideoaula))
                    .papelEmissao(papel != null ? papel.papel().name() : null)
                    .emissoraTenantId(papel != null ? papel.emissoraTenantId() : null)
                    .emissoraNome(emissora != null ? emissora.getRazaoSocial() : null)
                    .build();
            })
            .collect(Collectors.toList());
    }
}
