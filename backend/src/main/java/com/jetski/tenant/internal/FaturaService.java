package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.domain.Fatura;
import com.jetski.tenant.internal.repository.FaturaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Faturas na visão da EMPRESA (tenant-scoped): consulta e "informar
 * pagamento" (a empresa paga o PIX e registra o nº da transação; a
 * confirmação é do super admin — {@link PlatformFaturaService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaturaService {

    private final FaturaRepository faturaRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Fatura> minhas(UUID tenantId) {
        return faturaRepository.findByTenantIdOrderByCompetenciaDesc(tenantId);
    }

    /** Plano atual do tenant (nome, preço, limites) — cabeçalho da página Plano. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> planoAtual(UUID tenantId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT p.nome, p.preco_mensal, p.limites::text
                  FROM assinatura a JOIN plano p ON p.id = a.plano_id
                 WHERE a.tenant_id = :tid AND a.status = 'ativa'
                 ORDER BY a.created_at DESC LIMIT 1
                """)
            .setParameter("tid", tenantId)
            .getResultList();
        if (rows.isEmpty()) {
            return Map.of("plano", "—", "precoMensal", BigDecimal.ZERO, "limites", "{}");
        }
        Object[] r = rows.get(0);
        return Map.of("plano", r[0], "precoMensal", r[1], "limites", r[2]);
    }

    /** Uso atual × limites (página Plano): jetskis ativos, locações do mês, usuários. */
    @Transactional(readOnly = true)
    public Map<String, Object> uso(UUID tenantId) {
        Number jetskis = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM jetski WHERE tenant_id = :tid AND ativo = true")
            .setParameter("tid", tenantId).getSingleResult();
        Number locacoesMes = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM locacao WHERE tenant_id = :tid "
                + "AND data_check_in >= date_trunc('month', now())")
            .setParameter("tid", tenantId).getSingleResult();
        Number usuarios = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM membro WHERE tenant_id = :tid AND ativo = true")
            .setParameter("tid", tenantId).getSingleResult();
        return Map.of(
            "jetskisAtivos", jetskis.longValue(),
            "locacoesMes", locacoesMes.longValue(),
            "usuariosAtivos", usuarios.longValue());
    }

    /**
     * Empresa informa que pagou (nº da transação PIX) → EM_CONFERENCIA.
     * Reenvio é permitido (corrige txid digitado errado) enquanto não conferida.
     */
    @Transactional
    public Fatura informarPagamento(UUID tenantId, UUID faturaId, String txid) {
        if (txid == null || txid.trim().isEmpty()) {
            throw new BusinessException("Informe o número da transação PIX (txid) do comprovante");
        }
        Fatura fatura = faturaRepository.findByIdAndTenantId(faturaId, tenantId)
            .orElseThrow(() -> new NotFoundException("Fatura não encontrada: " + faturaId));
        if (fatura.getStatus() == Fatura.Status.PAGA || fatura.getStatus() == Fatura.Status.CANCELADA) {
            throw new BusinessException("Fatura já " + fatura.getStatus().name().toLowerCase());
        }
        fatura.setTxidInformado(txid.trim());
        fatura.setInformadoEm(Instant.now());
        fatura.setStatus(Fatura.Status.EM_CONFERENCIA);
        log.info("Pagamento de fatura informado: tenant={}, fatura={}, competencia={}",
            tenantId, faturaId, fatura.getCompetencia());
        return faturaRepository.save(fatura);
    }
}
