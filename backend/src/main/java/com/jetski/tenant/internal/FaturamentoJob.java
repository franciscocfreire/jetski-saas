package com.jetski.tenant.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job diário do billing (06:00, após trial 05:15 e exclusões 05:45):
 * gera as faturas da competência corrente (idempotente — única por
 * tenant/competência) e suspende inadimplentes (ABERTA vencida além
 * da carência). Falhas são logadas sem interromper o ciclo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaturamentoJob {

    private final PlatformFaturaService platformFaturaService;

    @Scheduled(cron = "0 0 6 * * *") // diário às 06:00 (TZ do backend: America/Sao_Paulo)
    public void executar() {
        try {
            int criadas = platformFaturaService.gerarFaturasDoMes();
            if (criadas > 0) {
                log.info("[PLATFORM] Faturamento: {} fatura(s) gerada(s)", criadas);
            }
        } catch (Exception e) {
            log.error("[PLATFORM] Falha na geração de faturas", e);
        }
        try {
            int suspensos = platformFaturaService.suspenderInadimplentes();
            if (suspensos > 0) {
                log.warn("[PLATFORM] Faturamento: {} tenant(s) suspenso(s) por inadimplência",
                    suspensos);
            }
        } catch (Exception e) {
            log.error("[PLATFORM] Falha na suspensão de inadimplentes", e);
        }
    }
}
