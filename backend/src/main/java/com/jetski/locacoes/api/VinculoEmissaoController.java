package com.jetski.locacoes.api;

import com.jetski.locacoes.api.dto.ConviteVinculoRequest;
import com.jetski.locacoes.api.dto.VinculoEmissaoResponse;
import com.jetski.locacoes.domain.VinculoEmissao;
import com.jetski.locacoes.domain.VinculoInstrutorOperadora;
import com.jetski.locacoes.internal.VinculoEmissaoService;
import com.jetski.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parceria de emissão delegada (EMISSAO_DELEGADA_SPEC §6, V048).
 *
 * <p>Gestão do vínculo é do ADMIN_TENANT (OPA: wildcard do papel); a lista de
 * instrutores do parceiro e o modo de emissão são liberados também ao staff que
 * emite (OPERADOR/GERENTE via rego). Aprovação dos instrutores da operadora
 * (V070) é da EAMA: ADMIN_TENANT ou GERENTE, como a designação.
 *
 * @author Jetski Team
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/vinculos-emissao")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Emissão delegada", description = "Parceria operadora × EAMA emissora")
public class VinculoEmissaoController {

    private final VinculoEmissaoService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Lista as parcerias de emissão do tenant (como operadora ou emissora)")
    public List<VinculoEmissaoResponse> listar(@PathVariable UUID tenantId) {
        return service.listar(tenantId).stream()
            .map(v -> VinculoEmissaoResponse.of(v, tenantId, nomeDoParceiro(v, tenantId)))
            .toList();
    }

    @GetMapping("/termo")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Texto vigente do termo de responsabilidade da parceria")
    public Map<String, String> termo(@PathVariable UUID tenantId) {
        return Map.of("termo", VinculoEmissaoService.TERMO_RESPONSABILIDADE);
    }

    /** Modo de emissão (§8.M): a parceria em vigor como operadora manda, não o plano. */
    @GetMapping("/modo")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Modo de emissão da empresa: PROPRIA, DELEGADA ou SEM_EMISSAO")
    public VinculoEmissaoService.ModoEmissaoInfo modo(@PathVariable UUID tenantId) {
        return service.modoEmissao(tenantId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Convida uma empresa para parceria de emissão (bilateral: exige aceite do outro lado)")
    public VinculoEmissaoResponse convidar(@PathVariable UUID tenantId,
                                           @RequestBody ConviteVinculoRequest body) {
        VinculoEmissaoService.PapelConvite papel = parsePapel(body.papel());
        VinculoEmissao v = service.convidar(tenantId, body.parceiroSlug(), papel);
        return VinculoEmissaoResponse.of(v, tenantId, nomeDoParceiro(v, tenantId));
    }

    @PostMapping("/{id}/aceitar")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Aceita o convite (lado convidado), assinando o termo de responsabilidade")
    public VinculoEmissaoResponse aceitar(@PathVariable UUID tenantId,
                                          @PathVariable("id") UUID id,
                                          @RequestBody Map<String, Boolean> body) {
        boolean termoAceito = Boolean.TRUE.equals(body.get("termoAceito"));
        VinculoEmissao v = service.aceitar(tenantId, id, termoAceito);
        return VinculoEmissaoResponse.of(v, tenantId, nomeDoParceiro(v, tenantId));
    }

    @PostMapping("/{id}/bloquear")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Kill switch da EAMA: bloqueia novas emissões em seu nome (efeito imediato)")
    public VinculoEmissaoResponse bloquear(@PathVariable UUID tenantId, @PathVariable("id") UUID id) {
        VinculoEmissao v = service.bloquear(tenantId, id);
        return VinculoEmissaoResponse.of(v, tenantId, nomeDoParceiro(v, tenantId));
    }

    @PostMapping("/{id}/liberar")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Libera a parceria bloqueada (EAMA)")
    public VinculoEmissaoResponse liberar(@PathVariable UUID tenantId, @PathVariable("id") UUID id) {
        VinculoEmissao v = service.liberar(tenantId, id);
        return VinculoEmissaoResponse.of(v, tenantId, nomeDoParceiro(v, tenantId));
    }

    @PostMapping("/{id}/revogar")
    @PreAuthorize("hasRole('ADMIN_TENANT')")
    @Operation(summary = "Revoga a parceria (qualquer lado, terminal; não devolve bônus estornado)")
    public VinculoEmissaoResponse revogar(@PathVariable UUID tenantId, @PathVariable("id") UUID id) {
        VinculoEmissao v = service.revogar(tenantId, id);
        return VinculoEmissaoResponse.of(v, tenantId, nomeDoParceiro(v, tenantId));
    }

    /** Instrutores designados da parceria (V049); visível aos dois lados. */
    @GetMapping("/{id}/instrutores-designados")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Instrutores da EAMA designados para esta parceria (vazio = nenhum)")
    public List<Map<String, Object>> instrutoresDesignados(@PathVariable UUID tenantId,
                                                           @PathVariable("id") UUID id) {
        return service.listarDesignados(tenantId, id).stream()
            .map(r -> Map.<String, Object>of("id", r[0], "nome", r[1]))
            .toList();
    }

    /** Substitui o conjunto de designados (só a EAMA; lista vazia = nenhum instrutor da EAMA). */
    @PutMapping("/{id}/instrutores-designados")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Designa quais instrutores da EAMA atendem esta parceria")
    public List<Map<String, Object>> designarInstrutores(@PathVariable UUID tenantId,
                                                         @PathVariable("id") UUID id,
                                                         @RequestBody Map<String, List<UUID>> body) {
        List<UUID> ids = body != null ? body.get("instrutorIds") : null;
        return service.designarInstrutores(tenantId, id, ids).stream()
            .map(r -> Map.<String, Object>of("id", r[0], "nome", r[1]))
            .toList();
    }

    /**
     * id + nome + origem dos instrutores disponíveis na emissão delegada: os da EAMA
     * (designados) e os da operadora aprovados pela EAMA (exposição mínima — LGPD §5.4).
     */
    @GetMapping("/instrutores-parceiro")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR')")
    @Operation(summary = "Instrutores para a emissão delegada (da EAMA ou da operadora aprovados pela EAMA)")
    public List<Map<String, Object>> instrutoresParceiro(@PathVariable UUID tenantId) {
        return service.instrutoresDoParceiro(tenantId).stream()
            .map(r -> Map.<String, Object>of("id", r[0], "nome", r[1],
                "origem", r.length > 2 && r[2] != null ? r[2] : "EAMA"))
            .toList();
    }

    // ==================== instrutores da operadora (V070) ====================

    /** Instrutores que a operadora submeteu à parceria, com status da aprovação. */
    @GetMapping("/{id}/instrutores-operadora")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Instrutores da operadora submetidos à aprovação da EAMA nesta parceria")
    public List<VinculoEmissaoService.InstrutorOperadoraInfo> instrutoresOperadora(
            @PathVariable UUID tenantId, @PathVariable("id") UUID id) {
        return service.listarInstrutoresOperadora(tenantId, id);
    }

    /** A operadora pede à EAMA a aprovação de um instrutor próprio. */
    @PostMapping("/instrutores-proprios/{instrutorId}/solicitar-aprovacao")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "Operadora submete um instrutor próprio à aprovação da EAMA parceira")
    public Map<String, Object> solicitarAprovacao(@PathVariable UUID tenantId,
                                                  @PathVariable UUID instrutorId) {
        return resposta(service.solicitarAprovacaoInstrutor(tenantId, instrutorId));
    }

    /**
     * EAMA decide sobre um instrutor da operadora: {@code {"decisao": "APROVAR|REJEITAR|REMOVER",
     * "motivo": "..."}}. Um endpoint só: o ActionExtractor casa o último segmento, e
     * "rejeitar"/"remover" já nomeiam rotas de outros recursos.
     */
    @PostMapping("/{id}/instrutores-operadora/{instrutorId}/decisao")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE')")
    @Operation(summary = "EAMA aprova, rejeita ou remove um instrutor da operadora")
    public Map<String, Object> decidirInstrutor(@PathVariable UUID tenantId, @PathVariable("id") UUID id,
                                                @PathVariable UUID instrutorId,
                                                @RequestBody Map<String, String> body) {
        VinculoEmissaoService.DecisaoInstrutor decisao;
        try {
            decisao = VinculoEmissaoService.DecisaoInstrutor.valueOf(body.get("decisao").trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException("Decisão inválida: informe APROVAR, REJEITAR ou REMOVER");
        }
        return resposta(service.decidirInstrutor(tenantId, id, instrutorId, decisao, body.get("motivo")));
    }

    private static Map<String, Object> resposta(VinculoInstrutorOperadora a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vinculoId", a.getVinculoId());
        m.put("instrutorId", a.getInstrutorId());
        m.put("status", a.getStatus().name());
        m.put("solicitadoEm", a.getSolicitadoEm());
        m.put("decididoEm", a.getDecididoEm());
        m.put("motivo", a.getMotivo());
        return m;
    }

    private String nomeDoParceiro(VinculoEmissao v, UUID tenantId) {
        UUID parceiro = tenantId.equals(v.getTenantOperadorId())
            ? v.getTenantEmissorId() : v.getTenantOperadorId();
        return service.nomeDoTenant(parceiro);
    }

    private static VinculoEmissaoService.PapelConvite parsePapel(String papel) {
        try {
            return VinculoEmissaoService.PapelConvite.valueOf(papel.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException("Papel inválido no convite: informe OPERADORA ou EMISSORA");
        }
    }
}
