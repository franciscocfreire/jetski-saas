package com.jetski.tenant.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.tenant.api.dto.ComissaoConfigRequest;
import com.jetski.tenant.api.dto.TenantGeralConfigRequest;
import com.jetski.tenant.api.dto.TenantGeralConfigResponse;
import com.jetski.shared.storage.StorageService;
import com.jetski.tenant.domain.AssinaturaConfig;
import com.jetski.tenant.domain.Branding;
import com.jetski.tenant.domain.ComissaoConfig;
import com.jetski.tenant.domain.DocumentoConfig;
import com.jetski.tenant.domain.Tenant;
import com.jetski.shared.security.SecretCipher;
import com.jetski.tenant.internal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private final StorageService storageService;

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
            .pixChave(t.getPixChave())
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
        if (req.getPixChave() != null) t.setPixChave(blankToNull(req.getPixChave()));
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

    /** Reforço jurídico da assinatura (página de auditoria + carimbo de tempo). */
    @Transactional(readOnly = true)
    public AssinaturaConfig getAssinaturaConfig(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        AssinaturaConfig cfg = tenant.getAssinaturaConfig();
        return (cfg != null ? cfg : AssinaturaConfig.padrao()).comDefaults();
    }

    @Transactional
    public AssinaturaConfig updateAssinaturaConfig(UUID tenantId, AssinaturaConfig request) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        if (request == null) {
            throw new BusinessException("Configuração de assinatura ausente");
        }
        AssinaturaConfig cfg = request.comDefaults();
        tenant.setAssinaturaConfig(cfg);
        tenantRepository.save(tenant);
        log.info("AssinaturaConfig atualizada para o tenant {}", tenantId);
        return cfg;
    }

    // ========== BRANDING (white-label) ==========

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final long LOGO_MAX_BYTES = 512 * 1024;
    private static final Set<String> LOGO_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    /** Branding do tenant (cores + logo). Nulos ⇒ identidade padrão Meu Jet. */
    @Transactional(readOnly = true)
    public Branding getBranding(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        Branding b = tenant.getBranding();
        return b != null ? b : Branding.padrao();
    }

    /** Logo como data URL (base64) para exibição direta em &lt;img&gt;; null se não houver. */
    @Transactional(readOnly = true)
    public String getLogoDataUrl(UUID tenantId) {
        Branding b = getBranding(tenantId);
        if (!b.temLogo()) {
            return null;
        }
        try {
            byte[] bytes = storageService.getObject(b.logoKey());
            String ct = b.logoContentType() != null ? b.logoContentType() : "image/png";
            return "data:" + ct + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("Logo do tenant {} inacessível no storage (key={}): {}", tenantId, b.logoKey(), e.getMessage());
            return null;
        }
    }

    /** Atualiza as cores; o logo é preservado (gerenciado por upload/remove dedicados). */
    @Transactional
    public Branding updateBranding(UUID tenantId, Branding request) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        if (request == null) {
            throw new BusinessException("Configuração de branding ausente");
        }
        validarCor("cor primária", request.corPrimaria(), true);
        validarCor("cor secundária", request.corSecundaria(), false);
        Branding atual = tenant.getBranding() != null ? tenant.getBranding() : Branding.padrao();
        Branding cfg = atual.comCores(normalizarCor(request.corPrimaria()), normalizarCor(request.corSecundaria()));
        tenant.setBranding(cfg);
        tenantRepository.save(tenant);
        log.info("Branding atualizado para o tenant {}", tenantId);
        return cfg;
    }

    @Transactional
    public Branding uploadLogo(UUID tenantId, byte[] content, String contentType) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        if (content == null || content.length == 0) {
            throw new BusinessException("Arquivo de logo vazio");
        }
        if (content.length > LOGO_MAX_BYTES) {
            throw new BusinessException("Logo excede o limite de 512 KB");
        }
        if (contentType == null || !LOGO_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("Formato de logo não suportado (use PNG, JPEG ou WebP)");
        }
        String ext = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "png";
        };
        String key = tenantId + "/branding/logo." + ext;
        storageService.putObject(key, content, contentType);
        Branding atual = tenant.getBranding() != null ? tenant.getBranding() : Branding.padrao();
        if (atual.temLogo() && !atual.logoKey().equals(key)) {
            try {
                storageService.deleteFile(atual.logoKey());
            } catch (Exception e) {
                log.warn("Falha ao remover logo antigo do tenant {} (key={}): {}", tenantId, atual.logoKey(), e.getMessage());
            }
        }
        Branding cfg = atual.comLogo(key, contentType);
        tenant.setBranding(cfg);
        tenantRepository.save(tenant);
        log.info("Logo de branding atualizado para o tenant {} ({} bytes, {})", tenantId, content.length, contentType);
        return cfg;
    }

    @Transactional
    public Branding removeLogo(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant não encontrado: " + tenantId));
        Branding atual = tenant.getBranding() != null ? tenant.getBranding() : Branding.padrao();
        if (atual.temLogo()) {
            try {
                storageService.deleteFile(atual.logoKey());
            } catch (Exception e) {
                log.warn("Falha ao remover logo do tenant {} (key={}): {}", tenantId, atual.logoKey(), e.getMessage());
            }
        }
        Branding cfg = atual.semLogo();
        tenant.setBranding(cfg);
        tenantRepository.save(tenant);
        log.info("Logo de branding removido para o tenant {}", tenantId);
        return cfg;
    }

    private void validarCor(String nomeCampo, String cor, boolean exigirContraste) {
        if (cor == null || cor.isBlank()) {
            return; // null/vazio ⇒ volta ao padrão Meu Jet
        }
        if (!HEX_COLOR.matcher(cor.trim()).matches()) {
            throw new BusinessException("Cor inválida em " + nomeCampo + ": use o formato #RRGGBB");
        }
        if (exigirContraste && luminanciaRelativa(cor.trim()) > 0.5) {
            throw new BusinessException(
                "A " + nomeCampo + " é clara demais: texto branco em botões ficaria ilegível. Escolha um tom mais escuro.");
        }
    }

    private String normalizarCor(String cor) {
        return (cor == null || cor.isBlank()) ? null : cor.trim().toUpperCase();
    }

    /** Luminância relativa WCAG (0=preto, 1=branco). */
    private double luminanciaRelativa(String hex) {
        double r = canalLinear(Integer.parseInt(hex.substring(1, 3), 16));
        double g = canalLinear(Integer.parseInt(hex.substring(3, 5), 16));
        double b = canalLinear(Integer.parseInt(hex.substring(5, 7), 16));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private double canalLinear(int canal) {
        double c = canal / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
