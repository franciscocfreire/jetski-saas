package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Foto;
import com.jetski.locacoes.domain.FotoTipo;
import com.jetski.locacoes.internal.repository.FotoRepository;
import com.jetski.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de validação de fotos obrigatórias.
 *
 * Regra de negócio: Check-in e check-out exigem 4 fotos cada:
 * - Check-in: CHECKIN_FRENTE, CHECKIN_LATERAL_ESQ, CHECKIN_LATERAL_DIR, CHECKIN_HORIMETRO
 * - Check-out: CHECKOUT_FRENTE, CHECKOUT_LATERAL_ESQ, CHECKOUT_LATERAL_DIR, CHECKOUT_HORIMETRO
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoValidationService {

    private final FotoRepository fotoRepository;

    /**
     * Fotos obrigatórias para check-in.
     */
    private static final Set<FotoTipo> REQUIRED_CHECKIN_PHOTOS = Set.of(
        FotoTipo.CHECKIN_FRENTE,
        FotoTipo.CHECKIN_LATERAL_ESQ,
        FotoTipo.CHECKIN_LATERAL_DIR,
        FotoTipo.CHECKIN_HORIMETRO
    );

    /**
     * Fotos obrigatórias para check-out.
     */
    private static final Set<FotoTipo> REQUIRED_CHECKOUT_PHOTOS = Set.of(
        FotoTipo.CHECKOUT_FRENTE,
        FotoTipo.CHECKOUT_LATERAL_ESQ,
        FotoTipo.CHECKOUT_LATERAL_DIR,
        FotoTipo.CHECKOUT_HORIMETRO
    );

    /**
     * Valida que todas as 4 fotos de check-in estão presentes e confirmadas.
     *
     * @throws BusinessException se alguma foto obrigatória estiver faltando
     */
    public void validateCheckInPhotos(UUID tenantId, UUID locacaoId) {
        log.info("Validating check-in photos: tenant={}, locacao={}", tenantId, locacaoId);

        List<Foto> fotos = fotoRepository.findByTenantIdAndLocacaoIdOrderByCreatedAtAsc(tenantId, locacaoId);

        // Filtra apenas fotos de check-in confirmadas
        Set<FotoTipo> uploadedTypes = fotos.stream()
            .filter(foto -> foto.getUploadedAt() != null)
            .filter(foto -> REQUIRED_CHECKIN_PHOTOS.contains(foto.getTipo()))
            .map(Foto::getTipo)
            .collect(Collectors.toSet());

        // Verifica se todas as 4 fotos obrigatórias estão presentes
        Set<FotoTipo> missingTypes = REQUIRED_CHECKIN_PHOTOS.stream()
            .filter(tipo -> !uploadedTypes.contains(tipo))
            .collect(Collectors.toSet());

        if (!missingTypes.isEmpty()) {
            String missing = missingTypes.stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));

            throw new BusinessException(
                String.format("Check-in requer 4 fotos obrigatórias. Faltando: %s", missing)
            );
        }

        log.info("Check-in photos validated successfully: locacao={}", locacaoId);
    }

    /**
     * Valida que todas as 4 fotos de check-out estão presentes e confirmadas.
     *
     * @throws BusinessException se alguma foto obrigatória estiver faltando
     */
    public void validateCheckOutPhotos(UUID tenantId, UUID locacaoId) {
        log.info("Validating check-out photos: tenant={}, locacao={}", tenantId, locacaoId);

        List<Foto> fotos = fotoRepository.findByTenantIdAndLocacaoIdOrderByCreatedAtAsc(tenantId, locacaoId);

        // Filtra apenas fotos de check-out confirmadas
        Set<FotoTipo> uploadedTypes = fotos.stream()
            .filter(foto -> foto.getUploadedAt() != null)
            .filter(foto -> REQUIRED_CHECKOUT_PHOTOS.contains(foto.getTipo()))
            .map(Foto::getTipo)
            .collect(Collectors.toSet());

        // Verifica se todas as 4 fotos obrigatórias estão presentes
        Set<FotoTipo> missingTypes = REQUIRED_CHECKOUT_PHOTOS.stream()
            .filter(tipo -> !uploadedTypes.contains(tipo))
            .collect(Collectors.toSet());

        if (!missingTypes.isEmpty()) {
            String missing = missingTypes.stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));

            throw new BusinessException(
                String.format("Check-out requer 4 fotos obrigatórias. Faltando: %s", missing)
            );
        }

        log.info("Check-out photos validated successfully: locacao={}", locacaoId);
    }

    /**
     * Retorna status de fotos de check-in (quantas faltam).
     */
    public CheckInPhotosStatus getCheckInPhotosStatus(UUID tenantId, UUID locacaoId) {
        List<Foto> fotos = fotoRepository.findByTenantIdAndLocacaoIdOrderByCreatedAtAsc(tenantId, locacaoId);

        Set<FotoTipo> uploadedTypes = fotos.stream()
            .filter(foto -> foto.getUploadedAt() != null)
            .filter(foto -> REQUIRED_CHECKIN_PHOTOS.contains(foto.getTipo()))
            .map(Foto::getTipo)
            .collect(Collectors.toSet());

        Set<FotoTipo> missingTypes = REQUIRED_CHECKIN_PHOTOS.stream()
            .filter(tipo -> !uploadedTypes.contains(tipo))
            .collect(Collectors.toSet());

        return new CheckInPhotosStatus(
            uploadedTypes.size(),
            REQUIRED_CHECKIN_PHOTOS.size(),
            missingTypes.isEmpty(),
            missingTypes
        );
    }

    /**
     * Retorna status de fotos de check-out (quantas faltam).
     */
    public CheckOutPhotosStatus getCheckOutPhotosStatus(UUID tenantId, UUID locacaoId) {
        List<Foto> fotos = fotoRepository.findByTenantIdAndLocacaoIdOrderByCreatedAtAsc(tenantId, locacaoId);

        Set<FotoTipo> uploadedTypes = fotos.stream()
            .filter(foto -> foto.getUploadedAt() != null)
            .filter(foto -> REQUIRED_CHECKOUT_PHOTOS.contains(foto.getTipo()))
            .map(Foto::getTipo)
            .collect(Collectors.toSet());

        Set<FotoTipo> missingTypes = REQUIRED_CHECKOUT_PHOTOS.stream()
            .filter(tipo -> !uploadedTypes.contains(tipo))
            .collect(Collectors.toSet());

        return new CheckOutPhotosStatus(
            uploadedTypes.size(),
            REQUIRED_CHECKOUT_PHOTOS.size(),
            missingTypes.isEmpty(),
            missingTypes
        );
    }

    /**
     * Status de fotos de check-in.
     */
    public record CheckInPhotosStatus(
        int uploadedCount,
        int requiredCount,
        boolean isComplete,
        Set<FotoTipo> missingTypes
    ) {}

    /**
     * Status de fotos de check-out.
     */
    public record CheckOutPhotosStatus(
        int uploadedCount,
        int requiredCount,
        boolean isComplete,
        Set<FotoTipo> missingTypes
    ) {}
}
