package com.jetski.creditos.internal;

import com.jetski.creditos.CreditoService;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Credita os créditos de adesão quando o tenant é aprovado.
 *
 * <p><b>Síncrono de propósito</b> (sem {@code @Async}): o listener roda na mesma
 * transação do {@code PlatformTenantService.approve()} — aprovação e grant são
 * atômicos (anti-fraude: não existe tenant aprovado sem o lançamento de adesão,
 * nem lançamento sem aprovação). Idempotente pelo unique parcial (1 ADESAO/tenant).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdesaoCreditoListener {

    private final CreditoService creditoService;

    @EventListener
    public void onTenantStatusChanged(TenantStatusChangedEvent event) {
        if (!"TENANT_APPROVED".equals(event.acao())) {
            return;
        }
        creditoService.lancarAdesao(event.tenantId());
    }
}
