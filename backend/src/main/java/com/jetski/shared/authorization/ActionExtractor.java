package com.jetski.shared.authorization;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a "action" (ação) de um HTTP request para uso em políticas ABAC.
 *
 * Mapeia:
 * - HTTP Method (GET, POST, PUT, DELETE)
 * - URI Path (ex: /v1/locacoes/{id}/checkin)
 * → Action (ex: "locacao:checkin")
 *
 * Exemplos:
 * - GET    /v1/locacoes         → locacao:list
 * - GET    /v1/locacoes/{id}    → locacao:view
 * - POST   /v1/locacoes         → locacao:create
 * - PUT    /v1/locacoes/{id}    → locacao:update
 * - DELETE /v1/locacoes/{id}    → locacao:delete
 * - POST   /v1/locacoes/{id}/checkin  → locacao:checkin
 * - POST   /v1/locacoes/{id}/checkout → locacao:checkout
 *
 * @author Jetski Team
 */
@Slf4j
@Component
public class ActionExtractor {

    // Padrões de regex para extração de resource e sub-action
    private static final Pattern RESOURCE_PATTERN = Pattern.compile("^/v1/([^/]+)");
    private static final Pattern SUB_ACTION_PATTERN = Pattern.compile("/([^/]+)$");

    /**
     * Extrai a action do request HTTP.
     *
     * @param request HTTP request
     * @return Action string (formato: "resource:action")
     */
    public String extractAction(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();

        // Remove context path (/api)
        if (contextPath != null && !contextPath.isEmpty()) {
            uri = uri.substring(contextPath.length());
        }

        // Remove query params
        int queryIndex = uri.indexOf('?');
        if (queryIndex > 0) {
            uri = uri.substring(0, queryIndex);
        }

        log.debug("Extracting action from: {} {}", method, uri);

        // Tenta extrair resource (primeiro segmento após /v1/)
        String resource = extractResource(uri);
        if (resource == null) {
            log.warn("Could not extract resource from URI: {}", uri);
            return "unknown:unknown";
        }

        // Extrai action baseada em method + URI
        String action = extractActionFromMethodAndUri(method, uri, resource);

        log.debug("Extracted action: {}", action);
        return action;
    }

    /**
     * Extrai o resource name do URI.
     *
     * Exemplo: /v1/locacoes/{id}/checkin → "locacao"
     * Exemplo: /v1/tenants/{id}/users/invite → "user"
     */
    private String extractResource(String uri) {
        // Trata nested resources: /v1/tenants/{id}/users/... → "user"
        Pattern nestedPattern = Pattern.compile("/tenants/[^/]+/([^/]+)");
        Matcher nestedMatcher = nestedPattern.matcher(uri);
        if (nestedMatcher.find()) {
            String nestedResource = nestedMatcher.group(1);
            return singularize(nestedResource);
        }

        // Extração padrão do primeiro segmento
        Matcher matcher = RESOURCE_PATTERN.matcher(uri);
        if (matcher.find()) {
            String resource = matcher.group(1);
            return singularize(resource);
        }
        return null;
    }

    /**
     * Converte plural para singular (heurística para português).
     */
    private String singularize(String resource) {
        // Remove plural (heurística para português)
        // Padrões: locacoes → locacao, reservas → reserva, jetskis → jetski
        if (resource.endsWith("oes")) {
            // locacoes → locacao, reservacoes → reservacao
            // Replace "oes" with "ao"
            return resource.substring(0, resource.length() - 3) + "ao";
        } else if (resource.endsWith("aes")) {
            // manutencaes → manutencao (caso exista)
            // Replace "aes" with "ao"
            return resource.substring(0, resource.length() - 3) + "ao";
        } else if (resource.endsWith("s")) {
            // jetskis → jetski, modelos → modelo, reservas → reserva, users → user
            return resource.substring(0, resource.length() - 1);
        }
        return resource;
    }

    /**
     * Extrai action baseada em HTTP method + URI pattern.
     */
    private String extractActionFromMethodAndUri(String method, String uri, String resource) {
        // Verifica se URI tem sub-action (ex: /checkin, /checkout, /desconto)
        String subAction = extractSubAction(uri);

        if (subAction != null) {
            // URIs com sub-action explícita: POST /locacoes/{id}/checkin → locacao:checkin
            return resource + ":" + subAction;
        }

        // URIs RESTful padrão
        return switch (method) {
            case "GET" -> {
                if (uri.matches(".*/\\{[^}]+\\}$") || uri.matches(".*/[0-9a-fA-F-]+$")) {
                    // GET /locacoes/{id} → locacao:view
                    yield resource + ":view";
                } else {
                    // GET /locacoes → locacao:list
                    yield resource + ":list";
                }
            }
            case "POST" -> resource + ":create";
            case "PUT", "PATCH" -> resource + ":update";
            case "DELETE" -> resource + ":delete";
            default -> resource + ":" + method.toLowerCase();
        };
    }

    /**
     * Extrai sub-action do final do URI.
     *
     * Exemplo: /locacoes/{id}/checkin → "checkin"
     */
    private String extractSubAction(String uri) {
        // Lista de sub-actions conhecidas
        String[] knownSubActions = {
            "checkin", "checkout", "desconto", "aprovar", "fechar", "cancelar",
            "criar", "create", "list", "view", "update", "delete", "registrar", "upload",
            "calcular", "diario", "mensal", "invite", "activate",
            "accept-terms", "confirmar-sinal", "confirmar", "alocar-jetski"
        };

        for (String subAction : knownSubActions) {
            if (uri.endsWith("/" + subAction)) {
                return subAction;
            }
        }

        return null;
    }

    /**
     * Extrai resource ID do request (path variables).
     *
     * @param request HTTP request
     * @return Resource ID (UUID string) ou null
     */
    @SuppressWarnings("unchecked")
    public String extractResourceId(HttpServletRequest request) {
        Map<String, String> pathVariables =
            (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        // Tenta extrair dos path variables primeiro (disponível após handler mapping)
        if (pathVariables != null && !pathVariables.isEmpty()) {
            String id = pathVariables.getOrDefault("id",
                    pathVariables.getOrDefault("locacaoId",
                            pathVariables.getOrDefault("jetskiId", null)));
            if (id != null) {
                return id;
            }
        }

        // Fallback: extrai UUID do path usando regex (para interceptor antes do handler mapping)
        return extractIdFromPath(request.getRequestURI());
    }

    /**
     * Extrai UUID do path usando regex.
     * Busca padrão UUID no path: /v1/locacoes/{uuid}/... ou /v1/locacoes/{uuid}
     *
     * @param path URI path
     * @return UUID string ou null
     */
    private String extractIdFromPath(String path) {
        // Pattern para UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        Pattern uuidPattern = Pattern.compile("/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
        Matcher matcher = uuidPattern.matcher(path);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
