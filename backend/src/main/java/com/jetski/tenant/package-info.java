/**
 * Módulo de Tenant (Tenant Module)
 *
 * <p>Este módulo gerencia tenants (empresas/clientes) do sistema multi-tenant SaaS.
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code api} - Serviços públicos para consulta de tenants</li>
 *   <li>{@code domain} - Entidades de domínio (Tenant, TenantStatus)</li>
 * </ul>
 *
 * <p><strong>Implementação Interna:</strong>
 * <ul>
 *   <li>{@code internal} - Repositórios e lógica de negócio</li>
 * </ul>
 *
 * @since 0.1.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Tenant Management"
)
package com.jetski.tenant;
