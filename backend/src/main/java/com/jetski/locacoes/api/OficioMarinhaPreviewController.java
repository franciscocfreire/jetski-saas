package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.OficioMarinhaPreviewRequest;
import com.jetski.locacoes.api.dto.OficioMarinhaPreviewResponse;
import com.jetski.locacoes.internal.OficioMarinhaPreviewService;
import com.jetski.shared.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pré-visualização do e-mail (ofício) à Capitania a partir da tela de configurações.
 *
 * <p>Vive em {@code locacoes} (dono do {@code MarinhaEmailTemplate}), mas responde sob
 * {@code /config} porque é uma ferramenta da tela de configurações da empresa: mesma
 * autorização do {@code /config/geral} (ADMIN_TENANT/GERENTE; OPA {@code config:*} —
 * o {@code ActionExtractor} mapeia este POST para {@code config:create}).
 * É POST porque recebe os valores ainda não salvos do formulário — não muda nada.
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/config/oficio-marinha")
@RequiredArgsConstructor
@Slf4j
public class OficioMarinhaPreviewController {

    private final OficioMarinhaPreviewService previewService;

    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(
        summary = "Pré-visualizar o e-mail à Capitania (sem enviar)",
        description = "Monta o ofício NORMAM-212 5.4.2 com um locatário fictício, aplicando por cima "
                    + "dos dados gravados os valores enviados no corpo (o que está digitado na tela). "
                    + "Devolve envelope, corpo HTML e pendências. Não envia, não persiste, não debita crédito."
    )
    public ResponseEntity<OficioMarinhaPreviewResponse> preview(
            @PathVariable UUID tenantId,
            @RequestBody(required = false) OficioMarinhaPreviewRequest request) {
        log.info("POST /v1/tenants/{}/config/oficio-marinha/preview", tenantId);
        UUID contextTenantId = TenantContext.getTenantId();
        if (!tenantId.equals(contextTenantId)) {
            log.error("Tenant mismatch: path={}, context={}", tenantId, contextTenantId);
            throw new IllegalArgumentException("Tenant ID mismatch");
        }
        return ResponseEntity.ok(previewService.preview(tenantId, request));
    }
}
