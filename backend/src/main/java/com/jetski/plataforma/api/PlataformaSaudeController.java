package com.jetski.plataforma.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saúde da plataforma (F5): infraestrutura + sinais de negócio que envelhecem em silêncio.
 *
 * <p>Duas fontes, de propósito:
 *
 * <ul>
 *   <li><strong>Infra</strong> — reusa o {@code HealthEndpoint} do Actuator (banco, Redis,
 *       disco…), em vez de reimplementar pings. O actuator não é exposto no edge (o nginx
 *       devolve 404), então este endpoint é a única porta para esse dado.</li>
 *   <li><strong>Operação</strong> — o que "para de acontecer" sem ninguém notar: o job do
 *       read model que não rodou, a emissão à Marinha que travou, a fila de conferência
 *       que cresceu. Verde na infra e produto parado é o cenário que o Grafana sozinho
 *       não conta.</li>
 * </ul>
 *
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/platform/saude")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlataformaSaudeController {

    private final HealthEndpoint healthEndpoint;
    private final JdbcTemplate jdbc;

    /**
     * @param infra      status por componente do Actuator (UP/DOWN)
     * @param operacao   sinais de negócio (última emissão, filas, frescor do read model)
     * @param statusGeral UP quando toda a infra está UP
     */
    public record SaudeResponse(
        String statusGeral,
        Map<String, String> infra,
        Map<String, Object> operacao) {}

    @GetMapping
    @Operation(summary = "Saúde da plataforma (infra + operação)",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<SaudeResponse> saude() {
        Map<String, String> infra = new LinkedHashMap<>();
        String geral;
        try {
            HealthComponent raiz = healthEndpoint.health();
            geral = raiz.getStatus().getCode();
            if (raiz instanceof org.springframework.boot.actuate.health.CompositeHealth composto) {
                composto.getComponents().forEach((nome, c) -> infra.put(nome, c.getStatus().getCode()));
            }
        } catch (Exception e) {
            // Saúde que derruba a tela de saúde não serve para nada.
            log.warn("[SAUDE] Falha ao consultar o HealthEndpoint: {}", e.getMessage());
            geral = "UNKNOWN";
        }

        Map<String, Object> operacao = new LinkedHashMap<>();
        operacao.put("readModel", umaLinha("""
            SELECT MAX(atualizado_em) AS atualizado_em,
                   MAX(dia) AS ultimo_dia,
                   count(*) AS linhas
              FROM plataforma_metrica_diaria
            """));
        operacao.put("emissao", umaLinha("""
            SELECT MAX(ocorrido_em) FILTER (WHERE tipo = 'DOCUMENTO') AS ultimo_documento,
                   MAX(ocorrido_em) FILTER (WHERE tipo = 'GRU') AS ultima_gru,
                   count(*) FILTER (WHERE ocorrido_em > now() - interval '7 days') AS ultimos_7_dias
              FROM emissao_uso
            """));
        operacao.put("filas", umaLinha("""
            SELECT (SELECT count(*) FROM tenant WHERE status = 'PENDENTE_APROVACAO')
                       AS empresas_aguardando_aprovacao,
                   (SELECT count(*) FROM credito_compra WHERE status = 'PENDENTE')
                       AS compras_de_credito_pendentes,
                   (SELECT count(*) FROM fatura WHERE status = 'EM_CONFERENCIA')
                       AS faturas_em_conferencia
            """));
        operacao.put("suporte", umaLinha("""
            SELECT count(*) FILTER (WHERE encerrada_em IS NULL AND expira_em > now()
                                      AND token_hash IS NOT NULL) AS sessoes_ativas,
                   MAX(iniciada_em) AS ultima_sessao
              FROM plataforma_sessao_suporte
            """));

        return ResponseEntity.ok(new SaudeResponse(geral, infra, operacao));
    }

    /**
     * Consulta que nunca derruba a tela: erro vira {@code {"erro": "..."}} no bloco, e os
     * demais indicadores continuam aparecendo. Uma tabela ausente (ambiente sem migration
     * nova) não pode apagar a página inteira.
     */
    private Map<String, Object> umaLinha(String sql) {
        try {
            List<Map<String, Object>> l = jdbc.queryForList(sql);
            return l.isEmpty() ? Map.of() : l.get(0);
        } catch (Exception e) {
            log.warn("[SAUDE] Indicador indisponível: {}", e.getMessage());
            return Map.of("erro", String.valueOf(e.getMessage()));
        }
    }
}
