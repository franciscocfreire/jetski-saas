package com.jetski.usuarios.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Usuários (staff) de uma empresa, vistos pelo console da plataforma.
 *
 * <p>Somente leitura, de propósito: papéis, convites e desativação continuam sendo
 * decisão da própria empresa (tela de usuários do backoffice). O operador precisa
 * <em>enxergar</em> quem opera a EAMA para dar suporte — quem é o administrador, se a
 * conta está bloqueada, se o e-mail foi verificado — sem abrir sessão de suporte.
 *
 * <p>Não reaproveita {@code MemberManagementService.listMembers}: aquele método consulta
 * {@code assinatura} (com RLS) para o limite do plano, e no escopo de plataforma não há
 * {@code app.tenant_id} na transação — a consulta falharia e envenenaria a transação.
 * {@code membro} e {@code usuario} não têm RLS, então o filtro por tenant aqui é o
 * único escopo — e é explícito (regra nº 1).
 */
@Service
@RequiredArgsConstructor
public class PlatformMembroService {

    private final JdbcTemplate jdbc;

    /**
     * @param usuarioId       id da identidade global
     * @param nome            nome exibido
     * @param email           e-mail de login
     * @param telefone        telefone do perfil (pode ser nulo)
     * @param papeis          papéis NA EMPRESA (ADMIN_TENANT, GERENTE, ...)
     * @param ativo           vínculo com a empresa ativo (membro.ativo)
     * @param contaAtiva      conta global ativa (usuario.ativo) — falso bloqueia em TODAS as empresas
     * @param emailVerificado e-mail verificado na identidade
     * @param desde           quando o vínculo com a empresa foi criado
     */
    public record MembroEmpresa(
        UUID usuarioId, String nome, String email, String telefone, List<String> papeis,
        boolean ativo, boolean contaAtiva, boolean emailVerificado, Instant desde) {}

    /** Membros da empresa (ativos e inativos): ativos primeiro, depois por nome. */
    @Transactional(readOnly = true)
    public List<MembroEmpresa> listar(UUID tenantId) {
        return jdbc.query("""
            SELECT u.id, u.nome, u.email, u.telefone, m.papeis, m.ativo,
                   u.ativo AS conta_ativa, u.email_verified, m.created_at
            FROM membro m
            JOIN usuario u ON u.id = m.usuario_id
            WHERE m.tenant_id = ?
            ORDER BY m.ativo DESC, lower(coalesce(u.nome, u.email))
            """,
            (rs, n) -> new MembroEmpresa(
                rs.getObject("id", UUID.class),
                rs.getString("nome"),
                rs.getString("email"),
                rs.getString("telefone"),
                papeis(rs.getArray("papeis")),
                rs.getBoolean("ativo"),
                rs.getBoolean("conta_ativa"),
                rs.getBoolean("email_verified"),
                rs.getTimestamp("created_at").toInstant()),
            tenantId);
    }

    private static List<String> papeis(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
