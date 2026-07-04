/**
 * Módulo de Métricas de Negócio (Business Metrics Module)
 *
 * <p>Traduz eventos de domínio em métricas Prometheus por tenant (check-in/out,
 * reservas, pagamentos, emissões, créditos), alimentando os dashboards do Grafana.
 *
 * <p><strong>Posicionamento arquitetural:</strong> módulo <em>observador</em>, no
 * mesmo desenho do módulo {@code audit} — depende dos eventos expostos pelos módulos
 * de negócio, mas nenhum módulo depende dele. Por isso vive como módulo top-level
 * (e não dentro de {@code shared}, que é a fundação e não pode depender de features).
 *
 * <p><strong>Implementação Interna:</strong>
 * <ul>
 *   <li>{@code internal} - {@code MetricsEventListener}, subscrito aos eventos de
 *       locacoes, reservas e creditos; incrementa os medidores via
 *       {@code shared.observability.BusinessMetrics}</li>
 * </ul>
 *
 * @since 0.8.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Metrics"
    // Sem allowedDependencies: módulo observador, depende de eventos/serviços expostos
    // de várias features (mesma abordagem dos módulos audit e metering).
)
package com.jetski.metrics;
