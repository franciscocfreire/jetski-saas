/**
 * Módulo de Gestão de Combustível
 *
 * Responsabilidades:
 * - Registro de abastecimentos (PRE_LOCACAO, POS_LOCACAO, FROTA)
 * - Políticas de cobrança de combustível (RN03):
 *   * INCLUSO: já incluído no preço/hora (custo rastreado, não cobrado)
 *   * MEDIDO: litros consumidos × preço do dia
 *   * TAXA_FIXA: valor fixo por hora faturável
 * - Hierarquia de aplicação: JETSKI → MODELO → GLOBAL
 * - Cálculo de preço médio diário
 * - Integração com módulo de locações para cálculo automático de custos
 *
 * Dependências permitidas:
 * - shared::security (TenantContext)
 * - shared::storage (fotos opcionais de abastecimento)
 * - locacoes::domain (integração com Locacao via eventos ou injeção)
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Combustivel",
    allowedDependencies = {"shared::security", "shared::exception", "shared::storage", "locacoes::domain"}
)
package com.jetski.combustivel;
