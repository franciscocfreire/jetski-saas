package com.jetski.despesas.domain;

/**
 * Categorias de despesas operacionais.
 *
 * <p>Representa os diferentes tipos de despesas do dia a dia
 * que nao estao vinculadas a locacoes especificas.</p>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
public enum CategoriaDespesa {

    /**
     * Diaria de funcionarios (operador, auxiliar, etc)
     */
    DIARIA_FUNCIONARIO,

    /**
     * Refeicao e alimentacao
     */
    REFEICAO,

    /**
     * Combustivel proprio (nao de locacao)
     * Ex: combustivel para embarcacao de apoio
     */
    COMBUSTIVEL_PROPRIO,

    /**
     * Limpeza e higienizacao
     */
    LIMPEZA,

    /**
     * Taxas administrativas
     */
    TAXA_ADMINISTRATIVA,

    /**
     * Transporte de funcionarios ou equipamentos
     */
    TRANSPORTE,

    /**
     * Material de escritorio
     */
    MATERIAL_ESCRITORIO,

    /**
     * Outros gastos nao categorizados
     */
    OUTROS
}
