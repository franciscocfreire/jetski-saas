package com.jetski.opa.service;

import com.jetski.opa.dto.OPADecision;
import com.jetski.opa.dto.OPAInput;
import com.jetski.opa.dto.OPARequest;
import com.jetski.opa.dto.OPAResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

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
     * Versão genérica para autorização (tenta RBAC, depois Alçada se necessário).
     *
     * Use este método quando a ação pode ter regras tanto de RBAC quanto de Alçada.
     *
     * @param input Contexto da autorização
     * @return OPADecision consolidada
     */
    public OPADecision authorize(OPAInput input) {
        // Primeiro tenta RBAC
        OPADecision rbacDecision = authorizeRBAC(input);

        // Se negado por RBAC, não adianta checar alçada
        if (!rbacDecision.isAllowed()) {
            log.debug("Negado por RBAC, pulando verificação de Alçada");
            return rbacDecision;
        }

        // Se ação envolve valores/limites, verifica alçada
        if (input.getOperation() != null) {
            log.debug("Operação com contexto, verificando Alçada");
            return authorizeAlcada(input);
        }

        // Permitido por RBAC sem necessidade de alçada
        return rbacDecision;
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
