package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service: Cliente Management
 *
 * Handles rental customer registration:
 * - Create, update, and list customers
 * - Manage liability term acceptance (Business rule RF03.4)
 * - Validate customer eligibility for rental
 *
 * Business Rules:
 * - RF03.4: Customer must accept terms (termoAceite) before first rental
 * - Customer data subject to LGPD compliance
 * - Contact information required for notifications
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

    /**
     * List all active customers for current tenant.
     * RLS automatically filters by tenant_id.
     *
     * @return List of active Cliente records
     */
    @Transactional(readOnly = true)
    public List<Cliente> listActiveCustomers() {
        log.debug("Listing all active customers");
        return clienteRepository.findAllActive();
    }

    /**
     * List all customers for current tenant (including inactive).
     * RLS automatically filters by tenant_id.
     *
     * @return List of all Cliente records
     */
    @Transactional(readOnly = true)
    public List<Cliente> listAllCustomers() {
        log.debug("Listing all customers (including inactive)");
        return clienteRepository.findAll();
    }

    /**
     * List customers eligible for rental (RF03.4).
     * Business rule: Must be active AND have accepted terms.
     *
     * @return List of customers who can rent
     */
    @Transactional(readOnly = true)
    public List<Cliente> listEligibleForRental() {
        log.debug("Listing customers eligible for rental");
        return clienteRepository.findAllEligibleForRental();
    }

    /**
     * Find customer by ID within current tenant.
     *
     * @param id Cliente UUID
     * @return Cliente entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public Cliente findById(UUID id) {
        log.debug("Finding customer by id: {}", id);
        return clienteRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Cliente não encontrado"));
    }

    /**
     * Search customers by name (partial, case-insensitive).
     *
     * @param nome Partial name to search
     * @return List of matching customers
     */
    @Transactional(readOnly = true)
    public List<Cliente> searchByNome(String nome) {
        log.debug("Searching customers by nome: {}", nome);
        return clienteRepository.searchByNome(nome);
    }

    /**
     * Create new customer.
     *
     * Validations:
     * - Name is required
     * - Contact information recommended
     *
     * @param cliente Cliente entity to create
     * @return Created Cliente
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Cliente createCliente(Cliente cliente) {
        log.info("Creating new customer: nome={}", cliente.getNome());

        // Validate name
        if (cliente.getNome() == null || cliente.getNome().isBlank()) {
            throw new BusinessException("Nome do cliente é obrigatório");
        }

        Cliente saved = clienteRepository.save(cliente);
        log.info("Customer created successfully: id={}, nome={}", saved.getId(), saved.getNome());
        return saved;
    }

    /**
     * Update existing customer.
     *
     * Validations:
     * - Customer must exist
     * - Name cannot be blank if provided
     *
     * @param id Cliente UUID
     * @param updates Cliente with updated fields
     * @return Updated Cliente
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Cliente updateCliente(UUID id, Cliente updates) {
        log.info("Updating customer: id={}", id);

        Cliente existing = findById(id);

        // Update fields
        if (updates.getNome() != null) {
            if (updates.getNome().isBlank()) {
                throw new BusinessException("Nome do cliente não pode ser vazio");
            }
            existing.setNome(updates.getNome());
        }

        if (updates.getDocumento() != null) {
            existing.setDocumento(updates.getDocumento());
        }

        if (updates.getDataNascimento() != null) {
            existing.setDataNascimento(updates.getDataNascimento());
        }

        if (updates.getGenero() != null) {
            existing.setGenero(updates.getGenero());
        }

        if (updates.getEmail() != null) {
            existing.setEmail(updates.getEmail());
        }

        if (updates.getTelefone() != null) {
            existing.setTelefone(updates.getTelefone());
        }

        if (updates.getWhatsapp() != null) {
            existing.setWhatsapp(updates.getWhatsapp());
        }

        if (updates.getEnderecoJson() != null) {
            existing.setEnderecoJson(updates.getEnderecoJson());
        }

        if (updates.getTermoAceite() != null) {
            existing.setTermoAceite(updates.getTermoAceite());
        }

        Cliente saved = clienteRepository.save(existing);
        log.info("Customer updated successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Accept liability terms for customer (RF03.4).
     * Required before first rental.
     *
     * @param id Cliente UUID
     * @return Updated Cliente
     * @throws BusinessException if not found
     */
    @Transactional
    public Cliente acceptTerms(UUID id) {
        log.info("Customer accepting liability terms: id={}", id);

        Cliente cliente = findById(id);

        if (Boolean.TRUE.equals(cliente.getTermoAceite())) {
            log.warn("Customer already accepted terms: id={}", id);
            return cliente;
        }

        cliente.setTermoAceite(true);
        Cliente saved = clienteRepository.save(cliente);

        log.info("Customer accepted terms successfully: id={}", id);
        return saved;
    }

    /**
     * Deactivate customer (soft delete).
     * Historical rentals are preserved for LGPD compliance.
     *
     * @param id Cliente UUID
     * @return Deactivated Cliente
     * @throws BusinessException if not found
     */
    @Transactional
    public Cliente deactivateCliente(UUID id) {
        log.info("Deactivating customer: id={}", id);

        Cliente cliente = findById(id);

        if (!Boolean.TRUE.equals(cliente.getAtivo())) {
            throw new BusinessException("Cliente já está inativo");
        }

        cliente.setAtivo(false);
        Cliente saved = clienteRepository.save(cliente);

        log.info("Customer deactivated successfully: id={}", id);
        return saved;
    }

    /**
     * Reactivate previously deactivated customer.
     *
     * @param id Cliente UUID
     * @return Reactivated Cliente
     * @throws BusinessException if not found
     */
    @Transactional
    public Cliente reactivateCliente(UUID id) {
        log.info("Reactivating customer: id={}", id);

        Cliente cliente = findById(id);

        if (Boolean.TRUE.equals(cliente.getAtivo())) {
            throw new BusinessException("Cliente já está ativo");
        }

        cliente.setAtivo(true);
        Cliente saved = clienteRepository.save(cliente);

        log.info("Customer reactivated successfully: id={}", id);
        return saved;
    }

    /**
     * Check if customer can rent (RF03.4).
     * Business rule: Must be active AND have accepted terms.
     *
     * @param id Cliente UUID
     * @return true if customer can rent
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public boolean canRent(UUID id) {
        Cliente cliente = findById(id);
        boolean eligible = cliente.canRent();
        log.debug("Customer rental eligibility: id={}, canRent={}", id, eligible);
        return eligible;
    }
}
