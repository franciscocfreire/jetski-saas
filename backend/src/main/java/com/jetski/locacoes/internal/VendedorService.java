package com.jetski.locacoes.internal;

import com.jetski.comissoes.api.ComissaoQueryService;
import com.jetski.locacoes.api.dto.VendedorDetalheResponse;
import com.jetski.locacoes.api.dto.VendedorResumoResponse;
import com.jetski.locacoes.domain.Vendedor;
import com.jetski.locacoes.domain.VendedorTipo;
import com.jetski.locacoes.internal.repository.VendedorRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.ComissaoConfig;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service: Vendedor Management
 *
 * Handles seller and partner registration:
 * - Create, update, and list sellers
 * - Manage commission rules (Business rules RF08, RN04)
 * - Filter by seller type (INTERNO vs PARCEIRO)
 *
 * Business Rules:
 * - RF08: Commission hierarchy (campaign > model > duration > seller default)
 * - RN04: Commission calculated on commissionable revenue only
 * - Seller tipo must be INTERNO or PARCEIRO
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VendedorService {

    private final VendedorRepository vendedorRepository;
    private final ComissaoQueryService comissaoQueryService;
    private final TenantQueryService tenantQueryService;

    /**
     * List all active sellers for current tenant.
     * RLS automatically filters by tenant_id.
     *
     * @return List of active Vendedor records
     */
    @Transactional(readOnly = true)
    public List<Vendedor> listActiveSellers() {
        log.debug("Listing all active sellers");
        return vendedorRepository.findAllActive();
    }

    /**
     * List all sellers for current tenant (including inactive).
     * RLS automatically filters by tenant_id.
     *
     * @return List of all Vendedor records
     */
    @Transactional(readOnly = true)
    public List<Vendedor> listAllSellers() {
        log.debug("Listing all sellers (including inactive)");
        return vendedorRepository.findAll();
    }

    /**
     * List sellers by type (INTERNO or PARCEIRO).
     *
     * @param tipo VendedorTipo enum
     * @return List of Vendedor records of specified type
     */
    @Transactional(readOnly = true)
    public List<Vendedor> listByTipo(VendedorTipo tipo) {
        log.debug("Listing sellers by type: {}", tipo);
        return vendedorRepository.findAllByTipo(tipo);
    }

    /**
     * Find seller by ID within current tenant.
     *
     * @param id Vendedor UUID
     * @return Vendedor entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public Vendedor findById(UUID id) {
        log.debug("Finding seller by id: {}", id);
        return vendedorRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Vendedor não encontrado"));
    }

    /**
     * Create new seller.
     *
     * Validations:
     * - Name is required
     * - Tipo must be INTERNO or PARCEIRO
     *
     * @param vendedor Vendedor entity to create
     * @return Created Vendedor
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Vendedor createVendedor(Vendedor vendedor) {
        log.info("Creating new seller: nome={}, tipo={}", vendedor.getNome(), vendedor.getTipo());

        // Validate name
        if (vendedor.getNome() == null || vendedor.getNome().isBlank()) {
            throw new BusinessException("Nome do vendedor é obrigatório");
        }

        // Validate tipo
        if (vendedor.getTipo() == null) {
            throw new BusinessException("Tipo do vendedor é obrigatório (INTERNO ou PARCEIRO)");
        }

        Vendedor saved = vendedorRepository.save(vendedor);
        log.info("Seller created successfully: id={}, nome={}", saved.getId(), saved.getNome());
        return saved;
    }

    /**
     * Update existing seller.
     *
     * Validations:
     * - Seller must exist
     * - Name cannot be blank if provided
     *
     * @param id Vendedor UUID
     * @param updates Vendedor with updated fields
     * @return Updated Vendedor
     * @throws BusinessException if validation fails
     */
    @Transactional
    public Vendedor updateVendedor(UUID id, Vendedor updates) {
        log.info("Updating seller: id={}", id);

        Vendedor existing = findById(id);

        // Update fields
        if (updates.getNome() != null) {
            if (updates.getNome().isBlank()) {
                throw new BusinessException("Nome do vendedor não pode ser vazio");
            }
            existing.setNome(updates.getNome());
        }

        if (updates.getDocumento() != null) {
            existing.setDocumento(updates.getDocumento());
        }

        if (updates.getEmail() != null) {
            existing.setEmail(updates.getEmail());
        }

        if (updates.getTelefone() != null) {
            existing.setTelefone(updates.getTelefone());
        }

        // PIX key fields - update both together or not at all
        if (updates.getChavePix() != null) {
            existing.setChavePix(updates.getChavePix());
            existing.setTipoChavePix(updates.getTipoChavePix());
        }

        if (updates.getTipo() != null) {
            existing.setTipo(updates.getTipo());
        }

        if (updates.getRegraComissaoJson() != null) {
            existing.setRegraComissaoJson(updates.getRegraComissaoJson());
        }

        if (updates.getDiariaBase() != null) {
            existing.setDiariaBase(updates.getDiariaBase());
        }

        Vendedor saved = vendedorRepository.save(existing);
        log.info("Seller updated successfully: id={}", saved.getId());
        return saved;
    }

    /**
     * Deactivate seller (soft delete).
     *
     * @param id Vendedor UUID
     * @return Deactivated Vendedor
     * @throws BusinessException if not found
     */
    @Transactional
    public Vendedor deactivateVendedor(UUID id) {
        log.info("Deactivating seller: id={}", id);

        Vendedor vendedor = findById(id);

        if (!Boolean.TRUE.equals(vendedor.getAtivo())) {
            throw new BusinessException("Vendedor já está inativo");
        }

        vendedor.setAtivo(false);
        Vendedor saved = vendedorRepository.save(vendedor);

        log.info("Seller deactivated successfully: id={}", id);
        return saved;
    }

    /**
     * Reactivate previously deactivated seller.
     *
     * @param id Vendedor UUID
     * @return Reactivated Vendedor
     * @throws BusinessException if not found
     */
    @Transactional
    public Vendedor reactivateVendedor(UUID id) {
        log.info("Reactivating seller: id={}", id);

        Vendedor vendedor = findById(id);

        if (Boolean.TRUE.equals(vendedor.getAtivo())) {
            throw new BusinessException("Vendedor já está ativo");
        }

        vendedor.setAtivo(true);
        Vendedor saved = vendedorRepository.save(vendedor);

        log.info("Seller reactivated successfully: id={}", id);
        return saved;
    }

    // ========== NOVOS MÉTODOS PARA RESUMO DE COMISSÕES ==========

    /**
     * List all sellers with commission summary.
     *
     * @param tenantId Tenant UUID
     * @param includeInactive Include inactive sellers
     * @return List of VendedorResumoResponse
     */
    @Transactional(readOnly = true)
    public List<VendedorResumoResponse> listSellersWithSummary(UUID tenantId, boolean includeInactive) {
        log.debug("Listing sellers with commission summary for tenant: {}", tenantId);

        List<Vendedor> vendedores = includeInactive
                ? vendedorRepository.findAll()
                : vendedorRepository.findAllActive();

        return vendedores.stream()
                .map(v -> buildResumoResponse(tenantId, v))
                .collect(Collectors.toList());
    }

    /**
     * Get seller details with commission summary and bonus status.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Vendedor UUID
     * @return VendedorDetalheResponse
     */
    @Transactional(readOnly = true)
    public VendedorDetalheResponse getSellerDetails(UUID tenantId, UUID vendedorId) {
        log.debug("Getting seller details for vendedor: {}", vendedorId);

        Vendedor vendedor = findById(vendedorId);
        return buildDetalheResponse(tenantId, vendedor);
    }

    /**
     * Build resumo response with commission totals.
     */
    private VendedorResumoResponse buildResumoResponse(UUID tenantId, Vendedor vendedor) {
        BigDecimal totalPendentes = comissaoQueryService.sumComissoesPendentesByVendedor(tenantId, vendedor.getId());
        BigDecimal totalAprovadas = comissaoQueryService.sumComissoesAprovadasByVendedor(tenantId, vendedor.getId());
        BigDecimal totalPagas = comissaoQueryService.sumComissoesPagasAllTimeByVendedor(tenantId, vendedor.getId());
        Long qtdLocacoes = comissaoQueryService.countLocacoesByVendedor(tenantId, vendedor.getId());

        return VendedorResumoResponse.builder()
                .id(vendedor.getId())
                .nome(vendedor.getNome())
                .email(vendedor.getEmail())
                .tipo(vendedor.getTipo())
                .ativo(vendedor.getAtivo())
                .diariaBase(vendedor.getDiariaBase())
                .totalPendentes(totalPendentes != null ? totalPendentes : BigDecimal.ZERO)
                .totalAprovadas(totalAprovadas != null ? totalAprovadas : BigDecimal.ZERO)
                .totalPagas(totalPagas != null ? totalPagas : BigDecimal.ZERO)
                .qtdLocacoes(qtdLocacoes != null ? qtdLocacoes : 0L)
                .build();
    }

    /**
     * Build detalhe response with commission totals and bonus status.
     */
    private VendedorDetalheResponse buildDetalheResponse(UUID tenantId, Vendedor vendedor) {
        BigDecimal totalPendentes = comissaoQueryService.sumComissoesPendentesByVendedor(tenantId, vendedor.getId());
        BigDecimal totalAprovadas = comissaoQueryService.sumComissoesAprovadasByVendedor(tenantId, vendedor.getId());
        BigDecimal totalPagas = comissaoQueryService.sumComissoesPagasAllTimeByVendedor(tenantId, vendedor.getId());
        Long qtdLocacoes = comissaoQueryService.countLocacoesByVendedor(tenantId, vendedor.getId());
        Long qtdAcimaPrecoBase = comissaoQueryService.countVendasAcimaPrecoBaseByVendedor(tenantId, vendedor.getId());

        // Calcular status do bonus
        VendedorDetalheResponse.BonusStatusResponse bonusStatus = calculateBonusStatus(tenantId, qtdAcimaPrecoBase);

        return VendedorDetalheResponse.builder()
                .id(vendedor.getId())
                .tenantId(vendedor.getTenantId())
                .nome(vendedor.getNome())
                .documento(vendedor.getDocumento())
                .email(vendedor.getEmail())
                .telefone(vendedor.getTelefone())
                .tipo(vendedor.getTipo())
                .ativo(vendedor.getAtivo())
                .createdAt(vendedor.getCreatedAt())
                .updatedAt(vendedor.getUpdatedAt())
                .totalPendentes(totalPendentes != null ? totalPendentes : BigDecimal.ZERO)
                .totalAprovadas(totalAprovadas != null ? totalAprovadas : BigDecimal.ZERO)
                .totalPagas(totalPagas != null ? totalPagas : BigDecimal.ZERO)
                .qtdLocacoes(qtdLocacoes != null ? qtdLocacoes : 0L)
                .qtdAcimaPrecoBase(qtdAcimaPrecoBase != null ? qtdAcimaPrecoBase : 0L)
                .bonusStatus(bonusStatus)
                .build();
    }

    /**
     * Calculate bonus status for a seller.
     */
    private VendedorDetalheResponse.BonusStatusResponse calculateBonusStatus(UUID tenantId, Long qtdAcimaPrecoBase) {
        // Buscar configuração do tenant
        Tenant tenant = tenantQueryService.findById(tenantId);
        ComissaoConfig config = tenant != null ? tenant.getComissaoConfig() : null;

        if (config == null || !Boolean.TRUE.equals(config.bonusAtivo())) {
            return VendedorDetalheResponse.BonusStatusResponse.builder()
                    .elegivel(false)
                    .metaAtual(qtdAcimaPrecoBase != null ? qtdAcimaPrecoBase : 0L)
                    .metaNecessaria(0)
                    .valorBonus(BigDecimal.ZERO)
                    .vendasFaltando(0L)
                    .build();
        }

        long metaAtual = qtdAcimaPrecoBase != null ? qtdAcimaPrecoBase : 0L;
        int metaNecessaria = config.bonusMetaVendas() != null ? config.bonusMetaVendas() : 50;
        BigDecimal valorBonus = config.bonusValor() != null ? config.bonusValor() : BigDecimal.ZERO;

        // Calcular quantas vendas faltam para o próximo bonus
        // O bonus é acumulativo contínuo, então se meta=50, bonus em 50, 100, 150...
        long proximaMeta = ((metaAtual / metaNecessaria) + 1) * metaNecessaria;
        long vendasFaltando = proximaMeta - metaAtual;

        boolean elegivel = metaAtual > 0 && metaAtual >= metaNecessaria;

        return VendedorDetalheResponse.BonusStatusResponse.builder()
                .elegivel(elegivel)
                .metaAtual(metaAtual)
                .metaNecessaria(metaNecessaria)
                .valorBonus(valorBonus)
                .vendasFaltando(vendasFaltando)
                .build();
    }
}
