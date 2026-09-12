package com.jetski.signup.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Quem pediu o cadastro da empresa, visto pelo console da plataforma.
 *
 * <p>No signup público o tenant nasce na hora, mas {@code usuario}/{@code membro} só
 * existem depois que a pessoa abre o link de ativação. Até lá (ou para sempre, se o
 * e-mail foi digitado errado) a empresa aparece sem nenhum usuário, e o único registro
 * do dono é {@code tenant_signup}, que pertence a este módulo.
 *
 * <p>Nunca devolve {@code token} nem {@code temporary_password}: com eles o operador
 * poderia ativar a conta no lugar do dono.
 *
 * <p>Empresa criada por usuário já cadastrado não tem {@code tenant_signup}: o dono entra
 * direto como {@code membro} ADMIN_TENANT, e a lista de usuários já mostra essa pessoa.
 */
@Service
@RequiredArgsConstructor
public class PlatformSignupService {

    private final JdbcTemplate jdbc;

    /** Situação derivada: o status EXPIRED só é gravado quando alguém tenta ativar tarde. */
    public enum Situacao { AGUARDANDO_ATIVACAO, LINK_EXPIRADO, ATIVADO }

    /**
     * @param nome      nome informado no pedido
     * @param email     e-mail informado no pedido (para onde o link foi)
     * @param situacao  aguardando ativação, link expirado ou ativado
     * @param pedidoEm  quando o pedido foi feito
     * @param expiraEm  validade do link de ativação
     * @param ativadoEm quando a conta foi ativada (nulo se nunca)
     */
    public record SolicitacaoCadastro(
        UUID id, String nome, String email, Situacao situacao,
        Instant pedidoEm, Instant expiraEm, Instant ativadoEm) {}

    /** Pedidos de cadastro da empresa, mais recente primeiro (lista vazia se não houve). */
    @Transactional(readOnly = true)
    public List<SolicitacaoCadastro> listar(UUID tenantId) {
        // Filtro por tenant explícito: tenant_signup não tem RLS (regra nº 1).
        return jdbc.query("""
            SELECT id, nome, email, created_at, expires_at, activated_at,
                   CASE
                     WHEN status = 'ACTIVATED' THEN 'ATIVADO'
                     WHEN status = 'EXPIRED' OR expires_at < now() THEN 'LINK_EXPIRADO'
                     ELSE 'AGUARDANDO_ATIVACAO'
                   END AS situacao
            FROM tenant_signup
            WHERE tenant_id = ?
            ORDER BY created_at DESC
            """,
            (rs, n) -> new SolicitacaoCadastro(
                rs.getObject("id", UUID.class),
                rs.getString("nome"),
                rs.getString("email"),
                Situacao.valueOf(rs.getString("situacao")),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("expires_at")),
                instant(rs.getTimestamp("activated_at"))),
            tenantId);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
