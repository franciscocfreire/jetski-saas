package com.jetski.manutencao.api;

import com.jetski.despesas.domain.StatusDespesa;
import com.jetski.manutencao.domain.DespesaManutencao;
import com.jetski.manutencao.domain.OSManutencao;
import com.jetski.manutencao.internal.repository.DespesaManutencaoRepository;
import com.jetski.manutencao.internal.repository.OSManutencaoRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for DespesaManutencao (Maintenance Expenses)
 *
 * <p>Gerencia despesas de manutencao com suporte a parcelamento.</p>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DespesaManutencaoService {

    private final DespesaManutencaoRepository repository;
    private final OSManutencaoRepository osManutencaoRepository;

    // ========== Geracao de despesas ==========

    /**
     * Gera despesas parceladas para uma OS de manutencao.
     * Distribui o valor total em N parcelas mensais.
     *
     * @param tenantId ID do tenant
     * @param osManutencaoId ID da OS de manutencao
     * @param numeroParcelas Numero de parcelas (1-12)
     * @param primeiroVencimento Data do primeiro vencimento
     * @param observacoes Observacoes opcionais
     * @return Lista de despesas geradas
     */
    public List<DespesaManutencao> gerarDespesasParceladas(
            UUID tenantId,
            UUID osManutencaoId,
            int numeroParcelas,
            LocalDate primeiroVencimento,
            String observacoes) {

        log.info("Gerando despesas parceladas: tenant={}, osId={}, parcelas={}, primeiroVencimento={}",
                tenantId, osManutencaoId, numeroParcelas, primeiroVencimento);

        // Validacoes
        if (numeroParcelas < 1 || numeroParcelas > 12) {
            throw new BusinessException("Numero de parcelas deve ser entre 1 e 12");
        }

        if (primeiroVencimento == null) {
            throw new BusinessException("Data do primeiro vencimento e obrigatoria");
        }

        // Busca a OS
        OSManutencao os = osManutencaoRepository.findById(osManutencaoId)
                .filter(o -> o.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("OS de manutencao nao encontrada: " + osManutencaoId));

        // Verifica se a OS esta concluida
        if (!"CONCLUIDA".equals(os.getStatus().name())) {
            throw new BusinessException("Somente OS concluidas podem gerar despesas. Status atual: " + os.getStatus());
        }

        // Verifica se ja existem despesas ativas para esta OS
        if (repository.existsDespesaAtivaByOsManutencaoId(tenantId, osManutencaoId)) {
            throw new BusinessException("Ja existem despesas geradas para esta OS de manutencao");
        }

        // Calcula valor total (pecas + mao de obra)
        BigDecimal valorPecas = os.getValorPecas() != null ? os.getValorPecas() : BigDecimal.ZERO;
        BigDecimal valorMaoObra = os.getValorMaoObra() != null ? os.getValorMaoObra() : BigDecimal.ZERO;
        BigDecimal valorTotal = valorPecas.add(valorMaoObra);

        if (valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor total da OS deve ser maior que zero para gerar despesas");
        }

        // Calcula valor de cada parcela
        BigDecimal valorParcela = valorTotal.divide(BigDecimal.valueOf(numeroParcelas), 2, RoundingMode.DOWN);
        BigDecimal valorUltimaParcela = valorTotal.subtract(valorParcela.multiply(BigDecimal.valueOf(numeroParcelas - 1)));

        // Gera as parcelas
        List<DespesaManutencao> despesas = new ArrayList<>();
        LocalDate vencimento = primeiroVencimento;

        for (int i = 1; i <= numeroParcelas; i++) {
            BigDecimal valor = (i == numeroParcelas) ? valorUltimaParcela : valorParcela;

            DespesaManutencao despesa = DespesaManutencao.builder()
                    .tenantId(tenantId)
                    .osManutencaoId(osManutencaoId)
                    .dtVencimento(vencimento)
                    .numeroParcela(i)
                    .totalParcelas(numeroParcelas)
                    .valor(valor)
                    .status(StatusDespesa.PENDENTE)
                    .observacoes(observacoes)
                    .build();

            despesas.add(repository.save(despesa));
            vencimento = vencimento.plusMonths(1);
        }

        log.info("Geradas {} despesas de manutencao para OS {}, valor total: {}",
                despesas.size(), osManutencaoId, valorTotal);

        return despesas;
    }

    /**
     * Gera despesas parceladas JÁ APROVADAS para uma OS de manutencao.
     * Usado quando a OS é concluída automaticamente - despesas são geradas como APROVADAS
     * para aparecerem imediatamente no dashboard financeiro.
     *
     * @param tenantId ID do tenant
     * @param osManutencaoId ID da OS de manutencao
     * @param numeroParcelas Numero de parcelas (1-12)
     * @param primeiroVencimento Data do primeiro vencimento
     * @param observacoes Observacoes opcionais
     * @return Lista de despesas geradas (já aprovadas)
     */
    public List<DespesaManutencao> gerarDespesasParceladasAprovadas(
            UUID tenantId,
            UUID osManutencaoId,
            int numeroParcelas,
            LocalDate primeiroVencimento,
            String observacoes) {

        log.info("Gerando despesas parceladas APROVADAS: tenant={}, osId={}, parcelas={}, primeiroVencimento={}",
                tenantId, osManutencaoId, numeroParcelas, primeiroVencimento);

        // Validacoes
        if (numeroParcelas < 1 || numeroParcelas > 12) {
            throw new BusinessException("Numero de parcelas deve ser entre 1 e 12");
        }

        if (primeiroVencimento == null) {
            throw new BusinessException("Data do primeiro vencimento e obrigatoria");
        }

        // Busca a OS
        OSManutencao os = osManutencaoRepository.findById(osManutencaoId)
                .filter(o -> o.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("OS de manutencao nao encontrada: " + osManutencaoId));

        // Verifica se ja existem despesas ativas para esta OS
        if (repository.existsDespesaAtivaByOsManutencaoId(tenantId, osManutencaoId)) {
            log.warn("Ja existem despesas geradas para OS {} - ignorando geracao automatica", osManutencaoId);
            return List.of();
        }

        // Calcula valor total (pecas + mao de obra)
        BigDecimal valorPecas = os.getValorPecas() != null ? os.getValorPecas() : BigDecimal.ZERO;
        BigDecimal valorMaoObra = os.getValorMaoObra() != null ? os.getValorMaoObra() : BigDecimal.ZERO;
        BigDecimal valorTotal = valorPecas.add(valorMaoObra);

        if (valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Valor total da OS {} e zero - nao gerando despesas", osManutencaoId);
            return List.of();
        }

        // Calcula valor de cada parcela
        BigDecimal valorParcela = valorTotal.divide(BigDecimal.valueOf(numeroParcelas), 2, RoundingMode.DOWN);
        BigDecimal valorUltimaParcela = valorTotal.subtract(valorParcela.multiply(BigDecimal.valueOf(numeroParcelas - 1)));

        // Gera as parcelas JÁ APROVADAS
        List<DespesaManutencao> despesas = new ArrayList<>();
        LocalDate vencimento = primeiroVencimento;

        for (int i = 1; i <= numeroParcelas; i++) {
            BigDecimal valor = (i == numeroParcelas) ? valorUltimaParcela : valorParcela;

            DespesaManutencao despesa = DespesaManutencao.builder()
                    .tenantId(tenantId)
                    .osManutencaoId(osManutencaoId)
                    .dtVencimento(vencimento)
                    .numeroParcela(i)
                    .totalParcelas(numeroParcelas)
                    .valor(valor)
                    .status(StatusDespesa.APROVADA)  // JÁ APROVADA
                    .observacoes(observacoes)
                    .build();

            despesas.add(repository.save(despesa));
            vencimento = vencimento.plusMonths(1);
        }

        log.info("Geradas {} despesas de manutencao APROVADAS para OS {}, valor total: {}",
                despesas.size(), osManutencaoId, valorTotal);

        return despesas;
    }

    // ========== Busca ==========

    /**
     * Busca despesa por ID
     */
    @Transactional(readOnly = true)
    public DespesaManutencao buscarPorId(UUID tenantId, UUID id) {
        return repository.findById(id)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Despesa de manutencao nao encontrada: " + id));
    }

    // ========== Listagens ==========

    /**
     * Lista despesas por periodo de vencimento
     */
    @Transactional(readOnly = true)
    public List<DespesaManutencao> listarPorPeriodo(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        return repository.findByTenantIdAndDtVencimentoBetweenOrderByDtVencimentoAsc(
                tenantId, dataInicio, dataFim);
    }

    /**
     * Lista despesas de uma OS especifica
     */
    @Transactional(readOnly = true)
    public List<DespesaManutencao> listarPorOS(UUID tenantId, UUID osManutencaoId) {
        return repository.findByTenantIdAndOsManutencaoIdOrderByNumeroParcelaAsc(
                tenantId, osManutencaoId);
    }

    /**
     * Lista despesas por dia especifico
     */
    @Transactional(readOnly = true)
    public List<DespesaManutencao> listarPorDia(UUID tenantId, LocalDate data) {
        return repository.findByTenantIdAndDtVencimentoOrderByCreatedAtDesc(tenantId, data);
    }

    /**
     * Lista despesas por status
     */
    @Transactional(readOnly = true)
    public List<DespesaManutencao> listarPorStatus(UUID tenantId, StatusDespesa status) {
        return repository.findByTenantIdAndStatusOrderByDtVencimentoAsc(tenantId, status);
    }

    /**
     * Lista despesas pendentes de aprovacao
     */
    @Transactional(readOnly = true)
    public List<DespesaManutencao> listarPendentesAprovacao(UUID tenantId) {
        return repository.findPendentesAprovacao(tenantId);
    }

    /**
     * Lista despesas aguardando pagamento
     */
    @Transactional(readOnly = true)
    public List<DespesaManutencao> listarAguardandoPagamento(UUID tenantId) {
        return repository.findAguardandoPagamento(tenantId);
    }

    // ========== Workflow ==========

    /**
     * Aprova uma despesa
     */
    public DespesaManutencao aprovar(UUID tenantId, UUID id, Integer membroId) {
        DespesaManutencao despesa = buscarPorId(tenantId, id);

        despesa.aprovar(membroId);

        DespesaManutencao saved = repository.save(despesa);
        log.info("Despesa de manutencao aprovada: id={}, aprovadoPor={}", id, membroId);

        return saved;
    }

    /**
     * Rejeita uma despesa
     */
    public DespesaManutencao rejeitar(UUID tenantId, UUID id, Integer membroId, String motivo) {
        DespesaManutencao despesa = buscarPorId(tenantId, id);

        despesa.rejeitar(membroId, motivo);

        DespesaManutencao saved = repository.save(despesa);
        log.info("Despesa de manutencao rejeitada: id={}, motivo={}", id, motivo);

        return saved;
    }

    /**
     * Marca uma despesa como paga
     */
    public DespesaManutencao marcarComoPaga(UUID tenantId, UUID id, Integer membroId, String referencia) {
        DespesaManutencao despesa = buscarPorId(tenantId, id);

        despesa.marcarComoPaga(membroId, referencia);

        DespesaManutencao saved = repository.save(despesa);
        log.info("Despesa de manutencao paga: id={}, referencia={}", id, referencia);

        return saved;
    }

    /**
     * Cancela uma despesa
     */
    public DespesaManutencao cancelar(UUID tenantId, UUID id, Integer membroId, String motivo) {
        DespesaManutencao despesa = buscarPorId(tenantId, id);

        despesa.cancelar(membroId, motivo);

        DespesaManutencao saved = repository.save(despesa);
        log.info("Despesa de manutencao cancelada: id={}, motivo={}", id, motivo);

        return saved;
    }

    // ========== Agregacoes para fechamento ==========

    /**
     * Soma total de despesas de um dia (APROVADA ou PAGA)
     */
    @Transactional(readOnly = true)
    public BigDecimal somarDespesasDia(UUID tenantId, LocalDate data) {
        return repository.sumByTenantIdAndDtVencimento(tenantId, data);
    }

    /**
     * Soma total de despesas de um periodo (APROVADA ou PAGA)
     */
    @Transactional(readOnly = true)
    public BigDecimal somarDespesasPeriodo(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        return repository.sumByTenantIdAndPeriodo(tenantId, dataInicio, dataFim);
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

    /**
     * Verifica se ja existem despesas para uma OS
     */
    @Transactional(readOnly = true)
    public boolean existeDespesaParaOS(UUID tenantId, UUID osManutencaoId) {
        return repository.existsDespesaAtivaByOsManutencaoId(tenantId, osManutencaoId);
    }
}
