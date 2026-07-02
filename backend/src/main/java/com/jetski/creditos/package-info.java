/**
 * Módulo de Créditos de Emissão (pré-pago)
 *
 * <p>Ledger append-only de créditos por tenant — a base do modelo de cobrança:
 * cada documento emitido com destino à Marinha consome 1 crédito; sem saldo, a
 * emissão é bloqueada. A adesão (aprovação do tenant) credita o valor configurado
 * em {@code jetski.creditos.adesao}; o super admin lança ajustes auditados.
 *
 * <p><strong>Anti-fraude:</strong> a tabela {@code credito_lancamento} tem trigger
 * que proíbe UPDATE/DELETE (histórico imutável), {@code saldo_apos} corrente em
 * cada linha e uniques parciais (1 ADESAO por tenant, 1 CONSUMO por documento).
 * O débito é <em>síncrono</em> na transação da emissão, serializado por advisory
 * lock por tenant — saldo nunca fica negativo.
 *
 * <p><strong>API Pública:</strong>
 * <ul>
 *   <li>{@link com.jetski.creditos.CreditoService} (root) - saldo, débito e extrato,
 *       consumido pelo módulo locacoes na emissão</li>
 *   <li>{@code api} - consulta do tenant e lançamentos do super admin</li>
 *   <li>{@code domain} - entidade e repositório; {@code domain.event} - eventos</li>
 * </ul>
 *
 * @since 0.21.0
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Créditos de Emissão"
)
package com.jetski.creditos;
