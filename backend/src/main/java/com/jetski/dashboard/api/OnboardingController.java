package com.jetski.dashboard.api;

import com.jetski.dashboard.api.dto.OnboardingChecklistResponse;
import com.jetski.dashboard.internal.OnboardingChecklistService;
import com.jetski.shared.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Checklist "primeiros passos" exibido no dashboard para a empresa recém-aprovada
 * — o caminho inicial de configuração (modelo → jetski → e-mails/PIX → equipe →
 * primeira locação), com cada passo auto-detectado dos dados reais.
 *
 * @author Jetski Team
 */
@Slf4j
@RestController
@RequestMapping("/v1/tenants/{tenantId}/dashboard/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingChecklistService onboardingChecklistService;

    @GetMapping
    public ResponseEntity<OnboardingChecklistResponse> getChecklist(@PathVariable UUID tenantId) {
        UUID contextTenantId = TenantContext.getTenantId();
        log.debug("GET onboarding checklist (tenant: {})", contextTenantId);
        return ResponseEntity.ok(onboardingChecklistService.checklist(contextTenantId));
    }
}
