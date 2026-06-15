package com.jetski.pagamentos.internal;

import com.jetski.bonus.domain.BonusVendedor;
import com.jetski.bonus.domain.StatusBonus;
import com.jetski.bonus.api.BonusService;
import com.jetski.comissoes.domain.Comissao;
import com.jetski.comissoes.domain.StatusComissao;
import com.jetski.comissoes.api.ComissaoQueryService;
import com.jetski.locacoes.domain.PresencaVendedor;
import com.jetski.locacoes.domain.Vendedor;
import com.jetski.locacoes.api.PresencaVendedorQueryService;
import com.jetski.locacoes.api.VendedorService;
import com.jetski.pagamentos.api.dto.DetalhesPendenciasResponse;
import com.jetski.pagamentos.api.dto.ItemPendente;
import com.jetski.pagamentos.api.dto.PagamentoVendedorResponse;
import com.jetski.pagamentos.api.dto.PendenciasPagamentoResponse;
import com.jetski.pagamentos.api.dto.RegistrarPagamentoRequest;
import com.jetski.pagamentos.domain.PagamentoVendedor;
import com.jetski.pagamentos.internal.repository.PagamentoVendedorRepository;
import com.jetski.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service: PagamentoVendedorService
 *
 * Handles seller payment operations:
 * - List pending payments (approved commissions + unpaid diárias)
 * - Register bulk payment
 * - List payment history
 *
 * @author Jetski Team
 * @since 0.12.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PagamentoVendedorService {

    private final PagamentoVendedorRepository pagamentoRepository;
    private final VendedorService vendedorService;
    private final ComissaoQueryService comissaoQueryService;
    private final PresencaVendedorQueryService presencaQueryService;
    private final BonusService bonusService;

    /**
     * List all sellers with pending payments.
     *
     * @param tenantId Tenant UUID
     * @return List of sellers with pending commissions and/or diárias
     */
    @Transactional(readOnly = true)
    public List<PendenciasPagamentoResponse> listarPendencias(UUID tenantId) {
        log.debug("Listing pending payments for tenant: {}", tenantId);

        List<Vendedor> vendedores = vendedorService.listActiveSellers();

        return vendedores.stream()
                .map(v -> buildPendenciasResponse(tenantId, v))
                .filter(p -> p.getValorTotal().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(PendenciasPagamentoResponse::getValorTotal).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get pending payments for a specific seller.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @return Pending payments summary
     */
    @Transactional(readOnly = true)
    public PendenciasPagamentoResponse getPendenciasVendedor(UUID tenantId, UUID vendedorId) {
        log.debug("Getting pending payments for vendor: {}", vendedorId);

        Vendedor vendedor = vendedorService.findById(vendedorId);

        return buildPendenciasResponse(tenantId, vendedor);
    }

    /**
     * Register a payment for a seller.
     * Supports both full payment (all pending items) and partial payment (selected items).
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @param request Payment details (includes optional item IDs for partial payment)
     * @param pagoPor User who made the payment
     * @return Created payment record
     */
    @Transactional
    public PagamentoVendedor registrarPagamento(UUID tenantId, UUID vendedorId,
                                                  RegistrarPagamentoRequest request, UUID pagoPor) {
        log.info("Registering payment for vendor: {} by user: {}", vendedorId, pagoPor);

        // 1. Find vendor
        Vendedor vendedor = vendedorService.findById(vendedorId);

        // 2. Determine items to pay (full or partial)
        boolean pagamentoParcial = request.isPagamentoParcial();
        List<Comissao> comissoes;
        List<PresencaVendedor> diarias;
        List<BonusVendedor> bonus;

        if (pagamentoParcial) {
            // Partial payment - get only selected items
            comissoes = getSelectedComissoes(tenantId, vendedorId, request.getComissaoIds());
            diarias = getSelectedDiarias(tenantId, vendedorId, request.getPresencaIds());
            bonus = getSelectedBonus(tenantId, vendedorId, request.getBonusIds());
        } else {
            // Full payment - get all pending items
            comissoes = comissaoQueryService
                    .findByVendedorAndStatus(
                            tenantId, vendedorId, StatusComissao.APROVADA);
            diarias = presencaQueryService.findNaoPagasByVendedor(tenantId, vendedorId);
            bonus = bonusService.findAprovadosByVendedor(tenantId, vendedorId);
        }

        // 3. Calculate totals
        BigDecimal valorComissoes = comissoes.stream()
                .map(Comissao::getValorComissao)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorDiarias = diarias.stream()
                .map(PresencaVendedor::getValorEfetivo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorBonus = bonus.stream()
                .map(BonusVendedor::getValorBonus)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Validate there's something to pay
        BigDecimal valorTotal = valorComissoes.add(valorDiarias).add(valorBonus);
        if (valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Não há pendências para pagar para este vendedor");
        }

        // 5. Calculate period
        LocalDate periodoInicio = null;
        LocalDate periodoFim = null;

        if (!comissoes.isEmpty()) {
            periodoInicio = comissoes.stream()
                    .map(c -> c.getDataLocacao().atZone(ZoneId.systemDefault()).toLocalDate())
                    .min(LocalDate::compareTo)
                    .orElse(null);
            periodoFim = comissoes.stream()
                    .map(c -> c.getDataLocacao().atZone(ZoneId.systemDefault()).toLocalDate())
                    .max(LocalDate::compareTo)
                    .orElse(null);
        }

        if (!diarias.isEmpty()) {
            LocalDate minDiaria = diarias.stream()
                    .map(PresencaVendedor::getDtReferencia)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            LocalDate maxDiaria = diarias.stream()
                    .map(PresencaVendedor::getDtReferencia)
                    .max(LocalDate::compareTo)
                    .orElse(null);

            if (periodoInicio == null || (minDiaria != null && minDiaria.isBefore(periodoInicio))) {
                periodoInicio = minDiaria;
            }
            if (periodoFim == null || (maxDiaria != null && maxDiaria.isAfter(periodoFim))) {
                periodoFim = maxDiaria;
            }
        }

        if (!bonus.isEmpty()) {
            LocalDate minBonus = bonus.stream()
                    .map(b -> b.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                    .min(LocalDate::compareTo)
                    .orElse(null);
            LocalDate maxBonus = bonus.stream()
                    .map(b -> b.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                    .max(LocalDate::compareTo)
                    .orElse(null);

            if (periodoInicio == null || (minBonus != null && minBonus.isBefore(periodoInicio))) {
                periodoInicio = minBonus;
            }
            if (periodoFim == null || (maxBonus != null && maxBonus.isAfter(periodoFim))) {
                periodoFim = maxBonus;
            }
        }

        // 6. Create payment record
        PagamentoVendedor pagamento = PagamentoVendedor.builder()
                .tenantId(tenantId)
                .vendedorId(vendedorId)
                .vendedorNome(vendedor.getNome())
                .tipoPagamento(request.getTipoPagamento())
                .valorComissoes(valorComissoes)
                .valorDiarias(valorDiarias)
                .valorBonus(valorBonus)
                .valorTotal(valorTotal)
                .chavePix(vendedor.getChavePix())
                .tipoChavePix(vendedor.getTipoChavePix())
                .referenciaPagamento(request.getReferenciaPagamento())
                .qtdComissoes(comissoes.size())
                .qtdDiarias(diarias.size())
                .qtdBonus(bonus.size())
                .periodoInicio(periodoInicio)
                .periodoFim(periodoFim)
                .pagoPor(pagoPor)
                .observacoes(request.getObservacoes())
                .build();

        pagamento = pagamentoRepository.save(pagamento);
        UUID pagamentoId = pagamento.getId();
        Instant agora = Instant.now();

        // 7. Mark commissions as PAGA
        for (Comissao comissao : comissoes) {
            comissao.setStatus(StatusComissao.PAGA);
            comissao.setPagoPor(pagoPor);
            comissao.setPagoEm(agora);
            comissao.setReferenciaPagamento(request.getReferenciaPagamento());
            comissaoQueryService.salvar(comissao);
        }

        // 8. Mark diárias as paid
        for (PresencaVendedor presenca : diarias) {
            presenca.setPagamentoId(pagamentoId);
            presenca.setPagoEm(agora);
            presenca.setPagoPor(pagoPor);
            presencaQueryService.salvar(presenca);
        }

        // 9. Mark bonuses as PAGO
        for (BonusVendedor b : bonus) {
            b.setStatus(StatusBonus.PAGO);
            b.setPagoPor(pagoPor);
            b.setPagoEm(agora);
            b.setPagamentoId(pagamentoId);
            b.setReferenciaPagamento(request.getReferenciaPagamento());
            bonusService.salvar(b);
        }

        log.info("Payment registered: id={}, vendor={}, type={}, total={}, comissoes={}, diarias={}, bonus={}",
                pagamento.getId(), vendedorId, request.getTipoPagamento(),
                valorTotal, comissoes.size(), diarias.size(), bonus.size());

        return pagamento;
    }

    // ========== Partial Payment Helpers ==========

    private List<Comissao> getSelectedComissoes(UUID tenantId, UUID vendedorId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return comissaoQueryService.findByIds(ids).stream()
                .filter(c -> c.getTenantId().equals(tenantId)
                          && c.getVendedorId().equals(vendedorId)
                          && c.getStatus() == StatusComissao.APROVADA)
                .toList();
    }

    private List<PresencaVendedor> getSelectedDiarias(UUID tenantId, UUID vendedorId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return presencaQueryService.findByIds(ids).stream()
                .filter(p -> p.getTenantId().equals(tenantId)
                          && p.getVendedorId().equals(vendedorId)
                          && p.getPagoEm() == null)
                .toList();
    }

    private List<BonusVendedor> getSelectedBonus(UUID tenantId, UUID vendedorId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return bonusService.findByIds(ids).stream()
                .filter(b -> b.getTenantId().equals(tenantId)
                          && b.getVendedorId().equals(vendedorId)
                          && b.getStatus() == StatusBonus.APROVADO)
                .toList();
    }

    /**
     * List payment history for all sellers.
     *
     * @param tenantId Tenant UUID
     * @return List of payment records
     */
    @Transactional(readOnly = true)
    public List<PagamentoVendedorResponse> listarHistorico(UUID tenantId) {
        log.debug("Listing payment history for tenant: {}", tenantId);

        return pagamentoRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * List payment history for a specific seller.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @return List of payment records
     */
    @Transactional(readOnly = true)
    public List<PagamentoVendedorResponse> listarHistoricoVendedor(UUID tenantId, UUID vendedorId) {
        log.debug("Listing payment history for vendor: {}", vendedorId);

        return pagamentoRepository.findByTenantIdAndVendedorIdOrderByCreatedAtDesc(tenantId, vendedorId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific payment record.
     *
     * @param tenantId Tenant UUID
     * @param pagamentoId Payment UUID
     * @return Payment record
     */
    @Transactional(readOnly = true)
    public PagamentoVendedor getPagamento(UUID tenantId, UUID pagamentoId) {
        return pagamentoRepository.findById(pagamentoId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessException("Pagamento não encontrado"));
    }

    /**
     * Update payment receipt URL after S3 upload confirmation.
     *
     * @param pagamentoId Payment UUID
     * @param comprovanteUrl Public URL of the receipt
     * @param comprovanteS3Key S3 key of the receipt
     */
    @Transactional
    public void updateComprovante(UUID pagamentoId, String comprovanteUrl, String comprovanteS3Key) {
        PagamentoVendedor pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new BusinessException("Pagamento não encontrado"));

        pagamento.setComprovanteUrl(comprovanteUrl);
        pagamento.setComprovanteS3Key(comprovanteS3Key);
        pagamentoRepository.save(pagamento);

        log.info("Receipt updated for payment: {}", pagamentoId);
    }

    // ========== Private Helper Methods ==========

    private PendenciasPagamentoResponse buildPendenciasResponse(UUID tenantId, Vendedor vendedor) {
        // Get approved commissions
        BigDecimal valorComissoes = comissaoQueryService
                .sumComissoesAprovadasByVendedor(tenantId, vendedor.getId());
        if (valorComissoes == null) valorComissoes = BigDecimal.ZERO;

        int qtdComissoes = comissaoQueryService
                .countComissoesAprovadasByVendedor(tenantId, vendedor.getId());

        // Get unpaid diárias
        BigDecimal valorDiarias = presencaQueryService
                .sumDiariasNaoPagasByVendedor(tenantId, vendedor.getId());
        if (valorDiarias == null) valorDiarias = BigDecimal.ZERO;

        int qtdDiarias = presencaQueryService
                .countDiariasNaoPagasByVendedor(tenantId, vendedor.getId());

        // Get approved bonuses
        BigDecimal valorBonus = bonusService
                .sumBonusAprovadosByVendedor(tenantId, vendedor.getId());
        if (valorBonus == null) valorBonus = BigDecimal.ZERO;

        int qtdBonus = bonusService
                .countBonusAprovadosByVendedor(tenantId, vendedor.getId());

        BigDecimal valorTotal = valorComissoes.add(valorDiarias).add(valorBonus);

        return PendenciasPagamentoResponse.builder()
                .vendedorId(vendedor.getId())
                .vendedorNome(vendedor.getNome())
                .vendedorEmail(vendedor.getEmail())
                .chavePix(vendedor.getChavePix())
                .tipoChavePix(vendedor.getTipoChavePix())
                .temPixCadastrado(vendedor.getChavePix() != null && !vendedor.getChavePix().isBlank())
                .valorComissoes(valorComissoes)
                .qtdComissoes(qtdComissoes)
                .valorDiarias(valorDiarias)
                .qtdDiarias(qtdDiarias)
                .valorBonus(valorBonus)
                .qtdBonus(qtdBonus)
                .valorTotal(valorTotal)
                .qtdTotal(qtdComissoes + qtdDiarias + qtdBonus)
                .build();
    }

    /**
     * Get detailed pending items for a specific seller.
     *
     * @param tenantId Tenant UUID
     * @param vendedorId Seller UUID
     * @return Detailed pending items list
     */
    @Transactional(readOnly = true)
    public DetalhesPendenciasResponse getDetalhesPendencias(UUID tenantId, UUID vendedorId) {
        log.debug("Getting detailed pending items for vendor: {}", vendedorId);

        Vendedor vendedor = vendedorService.findById(vendedorId);

        List<ItemPendente> itens = new ArrayList<>();

        // Add approved commissions
        comissaoQueryService.findByVendedorAndStatus(
                tenantId, vendedorId, StatusComissao.APROVADA)
                .forEach(c -> itens.add(ItemPendente.builder()
                        .id(c.getId())
                        .tipo("COMISSAO")
                        .dataReferencia(c.getDataLocacao().atZone(ZoneId.systemDefault()).toLocalDate())
                        .descricao("Comissão locação #" + c.getLocacaoId().toString().substring(0, 8))
                        .valor(c.getValorComissao())
                        .build()));

        // Add unpaid diárias
        presencaQueryService.findNaoPagasByVendedor(tenantId, vendedorId)
                .forEach(p -> itens.add(ItemPendente.builder()
                        .id(p.getId())
                        .tipo("DIARIA")
                        .dataReferencia(p.getDtReferencia())
                        .descricao("Diária " + p.getTipo())
                        .valor(p.getValorEfetivo())
                        .build()));

        // Add approved bonuses
        bonusService.findAprovadosByVendedor(tenantId, vendedorId)
                .forEach(b -> itens.add(ItemPendente.builder()
                        .id(b.getId())
                        .tipo("BONUS")
                        .dataReferencia(b.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                        .descricao("Bônus meta " + b.getMetaAtingida())
                        .valor(b.getValorBonus())
                        .build()));

        BigDecimal total = itens.stream()
                .map(ItemPendente::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DetalhesPendenciasResponse.builder()
                .vendedorId(vendedorId)
                .vendedorNome(vendedor.getNome())
                .chavePix(vendedor.getChavePix())
                .tipoChavePix(vendedor.getTipoChavePix())
                .temPixCadastrado(vendedor.getChavePix() != null && !vendedor.getChavePix().isBlank())
                .itens(itens)
                .valorTotal(total)
                .build();
    }

    private PagamentoVendedorResponse toResponse(PagamentoVendedor pagamento) {
        return PagamentoVendedorResponse.builder()
                .id(pagamento.getId())
                .tenantId(pagamento.getTenantId())
                .vendedorId(pagamento.getVendedorId())
                .vendedorNome(pagamento.getVendedorNome())
                .tipoPagamento(pagamento.getTipoPagamento())
                .valorComissoes(pagamento.getValorComissoes())
                .valorDiarias(pagamento.getValorDiarias())
                .valorBonus(pagamento.getValorBonus())
                .valorTotal(pagamento.getValorTotal())
                .chavePix(pagamento.getChavePix())
                .tipoChavePix(pagamento.getTipoChavePix())
                .referenciaPagamento(pagamento.getReferenciaPagamento())
                .comprovanteUrl(pagamento.getComprovanteUrl())
                .qtdComissoes(pagamento.getQtdComissoes())
                .qtdDiarias(pagamento.getQtdDiarias())
                .qtdBonus(pagamento.getQtdBonus())
                .periodoInicio(pagamento.getPeriodoInicio())
                .periodoFim(pagamento.getPeriodoFim())
                .pagoPor(pagamento.getPagoPor())
                .observacoes(pagamento.getObservacoes())
                .createdAt(pagamento.getCreatedAt())
                .build();
    }
}
