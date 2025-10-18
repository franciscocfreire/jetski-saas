package com.jetski.shared.security;

import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.authorization.OPAAuthorizationService;
import com.jetski.shared.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller de teste para validar configuração de autenticação/autorização OAuth2.
 *
 * Endpoints para testar:
 * - Extração de claims do JWT (tenant_id, roles, etc.)
 * - Validação de tenant_id vs header X-Tenant-Id
 * - RBAC (Role-Based Access Control)
 * - Method-level security (@PreAuthorize)
 * - OPA (Open Policy Agent) integration
 *
 * TODO: Remover este controller em produção (apenas para testes de segurança)
 *
 * @author Jetski Team
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth-test")
@RequiredArgsConstructor
public class AuthTestController {

    private final OPAAuthorizationService opaAuthorizationService;

    /**
     * Endpoint público (sem autenticação) para testar conectividade
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Public endpoint - no authentication required");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint protegido - requer qualquer usuário autenticado
     * Retorna informações do usuário e tenant do JWT
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        // Informações básicas
        response.put("authenticated", true);
        response.put("principal", authentication.getName());

        // Authorities (roles)
        response.put("authorities", authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList()));

        // Tenant Context
        response.put("tenantId", TenantContext.getTenantId());

        // JWT Claims (se disponível)
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            Map<String, Object> jwtClaims = new HashMap<>();
            jwtClaims.put("sub", jwt.getSubject());
            jwtClaims.put("tenant_id", jwt.getClaimAsString("tenant_id"));
            jwtClaims.put("email", jwt.getClaimAsString("email"));
            jwtClaims.put("preferred_username", jwt.getClaimAsString("preferred_username"));
            jwtClaims.put("roles", jwt.getClaimAsStringList("roles"));
            jwtClaims.put("iss", jwt.getIssuer());
            jwtClaims.put("exp", jwt.getExpiresAt());

            response.put("jwt", jwtClaims);
        }

        log.info("User authenticated successfully: tenantId={}, principal={}, roles={}",
            TenantContext.getTenantId(), authentication.getName(),
            authentication.getAuthorities());

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint que requer role OPERADOR
     * Testa @PreAuthorize com single role
     */
    @PreAuthorize("hasRole('OPERADOR')")
    @GetMapping("/operador-only")
    public ResponseEntity<Map<String, Object>> operadorOnly(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "message", "Acesso permitido apenas para OPERADOR",
            "user", authentication.getName(),
            "tenantId", TenantContext.getTenantId()
        ));
    }

    /**
     * Endpoint que requer role GERENTE ou ADMIN_TENANT
     * Testa @PreAuthorize com múltiplas roles
     */
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN_TENANT')")
    @GetMapping("/manager-only")
    public ResponseEntity<Map<String, Object>> managerOnly(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "message", "Acesso permitido para GERENTE ou ADMIN_TENANT",
            "user", authentication.getName(),
            "tenantId", TenantContext.getTenantId()
        ));
    }

    /**
     * Endpoint que requer role FINANCEIRO
     * Testa restrição mais específica
     */
    @PreAuthorize("hasRole('FINANCEIRO')")
    @GetMapping("/finance-only")
    public ResponseEntity<Map<String, Object>> financeOnly(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "message", "Acesso permitido apenas para FINANCEIRO",
            "user", authentication.getName(),
            "tenantId", TenantContext.getTenantId()
        ));
    }

    // ==================== OPA INTEGRATION TESTS ====================

    /**
     * Testa autorização RBAC via OPA
     *
     * Query params:
     * - action: ação a validar (ex: modelo:list, locacao:checkin)
     * - role: papel do usuário (ex: OPERADOR, GERENTE)
     * - resourceTenantId: tenant do recurso (opcional, usa tenantId do user se omitido)
     */
    @GetMapping("/opa/rbac")
    public ResponseEntity<Map<String, Object>> testOpaRbac(
            @RequestParam String action,
            @RequestParam String role,
            @RequestParam(required = false) String resourceTenantId,
            Authentication authentication) {

        String tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId().toString() : null;
        String userId = authentication != null ? authentication.getName() : "anonymous";

        // Se resourceTenantId não foi passado, usa o tenantId do usuário
        String effectiveResourceTenantId = resourceTenantId != null ? resourceTenantId : tenantId;

        OPAInput input = OPAInput.builder()
            .action(action)
            .user(OPAInput.UserContext.builder()
                .id(userId)
                .tenant_id(tenantId)
                .role(role)
                .build())
            .resource(OPAInput.ResourceContext.builder()
                .id(UUID.randomUUID().toString())
                .tenant_id(effectiveResourceTenantId)
                .build())
            .build();

        OPADecision decision = opaAuthorizationService.authorizeRBAC(input);

        Map<String, Object> response = new HashMap<>();
        response.put("input", Map.of(
            "action", action,
            "user", Map.of("tenant_id", tenantId, "role", role),
            "resource", Map.of("tenant_id", effectiveResourceTenantId)
        ));
        response.put("decision", Map.of(
            "allow", decision.isAllowed(),
            "tenant_is_valid", decision.isTenantValid()
        ));
        response.put("timestamp", System.currentTimeMillis());

        log.info("OPA RBAC Test: action={}, role={}, allowed={}", action, role, decision.isAllowed());

        return ResponseEntity.ok(response);
    }

    /**
     * Testa política de Alçada via OPA
     *
     * Query params:
     * - action: ação (ex: desconto:aplicar, os:aprovar)
     * - role: papel do usuário
     * - percentualDesconto: percentual de desconto (para desconto:aplicar)
     * - valorOs: valor da OS (para os:aprovar)
     */
    @GetMapping("/opa/alcada")
    public ResponseEntity<Map<String, Object>> testOpaAlcada(
            @RequestParam String action,
            @RequestParam String role,
            @RequestParam(required = false) BigDecimal percentualDesconto,
            @RequestParam(required = false) BigDecimal valorOs,
            Authentication authentication) {

        String tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId().toString() : null;
        String userId = authentication != null ? authentication.getName() : "anonymous";

        OPAInput.OperationContext operation = OPAInput.OperationContext.builder()
            .percentual_desconto(percentualDesconto)
            .valor_os(valorOs)
            .build();

        OPAInput input = OPAInput.builder()
            .action(action)
            .user(OPAInput.UserContext.builder()
                .id(userId)
                .tenant_id(tenantId)
                .role(role)
                .build())
            .resource(OPAInput.ResourceContext.builder()
                .id(UUID.randomUUID().toString())
                .tenant_id(tenantId)
                .build())
            .operation(operation)
            .build();

        OPADecision decision = opaAuthorizationService.authorizeAlcada(input);

        Map<String, Object> response = new HashMap<>();
        response.put("input", Map.of(
            "action", action,
            "user", Map.of("tenant_id", tenantId, "role", role),
            "operation", operation
        ));
        response.put("decision", Map.of(
            "allow", decision.isAllowed(),
            "requer_aprovacao", decision.requiresApproval(),
            "aprovador_requerido", decision.getAprovadorRequerido() != null ? decision.getAprovadorRequerido() : "N/A",
            "tenant_is_valid", decision.isTenantValid()
        ));
        response.put("timestamp", System.currentTimeMillis());

        log.info("OPA Alçada Test: action={}, role={}, allow={}, requer_aprovacao={}",
            action, role, decision.isAllowed(), decision.requiresApproval());

        return ResponseEntity.ok(response);
    }

    /**
     * Testa autorização genérica (RBAC + Alçada) via OPA
     *
     * Este endpoint combina validação RBAC e Alçada:
     * 1. Primeiro valida RBAC (role tem permissão?)
     * 2. Se sim e há contexto de operação, valida Alçada
     */
    @PostMapping("/opa/authorize")
    public ResponseEntity<Map<String, Object>> testOpaAuthorize(
            @RequestBody OPAInput input,
            Authentication authentication) {

        // Injeta tenant_id do contexto se não foi fornecido
        if (input.getUser() != null && input.getUser().getTenant_id() == null) {
            input.getUser().setTenant_id(TenantContext.getTenantId() != null ? TenantContext.getTenantId().toString() : null);
        }
        if (input.getResource() != null && input.getResource().getTenant_id() == null) {
            input.getResource().setTenant_id(TenantContext.getTenantId() != null ? TenantContext.getTenantId().toString() : null);
        }

        OPADecision decision = opaAuthorizationService.authorize(input);

        Map<String, Object> response = new HashMap<>();
        response.put("input", input);
        response.put("decision", Map.of(
            "allow", decision.isAllowed(),
            "requer_aprovacao", decision.requiresApproval(),
            "aprovador_requerido", decision.getAprovadorRequerido() != null ? decision.getAprovadorRequerido() : "N/A",
            "tenant_is_valid", decision.isTenantValid()
        ));
        response.put("timestamp", System.currentTimeMillis());

        log.info("OPA Generic Authorize: action={}, allow={}, requer_aprovacao={}",
            input.getAction(), decision.isAllowed(), decision.requiresApproval());

        return ResponseEntity.ok(response);
    }
}
