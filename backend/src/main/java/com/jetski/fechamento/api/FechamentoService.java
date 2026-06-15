package com.jetski.fechamento.api;

import com.jetski.comissoes.api.ComissaoQueryService;
import com.jetski.comissoes.domain.Comissao;
import com.jetski.despesas.api.DespesaOperacionalService;
import com.jetski.fechamento.api.dto.DivergenciaResponse;
import com.jetski.fechamento.api.dto.LocacaoAlterada;
import com.jetski.fechamento.domain.FechamentoDiario;
import com.jetski.fechamento.domain.FechamentoMensal;
import com.jetski.fechamento.internal.repository.FechamentoDiarioRepository;
import com.jetski.fechamento.internal.repository.FechamentoMensalRepository;
import com.jetski.locacoes.api.LocacaoQueryService;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.api.PresencaVendedorQueryService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final DespesaOperacionalService despesaOperacionalService;
    private final PresencaVendedorQueryService presencaVendedorQueryService;

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
            // RN03: Aggregate fuel costs
            if (locacao.getCombustivelCusto() != null) {
                totalCombustivel = totalCombustivel.add(locacao.getCombustivelCusto());
            }
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

        // Buscar despesas operacionais do dia
        BigDecimal totalDespesasOperacionais = despesaOperacionalService.somarDespesasDia(tenantId, data);

        // Buscar total de diárias de vendedores do dia
        BigDecimal totalDiariasVendedores = presencaVendedorQueryService.sumTotalDiariasByDate(tenantId, data);

        // Criar ou atualizar fechamento (idempotente)
        boolean isUpdate = fechamento != null;

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
                    .totalDespesasOperacionais(totalDespesasOperacionais)
                    .totalDiariasVendedores(totalDiariasVendedores)
                    .status("aberto")
                    .bloqueado(false)
                    .build();
        } else {
            // Reconsolidar: atualizar valores recalculados
            fechamento.setTotalLocacoes(totalLocacoes);
            fechamento.setTotalFaturado(totalFaturado);
            fechamento.setTotalCombustivel(totalCombustivel);
            fechamento.setTotalComissoes(totalComissoes);
            fechamento.setTotalDinheiro(totalDinheiro);
            fechamento.setTotalCartao(totalCartao);
            fechamento.setTotalPix(totalPix);
            fechamento.setTotalDespesasOperacionais(totalDespesasOperacionais);
            fechamento.setTotalDiariasVendedores(totalDiariasVendedores);
        }

        // Calcular e armazenar hash dos valores consolidados
        fechamento.atualizarHash();

        FechamentoDiario salvo = fechamentoDiarioRepository.save(fechamento);

        if (isUpdate) {
            log.info("Fechamento diário RECONSOLIDADO: {} (data: {}, locações: {}, total: {})",
                    salvo.getId(), data, totalLocacoes, totalFaturado);
        } else {
            log.info("Fechamento diário CRIADO: {} (data: {}, locações: {}, total: {})",
                    salvo.getId(), data, totalLocacoes, totalFaturado);
        }

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
     * Força reabertura de um fechamento diário, mesmo se aprovado
     * Requer permissão ADMIN_TENANT
     */
    public FechamentoDiario forcarReabrirFechamentoDiario(UUID tenantId, UUID id) {
        FechamentoDiario fechamento = buscarFechamentoDiario(tenantId, id);

        fechamento.forcarReabrir();
        FechamentoDiario salvo = fechamentoDiarioRepository.save(fechamento);

        log.warn("Fechamento diário FORÇADO a reabrir por ADMIN: {} (data: {}, status anterior: {})",
                id, fechamento.getDtReferencia(), fechamento.getStatus());

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
     * Verifica se existe fechamento diário para uma data (bloqueado ou não)
     */
    @Transactional(readOnly = true)
    public boolean existeFechamentoDiario(UUID tenantId, LocalDate data) {
        return fechamentoDiarioRepository.findByTenantIdAndDtReferencia(tenantId, data).isPresent();
    }

    /**
     * Verifica se existe fechamento bloqueado para uma data
     */
    @Transactional(readOnly = true)
    public boolean existeFechamentoBloqueadoParaData(UUID tenantId, LocalDate data) {
        return fechamentoDiarioRepository.existsBloqueadoParaData(tenantId, data);
    }

    /**
     * Verifica divergências entre valores consolidados e valores atuais das locações.
     *
     * <p>Compara o hash armazenado no fechamento com um hash recalculado
     * a partir dos valores atuais das locações.</p>
     *
     * @param tenantId   ID do tenant
     * @param dataInicio Data inicial do período
     * @param dataFim    Data final do período
     * @return Lista de divergências encontradas
     */
    @Transactional(readOnly = true)
    public List<DivergenciaResponse> verificarDivergencias(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        List<FechamentoDiario> fechamentos = fechamentoDiarioRepository
                .findByTenantIdAndDtReferenciaBetweenOrderByDtReferenciaDesc(tenantId, dataInicio, dataFim);

        List<DivergenciaResponse> divergencias = new ArrayList<>();

        for (FechamentoDiario f : fechamentos) {
            // Recalcular valores atuais das locações
            ValoresConsolidados atuais = calcularValoresAtuais(tenantId, f.getDtReferencia());

            // Comparar com valores armazenados
            if (!valoresIguais(f, atuais)) {
                // Buscar locações alteradas para detalhamento
                List<LocacaoAlterada> locacoesAlteradas = identificarLocacoesAlteradas(
                        tenantId, f.getDtReferencia(), f.getUpdatedAt());

                divergencias.add(DivergenciaResponse.builder()
                        .fechamentoId(f.getId())
                        .dtReferencia(f.getDtReferencia())
                        .status(f.getStatus())
                        .totalLocacoesArmazenado(f.getTotalLocacoes())
                        .totalFaturadoArmazenado(f.getTotalFaturado())
                        .totalCombustivelArmazenado(f.getTotalCombustivel())
                        .totalComissoesArmazenado(f.getTotalComissoes())
                        .totalLocacoesAtual(atuais.totalLocacoes)
                        .totalFaturadoAtual(atuais.totalFaturado)
                        .totalCombustivelAtual(atuais.totalCombustivel)
                        .totalComissoesAtual(atuais.totalComissoes)
                        .diferencaLocacoes(atuais.totalLocacoes - f.getTotalLocacoes())
                        .diferencaFaturado(atuais.totalFaturado.subtract(
                                f.getTotalFaturado() != null ? f.getTotalFaturado() : BigDecimal.ZERO))
                        .diferencaCombustivel(atuais.totalCombustivel.subtract(
                                f.getTotalCombustivel() != null ? f.getTotalCombustivel() : BigDecimal.ZERO))
                        .diferencaComissoes(atuais.totalComissoes.subtract(
                                f.getTotalComissoes() != null ? f.getTotalComissoes() : BigDecimal.ZERO))
                        .locacoesAlteradas(locacoesAlteradas)
                        .ultimaConsolidacao(f.getUpdatedAt())
                        .mensagem("Reconsolidacao necessaria: valores foram alterados apos ultima consolidacao")
                        .build());
            }
        }

        return divergencias;
    }

    /**
     * Classe interna para armazenar valores consolidados calculados
     */
    private record ValoresConsolidados(
            int totalLocacoes,
            BigDecimal totalFaturado,
            BigDecimal totalCombustivel,
            BigDecimal totalComissoes
    ) {}

    /**
     * Calcula os valores atuais das locações para uma data específica
     */
    private ValoresConsolidados calcularValoresAtuais(UUID tenantId, LocalDate data) {
        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = data.plusDays(1).atStartOfDay();

        List<Locacao> locacoes = locacaoQueryService.findByTenantIdAndDateRange(tenantId, inicioDia, fimDia);

        // Filtrar apenas locações finalizadas (com check-out)
        locacoes = locacoes.stream()
                .filter(l -> l.getDataCheckOut() != null &&
                             l.getDataCheckOut().toLocalDate().equals(data))
                .toList();

        int totalLocacoes = locacoes.size();
        BigDecimal totalFaturado = locacoes.stream()
                .map(Locacao::getValorTotal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCombustivel = locacoes.stream()
                .map(Locacao::getCombustivelCusto)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Buscar comissões do dia
        Instant inicioInstant = inicioDia.atZone(ZoneId.systemDefault()).toInstant();
        Instant fimInstant = fimDia.atZone(ZoneId.systemDefault()).toInstant();

        List<Comissao> comissoes = comissaoQueryService.findByPeriodo(tenantId, inicioInstant, fimInstant);
        BigDecimal totalComissoes = comissoes.stream()
                .map(Comissao::getValorComissao)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ValoresConsolidados(totalLocacoes, totalFaturado, totalCombustivel, totalComissoes);
    }

    /**
     * Verifica se os valores do fechamento são iguais aos valores atuais
     */
    private boolean valoresIguais(FechamentoDiario f, ValoresConsolidados atuais) {
        // Compara número de locações
        if (f.getTotalLocacoes() != atuais.totalLocacoes) {
            return false;
        }

        // Compara valores com tolerância para arredondamento
        BigDecimal faturadoArmazenado = f.getTotalFaturado() != null ? f.getTotalFaturado() : BigDecimal.ZERO;
        if (faturadoArmazenado.compareTo(atuais.totalFaturado) != 0) {
            return false;
        }

        BigDecimal combustivelArmazenado = f.getTotalCombustivel() != null ? f.getTotalCombustivel() : BigDecimal.ZERO;
        if (combustivelArmazenado.compareTo(atuais.totalCombustivel) != 0) {
            return false;
        }

        BigDecimal comissoesArmazenado = f.getTotalComissoes() != null ? f.getTotalComissoes() : BigDecimal.ZERO;
        if (comissoesArmazenado.compareTo(atuais.totalComissoes) != 0) {
            return false;
        }

        return true;
    }

    /**
     * Identifica locações que foram alteradas após a consolidação
     */
    private List<LocacaoAlterada> identificarLocacoesAlteradas(UUID tenantId, LocalDate data, Instant ultimaConsolidacao) {
        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = data.plusDays(1).atStartOfDay();

        List<Locacao> locacoes = locacaoQueryService.findByTenantIdAndDateRange(tenantId, inicioDia, fimDia);

        // Filtrar apenas locações finalizadas deste dia
        locacoes = locacoes.stream()
                .filter(l -> l.getDataCheckOut() != null &&
                             l.getDataCheckOut().toLocalDate().equals(data))
                .toList();

        List<LocacaoAlterada> alteradas = new ArrayList<>();

        for (Locacao locacao : locacoes) {
            // Verificar se a locação foi alterada após a consolidação
            if (locacao.getUpdatedAt() != null && ultimaConsolidacao != null &&
                locacao.getUpdatedAt().isAfter(ultimaConsolidacao)) {

                alteradas.add(LocacaoAlterada.builder()
                        .locacaoId(locacao.getId())
                        // Locacao armazena apenas IDs; para nomes seria necessário join
                        .clienteNome(locacao.getClienteId() != null ? locacao.getClienteId().toString() : "N/A")
                        .jetskiIdentificacao(locacao.getJetskiId() != null ?
                                locacao.getJetskiId().toString() : "N/A")
                        .dataCheckOut(locacao.getDataCheckOut())
                        .valorAnterior(null) // Não temos snapshot, seria necessário auditoria
                        .valorAtual(locacao.getValorTotal())
                        .diferenca(null) // Sem snapshot, não podemos calcular
                        .dataAlteracao(locacao.getUpdatedAt())
                        .alteradoPor(null) // Necessitaria integração com auditoria
                        .build());
            }
        }

        return alteradas;
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

        // Somar despesas operacionais dos fechamentos diários
        BigDecimal totalDespesasOperacionais = fechamentosDiarios.stream()
                .map(FechamentoDiario::getTotalDespesasOperacionais)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Somar diárias de vendedores dos fechamentos diários
        BigDecimal totalDiariasVendedores = fechamentosDiarios.stream()
                .map(FechamentoDiario::getTotalDiariasVendedores)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // TODO: Calcular total de manutenções do mês (quando módulo de manutenção estiver implementado)
        BigDecimal totalManutencoes = BigDecimal.ZERO;

        // Criar ou atualizar fechamento (idempotente)
        boolean isUpdate = fechamento != null;

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
                    .totalDespesasOperacionais(totalDespesasOperacionais)
                    .totalDiariasVendedores(totalDiariasVendedores)
                    .status("aberto")
                    .bloqueado(false)
                    .build();
        } else {
            // Reconsolidar: atualizar valores recalculados
            fechamento.setTotalLocacoes(totalLocacoes);
            fechamento.setTotalFaturado(totalFaturado);
            fechamento.setTotalCustos(totalCustos);
            fechamento.setTotalComissoes(totalComissoes);
            fechamento.setTotalManutencoes(totalManutencoes);
            fechamento.setTotalDespesasOperacionais(totalDespesasOperacionais);
            fechamento.setTotalDiariasVendedores(totalDiariasVendedores);
        }

        // Calcular resultado líquido
        fechamento.calcularResultadoLiquido();

        // Calcular e armazenar hash dos valores consolidados
        fechamento.atualizarHash();

        FechamentoMensal salvo = fechamentoMensalRepository.save(fechamento);

        if (isUpdate) {
            log.info("Fechamento mensal RECONSOLIDADO: {} (período: {}/{}, locações: {}, resultado: {})",
                    salvo.getId(), mes, ano, totalLocacoes, salvo.getResultadoLiquido());
        } else {
            log.info("Fechamento mensal CRIADO: {} (período: {}/{}, locações: {}, resultado: {})",
                    salvo.getId(), mes, ano, totalLocacoes, salvo.getResultadoLiquido());
        }

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
     * Força reabertura de um fechamento mensal, mesmo se aprovado
     * Requer permissão ADMIN_TENANT
     */
    public FechamentoMensal forcarReabrirFechamentoMensal(UUID tenantId, UUID id) {
        FechamentoMensal fechamento = buscarFechamentoMensal(tenantId, id);

        fechamento.forcarReabrir();
        FechamentoMensal salvo = fechamentoMensalRepository.save(fechamento);

        log.warn("Fechamento mensal FORÇADO a reabrir por ADMIN: {} (período: {}/{}, status anterior: {})",
                id, fechamento.getMes(), fechamento.getAno(), fechamento.getStatus());

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
     * Busca fechamento mensal por ano e mês (retorna Optional)
     * Usado quando não queremos lançar exceção se não existir.
     */
    @Transactional(readOnly = true)
    public Optional<FechamentoMensal> buscarFechamentoMensalPorPeriodoOptional(UUID tenantId, int ano, int mes) {
        return fechamentoMensalRepository.findByTenantIdAndAnoAndMes(tenantId, ano, mes);
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
     * Verifica se existe fechamento mensal para um período (bloqueado ou não)
     */
    @Transactional(readOnly = true)
    public boolean existeFechamentoMensal(UUID tenantId, int ano, int mes) {
        return fechamentoMensalRepository.findByTenantIdAndAnoAndMes(tenantId, ano, mes).isPresent();
    }

    /**
     * Verifica se existe fechamento bloqueado para um mês
     */
    @Transactional(readOnly = true)
    public boolean existeFechamentoBloqueadoParaMes(UUID tenantId, int ano, int mes) {
        return fechamentoMensalRepository.existsBloqueadoParaMes(tenantId, ano, mes);
    }
}
