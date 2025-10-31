package com.jetski.fechamento.internal;

import com.jetski.comissoes.api.ComissaoQueryService;
import com.jetski.comissoes.domain.Comissao;
import com.jetski.fechamento.domain.FechamentoDiario;
import com.jetski.fechamento.domain.FechamentoMensal;
import com.jetski.fechamento.internal.repository.FechamentoDiarioRepository;
import com.jetski.fechamento.internal.repository.FechamentoMensalRepository;
import com.jetski.locacoes.api.LocacaoQueryService;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Service for Financial Closure management (Daily and Monthly)
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Consolidate daily rentals into FechamentoDiario</li>
 *   <li>Calculate totals by payment method</li>
 *   <li>Consolidate month data into FechamentoMensal</li>
 *   <li>Calculate resultado_liquido (revenue - costs - commissions - maintenance)</li>
 *   <li>Enforce bloqueado=true preventing retroactive edits</li>
 *   <li>Handle status transitions (aberto → fechado → aprovado)</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.8.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FechamentoService {

    private final FechamentoDiarioRepository fechamentoDiarioRepository;
    private final FechamentoMensalRepository fechamentoMensalRepository;
    private final LocacaoQueryService locacaoQueryService;
    private final ComissaoQueryService comissaoQueryService;

    // ====================
    // Fechamento Diário
    // ====================

    /**
     * Consolida locações de um dia específico
     */
    public FechamentoDiario consolidarDia(UUID tenantId, LocalDate data, UUID operadorId) {
        // Verificar se já existe fechamento para esta data
        FechamentoDiario fechamento = fechamentoDiarioRepository
                .findByTenantIdAndDtReferencia(tenantId, data)
                .orElse(null);

        if (fechamento != null && fechamento.getBloqueado()) {
            throw new BusinessException("Fechamento diário já está bloqueado para a data: " + data);
        }

        // Buscar todas as locações finalizadas do dia (by check-out timestamp)
        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = data.plusDays(1).atStartOfDay();

        List<Locacao> locacoes = locacaoQueryService.findByTenantIdAndDateRange(tenantId, inicioDia, fimDia);

        // Filtrar apenas locações finalizadas (com check-out)
        locacoes = locacoes.stream()
                .filter(l -> l.getDataCheckOut() != null &&
                             l.getDataCheckOut().toLocalDate().equals(data))
                .toList();

        // Calcular totais
        int totalLocacoes = locacoes.size();
        BigDecimal totalFaturado = BigDecimal.ZERO;
        BigDecimal totalCombustivel = BigDecimal.ZERO;
        BigDecimal totalDinheiro = BigDecimal.ZERO;
        BigDecimal totalCartao = BigDecimal.ZERO;
        BigDecimal totalPix = BigDecimal.ZERO;

        for (Locacao locacao : locacoes) {
            if (locacao.getValorTotal() != null) {
                totalFaturado = totalFaturado.add(locacao.getValorTotal());
            }
            // TODO: Adicionar campo valorCombustivel quando implementar módulo de combustível
            // TODO: Adicionar campo formaPagamento quando implementar módulo de pagamentos
        }

        // Buscar comissões do dia
        Instant inicioInstant = inicioDia.atZone(ZoneId.systemDefault()).toInstant();
        Instant fimInstant = fimDia.atZone(ZoneId.systemDefault()).toInstant();

        List<Comissao> comissoes = comissaoQueryService.findByPeriodo(tenantId, inicioInstant, fimInstant);
        BigDecimal totalComissoes = comissoes.stream()
                .map(Comissao::getValorComissao)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Criar ou atualizar fechamento
        if (fechamento == null) {
            fechamento = FechamentoDiario.builder()
                    .tenantId(tenantId)
                    .dtReferencia(data)
                    .operadorId(operadorId)
                    .totalLocacoes(totalLocacoes)
                    .totalFaturado(totalFaturado)
                    .totalCombustivel(totalCombustivel)
                    .totalComissoes(totalComissoes)
                    .totalDinheiro(totalDinheiro)
                    .totalCartao(totalCartao)
                    .totalPix(totalPix)
                    .status("aberto")
                    .bloqueado(false)
                    .build();
        } else {
            fechamento.setTotalLocacoes(totalLocacoes);
            fechamento.setTotalFaturado(totalFaturado);
            fechamento.setTotalCombustivel(totalCombustivel);
            fechamento.setTotalComissoes(totalComissoes);
            fechamento.setTotalDinheiro(totalDinheiro);
            fechamento.setTotalCartao(totalCartao);
            fechamento.setTotalPix(totalPix);
        }

        FechamentoDiario salvo = fechamentoDiarioRepository.save(fechamento);
        log.info("Fechamento diário consolidado: {} (data: {}, locações: {}, total: {})",
                salvo.getId(), data, totalLocacoes, totalFaturado);

        return salvo;
    }

    /**
     * Fecha e bloqueia um fechamento diário
     */
    public FechamentoDiario fecharDia(UUID tenantId, UUID id, UUID operadorId) {
        FechamentoDiario fechamento = buscarFechamentoDiario(tenantId, id);

        if (fechamento.getBloqueado()) {
            throw new BusinessException("Fechamento diário já está bloqueado");
        }

        fechamento.fechar();
        FechamentoDiario salvo = fechamentoDiarioRepository.save(fechamento);

        log.info("Fechamento diário bloqueado: {} (data: {})", id, fechamento.getDtReferencia());

        return salvo;
    }

    /**
     * Aprova um fechamento diário
     */
    public FechamentoDiario aprovarFechamentoDiario(UUID tenantId, UUID id) {
        FechamentoDiario fechamento = buscarFechamentoDiario(tenantId, id);

        fechamento.aprovar();
        FechamentoDiario salvo = fechamentoDiarioRepository.save(fechamento);

        log.info("Fechamento diário aprovado: {} (data: {})", id, fechamento.getDtReferencia());

        return salvo;
    }

    /**
     * Reabre um fechamento diário (apenas se não estiver aprovado)
     */
    public FechamentoDiario reabrirFechamentoDiario(UUID tenantId, UUID id) {
        FechamentoDiario fechamento = buscarFechamentoDiario(tenantId, id);

        fechamento.reabrir();
        FechamentoDiario salvo = fechamentoDiarioRepository.save(fechamento);

        log.info("Fechamento diário reaberto: {} (data: {})", id, fechamento.getDtReferencia());

        return salvo;
    }

    /**
     * Busca fechamento diário por ID
     */
    @Transactional(readOnly = true)
    public FechamentoDiario buscarFechamentoDiario(UUID tenantId, UUID id) {
        return fechamentoDiarioRepository.findById(id)
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Fechamento diário não encontrado: " + id));
    }

    /**
     * Busca fechamento diário por data
     */
    @Transactional(readOnly = true)
    public FechamentoDiario buscarFechamentoDiarioPorData(UUID tenantId, LocalDate data) {
        return fechamentoDiarioRepository.findByTenantIdAndDtReferencia(tenantId, data)
                .orElseThrow(() -> new NotFoundException("Fechamento diário não encontrado para data: " + data));
    }

    /**
     * Lista fechamentos diários por intervalo de datas
     */
    @Transactional(readOnly = true)
    public List<FechamentoDiario> listarFechamentosDiarios(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        return fechamentoDiarioRepository.findByTenantIdAndDtReferenciaBetweenOrderByDtReferenciaDesc(tenantId, dataInicio, dataFim);
    }

    /**
     * Verifica se existe fechamento bloqueado para uma data
     */
    @Transactional(readOnly = true)
    public boolean existeFechamentoBloqueadoParaData(UUID tenantId, LocalDate data) {
        return fechamentoDiarioRepository.existsBloqueadoParaData(tenantId, data);
    }

    // ====================
    // Fechamento Mensal
    // ====================

    /**
     * Consolida fechamentos diários de um mês
     */
    public FechamentoMensal consolidarMes(UUID tenantId, int ano, int mes, UUID operadorId) {
        // Verificar se já existe fechamento para este mês
        FechamentoMensal fechamento = fechamentoMensalRepository
                .findByTenantIdAndAnoAndMes(tenantId, ano, mes)
                .orElse(null);

        if (fechamento != null && fechamento.getBloqueado()) {
            throw new BusinessException(String.format("Fechamento mensal já está bloqueado para %d/%d", mes, ano));
        }

        // Buscar fechamentos diários do mês
        YearMonth yearMonth = YearMonth.of(ano, mes);
        LocalDate dataInicio = yearMonth.atDay(1);
        LocalDate dataFim = yearMonth.atEndOfMonth();

        List<FechamentoDiario> fechamentosDiarios = fechamentoDiarioRepository
                .findByTenantIdAndDtReferenciaBetweenOrderByDtReferenciaDesc(tenantId, dataInicio, dataFim);

        // Calcular totais
        int totalLocacoes = fechamentosDiarios.stream()
                .mapToInt(FechamentoDiario::getTotalLocacoes)
                .sum();

        BigDecimal totalFaturado = fechamentosDiarios.stream()
                .map(FechamentoDiario::getTotalFaturado)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCustos = fechamentosDiarios.stream()
                .map(FechamentoDiario::getTotalCombustivel)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalComissoes = fechamentosDiarios.stream()
                .map(FechamentoDiario::getTotalComissoes)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // TODO: Calcular total de manutenções do mês (quando módulo de manutenção estiver implementado)
        BigDecimal totalManutencoes = BigDecimal.ZERO;

        // Criar ou atualizar fechamento
        if (fechamento == null) {
            fechamento = FechamentoMensal.builder()
                    .tenantId(tenantId)
                    .ano(ano)
                    .mes(mes)
                    .operadorId(operadorId)
                    .totalLocacoes(totalLocacoes)
                    .totalFaturado(totalFaturado)
                    .totalCustos(totalCustos)
                    .totalComissoes(totalComissoes)
                    .totalManutencoes(totalManutencoes)
                    .status("aberto")
                    .bloqueado(false)
                    .build();
        } else {
            fechamento.setTotalLocacoes(totalLocacoes);
            fechamento.setTotalFaturado(totalFaturado);
            fechamento.setTotalCustos(totalCustos);
            fechamento.setTotalComissoes(totalComissoes);
            fechamento.setTotalManutencoes(totalManutencoes);
        }

        // Calcular resultado líquido
        fechamento.calcularResultadoLiquido();

        FechamentoMensal salvo = fechamentoMensalRepository.save(fechamento);
        log.info("Fechamento mensal consolidado: {} (período: {}/{}, locações: {}, resultado: {})",
                salvo.getId(), mes, ano, totalLocacoes, salvo.getResultadoLiquido());

        return salvo;
    }

    /**
     * Fecha e bloqueia um fechamento mensal
     */
    public FechamentoMensal fecharMes(UUID tenantId, UUID id) {
        FechamentoMensal fechamento = buscarFechamentoMensal(tenantId, id);

        if (fechamento.getBloqueado()) {
            throw new BusinessException("Fechamento mensal já está bloqueado");
        }

        fechamento.fechar();
        FechamentoMensal salvo = fechamentoMensalRepository.save(fechamento);

        log.info("Fechamento mensal bloqueado: {} (período: {}/{})", id, fechamento.getMes(), fechamento.getAno());

        return salvo;
    }

    /**
     * Aprova um fechamento mensal
     */
    public FechamentoMensal aprovarFechamentoMensal(UUID tenantId, UUID id) {
        FechamentoMensal fechamento = buscarFechamentoMensal(tenantId, id);

        fechamento.aprovar();
        FechamentoMensal salvo = fechamentoMensalRepository.save(fechamento);

        log.info("Fechamento mensal aprovado: {} (período: {}/{})", id, fechamento.getMes(), fechamento.getAno());

        return salvo;
    }

    /**
     * Reabre um fechamento mensal (apenas se não estiver aprovado)
     */
    public FechamentoMensal reabrirFechamentoMensal(UUID tenantId, UUID id) {
        FechamentoMensal fechamento = buscarFechamentoMensal(tenantId, id);

        fechamento.reabrir();
        FechamentoMensal salvo = fechamentoMensalRepository.save(fechamento);

        log.info("Fechamento mensal reaberto: {} (período: {}/{})", id, fechamento.getMes(), fechamento.getAno());

        return salvo;
    }

    /**
     * Busca fechamento mensal por ID
     */
    @Transactional(readOnly = true)
    public FechamentoMensal buscarFechamentoMensal(UUID tenantId, UUID id) {
        return fechamentoMensalRepository.findById(id)
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Fechamento mensal não encontrado: " + id));
    }

    /**
     * Busca fechamento mensal por ano e mês
     */
    @Transactional(readOnly = true)
    public FechamentoMensal buscarFechamentoMensalPorPeriodo(UUID tenantId, int ano, int mes) {
        return fechamentoMensalRepository.findByTenantIdAndAnoAndMes(tenantId, ano, mes)
                .orElseThrow(() -> new NotFoundException(String.format("Fechamento mensal não encontrado para %d/%d", mes, ano)));
    }

    /**
     * Lista todos os fechamentos mensais
     */
    @Transactional(readOnly = true)
    public List<FechamentoMensal> listarFechamentosMensais(UUID tenantId) {
        return fechamentoMensalRepository.findByTenantIdOrderByAnoDescMesDesc(tenantId);
    }

    /**
     * Lista fechamentos mensais por ano
     */
    @Transactional(readOnly = true)
    public List<FechamentoMensal> listarFechamentosMensaisPorAno(UUID tenantId, int ano) {
        return fechamentoMensalRepository.findByTenantIdAndAnoOrderByMesAsc(tenantId, ano);
    }

    /**
     * Verifica se existe fechamento bloqueado para um mês
     */
    @Transactional(readOnly = true)
    public boolean existeFechamentoBloqueadoParaMes(UUID tenantId, int ano, int mes) {
        return fechamentoMensalRepository.existsBloqueadoParaMes(tenantId, ano, mes);
    }
}
