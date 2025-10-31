package com.jetski.locacoes.api;

import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.internal.repository.LocacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Public Query Service for Locacao (Rental) operations
 *
 * <p>This service is exposed to other modules and provides a clean API
 * for querying rentals without exposing internal repositories.</p>
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocacaoQueryService {

    private final LocacaoRepository locacaoRepository;

    /**
     * Find rentals by tenant and date range
     *
     * @param tenantId Tenant ID
     * @param start Start date/time
     * @param end End date/time
     * @return List of Locacao entities
     */
    public List<Locacao> findByTenantIdAndDateRange(UUID tenantId, LocalDateTime start, LocalDateTime end) {
        return locacaoRepository.findByTenantIdAndDateRange(tenantId, start, end);
    }
}
