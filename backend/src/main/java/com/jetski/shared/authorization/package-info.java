/**
 * Authorization API - Serviços de autorização via OPA (Open Policy Agent).
 *
 * <p>Contrato público de autorização do módulo shared:
 * <ul>
 *   <li>{@link OPAAuthorizationService} - Avaliação de políticas (ABAC/RBAC/alçada)
 *       e consulta de permissões efetivas / matriz papel×permissões</li>
 * </ul>
 *
 * <p><strong>Module Architecture:</strong><br>
 * Named interface do módulo 'shared'. O enforcement por requisição
 * ({@code ABACAuthorizationInterceptor}, {@code ActionExtractor}) é infraestrutura
 * registrada pelo próprio shared; outros módulos consomem apenas o serviço.
 *
 * @since 0.30.0
 */
@org.springframework.modulith.NamedInterface("authorization")
package com.jetski.shared.authorization;
