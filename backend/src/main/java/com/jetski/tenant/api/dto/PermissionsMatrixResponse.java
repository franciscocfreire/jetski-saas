package com.jetski.tenant.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Matriz papel → permissões (documento role_permissions do rbac.rego, via OPA).
 *
 * As permissões são cruas e podem conter wildcards ("*", "recurso:*").
 * Visualização read-only no backoffice — a edição de papéis por usuário é
 * feita em Gerenciar Usuários; overrides por tenant são escopo futuro.
 *
 * @param roles mapa papel → lista de permissões cruas
 */
public record PermissionsMatrixResponse(Map<String, List<String>> roles) {}
