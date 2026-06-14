/**
 * Módulo de Bônus de Vendedores (Seller Bonus).
 *
 * <p>Gerencia bônus acumulativos por metas de vendas acima do preço base.
 * Reage ao evento {@code ComissaoCalculadaEvent} do módulo comissoes.
 *
 * <p>API pública: {@code BonusService} (api) e {@code domain} (BonusVendedor, StatusBonus).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Seller Bonus"
)
package com.jetski.bonus;
