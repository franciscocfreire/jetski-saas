package com.jetski.fechamento.internal;

import com.jetski.fechamento.internal.repository.FechamentoDiarioRepository;
import com.jetski.locacoes.event.DiariasVendedoresAtualizadasEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atualiza o FechamentoDiario quando o total de diárias de vendedores muda.
 *
 * <p>Escuta evento do módulo locacoes — quebra o ciclo locacoes ↔ fechamento
 * (locacoes apenas publica; fechamento reage).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FechamentoDiariaListener {

    private final FechamentoDiarioRepository fechamentoDiarioRepository;

    @EventListener
    @Transactional
    public void onDiariasAtualizadas(DiariasVendedoresAtualizadasEvent event) {
        fechamentoDiarioRepository
                .findByTenantIdAndDtReferencia(event.tenantId(), event.dtReferencia())
                .ifPresent(fechamento -> {
                    fechamento.setTotalDiariasVendedores(event.totalDiarias());
                    fechamentoDiarioRepository.save(fechamento);
                    log.debug("Fechamento diário atualizado via evento: total diárias={}", event.totalDiarias());
                });
    }
}
