package com.jetski.tenant.internal;

import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Job diário da exclusão de empresas (padrão {@code TrialExpirationJob}):
 * <ol>
 *   <li>executa os expurgos com carência vencida
 *       ({@code exclusao_agendada_em <= agora});</li>
 *   <li>remove exports de arquivamento com mais de {@value #EXPORT_RETENCAO_DIAS}
 *       dias (retenção prometida na Fase 2) — a idade vem do timestamp no nome
 *       do arquivo ({@code slug-yyyyMMdd-HHmmss.zip}).</li>
 * </ol>
 * Falha em um tenant não interrompe os demais.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExclusaoJob {

    static final int EXPORT_RETENCAO_DIAS = 90;

    private static final Pattern STAMP_NO_NOME =
        Pattern.compile("-(\\d{8}-\\d{6})\\.zip$");
    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final TenantRepository tenantRepository;
    private final TenantExclusaoService tenantExclusaoService;
    private final StorageService storageService;

    @Scheduled(cron = "0 45 5 * * *") // diário às 05:45 (após o trial job das 05:15)
    public void executar() {
        expurgarVencidos();
        limparExportsAntigos();
    }

    public void expurgarVencidos() {
        Instant agora = Instant.now();
        List<Tenant> vencidos = tenantRepository.findAll().stream()
            .filter(t -> t.getExclusaoAgendadaEm() != null
                && !t.getExclusaoAgendadaEm().isAfter(agora)
                && t.getExcluidoEm() == null)
            .toList();
        if (vencidos.isEmpty()) {
            return;
        }
        log.info("[PLATFORM] {} exclusão(ões) com carência vencida — expurgando", vencidos.size());
        for (Tenant t : vencidos) {
            try {
                tenantExclusaoService.expurgar(t);
            } catch (Exception e) {
                log.error("[PLATFORM] Expurgo falhou para tenant={} ({}) — mantido para retry "
                    + "no próximo ciclo", t.getId(), t.getSlug(), e);
            }
        }
    }

    public void limparExportsAntigos() {
        Instant limite = Instant.now().minus(Duration.ofDays(EXPORT_RETENCAO_DIAS));
        int removidos = 0;
        for (String chave : storageService.listObjectKeys("_platform/exports/")) {
            Matcher m = STAMP_NO_NOME.matcher(chave);
            if (!m.find()) {
                continue; // nome fora do padrão — não arrisca
            }
            Instant geradoEm = LocalDateTime.parse(m.group(1), STAMP).atZone(ZONA).toInstant();
            if (geradoEm.isBefore(limite)) {
                try {
                    storageService.deleteFile(chave);
                    removidos++;
                } catch (Exception e) {
                    log.warn("[PLATFORM] Falha ao remover export antigo {}: {}", chave, e.getMessage());
                }
            }
        }
        if (removidos > 0) {
            log.info("[PLATFORM] {} export(s) de arquivamento com +{} dias removido(s)",
                removidos, EXPORT_RETENCAO_DIAS);
        }
    }
}
