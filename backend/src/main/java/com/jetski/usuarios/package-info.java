/**
 * Módulo de Usuários e Membros (Users & Members Module)
 *
 * <p>Este módulo gerencia usuários globais, membros de tenants e seus papéis/permissões
 * no sistema multi-tenant. Responsabilidades principais:
 *
 * <ul>
 *   <li>Gestão de usuários globais (cadastro, ativação, desativação)</li>
 *   <li>Associação de usuários a tenants (membros)</li>
 *   <li>Gestão de papéis por tenant (ADMIN_TENANT, GERENTE, OPERADOR, etc.)</li>
 *   <li>Verificação de acesso a tenants específicos</li>
 * </ul>
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code api} - Controllers REST e DTOs de resposta</li>
 *   <li>{@code domain} - Entidades de domínio (Usuario, Membro)</li>
 *   <li>{@code domain.event} - Eventos de domínio publicados por este módulo</li>
 * </ul>
 *
 * <p><strong>Implementação Interna:</strong>
 * <ul>
 *   <li>{@code internal} - Serviços, repositórios, listeners e lógica de negócio (não deve ser acessado diretamente)</li>
 * </ul>
 *
 * <p><strong>Dependências:</strong>
 * <ul>
 *   <li>{@code shared::security} - Contexto de tenant, autenticação e provisionamento de usuários</li>
 *   <li>{@code shared::exception} - Exceções de negócio</li>
 *   <li>{@code shared::email} - Serviço de envio de emails para notificações</li>
 * </ul>
 *
 * <p><strong>Arquitetura orientada a eventos:</strong><br>
 * Este módulo publica eventos de domínio (UserAccountActivatedEvent) e possui listeners internos
 * que reagem a esses eventos para executar ações assíncronas como envio de emails.
 *
 * @since 0.1.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Users and Members",
    allowedDependencies = {"shared::security", "shared::exception", "shared::email", "tenant", "tenant::domain"}
)
package com.jetski.usuarios;
