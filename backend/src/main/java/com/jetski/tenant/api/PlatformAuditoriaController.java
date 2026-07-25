package com.jetski.tenant.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trilha GLOBAL da plataforma (F5): quem concedeu acesso, quem entrou em qual empresa,
 * quem aprovou o quê.
 *
 * <p>Lê apenas linhas sem tenant ({@code tenant_id IS NULL}) — o audit dual grava a mesma
 * ação duas vezes, uma na empresa (que ela pode consultar) e uma global (esta). Por isso
 * o console não precisa varrer empresa a empresa nem furar RLS de dado alheio.
 *
 * <p>A leitura só existe desde a V057: até então a trilha global era gravada e ninguém
 * conseguia lê-la pela aplicação.
 *
 * @since 0.9.0
 */
@RestController
@RequestMapping("/v1/platform/auditoria")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Operação da plataforma (super admin)")
public class PlatformAuditoriaController {

    private static final Set<String> COLUNAS_JSON = Set.of("dados_anteriores", "dados_novos");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "Trilha global de ações de plataforma",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(required = false) String acao,
            @RequestParam(defaultValue = "100") int limite) {

        StringBuilder sql = new StringBuilder("""
            SELECT a.id, a.acao, a.entidade, a.entidade_id, a.usuario_id,
                   u.email AS usuario_email,
                   a.dados_anteriores::text AS dados_anteriores,
                   a.dados_novos::text      AS dados_novos,
                   a.ip, a.created_at
              FROM auditoria a
              LEFT JOIN usuario u ON u.id = a.usuario_id
             WHERE a.tenant_id IS NULL
            """);
        List<Object> args = new ArrayList<>();
        if (acao != null && !acao.isBlank()) {
            sql.append(" AND a.acao = ?");
            args.add(acao.trim());
        }
        sql.append(" ORDER BY a.created_at DESC LIMIT ?");
        args.add(Math.min(Math.max(limite, 1), 500));

        List<Map<String, Object>> linhas = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> l : linhas) {
            l.replaceAll((coluna, valor) -> COLUNAS_JSON.contains(coluna) ? comoJson(valor) : valor);
        }
        return ResponseEntity.ok(linhas);
    }

    /**
     * As colunas {@code jsonb} vêm como texto (cast no SELECT) e voltam a ser JSON aqui.
     *
     * <p>Sem isso o driver entrega um {@code PGobject} e o Jackson serializa o
     * <em>wrapper</em>: o console recebia {@code {"type":"jsonb","value":"{…}"}} com o
     * conteúdo escapado dentro de uma string — o "antes e depois" da trilha ficava ilegível.
     * O cast evita depender do driver em tempo de compilação (ele é {@code runtime}).
     */
    private Object comoJson(Object valor) {
        if (!(valor instanceof String texto) || texto.isBlank()) {
            return valor;
        }
        try {
            return objectMapper.readTree(texto);
        } catch (JsonProcessingException e) {
            // Conteúdo inválido não pode derrubar a trilha inteira: devolve como texto.
            return texto;
        }
    }

    /** Ações distintas presentes na trilha — alimenta o filtro sem lista chumbada. */
    @GetMapping("/acoes")
    @Operation(summary = "Ações distintas da trilha global",
        security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<List<String>> acoes() {
        return ResponseEntity.ok(jdbc.queryForList(
            "SELECT DISTINCT acao FROM auditoria WHERE tenant_id IS NULL ORDER BY acao",
            String.class));
    }
}
