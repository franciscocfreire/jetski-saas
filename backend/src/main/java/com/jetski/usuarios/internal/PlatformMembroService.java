package com.jetski.usuarios.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.usuarios.api.dto.ConviteSummaryDTO;
import com.jetski.usuarios.api.dto.InviteUserRequest;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Usuários (staff) de uma empresa, geridos pelo console da plataforma.
 *
 * <p>Leitura para qualquer operador; escrita (desativar, reativar, remover, convidar e
 * cancelar convite) para PLATFORM_ADMIN e PLATFORM_SUPORTE — a matriz está no
 * {@code platform.rego}. Toda escrita exige motivo e vai para a trilha da empresa.
 *
 * <p><b>Escopo RLS</b>: a rota de plataforma não tem tenant na sessão. As regras
 * reaproveitadas da própria empresa ({@code MemberManagementService},
 * {@code UserInvitationService}, {@code ConviteManagementService}) leem tabelas com RLS
 * ({@code assinatura} no limite do plano, {@code convite}), então cada escrita fixa a
 * empresa-alvo na transação antes de chamá-las ({@link #fixarEmpresa}). {@code membro},
 * {@code usuario} e {@code tenant_access} não têm RLS: o filtro por tenant nas consultas
 * daqui é o único escopo — e é explícito (regra nº 1).
 *
 * <p><b>Remover ≠ excluir a pessoa</b>: remover apaga só o vínculo com ESTA empresa
 * ({@code membro} + {@code tenant_access}). A identidade continua valendo para outras
 * empresas, lojas e o portal (identidade única).
 *
 * <p>Mudanças de acesso invalidam o cache {@code tenant-access}: sem isso a pessoa
 * desativada/removida continuaria entrando enquanto o cache estivesse quente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformMembroService {

    private static final Set<String> PAPEIS_VALIDOS = Set.of(
        "ADMIN_TENANT", "GERENTE", "OPERADOR", "VENDEDOR", "MECANICO", "FINANCEIRO");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final MemberManagementService memberManagementService;
    private final UserInvitationService userInvitationService;
    private final ConviteManagementService conviteManagementService;
    private final TenantQueryService tenantQueryService;
    private final ApplicationEventPublisher eventPublisher;

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

    /** Convite criado pelo console. */
    public record ConviteCriado(UUID conviteId, String email) {}

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

    /** Desativa o vínculo (reversível). Mantém a regra do último administrador. */
    @Transactional
    @CacheEvict(value = "tenant-access", allEntries = true)
    public void desativar(UUID tenantId, UUID usuarioId, String motivo) {
        String m = exigirMotivo(motivo);
        Tenant tenant = fixarEmpresa(tenantId);
        String email = emailDoMembro(tenantId, usuarioId);
        memberManagementService.deactivateMember(tenantId, usuarioId);
        auditar(tenant, "TENANT_MEMBRO_DESATIVADO", email + " — " + m);
    }

    /** Reativa o vínculo. Respeita o limite de usuários (plano ou personalizado). */
    @Transactional
    @CacheEvict(value = "tenant-access", allEntries = true)
    public void reativar(UUID tenantId, UUID usuarioId, String motivo) {
        String m = exigirMotivo(motivo);
        Tenant tenant = fixarEmpresa(tenantId);
        String email = emailDoMembro(tenantId, usuarioId);
        memberManagementService.reactivateMember(tenantId, usuarioId);
        auditar(tenant, "TENANT_MEMBRO_REATIVADO", email + " — " + m);
    }

    /**
     * Remove a pessoa DESTA empresa (apaga membro + tenant_access). A conta continua.
     *
     * <p>Bloqueia: último administrador ativo, e quem tem histórico que referencia o
     * vínculo ({@code despesa_manutencao.aprovado_por/pago_por} → {@code membro.id}, FK
     * sem cascata) — nesses casos o caminho é desativar.
     */
    @Transactional
    @CacheEvict(value = "tenant-access", allEntries = true)
    public void remover(UUID tenantId, UUID usuarioId, String motivo) {
        String m = exigirMotivo(motivo);
        Tenant tenant = fixarEmpresa(tenantId);

        List<Map<String, Object>> linhas = jdbc.queryForList("""
            SELECT m.id, m.ativo, 'ADMIN_TENANT' = ANY(m.papeis) AS admin, u.email,
                   array_to_string(m.papeis, ', ') AS papeis
            FROM membro m
            JOIN usuario u ON u.id = m.usuario_id
            WHERE m.tenant_id = ? AND m.usuario_id = ?
            """, tenantId, usuarioId);
        if (linhas.isEmpty()) {
            throw new NotFoundException("Este usuário não faz parte desta empresa.");
        }
        Map<String, Object> membro = linhas.get(0);
        Number membroId = (Number) membro.get("id");
        String email = (String) membro.get("email");

        if (Boolean.TRUE.equals(membro.get("ativo")) && Boolean.TRUE.equals(membro.get("admin"))) {
            Long admins = jdbc.queryForObject(
                "SELECT count(*) FROM membro WHERE tenant_id = ? AND ativo AND 'ADMIN_TENANT' = ANY(papeis)",
                Long.class, tenantId);
            if (admins == null || admins <= 1) {
                throw new BusinessException("Não é possível remover o último administrador ativo da empresa. "
                    + "Inclua outro administrador antes.");
            }
        }

        Long historico = jdbc.queryForObject(
            "SELECT count(*) FROM despesa_manutencao WHERE aprovado_por = ? OR pago_por = ?",
            Long.class, membroId.intValue(), membroId.intValue());
        if (historico != null && historico > 0) {
            throw new BusinessException("Este usuário tem histórico na empresa (aprovou ou pagou despesas "
                + "de manutenção) e não pode ser removido. Desative-o em vez de remover.");
        }

        jdbc.update("DELETE FROM tenant_access WHERE tenant_id = ? AND usuario_id = ?", tenantId, usuarioId);
        jdbc.update("DELETE FROM membro WHERE tenant_id = ? AND usuario_id = ?", tenantId, usuarioId);
        auditar(tenant, "TENANT_MEMBRO_REMOVIDO", email + " (" + membro.get("papeis") + ") — " + m);
    }

    /** Convites pendentes (e expirados ainda não refeitos) da empresa. */
    @Transactional(readOnly = true)
    public List<ConviteSummaryDTO> convites(UUID tenantId) {
        fixarEmpresa(tenantId);
        return conviteManagementService.listInvitations(tenantId);
    }

    /**
     * Convida alguém para a empresa. Serve para conta nova e para quem já tem conta
     * (identidade única): a pessoa aceita pelo e-mail — nada é vinculado sem esse aceite.
     * O convite registra o operador como quem convidou.
     */
    @Transactional
    public ConviteCriado convidar(UUID tenantId, String email, String nome, List<String> papeis, String motivo) {
        String m = exigirMotivo(motivo);
        String e = email == null ? "" : email.trim();
        if (!EMAIL.matcher(e).matches() || e.length() > 255) {
            throw new BusinessException("Informe um e-mail válido.");
        }
        String n = nome == null ? "" : nome.trim();
        if (n.length() < 2 || n.length() > 120) {
            throw new BusinessException("Informe o nome da pessoa (2 a 120 caracteres).");
        }
        if (papeis == null || papeis.isEmpty()) {
            throw new BusinessException("Escolha pelo menos um papel.");
        }
        for (String p : papeis) {
            if (!PAPEIS_VALIDOS.contains(p)) {
                throw new BusinessException("Papel inválido: " + p);
            }
        }
        Tenant tenant = fixarEmpresa(tenantId);

        InviteUserRequest request = InviteUserRequest.builder()
            .email(e)
            .nome(n)
            .papeis(papeis.toArray(new String[0]))
            .build();
        var convite = userInvitationService.inviteUser(tenantId, request, TenantContext.getUsuarioId());
        auditar(tenant, "TENANT_MEMBRO_CONVIDADO", e + " (" + String.join(", ", papeis) + ") — " + m);
        return new ConviteCriado(convite.getConviteId(), convite.getEmail());
    }

    /** Cancela um convite pendente da empresa. */
    @Transactional
    public void cancelarConvite(UUID tenantId, UUID conviteId, String motivo) {
        String m = exigirMotivo(motivo);
        Tenant tenant = fixarEmpresa(tenantId);
        List<String> emails = jdbc.queryForList(
            "SELECT email FROM convite WHERE id = ? AND tenant_id = ?", String.class, conviteId, tenantId);
        if (emails.isEmpty()) {
            throw new NotFoundException("Convite não encontrado nesta empresa.");
        }
        conviteManagementService.cancelInvitation(tenantId, conviteId);
        auditar(tenant, "TENANT_CONVITE_CANCELADO", emails.get(0) + " — " + m);
    }

    // ------------------------------------------------------------------

    /**
     * Fixa a empresa-alvo na transação (set_config local) e devolve o tenant. Mesma doutrina
     * do PlatformLimiteService/PlatformCreditoService: sem isto as tabelas com RLS lidas
     * pelas regras da empresa avaliam a sessão sem tenant.
     */
    private Tenant fixarEmpresa(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        Tenant tenant = tenantQueryService.findById(tenantId);
        if (tenant == null) {
            throw new NotFoundException("Empresa não encontrada: " + tenantId);
        }
        return tenant;
    }

    private String emailDoMembro(UUID tenantId, UUID usuarioId) {
        List<String> emails = jdbc.queryForList("""
            SELECT u.email FROM membro m JOIN usuario u ON u.id = m.usuario_id
            WHERE m.tenant_id = ? AND m.usuario_id = ?
            """, String.class, tenantId, usuarioId);
        if (emails.isEmpty()) {
            throw new NotFoundException("Este usuário não faz parte desta empresa.");
        }
        return emails.get(0);
    }

    private static String exigirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Informe o motivo da alteração (fica registrado na auditoria).");
        }
        return motivo.trim();
    }

    /** Trilha durável na empresa (mesmo evento das demais ações de plataforma sobre ela). */
    private void auditar(Tenant tenant, String acao, String detalhe) {
        String status = tenant.getStatus().name();
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenant.getId(), acao, status, status, TenantContext.getUsuarioId(),
            detalhe, tenant.getRazaoSocial(), tenant.getSlug()));
        log.warn("[PLATFORM] {}: tenant={}, {}", acao, tenant.getId(), detalhe);
    }

    private static List<String> papeis(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
