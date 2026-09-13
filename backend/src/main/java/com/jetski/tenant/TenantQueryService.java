package com.jetski.tenant;

import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Public service for tenant queries.
 *
 * This service exposes read-only tenant operations to other modules,
 * encapsulating the internal TenantRepository.
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantQueryService {

    private final TenantRepository tenantRepository;
    private final EntityManager entityManager;

    /**
     * Find tenants by their IDs.
     *
     * @param tenantIds List of tenant IDs to find
     * @return Map of tenant ID to Tenant entity
     */
    public Map<UUID, Tenant> findTenantsById(List<UUID> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        return tenantRepository.findAllById(tenantIds).stream()
            .collect(Collectors.toMap(Tenant::getId, t -> t));
    }

    /**
     * Find a tenant by ID.
     *
     * @param tenantId Tenant ID
     * @return Tenant if found, null otherwise
     */
    public Tenant findById(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElse(null);
    }

    /**
     * Lê um tenant que NÃO é o da sessão — a EAMA emissora a partir da operadora, no
     * ofício à Capitania da emissão delegada. A RLS de {@code tenant} (V042) só deixa
     * ler a própria linha, então a leitura abre uma janela própria:
     * {@code set_config('app.tenant_id', alvo, true)} numa transação {@code REQUIRES_NEW},
     * local a ela e descartada no commit, sem tocar no contexto do chamador.
     *
     * <p>Use só em relações explícitas e auditadas entre empresas (vínculo de emissão);
     * a entidade volta desanexada.
     *
     * @return o tenant, ou {@code null} se não existir
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Tenant findOutroTenantById(UUID tenantId) {
        if (tenantId == null) {
            return null;
        }
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', ?1, true)")
            .setParameter(1, tenantId.toString())
            .getSingleResult();
        return tenantRepository.findById(tenantId).orElse(null);
    }
}
