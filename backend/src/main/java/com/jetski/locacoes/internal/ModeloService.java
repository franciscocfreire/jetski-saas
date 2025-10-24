package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service: Modelo Management
 *
 * Handles jetski model registration and configuration:
 * - Create, update, and list models
 * - Validate model uniqueness per tenant
 * - Manage pricing and tolerance settings
 *
 * Business Rules:
 * - Model names must be unique within tenant
 * - Price must be positive
 * - Tolerance minutes must be >= 0
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModeloService {

    private final ModeloRepository modeloRepository;

    /**
     * List all active models for current tenant.
     * RLS automatically filters by tenant_id.
     *
     * @return List of active Modelo records
     */
    @Transactional(readOnly = true)
    public List<Modelo> listActiveModels() {
        log.debug("Listing all active models");
        return modeloRepository.findAllActive();
    }

    /**
     * List all models for current tenant (including inactive).
     * RLS automatically filters by tenant_id.
     *
     * @return List of all Modelo records
     */
    @Transactional(readOnly = true)
    public List<Modelo> listAllModels() {
        log.debug("Listing all models (including inactive)");
        return modeloRepository.findAllByTenant();
    }

    /**
     * Find model by ID within current tenant.
     *
     * @param id Modelo UUID
     * @return Modelo entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public Modelo findById(UUID id) {
        log.debug("Finding model by id: {}", id);
        return modeloRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Modelo não encontrado"));
    }

    /**
     * Create new model.
     *
     * Validations:
     * - Name must not already exist in tenant
     * - Price must be positive
     * - Tolerance must be >= 0
     *
     * @param modelo Modelo entity to create
     * @return Created Modelo
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Modelo createModelo(Modelo modelo) {
        log.info("Creating new model: {}", modelo.getNome());

        // Validate name uniqueness
        if (modeloRepository.existsByNome(modelo.getNome())) {
            throw new BusinessException("Já existe um modelo com este nome");
        }

        // Validate price
        if (modelo.getPrecoBaseHora() == null || modelo.getPrecoBaseHora().signum() <= 0) {
            throw new BusinessException("Preço base por hora deve ser maior que zero");
        }

        // Validate tolerance
        if (modelo.getToleranciaMin() != null && modelo.getToleranciaMin() < 0) {
            throw new BusinessException("Tolerância não pode ser negativa");
        }

        Modelo saved = modeloRepository.save(modelo);
        log.info("Model created successfully: id={}, name={}", saved.getId(), saved.getNome());
        return saved;
    }

    /**
     * Update existing model.
     *
     * Validations:
     * - Model must exist
     * - If name changed, new name must not exist
     * - Price must be positive if provided
     *
     * @param id Modelo UUID
     * @param updates Modelo with updated fields
     * @return Updated Modelo
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Modelo updateModelo(UUID id, Modelo updates) {
        log.info("Updating model: id={}", id);

        Modelo existing = findById(id);

        // Check name uniqueness if changed
        if (updates.getNome() != null && !updates.getNome().equals(existing.getNome())) {
            if (modeloRepository.existsByNome(updates.getNome())) {
                throw new BusinessException("Já existe um modelo com este nome");
            }
            existing.setNome(updates.getNome());
        }

        // Update fields
        if (updates.getFabricante() != null) {
            existing.setFabricante(updates.getFabricante());
        }
        if (updates.getPotenciaHp() != null) {
            existing.setPotenciaHp(updates.getPotenciaHp());
        }
        if (updates.getCapacidadePessoas() != null) {
            existing.setCapacidadePessoas(updates.getCapacidadePessoas());
        }
        if (updates.getPrecoBaseHora() != null) {
            if (updates.getPrecoBaseHora().signum() <= 0) {
                throw new BusinessException("Preço base por hora deve ser maior que zero");
            }
            existing.setPrecoBaseHora(updates.getPrecoBaseHora());
        }
        if (updates.getToleranciaMin() != null) {
            if (updates.getToleranciaMin() < 0) {
                throw new BusinessException("Tolerância não pode ser negativa");
            }
            existing.setToleranciaMin(updates.getToleranciaMin());
        }
        if (updates.getTaxaHoraExtra() != null) {
            existing.setTaxaHoraExtra(updates.getTaxaHoraExtra());
        }
        if (updates.getIncluiCombustivel() != null) {
            existing.setIncluiCombustivel(updates.getIncluiCombustivel());
        }
        if (updates.getCaucao() != null) {
            existing.setCaucao(updates.getCaucao());
        }
        if (updates.getFotoReferenciaUrl() != null) {
            existing.setFotoReferenciaUrl(updates.getFotoReferenciaUrl());
        }
        if (updates.getPacotesJson() != null) {
            existing.setPacotesJson(updates.getPacotesJson());
        }

        Modelo saved = modeloRepository.save(existing);
        log.info("Model updated successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Deactivate model (soft delete).
     *
     * @param id Modelo UUID
     * @return Deactivated Modelo
     * @throws BusinessException if not found
     */
    @Transactional
    public Modelo deactivateModelo(UUID id) {
        log.info("Deactivating model: id={}", id);

        Modelo modelo = findById(id);

        if (!Boolean.TRUE.equals(modelo.getAtivo())) {
            throw new BusinessException("Modelo já está inativo");
        }

        modelo.setAtivo(false);
        Modelo saved = modeloRepository.save(modelo);

        log.info("Model deactivated successfully: id={}", id);
        return saved;
    }

    /**
     * Reactivate previously deactivated model.
     *
     * @param id Modelo UUID
     * @return Reactivated Modelo
     * @throws BusinessException if not found
     */
    @Transactional
    public Modelo reactivateModelo(UUID id) {
        log.info("Reactivating model: id={}", id);

        Modelo modelo = findById(id);

        if (Boolean.TRUE.equals(modelo.getAtivo())) {
            throw new BusinessException("Modelo já está ativo");
        }

        modelo.setAtivo(true);
        Modelo saved = modeloRepository.save(modelo);

        log.info("Model reactivated successfully: id={}", id);
        return saved;
    }
}
