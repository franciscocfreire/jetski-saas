package com.jetski.tenant.api;

import com.jetski.tenant.domain.Fatura;
import com.jetski.tenant.internal.PlatformFaturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Billing na visão da PLATAFORMA (super admin): fila de conferência,
 * confirmação/cancelamento, geração manual e troca de plano. Autorização
 * via OPA {@code platform:*} (só {@code unrestricted_access}) — mesmo
 * modelo do {@link PlatformTenantController}.
 */
@RestController
@RequestMapping("/v1/platform")
@RequiredArgsConstructor
public class PlatformFaturaController {

    private final PlatformFaturaService platformFaturaService;

    /** Fila global: faturas EM_CONFERENCIA de todos os tenants. */
    @GetMapping("/faturas/pendentes")
    public List<Map<String, Object>> pendentes() {
        return platformFaturaService.pendentesConferencia().stream()
            .map(p -> Map.<String, Object>of(
                "fatura", p.fatura(),
                "tenantId", p.fatura().getTenantId().toString(),
                "slug", p.slug(),
                "razaoSocial", p.razaoSocial()))
            .toList();
    }

    /** Gera manualmente as faturas do mês (o job diário também faz). */
    @PostMapping("/faturas/gerar")
    public Map<String, Integer> gerar() {
        return Map.of("criadas", platformFaturaService.gerarFaturasDoMes());
    }

    /** Confirma o pagamento conferido no extrato → PAGA. */
    @PostMapping("/faturas/{tenantId}/{faturaId}/confirmar")
    public Fatura confirmar(@PathVariable UUID tenantId, @PathVariable UUID faturaId) {
        return platformFaturaService.confirmar(tenantId, faturaId);
    }

    /** Cancela a fatura (cortesia/erro) — observação obrigatória. */
    @PostMapping("/faturas/{tenantId}/{faturaId}/cancelar")
    public Fatura cancelar(@PathVariable UUID tenantId, @PathVariable UUID faturaId,
                           @RequestBody Map<String, String> body) {
        return platformFaturaService.cancelar(tenantId, faturaId, body.get("observacao"));
    }

    /** Troca o plano do tenant (contratação pós-trial / upgrade / downgrade). */
    @PostMapping("/tenants/{tenantId}/plano")
    public Map<String, String> mudarPlano(@PathVariable UUID tenantId,
                                          @RequestBody Map<String, String> body) {
        platformFaturaService.mudarPlano(tenantId, Integer.valueOf(body.get("planoId")));
        return Map.of("status", "alterado");
    }

    /** Planos ativos (para o seletor do painel). */
    @GetMapping("/planos")
    public List<Map<String, Object>> planos() {
        return platformFaturaService.planosAtivos();
    }

    /** Catálogo de módulos gateáveis (grade módulos × planos). */
    @GetMapping("/modulos")
    public List<Map<String, String>> modulos() {
        return java.util.Arrays.stream(com.jetski.tenant.ModuloPlano.values())
            .map(m -> Map.of("key", m.name(), "rotulo", m.rotulo(), "descricao", m.descricao()))
            .toList();
    }

    /** Define os módulos incluídos no plano (controle de oferta). */
    @PutMapping("/planos/{planoId}/modulos")
    public Map<String, String> salvarModulos(@PathVariable Integer planoId,
                                             @RequestBody Map<String, List<String>> body) {
        platformFaturaService.salvarModulos(planoId, body.get("modulos"));
        return Map.of("status", "salvo");
    }
}
