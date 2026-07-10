package com.jetski.tenant.internal;

import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.TenantStatus;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Varredura diária dos trials (padrão {@code PreventiveMaintenanceScheduler}):
 * para cada empresa ATIVO, delega ao {@link TrialExpirationService} — que suspende
 * quem venceu e avisa quem está para vencer (D-3/D-1). Cada tenant roda em
 * transação própria; falha em um não interrompe os demais.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialExpirationJob {

    private final TenantRepository tenantRepository;
    private final TrialExpirationService trialExpirationService;

    @Scheduled(cron = "0 15 5 * * *") // diário às 05:15 (TZ do backend: America/Sao_Paulo)
    public void verificarTrials() {
        LocalDate hoje = LocalDate.now();
        List<Tenant> ativos = tenantRepository.findByStatusOrderByCreatedAtAsc(TenantStatus.ATIVO);
        int suspensos = 0;
        for (Tenant tenant : ativos) {
            try {
                if (trialExpirationService.processar(tenant, hoje)) {
                    suspensos++;
                }
            } catch (Exception e) {
                log.error("[TRIAL] Falha ao processar tenant {} (seguindo para os demais): {}",
                    tenant.getSlug(), e.getMessage());
            }
        }
        log.info("[TRIAL] Varredura concluída: {} empresas verificadas, {} suspensas por trial vencido",
            ativos.size(), suspensos);
    }
}
