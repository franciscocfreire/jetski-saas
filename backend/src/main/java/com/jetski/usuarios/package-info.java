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
 * </ul>
 *
 * <p><strong>Implementação Interna:</strong>
 * <ul>
 *   <li>{@code internal} - Serviços, repositórios e lógica de negócio (não deve ser acessado diretamente)</li>
 * </ul>
 *
 * <p><strong>Dependências:</strong>
 * <ul>
 *   <li>{@code shared} - Utiliza infraestrutura de segurança e multi-tenancy</li>
 * </ul>
 *
 * @since 0.1.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Users and Members",
    allowedDependencies = "shared::security"
)
package com.jetski.usuarios;
