package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ItemOpcional;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.domain.LocacaoItemOpcional;
import com.jetski.locacoes.internal.repository.LocacaoItemOpcionalRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service: LocacaoItemOpcionalService
 *
 * Business logic for managing optional items attached to rentals.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocacaoItemOpcionalService {

    private final LocacaoItemOpcionalRepository locacaoItemOpcionalRepository;
    private final LocacaoService locacaoService;
    private final ItemOpcionalService itemOpcionalService;

    /**
     * Add an optional item to a rental.
     *
     * @param tenantId Tenant ID
     * @param locacaoId Rental ID
     * @param itemOpcionalId Optional item ID from catalog
     * @param valorCobrado Price to charge (may differ from base price)
     * @param observacao Optional note (e.g., reason for price adjustment)
     * @return Created LocacaoItemOpcional
     */
    @Transactional
    public LocacaoItemOpcional addItem(UUID tenantId, UUID locacaoId, UUID itemOpcionalId,
                                        BigDecimal valorCobrado, String observacao) {
        log.info("Adding optional item to rental: tenant={}, locacao={}, item={}",
                 tenantId, locacaoId, itemOpcionalId);

        // Validate locacao exists
        Locacao locacao = locacaoService.findById(locacaoId);
        if (!locacao.getTenantId().equals(tenantId)) {
            throw new NotFoundException("Locação não encontrada: " + locacaoId);
        }

        // Validate item exists and is active
        ItemOpcional item = itemOpcionalService.findById(tenantId, itemOpcionalId);
        if (!item.getAtivo()) {
            throw new BusinessException("Item opcional não está ativo: " + item.getNome());
        }

        // Check if item already added
        if (locacaoItemOpcionalRepository.existsByLocacaoIdAndItemOpcionalId(locacaoId, itemOpcionalId)) {
            throw new BusinessException("Este item opcional já foi adicionado a esta locação");
        }

        // Use base price if valorCobrado not provided
        BigDecimal valorFinal = valorCobrado != null ? valorCobrado : item.getPrecoBase();

        LocacaoItemOpcional locacaoItem = LocacaoItemOpcional.builder()
            .tenantId(tenantId)
            .locacaoId(locacaoId)
            .itemOpcionalId(itemOpcionalId)
            .valorCobrado(valorFinal)
            .valorOriginal(item.getPrecoBase())
            .observacao(observacao)
            .build();

        locacaoItem = locacaoItemOpcionalRepository.save(locacaoItem);
        log.info("Optional item added: id={}, valorCobrado={}", locacaoItem.getId(), valorFinal);

        return locacaoItem;
    }

    /**
     * Update the price of an optional item in a rental.
     *
     * @param tenantId Tenant ID
     * @param locacaoId Rental ID
     * @param id LocacaoItemOpcional ID
     * @param valorCobrado New price
     * @param observacao Optional note
     * @return Updated LocacaoItemOpcional
     */
    @Transactional
    public LocacaoItemOpcional updateItem(UUID tenantId, UUID locacaoId, UUID id,
                                           BigDecimal valorCobrado, String observacao) {
        log.info("Updating optional item in rental: tenant={}, locacao={}, id={}",
                 tenantId, locacaoId, id);

        LocacaoItemOpcional locacaoItem = findById(tenantId, locacaoId, id);

        if (valorCobrado != null) {
            locacaoItem.setValorCobrado(valorCobrado);
        }
        if (observacao != null) {
            locacaoItem.setObservacao(observacao);
        }

        locacaoItem = locacaoItemOpcionalRepository.save(locacaoItem);
        log.info("Optional item updated: id={}", locacaoItem.getId());

        return locacaoItem;
    }

    /**
     * Remove an optional item from a rental.
     *
     * @param tenantId Tenant ID
     * @param locacaoId Rental ID
     * @param id LocacaoItemOpcional ID
     */
    @Transactional
    public void removeItem(UUID tenantId, UUID locacaoId, UUID id) {
        log.info("Removing optional item from rental: tenant={}, locacao={}, id={}",
                 tenantId, locacaoId, id);

        LocacaoItemOpcional locacaoItem = findById(tenantId, locacaoId, id);
        locacaoItemOpcionalRepository.delete(locacaoItem);

        log.info("Optional item removed: id={}", id);
    }

    /**
     * Find optional item by ID within rental.
     *
     * @param tenantId Tenant ID
     * @param locacaoId Rental ID
     * @param id LocacaoItemOpcional ID
     * @return LocacaoItemOpcional
     */
    @Transactional(readOnly = true)
    public LocacaoItemOpcional findById(UUID tenantId, UUID locacaoId, UUID id) {
        return locacaoItemOpcionalRepository.findByTenantIdAndLocacaoIdAndId(tenantId, locacaoId, id)
            .orElseThrow(() -> new NotFoundException("Item opcional não encontrado na locação: " + id));
    }

    /**
     * List all optional items for a rental.
     *
     * @param tenantId Tenant ID
     * @param locacaoId Rental ID
     * @return List of LocacaoItemOpcional
     */
    @Transactional(readOnly = true)
    public List<LocacaoItemOpcional> listByLocacao(UUID tenantId, UUID locacaoId) {
        return locacaoItemOpcionalRepository.findByTenantIdAndLocacaoIdOrderByCreatedAtAsc(tenantId, locacaoId);
    }

    /**
     * Calculate total value of all optional items for a rental.
     *
     * @param locacaoId Rental ID
     * @return Sum of valorCobrado
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotal(UUID locacaoId) {
        return locacaoItemOpcionalRepository.sumValorCobradoByLocacaoId(locacaoId);
    }
}
