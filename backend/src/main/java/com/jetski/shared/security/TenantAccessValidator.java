package com.jetski.shared.security;

import java.util.UUID;

/**
 * Interface para validação de acesso a tenants.
 *
 * <p>Define o contrato para validar se um usuário tem acesso a um tenant específico.
 * Esta interface é parte da API pública do módulo `shared` e pode ser implementada
 * por outros módulos (ex: módulo `usuarios`).
 *
 * <p><strong>Inversão de Dependências:</strong>
 * O módulo `shared` define a interface mas não a implementa. O módulo `usuarios`
 * fornece a implementação concreta.  Isso evita dependência cíclica.
 *
 * @author Jetski Team
 * @since 0.1.0
 * @see TenantFilter
 */
public interface TenantAccessValidator {

    /**
     * Valida se um usuário tem acesso a um tenant específico usando identity provider.
     * **MÉTODO PREFERIDO** - Desacopla PostgreSQL UUIDs dos provider UUIDs
     *
     * @param provider Nome do identity provider (e.g., 'keycloak', 'google')
     * @param providerUserId ID externo do usuário no provider (JWT sub claim)
     * @param tenantId ID do tenant
     * @return informações sobre o acesso (se permitido, roles, etc.)
     */
    TenantAccessInfo validateAccess(String provider, String providerUserId, UUID tenantId);

    /**
     * Valida se um usuário tem acesso a um tenant específico usando UUID interno.
     * **MÉTODO LEGADO** - Usar validateAccess(String, String, UUID) em código novo
     *
     * @param usuarioId ID do usuário (PostgreSQL UUID interno)
     * @param tenantId ID do tenant
     * @return informações sobre o acesso (se permitido, roles, etc.)
     */
    TenantAccessInfo validateAccess(UUID usuarioId, UUID tenantId);
}
