package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ItemOpcional;
import com.jetski.locacoes.internal.repository.ItemOpcionalRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service: ItemOpcionalService
 *
 * Business logic for managing optional add-on items catalog.
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemOpcionalService {

    private final ItemOpcionalRepository itemOpcionalRepository;

    /**
     * Create a new optional item in the catalog.
     *
     * @param item ItemOpcional to create
     * @return Created ItemOpcional
     */
    @Transactional
    public ItemOpcional create(ItemOpcional item) {
        log.info("Creating optional item: tenant={}, nome={}", item.getTenantId(), item.getNome());

        // Check for duplicate name
        if (itemOpcionalRepository.existsByNome(item.getNome())) {
            throw new BusinessException("Já existe um item opcional com este nome: " + item.getNome());
        }

        item = itemOpcionalRepository.save(item);
        log.info("Optional item created: id={}", item.getId());

        return item;
    }

    /**
     * Update an existing optional item.
     *
     * @param tenantId Tenant ID
     * @param id Item ID
     * @param updates ItemOpcional with updated values
     * @return Updated ItemOpcional
     */
    @Transactional
    public ItemOpcional update(UUID tenantId, UUID id, ItemOpcional updates) {
        log.info("Updating optional item: tenant={}, id={}", tenantId, id);

        ItemOpcional item = findById(tenantId, id);

        // Check for duplicate name if changing
        if (updates.getNome() != null && !updates.getNome().equals(item.getNome())) {
            if (itemOpcionalRepository.existsByNome(updates.getNome())) {
                throw new BusinessException("Já existe um item opcional com este nome: " + updates.getNome());
            }
            item.setNome(updates.getNome());
        }

        if (updates.getDescricao() != null) {
            item.setDescricao(updates.getDescricao());
        }

        if (updates.getPrecoBase() != null) {
            item.setPrecoBase(updates.getPrecoBase());
        }

        if (updates.getAtivo() != null) {
            item.setAtivo(updates.getAtivo());
        }

        item = itemOpcionalRepository.save(item);
        log.info("Optional item updated: id={}", item.getId());

        return item;
    }

    /**
     * Find optional item by ID within tenant.
     *
     * @param tenantId Tenant ID
     * @param id Item ID
     * @return ItemOpcional
     */
    @Transactional(readOnly = true)
    public ItemOpcional findById(UUID tenantId, UUID id) {
        return itemOpcionalRepository.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new NotFoundException("Item opcional não encontrado: " + id));
    }

    /**
     * List all active optional items for a tenant.
     *
     * @param tenantId Tenant ID
     * @return List of active ItemOpcional
     */
    @Transactional(readOnly = true)
    public List<ItemOpcional> listActive(UUID tenantId) {
        return itemOpcionalRepository.findByTenantIdAndAtivoTrueOrderByNomeAsc(tenantId);
    }

    /**
     * List all optional items for a tenant (including inactive).
     *
     * @param tenantId Tenant ID
     * @return List of all ItemOpcional
     */
    @Transactional(readOnly = true)
    public List<ItemOpcional> listAll(UUID tenantId) {
        return itemOpcionalRepository.findByTenantIdOrderByNomeAsc(tenantId);
    }

    /**
     * Deactivate an optional item (soft delete).
     *
     * @param tenantId Tenant ID
     * @param id Item ID
     * @return Updated ItemOpcional
     */
    @Transactional
    public ItemOpcional deactivate(UUID tenantId, UUID id) {
        log.info("Deactivating optional item: tenant={}, id={}", tenantId, id);

        ItemOpcional item = findById(tenantId, id);
        item.setAtivo(false);
        item = itemOpcionalRepository.save(item);

        log.info("Optional item deactivated: id={}", item.getId());
        return item;
    }
}
