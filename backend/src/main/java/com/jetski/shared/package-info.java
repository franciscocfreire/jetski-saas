/**
 * Módulo compartilhado (Shared Module)
 *
 * <p>Este módulo contém infraestrutura transversal e funcionalidades compartilhadas
 * entre todos os módulos do sistema, incluindo:
 *
 * <ul>
 *   <li>Segurança e autenticação (OAuth2/JWT, multi-tenancy)</li>
 *   <li>Autorização via OPA (RBAC e Alçada)</li>
 *   <li>Tratamento global de exceções</li>
 *   <li>Configurações comuns (cache, web, etc.)</li>
 *   <li>Event listeners para eventos de domínio de outros módulos</li>
 * </ul>
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code security} - Contexto de tenant e configuração de segurança</li>
 *   <li>{@code authorization} - Serviços de autorização OPA</li>
 *   <li>{@code exception} - Classes de exceção e tratamento global</li>
 *   <li>{@code config} - Configurações compartilhadas</li>
 * </ul>
 *
 * <p><strong>Implementação Interna:</strong>
 * <ul>
 *   <li>{@code internal} - Filtros, conversores e infraestrutura (não deve ser acessado por outros módulos)</li>
 *   <li>{@code internal.listeners} - Event listeners para eventos de outros módulos (subscribed to usuarios::events)</li>
 * </ul>
 *
 * <p><strong>Event Subscriptions:</strong><br>
 * This module listens to domain events from other modules via Spring Modulith's ApplicationModuleListener,
 * including {@code UserAccountActivatedEvent} from usuarios::events.
 *
 * @since 0.1.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Shared Infrastructure"
    // No allowedDependencies specified - allows internal dependencies and event listening
)
package com.jetski.shared;
