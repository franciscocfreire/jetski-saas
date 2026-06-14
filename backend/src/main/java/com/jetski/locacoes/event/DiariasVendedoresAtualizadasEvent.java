package com.jetski.locacoes.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento publicado quando o total de diárias de vendedores muda para uma data
 * (registro/remoção/pagamento de presença).
 *
 * <p>Consumido pelo módulo {@code fechamento} para atualizar o FechamentoDiario,
 * desacoplando locacoes de fechamento (evita ciclo locacoes ↔ fechamento).
 */
public record DiariasVendedoresAtualizadasEvent(UUID tenantId, LocalDate dtReferencia, BigDecimal totalDiarias) {
}
