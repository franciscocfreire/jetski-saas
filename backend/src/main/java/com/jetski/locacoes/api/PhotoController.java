package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.FotoService;
import com.jetski.locacoes.internal.PhotoValidationService;
import com.jetski.locacoes.api.dto.FotoResponse;
import com.jetski.locacoes.api.dto.UploadUrlRequest;
import com.jetski.locacoes.api.dto.UploadUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller para gerenciamento de fotos de locações.
 *
 * Fluxo de upload:
 * 1. Cliente solicita presigned URL via POST /fotos/upload
 * 2. Backend retorna URL assinada com expiração de 15 minutos
 * 3. Cliente faz PUT diretamente para storage (S3/MinIO/Local)
 * 4. Cliente confirma upload via POST /fotos/{fotoId}/confirm
 * 5. Backend valida e marca foto como confirmada
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/fotos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fotos", description = "Gerenciamento de fotos de locações")
public class PhotoController {

    private final FotoService fotoService;
    private final PhotoValidationService photoValidationService;

    @PostMapping("/upload")
    @PreAuthorize("@tenantAccessService.canAccessTenant(#tenantId)")
    @Operation(
        summary = "Gera presigned URL para upload de foto",
        description = "Retorna uma URL pré-assinada para upload direto ao storage. " +
                      "O cliente deve fazer PUT na URL retornada e depois confirmar o upload."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Presigned URL gerada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Request inválido"),
        @ApiResponse(responseCode = "404", description = "Locação não encontrada"),
        @ApiResponse(responseCode = "409", description = "Foto do mesmo tipo já existe")
    })
    public ResponseEntity<UploadUrlResponse> generateUploadUrl(
        @Parameter(description = "ID do tenant") @PathVariable UUID tenantId,
        @Valid @RequestBody UploadUrlRequest request
    ) {
        log.info("POST /fotos/upload - tenant={}, locacao={}, tipo={}",
            tenantId, request.getLocacaoId(), request.getTipoFoto());

        UploadUrlResponse response = fotoService.generateUploadUrl(tenantId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{fotoId}/confirm")
    @PreAuthorize("@tenantAccessService.canAccessTenant(#tenantId)")
    @Operation(
        summary = "Confirma upload de foto",
        description = "Valida que o arquivo foi enviado ao storage e marca foto como confirmada"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Upload confirmado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Foto não encontrada"),
        @ApiResponse(responseCode = "409", description = "Foto já foi confirmada"),
        @ApiResponse(responseCode = "422", description = "Arquivo não encontrado no storage")
    })
    public ResponseEntity<Void> confirmUpload(
        @Parameter(description = "ID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "ID da foto") @PathVariable UUID fotoId
    ) {
        log.info("POST /fotos/{}/confirm - tenant={}", fotoId, tenantId);

        fotoService.confirmUpload(tenantId, fotoId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{fotoId}")
    @PreAuthorize("@tenantAccessService.canAccessTenant(#tenantId)")
    @Operation(
        summary = "Busca foto por ID",
        description = "Retorna metadados da foto incluindo presigned URL para download"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Foto encontrada"),
        @ApiResponse(responseCode = "404", description = "Foto não encontrada")
    })
    public ResponseEntity<FotoResponse> getFoto(
        @Parameter(description = "ID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "ID da foto") @PathVariable UUID fotoId
    ) {
        log.info("GET /fotos/{} - tenant={}", fotoId, tenantId);

        FotoResponse response = fotoService.getFoto(tenantId, fotoId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/locacoes/{locacaoId}")
    @PreAuthorize("@tenantAccessService.canAccessTenant(#tenantId)")
    @Operation(
        summary = "Lista fotos de uma locação",
        description = "Retorna todas as fotos da locação com presigned URLs para download"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de fotos retornada com sucesso")
    })
    public ResponseEntity<List<FotoResponse>> listFotosByLocacao(
        @Parameter(description = "ID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "ID da locação") @PathVariable UUID locacaoId
    ) {
        log.info("GET /fotos/locacoes/{} - tenant={}", locacaoId, tenantId);

        List<FotoResponse> fotos = fotoService.listFotosByLocacao(tenantId, locacaoId);
        return ResponseEntity.ok(fotos);
    }

    @DeleteMapping("/{fotoId}")
    @PreAuthorize("@tenantAccessService.canAccessTenant(#tenantId)")
    @Operation(
        summary = "Deleta foto",
        description = "Remove foto do storage e do banco de dados"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Foto deletada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Foto não encontrada")
    })
    public ResponseEntity<Void> deleteFoto(
        @Parameter(description = "ID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "ID da foto") @PathVariable UUID fotoId
    ) {
        log.info("DELETE /fotos/{} - tenant={}", fotoId, tenantId);

        fotoService.deleteFoto(tenantId, fotoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/locacoes/{locacaoId}/checkin-status")
    @PreAuthorize("@tenantAccessService.canAccessTenant(#tenantId)")
    @Operation(
        summary = "Verifica status de fotos de check-in",
        description = "Retorna quantas fotos de check-in faltam para completar"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retornado com sucesso")
    })
    public ResponseEntity<Map<String, Object>> getCheckInPhotosStatus(
        @Parameter(description = "ID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "ID da locação") @PathVariable UUID locacaoId
    ) {
        log.info("GET /fotos/locacoes/{}/checkin-status - tenant={}", locacaoId, tenantId);

        var status = photoValidationService.getCheckInPhotosStatus(tenantId, locacaoId);

        return ResponseEntity.ok(Map.of(
            "uploadedCount", status.uploadedCount(),
            "requiredCount", status.requiredCount(),
            "isComplete", status.isComplete(),
            "missingTypes", status.missingTypes()
        ));
    }

    @GetMapping("/locacoes/{locacaoId}/checkout-status")
    @PreAuthorize("@tenantAccessService.canAccessTenant(#tenantId)")
    @Operation(
        summary = "Verifica status de fotos de check-out",
        description = "Retorna quantas fotos de check-out faltam para completar"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retornado com sucesso")
    })
    public ResponseEntity<Map<String, Object>> getCheckOutPhotosStatus(
        @Parameter(description = "ID do tenant") @PathVariable UUID tenantId,
        @Parameter(description = "ID da locação") @PathVariable UUID locacaoId
    ) {
        log.info("GET /fotos/locacoes/{}/checkout-status - tenant={}", locacaoId, tenantId);

        var status = photoValidationService.getCheckOutPhotosStatus(tenantId, locacaoId);

        return ResponseEntity.ok(Map.of(
            "uploadedCount", status.uploadedCount(),
            "requiredCount", status.requiredCount(),
            "isComplete", status.isComplete(),
            "missingTypes", status.missingTypes()
        ));
    }
}
