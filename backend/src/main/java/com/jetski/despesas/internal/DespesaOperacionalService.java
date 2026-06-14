package com.jetski.despesas.internal;

import com.jetski.despesas.api.dto.DespesaOperacionalRequest;
import com.jetski.despesas.domain.CategoriaDespesa;
import com.jetski.despesas.domain.DespesaOperacional;
import com.jetski.despesas.domain.StatusDespesa;
import com.jetski.despesas.internal.repository.DespesaOperacionalRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for DespesaOperacional (Operational Expenses)
 *
 * <p>Gerencia despesas operacionais do dia a dia nao vinculadas a locacoes.</p>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DespesaOperacionalService {

    private final DespesaOperacionalRepository repository;

    // ========== CRUD ==========

    /**
     * Cria uma nova despesa operacional
     */
    public DespesaOperacional criar(UUID tenantId, DespesaOperacionalRequest request) {
        log.info("Criando despesa operacional: tenant={}, categoria={}, valor={}",
                tenantId, request.getCategoria(), request.getValor());

        DespesaOperacional despesa = DespesaOperacional.builder()
                .tenantId(tenantId)
                .dtReferencia(request.getDtReferencia())
                .categoria(request.getCategoria())
                .descricao(request.getDescricao())
                .valor(request.getValor())
                .responsavelId(request.getResponsavelId())
                .observacoes(request.getObservacoes())
                .status(StatusDespesa.PENDENTE)
                .build();

        DespesaOperacional saved = repository.save(despesa);
        log.info("Despesa operacional criada: id={}", saved.getId());

        return saved;
    }

    /**
     * Busca despesa por ID
     */
    @Transactional(readOnly = true)
    public DespesaOperacional buscarPorId(UUID tenantId, UUID id) {
        return repository.findById(id)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Despesa operacional nao encontrada: " + id));
    }

    /**
     * Atualiza uma despesa (somente se PENDENTE)
     */
    public DespesaOperacional atualizar(UUID tenantId, UUID id, DespesaOperacionalRequest request) {
        DespesaOperacional despesa = buscarPorId(tenantId, id);

        if (!despesa.podeEditar()) {
            throw new BusinessException("Despesa nao pode ser editada no status atual: " + despesa.getStatus());
        }

        despesa.setDtReferencia(request.getDtReferencia());
        despesa.setCategoria(request.getCategoria());
        despesa.setDescricao(request.getDescricao());
        despesa.setValor(request.getValor());
        despesa.setResponsavelId(request.getResponsavelId());
        despesa.setObservacoes(request.getObservacoes());

        return repository.save(despesa);
    }

    /**
     * Exclui uma despesa (somente se PENDENTE)
     */
    public void excluir(UUID tenantId, UUID id) {
        DespesaOperacional despesa = buscarPorId(tenantId, id);

        if (!despesa.podeEditar()) {
            throw new BusinessException("Despesa nao pode ser excluida no status atual: " + despesa.getStatus());
        }

        repository.delete(despesa);
        log.info("Despesa operacional excluida: id={}", id);
    }

    // ========== Listagens ==========

    /**
     * Lista despesas por periodo
     */
    @Transactional(readOnly = true)
    public List<DespesaOperacional> listarPorPeriodo(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        return repository.findByTenantIdAndDtReferenciaBetweenOrderByDtReferenciaDesc(
                tenantId, dataInicio, dataFim);
    }

    /**
     * Lista despesas de um dia especifico
     */
    @Transactional(readOnly = true)
    public List<DespesaOperacional> listarPorDia(UUID tenantId, LocalDate data) {
        return repository.findByTenantIdAndDtReferenciaOrderByCreatedAtDesc(tenantId, data);
    }

    /**
     * Lista despesas por categoria
     */
    @Transactional(readOnly = true)
    public List<DespesaOperacional> listarPorCategoria(UUID tenantId, CategoriaDespesa categoria) {
        return repository.findByTenantIdAndCategoriaOrderByDtReferenciaDesc(tenantId, categoria);
    }

    /**
     * Lista despesas por status
     */
    @Transactional(readOnly = true)
    public List<DespesaOperacional> listarPorStatus(UUID tenantId, StatusDespesa status) {
        return repository.findByTenantIdAndStatusOrderByDtReferenciaDesc(tenantId, status);
    }

    /**
     * Lista despesas pendentes de aprovacao
     */
    @Transactional(readOnly = true)
    public List<DespesaOperacional> listarPendentesAprovacao(UUID tenantId) {
        return repository.findPendentesAprovacao(tenantId);
    }

    /**
     * Lista despesas aguardando pagamento
     */
    @Transactional(readOnly = true)
    public List<DespesaOperacional> listarAguardandoPagamento(UUID tenantId) {
        return repository.findAguardandoPagamento(tenantId);
    }

    // ========== Workflow ==========

    /**
     * Aprova uma despesa
     */
    public DespesaOperacional aprovar(UUID tenantId, UUID id, UUID aprovadorId) {
        DespesaOperacional despesa = buscarPorId(tenantId, id);

        despesa.aprovar(aprovadorId);

        DespesaOperacional saved = repository.save(despesa);
        log.info("Despesa aprovada: id={}, aprovadoPor={}", id, aprovadorId);

        return saved;
    }

    /**
     * Rejeita uma despesa
     */
    public DespesaOperacional rejeitar(UUID tenantId, UUID id, UUID aprovadorId, String motivo) {
        DespesaOperacional despesa = buscarPorId(tenantId, id);

        despesa.rejeitar(aprovadorId, motivo);

        DespesaOperacional saved = repository.save(despesa);
        log.info("Despesa rejeitada: id={}, motivo={}", id, motivo);

        return saved;
    }

    /**
     * Marca uma despesa como paga
     */
    public DespesaOperacional marcarComoPaga(UUID tenantId, UUID id, UUID pagadorId, String referencia) {
        DespesaOperacional despesa = buscarPorId(tenantId, id);

        despesa.marcarComoPaga(pagadorId, referencia);

        DespesaOperacional saved = repository.save(despesa);
        log.info("Despesa paga: id={}, referencia={}", id, referencia);

        return saved;
    }

    // ========== Agregacoes para fechamento ==========

    /**
     * Soma total de despesas de um dia (APROVADA ou PAGA)
     */
    @Transactional(readOnly = true)
    public BigDecimal somarDespesasDia(UUID tenantId, LocalDate data) {
        return repository.sumByTenantIdAndDtReferencia(tenantId, data);
    }

    /**
     * Soma total de despesas de um periodo (APROVADA ou PAGA)
     */
    @Transactional(readOnly = true)
    public BigDecimal somarDespesasPeriodo(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        return repository.sumByTenantIdAndPeriodo(tenantId, dataInicio, dataFim);
    }

    /**
     * Soma despesas por categoria em um periodo
     */
    @Transactional(readOnly = true)
    public List<Object[]> somarPorCategoria(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        return repository.sumByCategoriaPeriodo(tenantId, dataInicio, dataFim);
    }

    // ========== Dashboard ==========

    /**
     * Conta despesas pendentes
     */
    @Transactional(readOnly = true)
    public long contarPendentes(UUID tenantId) {
        return repository.countPendentes(tenantId);
    }

    /**
     * Soma valor das despesas pendentes
     */
    @Transactional(readOnly = true)
    public BigDecimal somarValorPendentes(UUID tenantId) {
        return repository.sumValorPendentes(tenantId);
    }

    /**
     * Conta despesas aguardando pagamento
     */
    @Transactional(readOnly = true)
    public long contarAguardandoPagamento(UUID tenantId) {
        return repository.countAguardandoPagamento(tenantId);
    }

    /**
     * Soma valor das despesas aguardando pagamento
     */
    @Transactional(readOnly = true)
    public BigDecimal somarValorAguardandoPagamento(UUID tenantId) {
        return repository.sumValorAguardandoPagamento(tenantId);
    }
}
