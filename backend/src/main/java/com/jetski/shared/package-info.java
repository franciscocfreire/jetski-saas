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
 *   <li>Observabilidade (tracing, métricas, correlação MDC)</li>
 * </ul>
 *
 * <p>É a camada de <em>fundação</em>: todos os módulos dependem dela, e por isso ela
 * não deve depender de nenhum módulo de feature (consumo de eventos de negócio vive
 * no módulo {@code audit}, que é um observador top-level).
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code security} - Contexto de tenant e configuração de segurança</li>
 *   <li>{@code authorization} - Serviços de autorização OPA</li>
 *   <li>{@code exception} - Classes de exceção e tratamento global</li>
 *   <li>{@code config} - Configurações compartilhadas</li>
 *   <li>{@code storage} - Serviço de armazenamento de arquivos (S3/MinIO/Local)</li>
 *   <li>{@code observability} - Chaves de correlação MDC, métricas e tracing</li>
 * </ul>
 *
 * <p><strong>Implementação Interna:</strong>
 * <ul>
 *   <li>{@code internal} - Filtros, conversores e infraestrutura (não deve ser acessado por outros módulos)</li>
 * </ul>
 *
 * @since 0.1.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Shared Infrastructure"
    // No allowedDependencies specified - allows internal dependencies and event listening
)
package com.jetski.shared;
