package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.api.dto.ComissaoConfigRequest;
import com.jetski.tenant.api.dto.TenantGeralConfigRequest;
import com.jetski.tenant.api.dto.TenantGeralConfigResponse;
import com.jetski.tenant.domain.ComissaoConfig;
import com.jetski.tenant.domain.DocumentoConfig;
import com.jetski.tenant.domain.Tenant;
import com.jetski.shared.security.SecretCipher;
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
    private final SecretCipher secretCipher;

    /** Dados gerais/e-mail da empresa (tenant). */
    @Transactional(readOnly = true)
    public TenantGeralConfigResponse getGeralConfig(UUID tenantId) {
        Tenant t = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        return TenantGeralConfigResponse.builder()
            .slug(t.getSlug())
            .cnpj(t.getCnpj())
            .razaoSocial(t.getRazaoSocial())
            .cidade(t.getCidade())
            .marinhaEmail(t.getMarinhaEmail())
            .emailRemetente(t.getEmailRemetente())
            .smtpHost(t.getSmtpHost())
            .smtpPort(t.getSmtpPort())
            .smtpUsername(t.getSmtpUsername())
            .smtpFrom(t.getSmtpFrom())
            .smtpStarttls(t.getSmtpStarttls())
            .smtpConfigurado(t.getSmtpPassword() != null && !t.getSmtpPassword().isBlank())
            .build();
    }

    /** Atualiza dados gerais/e-mail da empresa (campos não-nulos). */
    @Transactional
    public TenantGeralConfigResponse updateGeralConfig(UUID tenantId, TenantGeralConfigRequest req) {
        Tenant t = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        if (req.getRazaoSocial() != null && !req.getRazaoSocial().isBlank())
            t.setRazaoSocial(req.getRazaoSocial().trim());
        if (req.getCidade() != null) t.setCidade(blankToNull(req.getCidade()));
        if (req.getMarinhaEmail() != null) t.setMarinhaEmail(blankToNull(req.getMarinhaEmail()));
        if (req.getEmailRemetente() != null) t.setEmailRemetente(blankToNull(req.getEmailRemetente()));
        // SMTP por tenant: host/usuário/from/porta/tls sempre que enviados; senha SÓ se
        // não-branca (preserva a existente quando o form não reenvia o segredo).
        if (req.getSmtpHost() != null) t.setSmtpHost(blankToNull(req.getSmtpHost()));
        if (req.getSmtpPort() != null) t.setSmtpPort(req.getSmtpPort());
        if (req.getSmtpUsername() != null) t.setSmtpUsername(blankToNull(req.getSmtpUsername()));
        if (req.getSmtpFrom() != null) t.setSmtpFrom(blankToNull(req.getSmtpFrom()));
        if (req.getSmtpStarttls() != null) t.setSmtpStarttls(req.getSmtpStarttls());
        if (req.getSmtpPassword() != null && !req.getSmtpPassword().isBlank())
            t.setSmtpPassword(secretCipher.encrypt(req.getSmtpPassword()));
        tenantRepository.save(t);
        log.info("Config geral do tenant {} atualizada (marinhaEmail e remetente)", tenantId);
        return getGeralConfig(tenantId);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

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

    /** Parametrização de emissão (o que vai para Marinha vs Cliente). */
    @Transactional(readOnly = true)
    public DocumentoConfig getDocumentoConfig(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        DocumentoConfig cfg = tenant.getDocumentoConfig();
        return (cfg != null ? cfg : DocumentoConfig.padrao()).comDefaults();
    }

    @Transactional
    public DocumentoConfig updateDocumentoConfig(UUID tenantId, DocumentoConfig request) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        if (request == null) {
            throw new BusinessException("Configuração de documentos ausente");
        }
        DocumentoConfig cfg = request.comDefaults();
        tenant.setDocumentoConfig(cfg);
        tenantRepository.save(tenant);
        log.info("DocumentoConfig atualizada para o tenant {}", tenantId);
        return cfg;
    }
}
