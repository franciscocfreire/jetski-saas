/**
 * Módulo de Comissões (Commissions Module)
 *
 * <p>Este módulo gerencia o cálculo e pagamento de comissões para vendedores/parceiros
 * com base em políticas hierárquicas configuráveis.
 *
 * <ul>
 *   <li>Cálculo automático de comissões por locação (RN04)</li>
 *   <li>Políticas hierárquicas: CAMPANHA → MODELO → DURACAO → VENDEDOR</li>
 *   <li>Tipos de comissão: PERCENTUAL, VALOR_FIXO, ESCALONADO</li>
 *   <li>Fluxo de aprovação (PENDENTE → APROVADA → PAGA)</li>
 *   <li>Receita comissionável = valor_total - combustível - multas - taxas</li>
 * </ul>
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@code api} - Controllers REST e DTOs</li>
 *   <li>{@code domain} - Entidades (Comissao, PoliticaComissao)</li>
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
 *   <li>{@code shared::security} - Contexto de tenant</li>
 *   <li>{@code shared::exception} - Exceções de negócio</li>
 * </ul>
 *
 * @since 0.7.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Commissions",
    allowedDependencies = {
        "usuarios::api",
        "shared::security",
        "shared::exception"
    }
)
package com.jetski.comissoes;
