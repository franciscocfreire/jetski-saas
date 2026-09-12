package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.internal.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Limite de usuários da empresa, definido pelo console da plataforma (V068).
 *
 * <p>O teto padrão vem do plano ({@code plano.limites.usuarios_max}); aqui a plataforma
 * personaliza o número para UMA empresa sem trocar o plano dela. Quem aplica o limite
 * (convite, reativação, adicionar membro existente) é o {@code PlanoLimiteService}, que
 * já lê a personalização — esta classe só lê e grava.
 *
 * <p>Baixar o limite abaixo do uso atual não desativa ninguém: só bloqueia novos
 * convites e reativações até a empresa caber no teto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformLimiteService {

    private final EntityManager entityManager;
    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param plano         nome do plano da assinatura ativa (nulo = sem assinatura)
     * @param doPlano       teto do plano (nulo = ilimitado ou sem assinatura)
     * @param personalizado teto definido pela plataforma (nulo = segue o plano)
     * @param efetivo       o que vale de fato (nulo = ilimitado)
     * @param ativos        usuários ativos hoje
     */
    public record LimiteUsuarios(
        String plano, Integer doPlano, Integer personalizado, Integer efetivo, long ativos) {}

    @Transactional
    public LimiteUsuarios usuarios(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new NotFoundException("Empresa não encontrada: " + tenantId);
        }
        // assinatura tem RLS e a rota de plataforma não tem tenant na sessão
        // (mesmo motivo do setTenant em PlatformFaturaService.mudarPlano).
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();

        @SuppressWarnings("unchecked")
        List<Object[]> plano = entityManager.createNativeQuery("""
                SELECT p.nome, (p.limites->>'usuarios_max')::int
                  FROM assinatura a JOIN plano p ON p.id = a.plano_id
                 WHERE a.tenant_id = :tid AND a.status = 'ativa'
                 ORDER BY a.created_at DESC LIMIT 1
                """)
            .setParameter("tid", tenantId)
            .getResultList();
        Number personalizado = (Number) entityManager.createNativeQuery(
                "SELECT (limites_override->>'usuarios_max')::int FROM tenant WHERE id = :tid")
            .setParameter("tid", tenantId)
            .getSingleResult();
        Number ativos = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM membro WHERE tenant_id = :tid AND ativo = true")
            .setParameter("tid", tenantId)
            .getSingleResult();

        String nomePlano = plano.isEmpty() ? null : (String) plano.get(0)[0];
        Integer doPlano = plano.isEmpty() ? null : ilimitadoSeNegativo((Number) plano.get(0)[1]);
        Integer teto = personalizado == null ? null : personalizado.intValue();
        return new LimiteUsuarios(nomePlano, doPlano, teto, teto != null ? teto : doPlano,
            ativos.longValue());
    }

    /**
     * Define (ou remove, com {@code maximo} nulo) o limite personalizado.
     *
     * @param maximo novo teto de usuários ativos; nulo = voltar a seguir o plano
     * @param motivo obrigatório: exceção comercial precisa dizer por quê na auditoria
     */
    @Transactional
    public LimiteUsuarios definirUsuarios(UUID tenantId, Integer maximo, String motivo) {
        if (maximo != null && maximo < 1) {
            throw new BusinessException(
                "O limite de usuários precisa ser pelo menos 1: a empresa precisa de um administrador.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Informe o motivo da alteração (fica registrado na auditoria).");
        }
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada: " + tenantId));
        LimiteUsuarios antes = usuarios(tenantId);

        if (maximo == null) {
            entityManager.createNativeQuery(
                    "UPDATE tenant SET limites_override = limites_override - 'usuarios_max', "
                    + "updated_at = now() WHERE id = :tid")
                .setParameter("tid", tenantId)
                .executeUpdate();
        } else {
            entityManager.createNativeQuery(
                    "UPDATE tenant SET limites_override = limites_override "
                    + "|| jsonb_build_object('usuarios_max', CAST(:max AS int)), "
                    + "updated_at = now() WHERE id = :tid")
                .setParameter("max", maximo)
                .setParameter("tid", tenantId)
                .executeUpdate();
        }

        LimiteUsuarios depois = usuarios(tenantId);
        String detalhe = "usuarios_max: " + descrever(antes) + " → " + descrever(depois)
            + " — " + motivo.trim();
        String status = tenant.getStatus().name();
        // Trilha durável (mesmo evento das demais ações de plataforma sobre a empresa).
        // Não gera e-mail: os listeners de aviso filtram pelas ações com template.
        eventPublisher.publishEvent(TenantStatusChangedEvent.of(
            tenantId, "TENANT_LIMITE_USUARIOS_ALTERADO", status, status,
            TenantContext.getUsuarioId(), detalhe, tenant.getRazaoSocial(), tenant.getSlug()));
        log.warn("[PLATFORM] Limite de usuários alterado: tenant={}, {}", tenantId, detalhe);
        return depois;
    }

    private static Integer ilimitadoSeNegativo(Number valor) {
        return valor == null || valor.intValue() < 0 ? null : valor.intValue();
    }

    private static String descrever(LimiteUsuarios l) {
        if (l.personalizado() != null) {
            return l.personalizado() + " (personalizado)";
        }
        return (l.doPlano() == null ? "ilimitado" : String.valueOf(l.doPlano())) + " (plano)";
    }
}
