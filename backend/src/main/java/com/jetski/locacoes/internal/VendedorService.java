package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Vendedor;
import com.jetski.locacoes.domain.VendedorTipo;
import com.jetski.locacoes.internal.repository.VendedorRepository;
import com.jetski.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service: Vendedor Management
 *
 * Handles seller and partner registration:
 * - Create, update, and list sellers
 * - Manage commission rules (Business rules RF08, RN04)
 * - Filter by seller type (INTERNO vs PARCEIRO)
 *
 * Business Rules:
 * - RF08: Commission hierarchy (campaign > model > duration > seller default)
 * - RN04: Commission calculated on commissionable revenue only
 * - Seller tipo must be INTERNO or PARCEIRO
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VendedorService {

    private final VendedorRepository vendedorRepository;

    /**
     * List all active sellers for current tenant.
     * RLS automatically filters by tenant_id.
     *
     * @return List of active Vendedor records
     */
    @Transactional(readOnly = true)
    public List<Vendedor> listActiveSellers() {
        log.debug("Listing all active sellers");
        return vendedorRepository.findAllActive();
    }

    /**
     * List all sellers for current tenant (including inactive).
     * RLS automatically filters by tenant_id.
     *
     * @return List of all Vendedor records
     */
    @Transactional(readOnly = true)
    public List<Vendedor> listAllSellers() {
        log.debug("Listing all sellers (including inactive)");
        return vendedorRepository.findAll();
    }

    /**
     * List sellers by type (INTERNO or PARCEIRO).
     *
     * @param tipo VendedorTipo enum
     * @return List of Vendedor records of specified type
     */
    @Transactional(readOnly = true)
    public List<Vendedor> listByTipo(VendedorTipo tipo) {
        log.debug("Listing sellers by type: {}", tipo);
        return vendedorRepository.findAllByTipo(tipo);
    }

    /**
     * Find seller by ID within current tenant.
     *
     * @param id Vendedor UUID
     * @return Vendedor entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public Vendedor findById(UUID id) {
        log.debug("Finding seller by id: {}", id);
        return vendedorRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Vendedor não encontrado"));
    }

    /**
     * Create new seller.
     *
     * Validations:
     * - Name is required
     * - Tipo must be INTERNO or PARCEIRO
     *
     * @param vendedor Vendedor entity to create
     * @return Created Vendedor
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Vendedor createVendedor(Vendedor vendedor) {
        log.info("Creating new seller: nome={}, tipo={}", vendedor.getNome(), vendedor.getTipo());

        // Validate name
        if (vendedor.getNome() == null || vendedor.getNome().isBlank()) {
            throw new BusinessException("Nome do vendedor é obrigatório");
        }

        // Validate tipo
        if (vendedor.getTipo() == null) {
            throw new BusinessException("Tipo do vendedor é obrigatório (INTERNO ou PARCEIRO)");
        }

        Vendedor saved = vendedorRepository.save(vendedor);
        log.info("Seller created successfully: id={}, nome={}", saved.getId(), saved.getNome());
        return saved;
    }

    /**
     * Update existing seller.
     *
     * Validations:
     * - Seller must exist
     * - Name cannot be blank if provided
     *
     * @param id Vendedor UUID
     * @param updates Vendedor with updated fields
     * @return Updated Vendedor
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Vendedor updateVendedor(UUID id, Vendedor updates) {
        log.info("Updating seller: id={}", id);

        Vendedor existing = findById(id);

        // Update fields
        if (updates.getNome() != null) {
            if (updates.getNome().isBlank()) {
                throw new BusinessException("Nome do vendedor não pode ser vazio");
            }
            existing.setNome(updates.getNome());
        }

        if (updates.getDocumento() != null) {
            existing.setDocumento(updates.getDocumento());
        }

        if (updates.getTipo() != null) {
            existing.setTipo(updates.getTipo());
        }

        if (updates.getRegraComissaoJson() != null) {
            existing.setRegraComissaoJson(updates.getRegraComissaoJson());
        }

        Vendedor saved = vendedorRepository.save(existing);
        log.info("Seller updated successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Deactivate seller (soft delete).
     *
     * @param id Vendedor UUID
     * @return Deactivated Vendedor
     * @throws BusinessException if not found
     */
    @Transactional
    public Vendedor deactivateVendedor(UUID id) {
        log.info("Deactivating seller: id={}", id);

        Vendedor vendedor = findById(id);

        if (!Boolean.TRUE.equals(vendedor.getAtivo())) {
            throw new BusinessException("Vendedor já está inativo");
        }

        vendedor.setAtivo(false);
        Vendedor saved = vendedorRepository.save(vendedor);

        log.info("Seller deactivated successfully: id={}", id);
        return saved;
    }

    /**
     * Reactivate previously deactivated seller.
     *
     * @param id Vendedor UUID
     * @return Reactivated Vendedor
     * @throws BusinessException if not found
     */
    @Transactional
    public Vendedor reactivateVendedor(UUID id) {
        log.info("Reactivating seller: id={}", id);

        Vendedor vendedor = findById(id);

        if (Boolean.TRUE.equals(vendedor.getAtivo())) {
            throw new BusinessException("Vendedor já está ativo");
        }

        vendedor.setAtivo(true);
        Vendedor saved = vendedorRepository.save(vendedor);

        log.info("Seller reactivated successfully: id={}", id);
        return saved;
    }
}
