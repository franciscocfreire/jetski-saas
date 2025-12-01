package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.JetskiSearchCriteria;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.JetskiStatus;
import com.jetski.locacoes.internal.repository.JetskiRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service: Jetski Management
 *
 * Handles individual jetski unit registration and fleet management:
 * - Create, update, and list jetskis
 * - Validate serial number uniqueness per tenant
 * - Manage availability status (Business rule RN06)
 * - Track odometer readings
 *
 * Business Rules:
 * - RN06: Jetskis in MANUTENCAO status cannot be reserved
 * - Serial numbers must be unique within tenant
 * - Model must exist before creating jetski
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JetskiService {

    private final JetskiRepository jetskiRepository;
    private final ModeloRepository modeloRepository;

    /**
     * List all active jetskis for current tenant.
     * RLS automatically filters by tenant_id.
     *
     * @return List of active Jetski records
     */
    @Transactional(readOnly = true)
    public List<Jetski> listActiveJetskis() {
        log.debug("Listing all active jetskis");
        return jetskiRepository.findAllActive();
    }

    /**
     * List all jetskis for current tenant (including inactive).
     * RLS automatically filters by tenant_id.
     *
     * @return List of all Jetski records
     */
    @Transactional(readOnly = true)
    public List<Jetski> listAllJetskis() {
        log.debug("Listing all jetskis (including inactive)");
        return jetskiRepository.findAll();
    }

    /**
     * List available jetskis for rental (RN06).
     * Business rule: Only DISPONIVEL jetskis can be reserved.
     *
     * @return List of available Jetski records
     */
    @Transactional(readOnly = true)
    public List<Jetski> listAvailableJetskis() {
        log.debug("Listing available jetskis for rental");
        return jetskiRepository.findAllByStatus(JetskiStatus.DISPONIVEL);
    }

    /**
     * List jetskis by status.
     *
     * @param status JetskiStatus to filter by
     * @return List of Jetski records with the specified status
     */
    @Transactional(readOnly = true)
    public List<Jetski> listByStatus(JetskiStatus status) {
        log.debug("Listing jetskis by status: {}", status);
        return jetskiRepository.findAllByStatus(status);
    }

    /**
     * List jetskis by model.
     *
     * @param modeloId Modelo UUID
     * @return List of Jetski records for this model
     */
    @Transactional(readOnly = true)
    public List<Jetski> listByModelo(UUID modeloId) {
        log.debug("Listing jetskis for model: {}", modeloId);
        return jetskiRepository.findAllByModeloId(modeloId);
    }

    /**
     * Find jetski by ID within current tenant.
     *
     * @param id Jetski UUID
     * @return Jetski entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public Jetski findById(UUID id) {
        log.debug("Finding jetski by id: {}", id);
        return jetskiRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Jetski não encontrado"));
    }

    /**
     * Create new jetski.
     *
     * Validations:
     * - Model must exist
     * - Serial number must not already exist in tenant
     * - Initial odometer must be >= 0
     *
     * @param jetski Jetski entity to create
     * @return Created Jetski
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Jetski createJetski(Jetski jetski) {
        log.info("Creating new jetski: serie={}, modelo={}", jetski.getSerie(), jetski.getModeloId());

        // Validate model exists
        if (!modeloRepository.existsById(jetski.getModeloId())) {
            throw new BusinessException("Modelo não encontrado");
        }

        // Validate serial number uniqueness
        if (jetskiRepository.existsBySerie(jetski.getSerie())) {
            throw new BusinessException("Já existe um jetski com esta série");
        }

        // Validate odometer
        if (jetski.getHorimetroAtual() != null && jetski.getHorimetroAtual().signum() < 0) {
            throw new BusinessException("Horímetro não pode ser negativo");
        }

        Jetski saved = jetskiRepository.save(jetski);
        log.info("Jetski created successfully: id={}, serie={}", saved.getId(), saved.getSerie());
        return saved;
    }

    /**
     * Update existing jetski.
     *
     * Validations:
     * - Jetski must exist
     * - If serial changed, new serial must not exist
     * - Odometer cannot decrease
     *
     * @param id Jetski UUID
     * @param updates Jetski with updated fields
     * @return Updated Jetski
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Jetski updateJetski(UUID id, Jetski updates) {
        log.info("Updating jetski: id={}", id);

        Jetski existing = findById(id);

        // Check serial uniqueness if changed
        if (updates.getSerie() != null && !updates.getSerie().equals(existing.getSerie())) {
            if (jetskiRepository.existsBySerie(updates.getSerie())) {
                throw new BusinessException("Já existe um jetski com esta série");
            }
            existing.setSerie(updates.getSerie());
        }

        // Update fields
        if (updates.getAno() != null) {
            existing.setAno(updates.getAno());
        }

        // Validate odometer cannot decrease
        if (updates.getHorimetroAtual() != null) {
            if (updates.getHorimetroAtual().compareTo(existing.getHorimetroAtual()) < 0) {
                throw new BusinessException("Horímetro não pode diminuir");
            }
            existing.setHorimetroAtual(updates.getHorimetroAtual());
        }

        if (updates.getStatus() != null) {
            existing.setStatus(updates.getStatus());
        }

        Jetski saved = jetskiRepository.save(existing);
        log.info("Jetski updated successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Update jetski status.
     *
     * @param id Jetski UUID
     * @param newStatus New status
     * @return Updated Jetski
     * @throws BusinessException if not found
     */
    @Transactional
    public Jetski updateStatus(UUID id, JetskiStatus newStatus) {
        log.info("Updating jetski status: id={}, newStatus={}", id, newStatus);

        Jetski jetski = findById(id);
        jetski.setStatus(newStatus);
        Jetski saved = jetskiRepository.save(jetski);

        log.info("Jetski status updated successfully: id={}, status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    /**
     * Deactivate jetski (soft delete).
     *
     * @param id Jetski UUID
     * @return Deactivated Jetski
     * @throws BusinessException if not found
     */
    @Transactional
    public Jetski deactivateJetski(UUID id) {
        log.info("Deactivating jetski: id={}", id);

        Jetski jetski = findById(id);

        if (!Boolean.TRUE.equals(jetski.getAtivo())) {
            throw new BusinessException("Jetski já está inativo");
        }

        jetski.setAtivo(false);
        Jetski saved = jetskiRepository.save(jetski);

        log.info("Jetski deactivated successfully: id={}", id);
        return saved;
    }

    /**
     * Reactivate previously deactivated jetski.
     *
     * @param id Jetski UUID
     * @return Reactivated Jetski
     * @throws BusinessException if not found
     */
    @Transactional
    public Jetski reactivateJetski(UUID id) {
        log.info("Reactivating jetski: id={}", id);

        Jetski jetski = findById(id);

        if (Boolean.TRUE.equals(jetski.getAtivo())) {
            throw new BusinessException("Jetski já está ativo");
        }

        jetski.setAtivo(true);
        Jetski saved = jetskiRepository.save(jetski);

        log.info("Jetski reactivated successfully: id={}", id);
        return saved;
    }

    /**
     * Search jetskis with advanced filtering (v0.9.0).
     *
     * Supports multiple filters:
     * - Status (multiple values)
     * - Modelo
     * - Serial number (partial match)
     * - Hourmeter range
     * - Active status
     *
     * @param criteria Search criteria
     * @return List of Jetski records matching criteria
     */
    @Transactional(readOnly = true)
    public List<Jetski> searchJetskis(JetskiSearchCriteria criteria) {
        log.info("Searching jetskis with criteria: {}", criteria);

        // Build specification from criteria
        Specification<Jetski> spec = JetskiSpecification.fromCriteria(criteria);

        // Build sort
        Sort sort = buildSort(criteria);

        // Execute query
        List<Jetski> results = jetskiRepository.findAll(spec, sort);

        log.info("Found {} jetskis matching criteria", results.size());
        return results;
    }

    /**
     * Build Sort object from search criteria
     */
    private Sort buildSort(JetskiSearchCriteria criteria) {
        String sortBy = criteria.getSortBy() != null ? criteria.getSortBy() : "serie";
        String sortDirection = criteria.getSortDirection() != null ? criteria.getSortDirection() : "asc";

        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return Sort.by(direction, sortBy);
    }
}
