package com.jetski.bonus.internal;

import com.jetski.bonus.domain.BonusVendedor;
import com.jetski.bonus.domain.StatusBonus;
import com.jetski.bonus.internal.repository.BonusVendedorRepository;
import com.jetski.comissoes.event.ComissaoCalculadaEvent;
import com.jetski.comissoes.api.ComissaoQueryService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.ComissaoConfig;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for Bonus management
 *
 * The bonus system is cumulative and never resets:
 * - When a seller reaches X sales above base price, they earn a bonus
 * - For every multiple of the meta (e.g., 50, 100, 150...), a new bonus is created
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BonusService {

    private final BonusVendedorRepository bonusRepository;
    private final ComissaoQueryService comissaoQueryService;
    private final TenantQueryService tenantQueryService;

    /**
     * Check and create bonuses when a seller reaches milestones.
     * Called after each commission calculation for sales above base price.
     *
     * @param tenantId Tenant ID
     * @param vendedorId Seller ID
     */
    /**
     * Escuta a comissão calculada acima do preço base e cria bônus se elegível.
     * Desacopla bonus de comissoes (evita ciclo): comissoes apenas publica o evento.
     */
    @EventListener
    public void onComissaoCalculada(ComissaoCalculadaEvent event) {
        verificarECriarBonus(event.tenantId(), event.vendedorId());
    }

    @Transactional
    public void verificarECriarBonus(UUID tenantId, UUID vendedorId) {
        log.debug("Checking bonus eligibility for vendedor: {}", vendedorId);

        // 1. Get tenant config
        Tenant tenant = tenantQueryService.findById(tenantId);
        ComissaoConfig config = tenant != null ? tenant.getComissaoConfig() : null;

        if (config == null || !Boolean.TRUE.equals(config.bonusAtivo())) {
            log.debug("Bonus system not active for tenant: {}", tenantId);
            return;
        }

        Integer metaNecessaria = config.bonusMetaVendas();
        BigDecimal valorBonus = config.bonusValor();

        if (metaNecessaria == null || metaNecessaria <= 0) {
            log.debug("Invalid bonus meta for tenant: {}", tenantId);
            return;
        }

        // 2. Count total sales above base price (cumulative, never resets)
        Long vendasAcimaBase = comissaoQueryService.countVendasAcimaPrecoBaseByVendedor(tenantId, vendedorId);
        if (vendasAcimaBase == null || vendasAcimaBase == 0) {
            return;
        }

        // 3. Get last milestone achieved
        Integer ultimaMetaAtingida = bonusRepository.findUltimaMetaAtingida(tenantId, vendedorId)
                .orElse(0);

        // 4. Create bonuses for each new milestone reached
        int metaAtual = ultimaMetaAtingida;
        while (vendasAcimaBase >= metaAtual + metaNecessaria) {
            metaAtual += metaNecessaria;

            // Check if this bonus already exists (safety check)
            if (!bonusRepository.existsByTenantIdAndVendedorIdAndMetaAtingida(tenantId, vendedorId, metaAtual)) {
                BonusVendedor bonus = BonusVendedor.builder()
                        .tenantId(tenantId)
                        .vendedorId(vendedorId)
                        .metaAtingida(metaAtual)
                        .valorBonus(valorBonus != null ? valorBonus : BigDecimal.ZERO)
                        .status(StatusBonus.PENDENTE)
                        .build();

                bonusRepository.save(bonus);
                log.info("Bonus created for vendedor {} - meta: {}, valor: R$ {}",
                        vendedorId, metaAtual, valorBonus);
            }
        }
    }

    /**
     * Approve a bonus (GERENTE)
     */
    @Transactional
    public BonusVendedor aprovarBonus(UUID tenantId, UUID bonusId, UUID aprovadoPor) {
        BonusVendedor bonus = findById(tenantId, bonusId);

        if (!bonus.podeAprovar()) {
            throw new BusinessException("Bonus não pode ser aprovado. Status atual: " + bonus.getStatus());
        }

        bonus.setStatus(StatusBonus.APROVADO);
        bonus.setAprovadoPor(aprovadoPor);
        bonus.setAprovadoEm(Instant.now());

        BonusVendedor saved = bonusRepository.save(bonus);
        log.info("Bonus {} approved by {}", bonusId, aprovadoPor);

        return saved;
    }

    /**
     * Pay a bonus (FINANCEIRO)
     */
    @Transactional
    public BonusVendedor pagarBonus(UUID tenantId, UUID bonusId, UUID pagoPor, String referenciaPagamento) {
        BonusVendedor bonus = findById(tenantId, bonusId);

        if (!bonus.podePagar()) {
            throw new BusinessException("Bonus não pode ser pago. Status atual: " + bonus.getStatus());
        }

        bonus.setStatus(StatusBonus.PAGO);
        bonus.setPagoPor(pagoPor);
        bonus.setPagoEm(Instant.now());
        bonus.setReferenciaPagamento(referenciaPagamento);

        BonusVendedor saved = bonusRepository.save(bonus);
        log.info("Bonus {} paid by {} (ref: {})", bonusId, pagoPor, referenciaPagamento);

        return saved;
    }

    /**
     * Cancel a bonus
     */
    @Transactional
    public BonusVendedor cancelarBonus(UUID tenantId, UUID bonusId) {
        BonusVendedor bonus = findById(tenantId, bonusId);

        if (bonus.getStatus() == StatusBonus.PAGO) {
            throw new BusinessException("Bonus já pago não pode ser cancelado");
        }

        bonus.setStatus(StatusBonus.CANCELADO);
        BonusVendedor saved = bonusRepository.save(bonus);
        log.info("Bonus {} cancelled", bonusId);

        return saved;
    }

    /**
     * Find bonus by ID
     */
    @Transactional(readOnly = true)
    public BonusVendedor findById(UUID tenantId, UUID bonusId) {
        return bonusRepository.findById(bonusId)
                .filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Bonus não encontrado: " + bonusId));
    }

    /**
     * List bonuses for a seller
     */
    @Transactional(readOnly = true)
    public List<BonusVendedor> listarPorVendedor(UUID tenantId, UUID vendedorId) {
        return bonusRepository.findByTenantIdAndVendedorIdOrderByMetaAtingidaDesc(tenantId, vendedorId);
    }

    /**
     * List pending bonuses for tenant
     */
    @Transactional(readOnly = true)
    public List<BonusVendedor> listarPendentes(UUID tenantId) {
        return bonusRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, StatusBonus.PENDENTE);
    }

    /**
     * List approved bonuses waiting for payment
     */
    @Transactional(readOnly = true)
    public List<BonusVendedor> listarAguardandoPagamento(UUID tenantId) {
        return bonusRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, StatusBonus.APROVADO);
    }
}
