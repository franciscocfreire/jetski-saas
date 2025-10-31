package com.jetski.comissoes.domain;

/**
 * Nível hierárquico da política de comissão
 *
 * <p><strong>Hierarquia (primeiro match ganha):</strong></p>
 * <ol>
 *   <li>CAMPANHA - Promoção temporária (maior prioridade)</li>
 *   <li>MODELO - Específico por modelo de jet ski</li>
 *   <li>DURACAO - Por faixa de duração (ex: até 2h, acima 2h)</li>
 *   <li>VENDEDOR - Padrão do vendedor (menor prioridade)</li>
 * </ol>
 *
 * <p>Baseado no CLAUDE.md: RN04 - Hierarquia de comissões</p>
 *
 * @author Jetski Team
 * @since 0.7.0
 */
public enum NivelPolitica {
    /**
     * Campanha/promoção temporária (maior prioridade)
     * Exemplo: "Black Friday 2025", "Verão 2025"
     */
    CAMPANHA(1),

    /**
     * Específico por modelo de jet ski
     * Exemplo: 15% para Yamaha VX, 12% para Sea-Doo GTI
     */
    MODELO(2),

    /**
     * Por faixa de duração da locação
     * Exemplo: 10% até 120min, 12% acima de 120min
     */
    DURACAO(3),

    /**
     * Padrão do vendedor (menor prioridade)
     * Exemplo: 10% para todas as locações deste vendedor
     */
    VENDEDOR(4);

    private final int prioridade;

    NivelPolitica(int prioridade) {
        this.prioridade = prioridade;
    }

    public int getPrioridade() {
        return prioridade;
    }
}
