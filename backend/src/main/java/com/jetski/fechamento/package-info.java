/**
 * Módulo de Fechamento Diário e Mensal (Daily and Monthly Closure Module)
 *
 * <p>Este módulo gerencia o fechamento financeiro diário e mensal, consolidando
 * locações, comissões e custos operacionais.
 *
 * <ul>
 *   <li>Consolidação diária de receitas, combustível, comissões por forma de pagamento</li>
 *   <li>Consolidação mensal com cálculo de resultado líquido</li>
 *   <li>Bloqueio de edições retroativas (RN06)</li>
 *   <li>Fluxo de aprovação (aberto → fechado → aprovado)</li>
 * </ul>
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code api} - Controllers REST e DTOs</li>
 *   <li>{@code domain} - Entidades (FechamentoDiario, FechamentoMensal)</li>
 * </ul>
 *
 * <p><strong>Implementação Interna:</strong>
 * <ul>
 *   <li>{@code internal} - Serviços e repositórios</li>
 * </ul>
 *
 * <p><strong>Dependências:</strong>
 * <ul>
 *   <li>{@code usuarios::api} - Resolução de usuário por email</li>
 *   <li>{@code locacoes} - Acesso a locações finalizadas</li>
 *   <li>{@code comissoes} - Acesso a comissões calculadas</li>
 *   <li>{@code shared::security} - Contexto de tenant</li>
 *   <li>{@code shared::exception} - Exceções de negócio</li>
 * </ul>
 *
 * @since 0.8.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Financial Closure",
    allowedDependencies = {
        "usuarios::api",
        "locacoes::api",
        "locacoes::domain",
        "comissoes::api",
        "comissoes::domain",
        "shared::security",
        "shared::exception"
    }
)
package com.jetski.fechamento;
