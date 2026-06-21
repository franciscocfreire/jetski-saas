package com.jetski.tenant.internal;

import com.jetski.shared.exception.ConflictException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.api.dto.PendingTenantDTO;
import com.jetski.tenant.api.dto.PlatformTenantSummary;
import com.jetski.tenant.api.dto.TenantStatusResult;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.TenantStatus;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service de plataforma (super admin): ciclo de aprovação/bloqueio de tenants.
 *
 * <p>Acessível apenas a usuários com acesso irrestrito (super admin) — a autorização é
 * garantida pelo OPA via ações {@code platform:*} (ver ABACAuthorizationInterceptor) e o
 * super admin opera dentro da sessão do tenant alvo (X-Tenant-Id), sem bypass de RLS.
 *
 * <p>Mudanças de status invalidam o cache {@code tenant-access} (usado pelo gate), para
 * que a aprovação/bloqueio tenha efeito imediato (sem esperar o TTL).
 *
 * @author Jetski Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformTenantService {

    private static final int TRIAL_DAYS = 14;

    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    /** Lista TODAS as empresas (qualquer status) — visão de plataforma do super admin. */
    @Transactional(readOnly = true)
    public List<PlatformTenantSummary> listAll() {
        return tenantRepository.findAll().stream()
            .sorted((a, b) -> a.getRazaoSocial().compareToIgnoreCase(b.getRazaoSocial()))
            .map(t -> PlatformTenantSummary.of(t.getId(), t.getSlug(), t.getRazaoSocial(), t.getStatus().name()))
            .toList();
    }

    /** Fila de empresas aguardando aprovação. */
    @Transactional(readOnly = true)
    public List<PendingTenantDTO> listPending() {
        return tenantRepository.findByStatusOrderByCreatedAtAsc(TenantStatus.PENDENTE_APROVACAO).stream()
            .map(t -> new PendingTenantDTO(t.getId(), t.getSlug(), t.getRazaoSocial(), t.getCnpj(), t.getCreatedAt()))
            .toList();
    }

    /** Aprova uma empresa pendente: PENDENTE_APROVACAO → ATIVO + cria assinatura Trial. */
    @Transactional
    @CacheEvict(value = "tenant-access", allEntries = true)
    public TenantStatusResult approve(UUID tenantId) {
        Tenant tenant = require(tenantId);
        if (tenant.getStatus() != TenantStatus.PENDENTE_APROVACAO) {
            throw new ConflictException(
                "Tenant não está pendente de aprovação (status atual: " + tenant.getStatus() + ")");
        }
        tenant.setStatus(TenantStatus.ATIVO);
        tenantRepository.save(tenant);
        createTrialSubscription(tenantId);
        UUID actor = actor();
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_APPROVED", "PENDENTE_APROVACAO", "ATIVO", actor, null));
        log.info("[PLATFORM] Tenant aprovado: tenant={}, por={}", tenantId, actor);
        return new TenantStatusResult(tenantId, tenant.getStatus().name(),
            "Empresa aprovada e ativada (trial de " + TRIAL_DAYS + " dias iniciado).");
    }

    /** Suspende uma empresa ativa: ATIVO/TRIAL → SUSPENSO. */
    @Transactional
    @CacheEvict(value = "tenant-access", allEntries = true)
    public TenantStatusResult suspend(UUID tenantId, String motivo) {
        Tenant tenant = require(tenantId);
        if (tenant.getStatus() != TenantStatus.ATIVO && tenant.getStatus() != TenantStatus.TRIAL) {
            throw new ConflictException(
                "Somente empresas ATIVO/TRIAL podem ser suspensas (status atual: " + tenant.getStatus() + ")");
        }
        String from = tenant.getStatus().name();
        tenant.setStatus(TenantStatus.SUSPENSO);
        tenantRepository.save(tenant);
        UUID actor = actor();
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_SUSPENDED", from, "SUSPENSO", actor, motivo));
        log.info("[PLATFORM] Tenant suspenso: tenant={}, por={}, motivo={}", tenantId, actor, motivo);
        return new TenantStatusResult(tenantId, tenant.getStatus().name(), "Empresa suspensa.");
    }

    /** Reativa uma empresa suspensa: SUSPENSO → ATIVO. */
    @Transactional
    @CacheEvict(value = "tenant-access", allEntries = true)
    public TenantStatusResult reactivate(UUID tenantId) {
        Tenant tenant = require(tenantId);
        if (tenant.getStatus() != TenantStatus.SUSPENSO) {
            throw new ConflictException(
                "Somente empresas SUSPENSO podem ser reativadas (status atual: " + tenant.getStatus() + ")");
        }
        tenant.setStatus(TenantStatus.ATIVO);
        tenantRepository.save(tenant);
        UUID actor = actor();
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_REACTIVATED", "SUSPENSO", "ATIVO", actor, null));
        log.info("[PLATFORM] Tenant reativado: tenant={}, por={}", tenantId, actor);
        return new TenantStatusResult(tenantId, tenant.getStatus().name(), "Empresa reativada.");
    }

    private Tenant require(UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
    }

    private void createTrialSubscription(UUID tenantId) {
        entityManager.createNativeQuery(
            """
            INSERT INTO assinatura (tenant_id, plano_id, ciclo, dt_inicio, dt_fim, status)
            SELECT ?1, p.id, 'mensal', CURRENT_DATE, ?2, 'ativa'
            FROM plano p WHERE p.nome = 'Trial'
            """
        )
        .setParameter(1, tenantId)
        .setParameter(2, LocalDate.now().plusDays(TRIAL_DAYS))
        .executeUpdate();
        log.info("Trial subscription created for tenant: {}", tenantId);
    }

    private UUID actor() {
        try {
            return TenantContext.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }
}
