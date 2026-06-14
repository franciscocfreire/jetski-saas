package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.api.dto.ComissaoConfigRequest;
import com.jetski.tenant.domain.ComissaoConfig;
import com.jetski.tenant.domain.Tenant;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service: TenantConfigService
 *
 * Manages tenant configuration settings including commission and bonus configuration.
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantConfigService {

    private final TenantRepository tenantRepository;

    /**
     * Get the commission configuration for a tenant.
     * Returns default configuration if none is set.
     *
     * @param tenantId Tenant ID
     * @return ComissaoConfig
     */
    @Transactional(readOnly = true)
    public ComissaoConfig getComissaoConfig(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));

        ComissaoConfig config = tenant.getComissaoConfig();
        if (config == null) {
            log.debug("Tenant {} has no comissao config, returning defaults", tenantId);
            return ComissaoConfig.padrao();
        }
        return config;
    }

    /**
     * Update the commission configuration for a tenant.
     *
     * @param tenantId Tenant ID
     * @param request Update request with new configuration values
     * @return Updated ComissaoConfig
     */
    @Transactional
    public ComissaoConfig updateComissaoConfig(UUID tenantId, ComissaoConfigRequest request) {
        log.info("Updating comissao config for tenant {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));

        // Build new config from request
        ComissaoConfig config = new ComissaoConfig(
            request.getPercentualPadrao(),
            request.getPercentualAbaixoBase(),
            request.getBonusAtivo(),
            request.getBonusMetaVendas(),
            request.getBonusValor()
        );

        // Validate config
        if (!config.isValid()) {
            throw new BusinessException("Configuração de comissão inválida. Verifique os valores.");
        }

        // Additional business validations
        if (Boolean.TRUE.equals(request.getBonusAtivo())) {
            if (request.getBonusMetaVendas() == null || request.getBonusMetaVendas() < 1) {
                throw new BusinessException("Meta de vendas é obrigatória quando o bônus está ativo");
            }
            if (request.getBonusValor() == null || request.getBonusValor().signum() < 0) {
                throw new BusinessException("Valor do bônus é obrigatório quando o bônus está ativo");
            }
        }

        tenant.setComissaoConfig(config);
        tenantRepository.save(tenant);

        log.info("ComissaoConfig updated for tenant {}: percentualPadrao={}, percentualAbaixoBase={}, bonusAtivo={}",
                tenantId, config.percentualPadrao(), config.percentualAbaixoBase(), config.bonusAtivo());

        return config;
    }
}
