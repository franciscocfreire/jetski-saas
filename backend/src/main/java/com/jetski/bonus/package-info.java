/**
 * Módulo de Bônus de Vendedores (Seller Bonus).
 *
 * <p>Gerencia bônus acumulativos por metas de vendas acima do preço base.
 * Reage ao evento {@code ComissaoCalculadaEvent} do módulo comissoes.
 *
 * <p>Marcado como OPEN: expõe entidades/repositórios para consumo por pagamentos
 * (relatórios de bônus) durante a migração para APIs dedicadas.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Seller Bonus",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package com.jetski.bonus;
