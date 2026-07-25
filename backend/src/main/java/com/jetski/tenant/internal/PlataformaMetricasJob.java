package com.jetski.tenant.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recalcula o read model da plataforma (F4).
 *
 * <p>04:15 de propósito: depois do movimento da véspera fechar e ANTES do
 * {@code TenantExclusaoJob} (05:45) e do backup — se o expurgo rodasse primeiro, o
 * agregado do dia da exclusão nasceria zerado sem ninguém entender por quê.
 *
 * @since 0.9.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlataformaMetricasJob {

    private final PlataformaMetricasService service;

    @Scheduled(cron = "0 15 4 * * *", zone = "America/Sao_Paulo")
    public void recalcular() {
        try {
            var r = service.recalcularJanela();
            log.info("[METRICAS] Job concluído: {} empresas, {} dias, {} linhas",
                r.empresas(), r.dias(), r.linhas());
        } catch (Exception e) {
            // Falha aqui não pode derrubar os demais jobs agendados — o dashboard fica
            // com o dado da última execução boa, o que é visível (atualizado_em) em vez
            // de silencioso.
            log.error("[METRICAS] Falha ao recalcular o read model: {}", e.getMessage(), e);
        }
    }
}
