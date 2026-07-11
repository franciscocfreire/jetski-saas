package com.jetski.tenant;

import com.jetski.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Enforcement de limites do plano (v2, item 2) — API pública do módulo tenant
 * (padrão {@link TenantQueryService}): outros módulos consultam/validam os
 * limites da assinatura ativa ({@code plano.limites} jsonb).
 *
 * <p>Convenção: limite ausente ou {@code -1} = ilimitado. Sem assinatura
 * ativa = ilimitado (não bloquear operação por falha de cadastro — o gate de
 * status do tenant é quem barra empresa não-operacional).
 *
 * <p>Chaves em uso: {@code usuarios_max} (enforçado no convite/membro),
 * {@code frota_max} (criação de jetski), {@code locacoes_mes} (check-in).
 * {@code storage_gb} não é enforçado (medir custa caro; aviso fica p/ v3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanoLimiteService {

    private final EntityManager entityManager;

    /** Limite da chave para o tenant; null = ilimitado (ausente, -1 ou sem assinatura). */
    @Transactional(readOnly = true)
    public Integer limite(UUID tenantId, String chave) {
        try {
            List<?> rows = entityManager.createNativeQuery(
                    "SELECT (p.limites->>:chave)::int FROM assinatura a "
                    + "JOIN plano p ON p.id = a.plano_id "
                    + "WHERE a.tenant_id = :tid AND a.status = 'ativa' "
                    + "ORDER BY a.created_at DESC LIMIT 1")
                .setParameter("chave", chave)
                .setParameter("tid", tenantId)
                .getResultList();
            if (rows.isEmpty() || rows.get(0) == null) {
                return null;
            }
            Integer limite = ((Number) rows.get(0)).intValue();
            return limite < 0 ? null : limite;
        } catch (Exception e) {
            log.warn("Falha ao ler limite '{}' do tenant {}: {}", chave, tenantId, e.getMessage());
            return null; // nunca bloquear por falha de leitura
        }
    }

    /**
     * Lança negação de negócio (400) se o uso atual já atingiu o limite.
     *
     * @param usoAtual quantos o tenant JÁ tem (o novo item estouraria o limite)
     */
    @Transactional(readOnly = true)
    public void verificar(UUID tenantId, String chave, long usoAtual, String recurso) {
        Integer max = limite(tenantId, chave);
        if (max != null && usoAtual >= max) {
            throw new BusinessException(String.format(
                "Limite de %s do seu plano atingido (%d/%d). "
                + "Faça upgrade do plano em Plano e Faturas para continuar.",
                recurso, usoAtual, max));
        }
    }
}
