package com.jetski.locacoes.api;

import com.jetski.locacoes.domain.PresencaVendedor;
import com.jetski.locacoes.internal.repository.PresencaVendedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Serviço público de consulta de presenças de vendedores (diárias).
 *
 * <p>Expõe operações sobre PresencaVendedor a outros módulos (fechamento, pagamentos,
 * dashboard) sem expor o repositório interno.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PresencaVendedorQueryService {

    private final PresencaVendedorRepository presencaRepository;

    /** Soma das diárias de vendedores em uma data. */
    public BigDecimal sumTotalDiariasByDate(UUID tenantId, LocalDate dtReferencia) {
        return presencaRepository.sumTotalDiariasByDate(tenantId, dtReferencia);
    }

    /** Soma das diárias de vendedores num período. */
    public BigDecimal sumTotalDiariasByTenantAndPeriodo(UUID tenantId, LocalDate dtInicio, LocalDate dtFim) {
        return presencaRepository.sumTotalDiariasByTenantAndPeriodo(tenantId, dtInicio, dtFim);
    }

    /** Presenças não pagas de um vendedor. */
    public List<PresencaVendedor> findNaoPagasByVendedor(UUID tenantId, UUID vendedorId) {
        return presencaRepository.findNaoPagasByVendedor(tenantId, vendedorId);
    }

    /** Busca presenças por IDs. */
    public List<PresencaVendedor> findByIds(Iterable<UUID> ids) {
        return presencaRepository.findAllById(ids);
    }

    /** Persiste uma presença (ex.: marcação de pagamento pelo módulo pagamentos). */
    @Transactional
    public PresencaVendedor salvar(PresencaVendedor presenca) {
        return presencaRepository.save(presenca);
    }

    /** Soma das diárias não pagas de um vendedor. */
    public BigDecimal sumDiariasNaoPagasByVendedor(UUID tenantId, UUID vendedorId) {
        return presencaRepository.sumDiariasNaoPagasByVendedor(tenantId, vendedorId);
    }

    /** Quantidade de diárias não pagas de um vendedor. */
    public int countDiariasNaoPagasByVendedor(UUID tenantId, UUID vendedorId) {
        return presencaRepository.countDiariasNaoPagasByVendedor(tenantId, vendedorId);
    }
}
