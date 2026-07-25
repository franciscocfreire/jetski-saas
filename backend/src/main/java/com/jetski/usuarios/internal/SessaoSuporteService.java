package com.jetski.usuarios.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.SessaoSuporte;
import com.jetski.shared.security.SessaoSuporteValidator;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.event.SessaoSuporteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sessão de suporte (F3): acesso explícito, com motivo e prazo, de um operador de
 * plataforma a uma empresa.
 *
 * <p>Substitui o god mode implícito — trocar de empresa no switcher e operar como membro,
 * sem motivo, sem prazo e sem trilha. Três garantias:
 *
 * <ul>
 *   <li><strong>Motivo obrigatório</strong> (mín. 5 caracteres, CHECK no banco também);</li>
 *   <li><strong>Prazo curto</strong> — 30 minutos, sem renovação por uso: acabou, abre outra;</li>
 *   <li><strong>Somente leitura por padrão</strong> — escrita é escolha consciente.</li>
 * </ul>
 *
 * <p><strong>Handoff console → backoffice por CÓDIGO de uso único.</strong> O console vive
 * em {@code admin.*} e o backoffice em {@code app.*}: cookie não atravessa. O que trafega na
 * URL é um código com minutos de vida, trocado uma única vez pelo cookie de sessão. O token
 * do cookie NUNCA vai na URL — URL vaza em log de proxy, Referer e histórico.
 *
 * <p><strong>Sem Redis, só Postgres</strong> (a spec cogitava os dois): o TTL já está em
 * {@code expira_em} e a revogação precisa valer no mesmo instante — cache de sessão de
 * suporte seria uma janela em que um acesso revogado ainda funciona. É uma consulta por PK
 * indexada por request; o custo não justifica o risco.
 *
 * @since 0.9.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessaoSuporteService implements SessaoSuporteValidator {

    /** Vida do código de handoff: tempo de clicar, não de guardar. */
    private static final Duration VALIDADE_CODIGO = Duration.ofMinutes(2);
    /** Vida da sessão. Sem sliding window: renovar por uso anularia o prazo. */
    private static final Duration VALIDADE_SESSAO = Duration.ofMinutes(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param sessaoId id da sessão criada
     * @param codigo   código de uso único (mostrar só ao console; some depois do resgate)
     * @param expiraEm fim da sessão, se o código for resgatado
     */
    public record Abertura(UUID sessaoId, String codigo, Instant expiraEm) {}

    /** Uma sessão na trilha (para o painel do console e o histórico da empresa). */
    public record Registro(
        UUID id, UUID tenantId, String tenantSlug, UUID operadorId, String operadorEmail,
        String motivo, boolean somenteLeitura,
        Instant iniciadaEm, Instant expiraEm, Instant encerradaEm) {

        /** Ativa = resgatada, no prazo e não encerrada. */
        public boolean ativa() {
            return encerradaEm == null && expiraEm.isAfter(Instant.now());
        }
    }

    /**
     * Abre uma sessão para a empresa alvo e devolve o código de handoff.
     *
     * <p>Não dá acesso sozinha: enquanto o código não for resgatado no backoffice, não
     * existe cookie nem token — a linha fica registrada como uma intenção declarada.
     */
    @Transactional
    public Abertura abrir(UUID tenantId, String motivo, boolean somenteLeitura, String ip,
                          String userAgent) {
        UUID operadorId = TenantContext.getUsuarioId();
        if (operadorId == null) {
            throw new BusinessException("Operador não identificado na sessão.");
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new BusinessException(
                "Descreva o motivo do acesso (mínimo 5 caracteres). Ele fica na trilha "
                + "e a empresa pode consultá-lo.");
        }
        Integer existe = jdbc.queryForObject(
            "SELECT count(*) FROM tenant WHERE id = ?", Integer.class, tenantId);
        if (existe == null || existe == 0) {
            throw new NotFoundException("Empresa não encontrada: " + tenantId);
        }

        String codigo = novoSegredo();
        Instant agora = Instant.now();
        UUID id = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO plataforma_sessao_suporte
                (id, operador_id, tenant_id, motivo, somente_leitura,
                 codigo_hash, codigo_expira_em, iniciada_em, expira_em, ip, user_agent)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?)
            """,
            id, operadorId, tenantId, motivo.trim(), somenteLeitura,
            sha256(codigo), java.sql.Timestamp.from(agora.plus(VALIDADE_CODIGO)),
            java.sql.Timestamp.from(agora),
            java.sql.Timestamp.from(agora.plus(VALIDADE_SESSAO)), ip, userAgent);

        eventPublisher.publishEvent(SessaoSuporteEvent.aberta(
            id, tenantId, operadorId, motivo.trim(), somenteLeitura));
        log.warn("[SUPORTE] Sessão aberta: sessao={}, tenant={}, operador={}, leitura={}, motivo={}",
            id, tenantId, operadorId, somenteLeitura, motivo.trim());

        return new Abertura(id, codigo, agora.plus(VALIDADE_SESSAO));
    }

    /**
     * Troca o código de uso único pelo token do cookie.
     *
     * <p>O UPDATE condicional é o que garante o uso único: dois resgates simultâneos, só um
     * casa {@code codigo_usado_em IS NULL} e afeta linha.
     *
     * @return token do cookie (só aqui ele existe em claro)
     */
    @Transactional
    public String resgatar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new BusinessException("Código de suporte ausente.");
        }
        UUID quemResgata = TenantContext.getUsuarioId();
        if (quemResgata == null) {
            throw new BusinessException("Autentique-se antes de resgatar o código de suporte.");
        }
        String token = novoSegredo();
        // operador_id na cláusula: código só vale para QUEM abriu. Vazamento por URL,
        // Referer ou log de proxy não vira acesso na mão de outra pessoa.
        int afetadas = jdbc.update("""
            UPDATE plataforma_sessao_suporte
               SET codigo_usado_em = now(), token_hash = ?
             WHERE codigo_hash = ?
               AND operador_id = ?
               AND codigo_usado_em IS NULL
               AND codigo_expira_em > now()
               AND encerrada_em IS NULL
            """, sha256(token), sha256(codigo), quemResgata);

        if (afetadas == 0) {
            throw new BusinessException(
                "Código de suporte inválido, expirado, já utilizado ou de outro operador. "
                + "Abra a sessão novamente pelo console.");
        }
        log.info("[SUPORTE] Código resgatado — sessão ativa");
        return token;
    }

    /**
     * Valida o cookie a cada request. Sem cache: revogação precisa valer na hora.
     */
    @Override
    @Transactional(readOnly = true)
    public SessaoSuporte validar(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        List<Map<String, Object>> linhas = jdbc.queryForList("""
            SELECT id, operador_id, tenant_id, somente_leitura
              FROM plataforma_sessao_suporte
             WHERE token_hash = ?
               AND encerrada_em IS NULL
               AND expira_em > now()
            """, sha256(token));
        if (linhas.isEmpty()) {
            return null;
        }
        Map<String, Object> l = linhas.get(0);
        return new SessaoSuporte(
            (UUID) l.get("id"), (UUID) l.get("operador_id"), (UUID) l.get("tenant_id"),
            Boolean.TRUE.equals(l.get("somente_leitura")));
    }

    /** Encerra a sessão (botão do banner, ou revogação por outro operador). */
    @Transactional
    public void encerrar(UUID sessaoId) {
        UUID quem = TenantContext.getUsuarioId();
        int n = jdbc.update("""
            UPDATE plataforma_sessao_suporte
               SET encerrada_em = now(), encerrada_por = ?
             WHERE id = ? AND encerrada_em IS NULL
            """, quem, sessaoId);
        if (n > 0) {
            eventPublisher.publishEvent(SessaoSuporteEvent.encerrada(sessaoId, quem));
            log.warn("[SUPORTE] Sessão encerrada: sessao={}, por={}", sessaoId, quem);
        }
    }

    /** Trilha: últimas sessões, opcionalmente de uma empresa. */
    @Transactional(readOnly = true)
    public List<Registro> listar(UUID tenantId, int limite) {
        String sql = """
            SELECT s.id, s.tenant_id, t.slug, s.operador_id, u.email, s.motivo,
                   s.somente_leitura, s.iniciada_em, s.expira_em, s.encerrada_em
              FROM plataforma_sessao_suporte s
              JOIN tenant t ON t.id = s.tenant_id
              LEFT JOIN usuario u ON u.id = s.operador_id
             WHERE (?::uuid IS NULL OR s.tenant_id = ?::uuid)
             ORDER BY s.iniciada_em DESC
             LIMIT ?
            """;
        return jdbc.query(sql, (rs, n) -> new Registro(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
            rs.getString("slug"), rs.getObject("operador_id", UUID.class),
            rs.getString("email"), rs.getString("motivo"),
            rs.getBoolean("somente_leitura"),
            rs.getTimestamp("iniciada_em").toInstant(),
            rs.getTimestamp("expira_em").toInstant(),
            rs.getTimestamp("encerrada_em") == null
                ? null : rs.getTimestamp("encerrada_em").toInstant()),
            tenantId, tenantId, limite);
    }

    // ------------------------------------------------------------------ segredos

    /** 32 bytes de entropia, base64url — o que vai no cookie/código. */
    private static String novoSegredo() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** O banco guarda só o hash: vazamento de dump não entrega sessão viva. */
    static String sha256(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
