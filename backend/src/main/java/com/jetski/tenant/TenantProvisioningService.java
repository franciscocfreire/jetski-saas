package com.jetski.tenant;

import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public service for tenant provisioning (criação/atualização).
 *
 * <p>Expõe operações de escrita de tenant a outros módulos (ex.: signup),
 * encapsulando o TenantRepository interno.
 *
 * @author Jetski Team
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;

    /** @return true se já existe tenant com o slug informado. */
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        return tenantRepository.existsBySlug(slug);
    }

    /** Persiste (cria/atualiza) um tenant. */
    @Transactional
    public Tenant save(Tenant tenant) {
        return tenantRepository.save(tenant);
    }
}
