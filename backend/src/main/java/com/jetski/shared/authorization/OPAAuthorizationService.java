package com.jetski.shared.authorization;

import com.jetski.shared.authorization.dto.OPADecision;
import com.jetski.shared.authorization.dto.OPAInput;
import com.jetski.shared.authorization.dto.OPARequest;
import com.jetski.shared.authorization.dto.OPAResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service para autorização via OPA (Open Policy Agent).
 *
 * Comunica com OPA Server para avaliar políticas de autorização:
 * - RBAC: Controle de acesso baseado em papéis
 * - Alçadas: Limites de aprovação por hierarquia
 *
 * @author Jetski Team
 */
@Slf4j
@Service
public class OPAAuthorizationService {

    private static final String RBAC_ENDPOINT = "/v1/data/jetski/authz/rbac/allow";
    private static final String ALCADA_ENDPOINT = "/v1/data/jetski/authz/alcada";
    private static final String AUTHORIZATION_ENDPOINT = "/v1/data/jetski/authorization/result";
    private static final String USER_PERMISSIONS_ENDPOINT = "/v1/data/jetski/rbac/user_permissions";
    private static final String ROLE_PERMISSIONS_ENDPOINT = "/v1/data/jetski/rbac/role_permissions";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient opaWebClient;

    public OPAAuthorizationService(@Qualifier("opaWebClient") WebClient opaWebClient) {
        this.opaWebClient = opaWebClient;
    }

    /**
     * Avalia política RBAC (Role-Based Access Control).
     *
     * Consulta OPA endpoint: /v1/data/jetski/authz/rbac/allow
     * Retorna se ação é permitida para o role do usuário.
     *
     * @param input Contexto da autorização (user, resource, action)
     * @return OPADecision com resultado da autorização
     */
    public OPADecision authorizeRBAC(OPAInput input) {
        log.debug("Autorizando RBAC: action={}, user.role={}, tenant={}",
            input.getAction(),
            input.getUser() != null ? input.getUser().getRole() : "null",
            input.getUser() != null ? input.getUser().getTenant_id() : "null");

        try {
            OPAResponse<Boolean> response = opaWebClient
                .post()
                .uri(RBAC_ENDPOINT)
                .bodyValue(OPARequest.of(input))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<OPAResponse<Boolean>>() {})
                .timeout(DEFAULT_TIMEOUT)
                .block();

            boolean allowed = response != null && Boolean.TRUE.equals(response.getResult());

            log.info("RBAC Decision: action={}, allowed={}", input.getAction(), allowed);

            return OPADecision.builder()
                .allow(allowed)
                .tenantIsValid(true) // RBAC já valida tenant no Rego
                .build();

        } catch (WebClientResponseException e) {
            log.error("Erro ao consultar OPA RBAC: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString(), e);
            return denyDecision("OPA request failed: " + e.getMessage());

        } catch (Exception e) {
            log.error("Erro inesperado ao consultar OPA RBAC", e);
            return denyDecision("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Avalia política de Alçada (limites de aprovação).
     *
     * Consulta OPA endpoint: /v1/data/jetski/authz/alcada
     * Retorna decisão estruturada com:
     * - allow: operação permitida?
     * - requer_aprovacao: precisa de aprovação superior?
     * - aprovador_requerido: qual role deve aprovar?
     *
     * @param input Contexto da autorização com dados da operação (valores, percentuais)
     * @return OPADecision com resultado da autorização e alçada
     */
    public OPADecision authorizeAlcada(OPAInput input) {
        log.debug("Autorizando Alçada: action={}, user.role={}, operation={}",
            input.getAction(),
            input.getUser() != null ? input.getUser().getRole() : "null",
            input.getOperation());

        try {
            OPAResponse<OPADecision> response = opaWebClient
                .post()
                .uri(ALCADA_ENDPOINT)
                .bodyValue(OPARequest.of(input))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<OPAResponse<OPADecision>>() {})
                .timeout(DEFAULT_TIMEOUT)
                .block();

            OPADecision decision = response != null ? response.getResult() : null;

            if (decision == null) {
                log.warn("OPA retornou decisão nula para Alçada");
                return denyDecision("OPA returned null decision");
            }

            log.info("Alçada Decision: action={}, allow={}, requer_aprovacao={}, aprovador={}",
                input.getAction(),
                decision.isAllowed(),
                decision.requiresApproval(),
                decision.getAprovadorRequerido());

            return decision;

        } catch (WebClientResponseException e) {
            log.error("Erro ao consultar OPA Alçada: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString(), e);
            return denyDecision("OPA request failed: " + e.getMessage());

        } catch (Exception e) {
            log.error("Erro inesperado ao consultar OPA Alçada", e);
            return denyDecision("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Versão genérica para autorização ABAC completa (RBAC + Alçada + Business + Context + Multi-tenant).
     *
     * Consulta endpoint principal: /v1/data/jetski/authorization/result
     * que combina todas as políticas (rbac.rego, alcada.rego, business_rules.rego, context.rego, multi_tenant.rego)
     *
     * @param input Contexto completo da autorização (user, resource, operation, context)
     * @return OPADecision consolidada com todas as validações
     */
    public OPADecision authorize(OPAInput input) {
        log.debug("Autorizando ABAC: action={}, user.role={}, tenant={}, context={}",
            input.getAction(),
            input.getUser() != null ? input.getUser().getRole() : "null",
            input.getUser() != null ? input.getUser().getTenant_id() : "null",
            input.getContext() != null ? input.getContext().getTimestamp() : "null");

        try {
            OPAResponse<OPADecision> response = opaWebClient
                .post()
                .uri(AUTHORIZATION_ENDPOINT)
                .bodyValue(OPARequest.of(input))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<OPAResponse<OPADecision>>() {})
                .timeout(DEFAULT_TIMEOUT)
                .block();

            OPADecision decision = response != null ? response.getResult() : null;

            if (decision == null) {
                log.warn("OPA retornou decisão nula para Authorization");
                return denyDecision("OPA returned null decision");
            }

            log.info("ABAC Decision: action={}, allow={}, tenant_valid={}, requer_aprovacao={}, aprovador={}",
                input.getAction(),
                decision.isAllowed(),
                decision.isTenantValid(),
                decision.requiresApproval(),
                decision.getAprovadorRequerido());

            return decision;

        } catch (WebClientResponseException e) {
            log.error("Erro ao consultar OPA Authorization: status={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString(), e);
            return denyDecision("OPA request failed: " + e.getMessage());

        } catch (Exception e) {
            log.error("Erro inesperado ao consultar OPA Authorization", e);
            return denyDecision("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Permissões efetivas (cruas, com wildcards "*" e "recurso:*") da união dos
     * papéis informados, conforme o mapa role_permissions do rbac.rego.
     *
     * Consulta OPA endpoint: /v1/data/jetski/rbac/user_permissions
     * Erro → lista vazia (fail-safe: menu conservador, nunca 500; o enforcement
     * real continua no ABACAuthorizationInterceptor).
     *
     * @param roles papéis do usuário no tenant atual (TenantContext)
     * @return permissões cruas, deduplicadas e ordenadas pelo OPA
     */
    public List<String> getUserPermissions(List<String> roles) {
        OPAInput input = OPAInput.builder()
            .user(OPAInput.UserContext.builder().roles(roles).build())
            .build();

        try {
            OPAResponse<List<String>> response = opaWebClient
                .post()
                .uri(USER_PERMISSIONS_ENDPOINT)
                .bodyValue(OPARequest.of(input))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<OPAResponse<List<String>>>() {})
                .timeout(DEFAULT_TIMEOUT)
                .block();

            List<String> permissions = response != null && response.getResult() != null
                ? response.getResult()
                : List.of();

            log.debug("User permissions: roles={}, count={}", roles, permissions.size());
            return permissions;

        } catch (Exception e) {
            log.error("Erro ao consultar OPA user_permissions: roles={}", roles, e);
            return List.of();
        }
    }

    /**
     * Matriz completa papel → permissões (documento role_permissions do rbac.rego),
     * para a tela read-only de permissões do backoffice.
     *
     * Consulta OPA endpoint: /v1/data/jetski/rbac/role_permissions
     * Erro → mapa vazio.
     */
    public Map<String, List<String>> getRolePermissionsMatrix() {
        try {
            OPAResponse<Map<String, List<String>>> response = opaWebClient
                .post()
                .uri(ROLE_PERMISSIONS_ENDPOINT)
                .bodyValue(OPARequest.of(OPAInput.builder().build()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<OPAResponse<Map<String, List<String>>>>() {})
                .timeout(DEFAULT_TIMEOUT)
                .block();

            return response != null && response.getResult() != null
                ? response.getResult()
                : Map.of();

        } catch (Exception e) {
            log.error("Erro ao consultar OPA role_permissions", e);
            return Map.of();
        }
    }

    /**
     * Helper para criar decisão de negação com mensagem.
     */
    private OPADecision denyDecision(String reason) {
        log.warn("Decisão negada: {}", reason);
        return OPADecision.builder()
            .allow(false)
            .tenantIsValid(false)
            .build();
    }
}
