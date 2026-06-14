/**
 * Módulo de Auditoria (Audit Module)
 *
 * <p>Registra "quem fez o quê, quando" de forma transversal, consumindo eventos de
 * domínio publicados pelos demais módulos (check-in/out, reservas, membros, etc.).
 *
 * <p><strong>Posicionamento arquitetural:</strong> este é um módulo <em>observador</em>,
 * que fica acima das features — ele depende dos eventos dos módulos de negócio, mas
 * nenhum módulo depende dele. Por isso vive como módulo top-level (e não dentro de
 * {@code shared}, que é a fundação e não pode depender de features — isso criaria ciclo).
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code api} - Controller REST e DTOs de consulta/exportação de auditoria</li>
 *   <li>{@code domain} - Entidade {@code Auditoria} e repositório</li>
 * </ul>
 *
 * <p><strong>Implementação Interna:</strong>
 * <ul>
 *   <li>{@code internal} - {@code AuditoriaService} e {@code AuditEventListener}
 *       (subscrito aos eventos de locacoes::events, reservas, usuarios::events)</li>
 * </ul>
 *
 * @since 0.10.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Audit"
    // Sem allowedDependencies: módulo observador, depende de eventos/serviços expostos
    // de várias features (mesma abordagem do módulo shared).
)
package com.jetski.audit;
