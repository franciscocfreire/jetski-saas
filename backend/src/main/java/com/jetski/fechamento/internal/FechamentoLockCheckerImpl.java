package com.jetski.fechamento.internal;

import com.jetski.fechamento.internal.repository.FechamentoDiarioRepository;
import com.jetski.locacoes.api.FechamentoLockChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Implementação do port {@link FechamentoLockChecker} (módulo locacoes).
 *
 * <p>Permite que locacoes verifique bloqueio de fechamento sem depender de
 * fechamento — quebra o ciclo locacoes ↔ fechamento.
 */
@Component
@RequiredArgsConstructor
public class FechamentoLockCheckerImpl implements FechamentoLockChecker {

    private final FechamentoDiarioRepository fechamentoDiarioRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isDataBloqueada(UUID tenantId, LocalDate data) {
        return fechamentoDiarioRepository.existsBloqueadoParaData(tenantId, data);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFechamentoNaoEditavel(UUID tenantId, LocalDate data) {
        return fechamentoDiarioRepository.findByTenantIdAndDtReferencia(tenantId, data)
                .map(f -> Boolean.TRUE.equals(f.getBloqueado()) || "FECHADO".equals(f.getStatus()))
                .orElse(false);
    }
}
