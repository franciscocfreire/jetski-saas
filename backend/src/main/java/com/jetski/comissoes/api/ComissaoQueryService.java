package com.jetski.comissoes.api;

import com.jetski.comissoes.domain.Comissao;
import com.jetski.comissoes.domain.StatusComissao;
import com.jetski.comissoes.internal.repository.ComissaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    /**
     * Find pending commissions awaiting approval
     *
     * @param tenantId Tenant ID
     * @return List of pending Comissao
     */
    public List<Comissao> findPendentesAprovacao(UUID tenantId) {
        return comissaoRepository.findPendentesAprovacao(tenantId);
    }

    /**
     * Find approved commissions awaiting payment
     *
     * @param tenantId Tenant ID
     * @return List of approved Comissao
     */
    public List<Comissao> findAguardandoPagamento(UUID tenantId) {
        return comissaoRepository.findAguardandoPagamento(tenantId);
    }

    /**
     * Sum all pending/approved commissions (not paid yet)
     *
     * @param tenantId Tenant ID
     * @return Total value of unpaid commissions
     */
    public BigDecimal sumComissoesNaoPagas(UUID tenantId) {
        List<Comissao> pendentes = comissaoRepository.findPendentesAprovacao(tenantId);
        List<Comissao> aguardando = comissaoRepository.findAguardandoPagamento(tenantId);

        BigDecimal total = BigDecimal.ZERO;
        for (Comissao c : pendentes) {
            total = total.add(c.getValorComissao());
        }
        for (Comissao c : aguardando) {
            total = total.add(c.getValorComissao());
        }
        return total;
    }

    /**
     * Count commissions by status
     *
     * @param tenantId Tenant ID
     * @param status Commission status
     * @return Count of commissions
     */
    public int countByStatus(UUID tenantId, StatusComissao status) {
        return comissaoRepository.findByTenantIdAndStatusOrderByDataLocacaoDesc(tenantId, status).size();
    }

    /** Conta vendas acima do preço base de um vendedor (usado pelo módulo bonus). */
    public Long countVendasAcimaPrecoBaseByVendedor(UUID tenantId, UUID vendedorId) {
        return comissaoRepository.countVendasAcimaPrecoBaseByVendedor(tenantId, vendedorId);
    }

    /** Lista comissões de um vendedor por status (mais recentes primeiro). */
    public List<Comissao> findByVendedorAndStatus(UUID tenantId, UUID vendedorId, StatusComissao status) {
        return comissaoRepository.findByTenantIdAndVendedorIdAndStatusOrderByCreatedAtDesc(tenantId, vendedorId, status);
    }

    /** Lista todas as comissões de um vendedor (mais recentes primeiro). */
    public List<Comissao> findByVendedor(UUID tenantId, UUID vendedorId) {
        return comissaoRepository.findByTenantIdAndVendedorIdOrderByCreatedAtDesc(tenantId, vendedorId);
    }

    /** Busca comissões por IDs. */
    public List<Comissao> findByIds(Iterable<UUID> ids) {
        return comissaoRepository.findAllById(ids);
    }

    /** Persiste uma comissão (ex.: atualização de status de pagamento pelo módulo pagamentos). */
    @Transactional
    public Comissao salvar(Comissao comissao) {
        return comissaoRepository.save(comissao);
    }

    /** Soma das comissões aprovadas (aguardando pagamento) de um vendedor. */
    public BigDecimal sumComissoesAprovadasByVendedor(UUID tenantId, UUID vendedorId) {
        return comissaoRepository.sumComissoesAprovadasByVendedor(tenantId, vendedorId);
    }

    /** Quantidade de comissões aprovadas de um vendedor. */
    public int countComissoesAprovadasByVendedor(UUID tenantId, UUID vendedorId) {
        return comissaoRepository.countComissoesAprovadasByVendedor(tenantId, vendedorId);
    }

    /** Soma das comissões pendentes de um vendedor. */
    public BigDecimal sumComissoesPendentesByVendedor(UUID tenantId, UUID vendedorId) {
        return comissaoRepository.sumComissoesPendentesByVendedor(tenantId, vendedorId);
    }

    /** Soma das comissões já pagas (histórico) de um vendedor. */
    public BigDecimal sumComissoesPagasAllTimeByVendedor(UUID tenantId, UUID vendedorId) {
        return comissaoRepository.sumComissoesPagasAllTimeByVendedor(tenantId, vendedorId);
    }

    /** Quantidade de locações com comissão de um vendedor. */
    public Long countLocacoesByVendedor(UUID tenantId, UUID vendedorId) {
        return comissaoRepository.countLocacoesByVendedor(tenantId, vendedorId);
    }
}
