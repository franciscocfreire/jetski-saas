package com.jetski.comissoes.api;

import com.jetski.comissoes.domain.Comissao;
import com.jetski.comissoes.internal.repository.ComissaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public Query Service for Comissao (Commission) operations
 *
 * <p>This service is exposed to other modules and provides a clean API
 * for querying commissions without exposing internal repositories.</p>
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComissaoQueryService {

    private final ComissaoRepository comissaoRepository;

    /**
     * Find commissions by tenant and period
     *
     * @param tenantId Tenant ID
     * @param inicio Start instant
     * @param fim End instant
     * @return List of Comissao entities
     */
    public List<Comissao> findByPeriodo(UUID tenantId, Instant inicio, Instant fim) {
        return comissaoRepository.findByPeriodo(tenantId, inicio, fim);
    }
}
