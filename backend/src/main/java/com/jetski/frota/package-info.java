/**
 * Módulo de Frota (Fleet Management Module)
 *
 * <p>Este módulo gerencia KPIs e operações automatizadas da frota de jetskis.
 * Fornece dashboards, métricas de utilização, e agendamento automático de manutenção preventiva.
 *
 * <h2>Funcionalidades</h2>
 * <ul>
 *   <li><strong>RF-FROTA-01</strong>: Dashboard de KPIs da frota (utilização, disponibilidade, receita)</li>
 *   <li><strong>RF-FROTA-02</strong>: Busca avançada de jetskis com filtros múltiplos</li>
 *   <li><strong>RF-FROTA-03</strong>: Agendamento automático de manutenção preventiva (RN07)</li>
 *   <li><strong>RF-FROTA-04</strong>: Alertas de jetskis próximos à manutenção</li>
 * </ul>
 *
 * <h2>Arquitetura do Módulo</h2>
 * <ul>
 *   <li><strong>api</strong> - Controllers e DTOs REST públicos</li>
 *   <li><strong>internal</strong> - Schedulers, serviços e lógica de negócio (privado)</li>
 * </ul>
 *
 * <h2>Exposed API</h2>
 * <ul>
 *   <li>{@code api} - Public REST controllers for fleet dashboard and jetski search</li>
 * </ul>
 *
 * <h2>Dependências Declaradas</h2>
 * <ul>
 *   <li><strong>shared::security</strong> - Contexto de tenant e segurança</li>
 *   <li><strong>shared::exception</strong> - Exceções de negócio padronizadas</li>
 *   <li><strong>locacoes::domain</strong> - Entidades Jetski, JetskiStatus</li>
 *   <li><strong>manutencao::api</strong> - Serviço público para criar ordens de manutenção</li>
 *   <li><strong>manutencao::domain</strong> - Entidades OSManutencao, enums (read-only)</li>
 * </ul>
 *
 * <h2>Exemplo de Uso</h2>
 * <pre>{@code
 * // GET /api/v1/frota/dashboard
 * // Returns: FrotaDashboardResponse with all KPIs
 *
 * // POST /api/v1/jetskis/search
 * // Body: JetskiSearchCriteria
 * // Returns: List<Jetski> matching criteria
 * }</pre>
 *
 * @since 0.9.0
 * @author Jetski Team
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Fleet Management",
    allowedDependencies = {
        "shared::security",
        "shared::exception",
        "locacoes::domain",
        "manutencao::api",
        "manutencao::domain"
    }
)
package com.jetski.frota;
