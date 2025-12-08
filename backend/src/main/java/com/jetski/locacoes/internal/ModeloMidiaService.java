package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.ModeloMidiaRequest;
import com.jetski.locacoes.api.dto.ModeloMidiaResponse;
import com.jetski.locacoes.domain.ModeloMidia;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.shared.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service: ModeloMidiaService
 *
 * Manages media items (images/videos) for jetski models.
 * Supports upload, ordering, and principal image selection.
 *
 * Business Rules:
 * - Each model can have multiple images and videos
 * - Only one image can be marked as principal per model
 * - Media items are ordered by 'ordem' field
 * - Tenant isolation via RLS
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModeloMidiaService {

    private final ModeloMidiaRepository midiaRepository;
    private final ModeloRepository modeloRepository;

    /**
     * List all media for a model
     */
    @Transactional(readOnly = true)
    public List<ModeloMidiaResponse> listByModelo(UUID modeloId) {
        return midiaRepository.findByModeloIdOrderByOrdemAsc(modeloId).stream()
            .map(ModeloMidiaResponse::from)
            .toList();
    }

    /**
     * Get a specific media item by ID
     */
    @Transactional(readOnly = true)
    public ModeloMidiaResponse getById(UUID midiaId) {
        return midiaRepository.findById(midiaId)
            .map(ModeloMidiaResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("Mídia não encontrada: " + midiaId));
    }

    /**
     * Add a new media item to a model
     */
    @Transactional
    public ModeloMidiaResponse addMidia(UUID modeloId, ModeloMidiaRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        // Verify modelo exists and belongs to tenant
        modeloRepository.findById(modeloId)
            .orElseThrow(() -> new EntityNotFoundException("Modelo não encontrado: " + modeloId));

        // If this is marked as principal, clear other principals first
        if (Boolean.TRUE.equals(request.principal())) {
            midiaRepository.clearPrincipalByModeloId(modeloId);
        }

        // Get next ordem if not specified
        Integer ordem = request.ordem() != null ? request.ordem() : midiaRepository.getNextOrdem(modeloId);

        ModeloMidia midia = ModeloMidia.builder()
            .tenantId(tenantId)
            .modeloId(modeloId)
            .tipo(request.tipo())
            .url(request.url())
            .thumbnailUrl(request.thumbnailUrl())
            .ordem(ordem)
            .principal(request.principal())
            .titulo(request.titulo())
            .build();

        ModeloMidia saved = midiaRepository.save(midia);

        // If this is the first media and is an image, make it principal
        if (midia.getTipo() == ModeloMidia.TipoMidia.IMAGEM && midiaRepository.countByModeloId(modeloId) == 1) {
            saved.setPrincipal(true);
            midiaRepository.save(saved);
        }

        // Update modelo.fotoReferenciaUrl with principal image
        updateModeloFotoReferencia(modeloId);

        log.info("Mídia adicionada: modeloId={}, midiaId={}, tipo={}", modeloId, saved.getId(), saved.getTipo());
        return ModeloMidiaResponse.from(saved);
    }

    /**
     * Update an existing media item
     */
    @Transactional
    public ModeloMidiaResponse updateMidia(UUID midiaId, ModeloMidiaRequest request) {
        ModeloMidia midia = midiaRepository.findById(midiaId)
            .orElseThrow(() -> new EntityNotFoundException("Mídia não encontrada: " + midiaId));

        // If setting as principal, clear others first
        if (Boolean.TRUE.equals(request.principal()) && !Boolean.TRUE.equals(midia.getPrincipal())) {
            midiaRepository.clearPrincipalByModeloId(midia.getModeloId());
        }

        midia.setTipo(request.tipo());
        midia.setUrl(request.url());
        midia.setThumbnailUrl(request.thumbnailUrl());
        if (request.ordem() != null) {
            midia.setOrdem(request.ordem());
        }
        midia.setPrincipal(request.principal());
        midia.setTitulo(request.titulo());

        ModeloMidia saved = midiaRepository.save(midia);

        // Update modelo.fotoReferenciaUrl
        updateModeloFotoReferencia(midia.getModeloId());

        log.info("Mídia atualizada: midiaId={}", midiaId);
        return ModeloMidiaResponse.from(saved);
    }

    /**
     * Delete a media item
     */
    @Transactional
    public void deleteMidia(UUID midiaId) {
        ModeloMidia midia = midiaRepository.findById(midiaId)
            .orElseThrow(() -> new EntityNotFoundException("Mídia não encontrada: " + midiaId));

        UUID modeloId = midia.getModeloId();
        boolean wasPrincipal = Boolean.TRUE.equals(midia.getPrincipal());

        midiaRepository.delete(midia);

        // If deleted was principal, set next image as principal
        if (wasPrincipal) {
            midiaRepository.findByModeloIdAndTipoOrderByOrdemAsc(modeloId, ModeloMidia.TipoMidia.IMAGEM)
                .stream()
                .findFirst()
                .ifPresent(next -> {
                    next.setPrincipal(true);
                    midiaRepository.save(next);
                });
        }

        // Update modelo.fotoReferenciaUrl
        updateModeloFotoReferencia(modeloId);

        log.info("Mídia removida: midiaId={}, modeloId={}", midiaId, modeloId);
    }

    /**
     * Set a media item as principal (main image)
     */
    @Transactional
    public ModeloMidiaResponse setPrincipal(UUID midiaId) {
        ModeloMidia midia = midiaRepository.findById(midiaId)
            .orElseThrow(() -> new EntityNotFoundException("Mídia não encontrada: " + midiaId));

        if (midia.getTipo() != ModeloMidia.TipoMidia.IMAGEM) {
            throw new IllegalArgumentException("Apenas imagens podem ser definidas como principal");
        }

        // Clear all other principals for this model
        midiaRepository.clearPrincipalByModeloId(midia.getModeloId());

        // Set this one as principal
        midia.setPrincipal(true);
        ModeloMidia saved = midiaRepository.save(midia);

        // Update modelo.fotoReferenciaUrl
        updateModeloFotoReferencia(midia.getModeloId());

        log.info("Mídia definida como principal: midiaId={}", midiaId);
        return ModeloMidiaResponse.from(saved);
    }

    /**
     * Reorder media items for a model
     */
    @Transactional
    public List<ModeloMidiaResponse> reorder(UUID modeloId, List<UUID> orderedIds) {
        List<ModeloMidia> midias = midiaRepository.findByModeloIdOrderByOrdemAsc(modeloId);

        for (int i = 0; i < orderedIds.size(); i++) {
            UUID midiaId = orderedIds.get(i);
            final int newOrdem = i;
            midias.stream()
                .filter(m -> m.getId().equals(midiaId))
                .findFirst()
                .ifPresent(m -> m.setOrdem(newOrdem));
        }

        midiaRepository.saveAll(midias);

        log.info("Mídias reordenadas: modeloId={}, count={}", modeloId, orderedIds.size());
        return listByModelo(modeloId);
    }

    /**
     * Update modelo.fotoReferenciaUrl with the principal image URL
     * This keeps backward compatibility with existing code
     */
    private void updateModeloFotoReferencia(UUID modeloId) {
        modeloRepository.findById(modeloId).ifPresent(modelo -> {
            String principalUrl = midiaRepository.findByModeloIdAndPrincipalTrue(modeloId)
                .map(ModeloMidia::getUrl)
                .orElse(null);

            modelo.setFotoReferenciaUrl(principalUrl);
            modeloRepository.save(modelo);
        });
    }
}
