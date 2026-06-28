package com.jetski.tenant.api;

import com.jetski.tenant.api.dto.PendingTenantDTO;
import com.jetski.tenant.api.dto.PlatformTenantSummary;
import com.jetski.tenant.api.dto.SuspendTenantRequest;
import com.jetski.tenant.api.dto.TenantStatusResult;
import com.jetski.tenant.internal.PlatformSecretsService;
import com.jetski.tenant.internal.PlatformTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de plataforma (super admin global): aprovação/bloqueio de empresas (tenants).
 *
 * <p>Autorização: ações {@code platform:*} liberadas apenas para usuários com
 * {@code unrestricted_access} (via OPA). O super admin opera dentro da sessão do tenant
 * alvo (header X-Tenant-Id), sem bypass de RLS. Ver ONBOARDING_EMPRESA_SPEC §A/§B.
 *
 * @author Jetski Team
 */
@RestController
@RequestMapping("/v1/platform")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final PlatformTenantService platformTenantService;
    private final PlatformSecretsService platformSecretsService;

    /** Lista TODAS as empresas (qualquer status) — visão completa do super admin. */
    @GetMapping("/tenants")
    public List<PlatformTenantSummary> allTenants() {
        return platformTenantService.listAll();
    }

    /** Lista empresas aguardando aprovação. */
    @GetMapping("/pending-signups")
    public List<PendingTenantDTO> pendingSignups() {
        return platformTenantService.listPending();
    }

    /** Aprova uma empresa pendente (→ ATIVO + trial). */
    @PostMapping("/tenants/{id}/approve")
    public TenantStatusResult approve(@PathVariable("id") UUID id) {
        return platformTenantService.approve(id);
    }

    /** Suspende uma empresa ativa (→ SUSPENSO). */
    @PostMapping("/tenants/{id}/suspend")
    public TenantStatusResult suspend(@PathVariable("id") UUID id,
                                      @RequestBody(required = false) SuspendTenantRequest body) {
        return platformTenantService.suspend(id, body != null ? body.motivo() : null);
    }

    /** Reativa uma empresa suspensa (→ ATIVO). */
    @PostMapping("/tenants/{id}/reactivate")
    public TenantStatusResult reactivate(@PathVariable("id") UUID id) {
        return platformTenantService.reactivate(id);
    }

    /**
     * Re-cifra os segredos de todos os tenants com a chave ATUAL (passo eager da
     * rotação de chave). Após rodar e checar falhas=0, a JETSKI_SECRET_KEY_PREVIOUS
     * pode ser removida. Ação: platform:reencrypt (só super admin via OPA).
     */
    @PostMapping("/secrets/reencrypt")
    public PlatformSecretsService.ReencryptResult reencryptSecrets() {
        return platformSecretsService.reencrypt();
    }
}
