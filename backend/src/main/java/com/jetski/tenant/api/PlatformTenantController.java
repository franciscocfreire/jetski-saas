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
    private final com.jetski.tenant.internal.TenantResetService tenantResetService;
    private final com.jetski.tenant.internal.TenantExportService tenantExportService;
    private final com.jetski.tenant.internal.TenantExclusaoService tenantExclusaoService;

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
     * Habilita a empresa como EAMA emissora após validação do registro na
     * Capitania (V047). Ação OPA: {@code platform:habilitar-emissora}.
     */
    @PostMapping("/tenants/{id}/habilitar-emissora")
    public com.jetski.tenant.api.dto.EmissoraStatusResult habilitarEmissora(@PathVariable("id") UUID id) {
        return platformTenantService.habilitarEmissora(id);
    }

    /** Remove a habilitação de emissora. Ação OPA: {@code platform:desabilitar-emissora}. */
    @PostMapping("/tenants/{id}/desabilitar-emissora")
    public com.jetski.tenant.api.dto.EmissoraStatusResult desabilitarEmissora(@PathVariable("id") UUID id) {
        return platformTenantService.desabilitarEmissora(id);
    }

    /**
     * Dry-run do reset: contagem por tabela do que o nível apagaria.
     * Ação OPA: {@code platform:reset-preview} (só super admin).
     */
    @GetMapping("/tenants/{id}/reset-preview")
    public java.util.Map<String, Long> resetPreview(
            @PathVariable("id") UUID id,
            @RequestParam("nivel") com.jetski.tenant.internal.TenantResetService.Nivel nivel) {
        return tenantResetService.preview(id, nivel);
    }

    /**
     * RESET da empresa (zona de perigo): zera os dados do nível escolhido,
     * preservando tenant/assinatura/créditos/metering/auditoria. Exige o slug
     * digitado. Ação OPA: {@code platform:reset} (só super admin).
     */
    @PostMapping("/tenants/{id}/reset")
    public java.util.Map<String, Object> reset(
            @PathVariable("id") UUID id,
            @jakarta.validation.Valid @RequestBody com.jetski.tenant.api.dto.ResetTenantRequest body) {
        var resultado = tenantResetService.reset(id, body.nivel(), body.confirmacaoSlug());
        return java.util.Map.of(
            "nivel", body.nivel().name(),
            "apagados", resultado.apagados(),
            "totalLinhas", resultado.apagados().values().stream().mapToLong(Long::longValue).sum(),
            "exportKey", resultado.exportKey(),
            "exportBytes", resultado.exportBytes());
    }

    /**
     * EXCLUSÃO da empresa. CARENCIA: suspende agora + expurgo em D+30 (job
     * diário, cancelável). IMEDIATO: expurga na hora. Export de arquivamento
     * sempre acontece antes do expurgo. Ação OPA: {@code platform:excluir}.
     */
    @PostMapping("/tenants/{id}/excluir")
    public java.util.Map<String, Object> excluir(
            @PathVariable("id") UUID id,
            @jakarta.validation.Valid @RequestBody com.jetski.tenant.api.dto.ExcluirTenantRequest body) {
        if (body.modo() == com.jetski.tenant.api.dto.ExcluirTenantRequest.Modo.IMEDIATO) {
            var apagados = tenantExclusaoService.excluirAgora(id, body.confirmacaoSlug());
            return java.util.Map.of(
                "modo", "IMEDIATO",
                "apagados", apagados,
                "totalLinhas", apagados.values().stream().mapToLong(Long::longValue).sum());
        }
        java.time.Instant quando = tenantExclusaoService.agendar(id, body.confirmacaoSlug());
        return java.util.Map.of("modo", "CARENCIA", "expurgoEm", quando.toString());
    }

    /** Cancela uma exclusão agendada (empresa segue SUSPENSA). Ação OPA: {@code platform:cancelar-exclusao}. */
    @PostMapping("/tenants/{id}/cancelar-exclusao")
    public java.util.Map<String, String> cancelarExclusao(@PathVariable("id") UUID id) {
        tenantExclusaoService.cancelar(id);
        return java.util.Map.of("status", "cancelada");
    }

    /**
     * Gera sob demanda o export de arquivamento da empresa (.zip com dados
     * JSON + arquivos do storage). Ação OPA: {@code platform:export}.
     */
    @PostMapping("/tenants/{id}/export")
    public com.jetski.tenant.internal.TenantExportService.Export export(@PathVariable("id") UUID id) {
        return tenantExportService.exportar(id);
    }

    /** Lista os exports já gerados da empresa. Ação OPA: {@code platform:exports}. */
    @GetMapping("/tenants/{id}/exports")
    public java.util.List<String> exports(@PathVariable("id") UUID id) {
        return tenantExportService.listar(id);
    }

    /** Download de um export (.zip). Ação OPA: {@code platform:download}. */
    @GetMapping("/tenants/{id}/exports/download")
    public org.springframework.http.ResponseEntity<byte[]> downloadExport(
            @PathVariable("id") UUID id, @RequestParam("key") String key) {
        byte[] zip = tenantExportService.baixar(id, key);
        String nome = key.substring(key.lastIndexOf('/') + 1);
        return org.springframework.http.ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + nome + "\"")
            .header("Content-Type", "application/zip")
            .body(zip);
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
