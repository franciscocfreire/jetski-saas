package com.jetski.shared.storage;

import com.jetski.locacoes.domain.Foto;
import com.jetski.locacoes.domain.FotoTipo;
import com.jetski.locacoes.internal.repository.FotoRepository;
import com.jetski.locacoes.internal.repository.LocacaoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.storage.dto.FotoResponse;
import com.jetski.shared.storage.dto.UploadUrlRequest;
import com.jetski.shared.storage.dto.UploadUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço de negócio para gerenciamento de fotos.
 *
 * Orquestra a geração de presigned URLs, validação de uploads
 * e persistência de metadados no banco de dados.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FotoService {

    private final StorageService storageService;
    private final FotoRepository fotoRepository;
    private final LocacaoRepository locacaoRepository;

    @Value("${storage.presigned-url-expiration-minutes:15}")
    private int presignedUrlExpirationMinutes;

    @Value("${storage.download-url-expiration-minutes:60}")
    private int downloadUrlExpirationMinutes;

    /**
     * Gera presigned URL para upload de foto.
     */
    @Transactional
    public UploadUrlResponse generateUploadUrl(UUID tenantId, UploadUrlRequest request) {
        log.info("Generating upload URL: tenant={}, locacao={}, tipo={}",
            tenantId, request.getLocacaoId(), request.getTipoFoto());

        // 1. Valida que locação existe
        var locacao = locacaoRepository.findByIdAndTenantId(request.getLocacaoId(), tenantId)
            .orElseThrow(() -> new NotFoundException("Locação não encontrada: " + request.getLocacaoId()));

        // 2. Verifica se já existe foto do mesmo tipo
        boolean exists = fotoRepository.existsByTenantIdAndLocacaoIdAndTipo(
            tenantId, request.getLocacaoId(), request.getTipoFoto()
        );
        if (exists) {
            throw new BusinessException(
                String.format("Já existe foto do tipo %s para esta locação", request.getTipoFoto())
            );
        }

        // 3. Gera chave única
        String extension = getExtensionFromContentType(request.getContentType());
        String storageKey = String.format("%s/%s/%s%s",
            tenantId, request.getLocacaoId(), request.getTipoFoto(), extension);

        // 4. Cria registro Foto
        Foto foto = Foto.builder()
            .tenantId(tenantId)
            .locacaoId(request.getLocacaoId())
            .tipo(request.getTipoFoto())
            .s3Key(storageKey)
            .url("pending")  // Will be updated on confirmation
            .filename(request.getTipoFoto() + extension)
            .contentType(request.getContentType())
            .sizeBytes(request.getFileSize())
            .sha256Hash(request.getSha256Hash())
            .uploadedAt(null) // Será preenchido na confirmação
            .build();

        foto = fotoRepository.save(foto);
        log.info("Foto record created: id={}, key={}", foto.getId(), storageKey);

        // 5. Gera presigned URL
        PresignedUrl presignedUrl = storageService.generatePresignedUploadUrl(
            storageKey,
            request.getContentType(),
            presignedUrlExpirationMinutes
        );

        return UploadUrlResponse.builder()
            .fotoId(foto.getId())
            .uploadUrl(presignedUrl.getUrl())
            .key(storageKey)
            .expiresAt(presignedUrl.getExpiresAt())
            .maxSizeBytes(presignedUrl.getMaxSizeBytes())
            .contentType(request.getContentType())
            .build();
    }

    /**
     * Confirma upload de foto após cliente ter feito PUT na presigned URL.
     */
    @Transactional
    public void confirmUpload(UUID tenantId, UUID fotoId) {
        log.info("Confirming upload: tenant={}, fotoId={}", tenantId, fotoId);

        Foto foto = fotoRepository.findByIdAndTenantId(fotoId, tenantId)
            .orElseThrow(() -> new NotFoundException("Foto não encontrada: " + fotoId));

        if (foto.getUploadedAt() != null) {
            throw new BusinessException("Foto já foi confirmada anteriormente");
        }

        // Verifica que arquivo existe no storage
        if (!storageService.fileExists(foto.getS3Key())) {
            throw new BusinessException("Arquivo não encontrado no storage: " + foto.getS3Key());
        }

        // Atualiza registro
        foto.setUploadedAt(Instant.now());
        fotoRepository.save(foto);

        log.info("Upload confirmed: fotoId={}, key={}", fotoId, foto.getS3Key());
    }

    /**
     * Lista fotos de uma locação.
     */
    @Transactional(readOnly = true)
    public List<FotoResponse> listFotosByLocacao(UUID tenantId, UUID locacaoId) {
        log.info("Listing photos: tenant={}, locacao={}", tenantId, locacaoId);

        List<Foto> fotos = fotoRepository.findByTenantIdAndLocacaoIdOrderByCreatedAtAsc(tenantId, locacaoId);

        return fotos.stream()
            .map(foto -> toFotoResponse(foto))
            .collect(Collectors.toList());
    }

    /**
     * Busca foto específica por ID.
     */
    @Transactional(readOnly = true)
    public FotoResponse getFoto(UUID tenantId, UUID fotoId) {
        log.info("Getting photo: tenant={}, fotoId={}", tenantId, fotoId);

        Foto foto = fotoRepository.findByIdAndTenantId(fotoId, tenantId)
            .orElseThrow(() -> new NotFoundException("Foto não encontrada: " + fotoId));

        return toFotoResponse(foto);
    }

    /**
     * Deleta foto (storage + banco).
     */
    @Transactional
    public void deleteFoto(UUID tenantId, UUID fotoId) {
        log.info("Deleting photo: tenant={}, fotoId={}", tenantId, fotoId);

        Foto foto = fotoRepository.findByIdAndTenantId(fotoId, tenantId)
            .orElseThrow(() -> new NotFoundException("Foto não encontrada: " + fotoId));

        // Deleta do storage
        if (storageService.fileExists(foto.getS3Key())) {
            storageService.deleteFile(foto.getS3Key());
        }

        // Deleta do banco
        fotoRepository.delete(foto);

        log.info("Photo deleted: fotoId={}, key={}", fotoId, foto.getS3Key());
    }

    private FotoResponse toFotoResponse(Foto foto) {
        // Gera download URL se foto foi confirmada
        String downloadUrl = null;
        LocalDateTime downloadUrlExpiresAt = null;

        if (foto.getUploadedAt() != null) {
            PresignedUrl presignedUrl = storageService.generatePresignedDownloadUrl(
                foto.getS3Key(),
                downloadUrlExpirationMinutes
            );
            downloadUrl = presignedUrl.getUrl();
            downloadUrlExpiresAt = presignedUrl.getExpiresAt();
        }

        return FotoResponse.builder()
            .id(foto.getId())
            .locacaoId(foto.getLocacaoId())
            .tipo(foto.getTipo())
            .storageKey(foto.getS3Key())
            .downloadUrl(downloadUrl)
            .tamanhoBytes(foto.getSizeBytes())
            .contentType(foto.getContentType())
            .sha256Hash(foto.getSha256Hash())
            .uploadedAt(foto.getUploadedAt() != null ?
                LocalDateTime.ofInstant(foto.getUploadedAt(), ZoneId.systemDefault()) : null)
            .downloadUrlExpiresAt(downloadUrlExpiresAt)
            .build();
    }

    private String getExtensionFromContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
