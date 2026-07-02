package com.jetski.creditos.internal;

import com.jetski.creditos.CreditoService;
import com.jetski.creditos.api.dto.PlatformSaldoTenantDTO;
import com.jetski.creditos.domain.CreditoLancamento;
import com.jetski.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Operações de crédito do super admin, cross-tenant SEM bypass de RLS:
 * troca o tenant da transação com {@code set_config('app.tenant_id', ..., true)}
 * (local à transação — mesma doutrina do PlatformMeteringService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformCreditoService {

    private final CreditoService creditoService;
    private final EntityManager entityManager;

    /** Lança créditos (±) para o tenant alvo, com auditoria via evento. */
    @Transactional
    public CreditoLancamento lancar(UUID tenantId, int quantidade, String motivo) {
        UUID actor = actorOrNull();
        setTenant(tenantId);
        CreditoLancamento lanc = creditoService.lancarAjuste(tenantId, quantidade, motivo, actor);
        log.info("[PLATFORM] Créditos lançados: tenant={}, quantidade={}, actor={}", tenantId, quantidade, actor);
        return lanc;
    }

    /** Saldo de todos os tenants (iteração por tenant). */
    @Transactional
    public List<PlatformSaldoTenantDTO> saldos() {
        @SuppressWarnings("unchecked")
        List<Object[]> tenants = entityManager.createNativeQuery(
                "SELECT id, slug, razao_social FROM tenant ORDER BY razao_social")
            .getResultList();

        List<PlatformSaldoTenantDTO> resultado = new ArrayList<>(tenants.size());
        for (Object[] t : tenants) {
            UUID tenantId = (UUID) t[0];
            setTenant(tenantId);
            resultado.add(new PlatformSaldoTenantDTO(
                tenantId, (String) t[1], (String) t[2], creditoService.saldo(tenantId)));
        }
        return resultado;
    }

    private void setTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', ?1, true)")
            .setParameter(1, tenantId.toString())
            .getSingleResult();
    }

    private UUID actorOrNull() {
        try {
            return TenantContext.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }
}
