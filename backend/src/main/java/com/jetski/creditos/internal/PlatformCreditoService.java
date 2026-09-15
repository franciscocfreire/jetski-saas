package com.jetski.creditos.internal;

import com.jetski.creditos.CreditoService;
import com.jetski.creditos.api.dto.PlatformCompraDTO;
import com.jetski.creditos.api.dto.PlatformSaldoTenantDTO;
import com.jetski.creditos.domain.CreditoCompra;
import com.jetski.creditos.domain.CreditoLancamento;
import com.jetski.creditos.domain.TipoLancamento;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.TenantQueryService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Operações de crédito do super admin, cross-tenant SEM bypass de RLS:
 * troca o tenant da transação com {@code set_config('app.tenant_id', ..., true)}
 * (local à transação — mesma doutrina do PlatformMeteringService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformCreditoService {

    private final CreditoService creditoService;
    private final EntityManager entityManager;
    private final TenantQueryService tenantQueryService;

    /** Lança créditos (±) para o tenant alvo, com auditoria via evento. */
    @Transactional
    public CreditoLancamento lancar(UUID tenantId, int quantidade, String motivo) {
        return lancar(tenantId, quantidade, motivo, null, null);
    }

    /**
     * @param tipo       AJUSTE (padrão; negativo vira ESTORNO) ou CORTESIA (sempre positiva)
     * @param condicaoId só na CORTESIA: condição comercial que a motivou (opcional)
     */
    @Transactional
    public CreditoLancamento lancar(UUID tenantId, int quantidade, String motivo,
                                    String tipo, UUID condicaoId) {
        tenantQueryService.exigirNaoExcluida(tenantId);
        UUID actor = actorOrNull();
        setTenant(tenantId);
        CreditoLancamento lanc;
        if (tipo == null || tipo.isBlank() || TipoLancamento.AJUSTE.name().equals(tipo)) {
            if (condicaoId != null) {
                throw new BusinessException("Vínculo com condição comercial só vale para cortesia");
            }
            lanc = creditoService.lancarAjuste(tenantId, quantidade, motivo, actor);
        } else if (TipoLancamento.CORTESIA.name().equals(tipo)) {
            lanc = creditoService.lancarCortesia(tenantId, quantidade, motivo, condicaoId, actor);
        } else {
            throw new BusinessException("Tipo de lançamento inválido: use AJUSTE ou CORTESIA");
        }
        log.info("[PLATFORM] Créditos lançados: tenant={}, tipo={}, quantidade={}, actor={}",
            tenantId, lanc.getTipo(), quantidade, actor);
        return lanc;
    }

    /** Compras de créditos pendentes de todos os tenants (iteração por tenant). */
    @Transactional
    public List<PlatformCompraDTO> comprasPendentes() {
        @SuppressWarnings("unchecked")
        List<Object[]> tenants = entityManager.createNativeQuery(
                "SELECT id, slug, razao_social FROM tenant ORDER BY razao_social")
            .getResultList();

        List<PlatformCompraDTO> resultado = new ArrayList<>();
        for (Object[] t : tenants) {
            UUID tenantId = (UUID) t[0];
            setTenant(tenantId);
            for (CreditoCompra c : creditoService.comprasPendentesDoTenant(tenantId)) {
                resultado.add(PlatformCompraDTO.from(c, (String) t[1], (String) t[2]));
            }
        }
        return resultado;
    }

    /** Aprova a compra: credita no ledger (auditado) e marca APROVADA. */
    @Transactional
    public CreditoCompra aprovarCompra(UUID tenantId, UUID compraId) {
        tenantQueryService.exigirNaoExcluida(tenantId);
        setTenant(tenantId);
        CreditoCompra compra = creditoService.aprovarCompra(tenantId, compraId, actorOrNull());
        log.info("[PLATFORM] Compra de créditos aprovada: tenant={}, compra={}, quantidade={}",
            tenantId, compraId, compra.getQuantidade());
        return compra;
    }

    @Transactional
    public CreditoCompra rejeitarCompra(UUID tenantId, UUID compraId, String observacao) {
        setTenant(tenantId);
        return creditoService.rejeitarCompra(tenantId, compraId, observacao, actorOrNull());
    }

    /** Comprovante PIX da compra (cross-tenant via set_config, mesma doutrina). */
    @Transactional(readOnly = true)
    public CreditoService.ComprovanteArquivo comprovante(UUID tenantId, UUID compraId) {
        setTenant(tenantId);
        return creditoService.comprovante(tenantId, compraId);
    }

    /** Preço do crédito (config global — sem escopo de tenant). */
    public java.math.BigDecimal precoUnitario() {
        return creditoService.precoUnitario();
    }

    @Transactional
    public java.math.BigDecimal atualizarPrecoUnitario(java.math.BigDecimal preco) {
        return creditoService.atualizarPrecoUnitario(preco, actorOrNull());
    }

    /** Saldo de todos os tenants (iteração por tenant). */
    @Transactional
    public List<PlatformSaldoTenantDTO> saldos() {
        @SuppressWarnings("unchecked")
        List<Object[]> tenants = entityManager.createNativeQuery(
                "SELECT id, slug, razao_social FROM tenant ORDER BY razao_social")
            .getResultList();

        List<PlatformSaldoTenantDTO> resultado = new ArrayList<>(tenants.size());
        for (Object[] t : tenants) {
            UUID tenantId = (UUID) t[0];
            setTenant(tenantId);
            resultado.add(new PlatformSaldoTenantDTO(
                tenantId, (String) t[1], (String) t[2], creditoService.saldo(tenantId)));
        }
        return resultado;
    }

    private void setTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', ?1, true)")
            .setParameter(1, tenantId.toString())
            .getSingleResult();
    }

    private UUID actorOrNull() {
        try {
            return TenantContext.getUsuarioId();
        } catch (Exception e) {
            return null;
        }
    }
}
