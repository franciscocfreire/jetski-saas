package com.jetski.tenant.internal;

import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.event.TrialExpiringEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Expiração REAL do período de teste: quando {@code assinatura.dt_fim} do plano Trial
 * passa, a assinatura vira {@code expirada} e a empresa é SUSPENSA automaticamente —
 * o fluxo de suspensão existente cuida do resto (e-mail aos admins, auditoria,
 * evicção do cache do gate). Antes disso, avisos em D-3 e D-1 por e-mail.
 *
 * <p>A leitura de {@code assinatura} é escopada com {@code SET LOCAL app.tenant_id}
 * por tenant (RLS forçada; sem bypass — decisão do projeto).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrialExpirationService {

    /** Dias antes do vencimento em que os admins são avisados. */
    static final List<Integer> DIAS_DE_AVISO = List.of(3, 1);

    private final PlatformTenantService platformTenantService;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Processa UM tenant (transação própria: falha em um não derruba a varredura).
     *
     * @return true se o tenant foi suspenso por trial vencido
     */
    @Transactional
    public boolean processar(Tenant tenant, LocalDate hoje) {
        LocalDate dtFim = trialAtivoDtFim(tenant.getId());
        if (dtFim == null) {
            return false; // sem assinatura Trial ativa — nada a fazer
        }

        if (dtFim.isBefore(hoje)) {
            // dt_fim já passou → expira a assinatura e suspende a empresa
            expirarAssinatura(tenant.getId());
            platformTenantService.suspend(tenant.getId(),
                "Período de teste de 14 dias encerrado em " + dtFim);
            log.info("[TRIAL] Empresa suspensa por trial vencido: tenant={}, dtFim={}",
                tenant.getSlug(), dtFim);
            return true;
        }

        long diasRestantes = java.time.temporal.ChronoUnit.DAYS.between(hoje, dtFim);
        if (DIAS_DE_AVISO.contains((int) diasRestantes)) {
            eventPublisher.publishEvent(TrialExpiringEvent.of(
                tenant.getId(), tenant.getRazaoSocial(), tenant.getSlug(),
                (int) diasRestantes, dtFim));
            log.info("[TRIAL] Aviso de vencimento publicado: tenant={}, faltam {} dias (dtFim={})",
                tenant.getSlug(), diasRestantes, dtFim);
        }
        return false;
    }

    /** dt_fim da assinatura Trial ativa do tenant, ou null. Escopo RLS via SET LOCAL. */
    private LocalDate trialAtivoDtFim(UUID tenantId) {
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = '" + tenantId + "'")
            .executeUpdate();
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(
            """
            SELECT a.dt_fim FROM assinatura a
            JOIN plano p ON p.id = a.plano_id
            WHERE a.tenant_id = ?1 AND a.status = 'ativa' AND p.nome = 'Trial'
            ORDER BY a.dt_inicio DESC LIMIT 1
            """)
            .setParameter(1, tenantId)
            .getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        return ((java.sql.Date) rows.get(0)).toLocalDate();
    }

    private void expirarAssinatura(UUID tenantId) {
        entityManager.createNativeQuery(
            "UPDATE assinatura SET status = 'expirada' WHERE tenant_id = ?1 AND status = 'ativa'")
            .setParameter(1, tenantId)
            .executeUpdate();
    }
}
