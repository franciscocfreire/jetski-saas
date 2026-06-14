package com.jetski.dashboard.internal;

import com.jetski.comissoes.api.ComissaoQueryService;
import com.jetski.comissoes.domain.Comissao;
import com.jetski.dashboard.api.dto.*;
import com.jetski.despesas.domain.CategoriaDespesa;
import com.jetski.despesas.domain.DespesaOperacional;
import com.jetski.despesas.internal.DespesaOperacionalService;
import com.jetski.fechamento.domain.FechamentoDiario;
import com.jetski.fechamento.domain.FechamentoMensal;
import com.jetski.fechamento.internal.FechamentoService;
import com.jetski.locacoes.internal.repository.PresencaVendedorRepository;
import com.jetski.manutencao.domain.DespesaManutencao;
import com.jetski.manutencao.internal.DespesaManutencaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for Financial Dashboard data aggregation.
 *
 * <p>Provides data for:</p>
 * <ul>
 *   <li>Financial Calendar (monthly view with daily indicators)</li>
 *   <li>Revenue vs Expenses chart</li>
 *   <li>Simplified DRE (Income Statement)</li>
 *   <li>Pending records summary</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.9.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardFinanceiroService {

    private final FechamentoService fechamentoService;
    private final DespesaOperacionalService despesaOperacionalService;
    private final DespesaManutencaoService despesaManutencaoService;
    private final ComissaoQueryService comissaoQueryService;
    private final PresencaVendedorRepository presencaVendedorRepository;

    /**
     * Get monthly financial calendar data.
     * Returns daily data for all days of the specified month.
     */
    public CalendarioFinanceiroResponse getCalendarioMensal(UUID tenantId, int ano, int mes) {
        YearMonth yearMonth = YearMonth.of(ano, mes);
        LocalDate dataInicio = yearMonth.atDay(1);
        LocalDate dataFim = yearMonth.atEndOfMonth();

        // Fetch daily closures for the month
        List<FechamentoDiario> fechamentos = fechamentoService.listarFechamentosDiarios(
                tenantId, dataInicio, dataFim);

        // Create a map for quick lookup
        Map<LocalDate, FechamentoDiario> fechamentoMap = fechamentos.stream()
                .collect(Collectors.toMap(FechamentoDiario::getDtReferencia, f -> f));

        // Fetch all expenses for the month
        List<DespesaOperacional> despesasMes = despesaOperacionalService.listarPorPeriodo(
                tenantId, dataInicio, dataFim);
        Map<LocalDate, List<DespesaOperacional>> despesasPorDia = despesasMes.stream()
                .collect(Collectors.groupingBy(DespesaOperacional::getDtReferencia));

        // Fetch maintenance expenses for the month
        List<DespesaManutencao> manutencoesAprovadas = despesaManutencaoService.listarPorPeriodo(
                tenantId, dataInicio, dataFim);
        Map<LocalDate, List<DespesaManutencao>> manutencoesPorDia = manutencoesAprovadas.stream()
                .filter(m -> m.getStatus().name().equals("APROVADA") || m.getStatus().name().equals("PAGA"))
                .collect(Collectors.groupingBy(DespesaManutencao::getDtVencimento));

        // Build daily data
        List<DiaFinanceiroResponse> dias = new ArrayList<>();
        BigDecimal totalReceitas = BigDecimal.ZERO;
        BigDecimal totalDespesas = BigDecimal.ZERO;
        int diasPositivos = 0;
        int diasNegativos = 0;
        int diasNeutros = 0;

        for (LocalDate data = dataInicio; !data.isAfter(dataFim); data = data.plusDays(1)) {
            FechamentoDiario fechamento = fechamentoMap.get(data);
            List<DespesaOperacional> despesasDia = despesasPorDia.getOrDefault(data, List.of());
            List<DespesaManutencao> manutencoesDia = manutencoesPorDia.getOrDefault(data, List.of());

            BigDecimal receita = BigDecimal.ZERO;
            BigDecimal combustivel = BigDecimal.ZERO;
            BigDecimal comissoes = BigDecimal.ZERO;
            BigDecimal despesasOp = BigDecimal.ZERO;
            BigDecimal diariasVendedores = BigDecimal.ZERO;
            BigDecimal manutencoes = BigDecimal.ZERO;
            String statusFechamento = null;
            boolean temFechamento = false;

            if (fechamento != null) {
                receita = fechamento.getTotalFaturado() != null ? fechamento.getTotalFaturado() : BigDecimal.ZERO;
                combustivel = fechamento.getTotalCombustivel() != null ? fechamento.getTotalCombustivel() : BigDecimal.ZERO;
                comissoes = fechamento.getTotalComissoes() != null ? fechamento.getTotalComissoes() : BigDecimal.ZERO;
                despesasOp = fechamento.getTotalDespesasOperacionais() != null ?
                        fechamento.getTotalDespesasOperacionais() : BigDecimal.ZERO;
                diariasVendedores = fechamento.getTotalDiariasVendedores() != null ?
                        fechamento.getTotalDiariasVendedores() : BigDecimal.ZERO;
                statusFechamento = fechamento.getStatus();
                temFechamento = true;
            } else {
                // Calculate from raw expenses if no closure exists
                despesasOp = despesasDia.stream()
                        .map(DespesaOperacional::getValor)
                        .filter(v -> v != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                // Calculate diárias from presenca_vendedor
                diariasVendedores = presencaVendedorRepository.sumTotalDiariasByDate(tenantId, data);
                if (diariasVendedores == null) {
                    diariasVendedores = BigDecimal.ZERO;
                }
            }

            // Calculate maintenance expenses for this day (from DespesaManutencao)
            manutencoes = manutencoesDia.stream()
                    .map(DespesaManutencao::getValor)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalDespesasDia = combustivel.add(comissoes).add(despesasOp).add(diariasVendedores).add(manutencoes);
            BigDecimal saldo = receita.subtract(totalDespesasDia);

            // Determine indicator
            String indicador = "NEUTRO";
            if (saldo.compareTo(BigDecimal.ZERO) > 0) {
                indicador = "POSITIVO";
                diasPositivos++;
            } else if (saldo.compareTo(BigDecimal.ZERO) < 0) {
                indicador = "NEGATIVO";
                diasNegativos++;
            } else {
                diasNeutros++;
            }

            totalReceitas = totalReceitas.add(receita);
            totalDespesas = totalDespesas.add(totalDespesasDia);

            dias.add(DiaFinanceiroResponse.builder()
                    .data(data)
                    .receita(receita)
                    .despesasOperacionais(despesasOp)
                    .combustivel(combustivel)
                    .comissoes(comissoes)
                    .diariasVendedores(diariasVendedores)
                    .manutencoes(manutencoes)
                    .totalDespesas(totalDespesasDia)
                    .saldo(saldo)
                    .indicador(indicador)
                    .statusFechamento(statusFechamento)
                    .temFechamento(temFechamento)
                    .build());
        }

        return CalendarioFinanceiroResponse.builder()
                .ano(ano)
                .mes(mes)
                .totalReceitas(totalReceitas)
                .totalDespesas(totalDespesas)
                .saldoMes(totalReceitas.subtract(totalDespesas))
                .diasPositivos(diasPositivos)
                .diasNegativos(diasNegativos)
                .diasNeutros(diasNeutros)
                .dias(dias)
                .build();
    }

    /**
     * Get revenue vs expenses chart data for a date range.
     */
    public List<ReceitaDespesaDiaResponse> getReceitasDespesas(UUID tenantId, LocalDate dataInicio, LocalDate dataFim) {
        List<FechamentoDiario> fechamentos = fechamentoService.listarFechamentosDiarios(
                tenantId, dataInicio, dataFim);

        Map<LocalDate, FechamentoDiario> fechamentoMap = fechamentos.stream()
                .collect(Collectors.toMap(FechamentoDiario::getDtReferencia, f -> f));

        // Fetch maintenance expenses for the period
        List<DespesaManutencao> manutencoesAprovadas = despesaManutencaoService.listarPorPeriodo(
                tenantId, dataInicio, dataFim);
        Map<LocalDate, List<DespesaManutencao>> manutencoesPorDia = manutencoesAprovadas.stream()
                .filter(m -> m.getStatus().name().equals("APROVADA") || m.getStatus().name().equals("PAGA"))
                .collect(Collectors.groupingBy(DespesaManutencao::getDtVencimento));

        List<ReceitaDespesaDiaResponse> result = new ArrayList<>();

        for (LocalDate data = dataInicio; !data.isAfter(dataFim); data = data.plusDays(1)) {
            FechamentoDiario f = fechamentoMap.get(data);
            List<DespesaManutencao> manutencoesDia = manutencoesPorDia.getOrDefault(data, List.of());

            BigDecimal receita = BigDecimal.ZERO;
            BigDecimal despesasOp = BigDecimal.ZERO;
            BigDecimal combustivel = BigDecimal.ZERO;
            BigDecimal comissoes = BigDecimal.ZERO;
            BigDecimal diariasVendedores = BigDecimal.ZERO;

            if (f != null) {
                receita = f.getTotalFaturado() != null ? f.getTotalFaturado() : BigDecimal.ZERO;
                despesasOp = f.getTotalDespesasOperacionais() != null ?
                        f.getTotalDespesasOperacionais() : BigDecimal.ZERO;
                combustivel = f.getTotalCombustivel() != null ? f.getTotalCombustivel() : BigDecimal.ZERO;
                comissoes = f.getTotalComissoes() != null ? f.getTotalComissoes() : BigDecimal.ZERO;
                diariasVendedores = f.getTotalDiariasVendedores() != null ?
                        f.getTotalDiariasVendedores() : BigDecimal.ZERO;
            } else {
                // Calculate diárias from presenca_vendedor if no closure exists
                diariasVendedores = presencaVendedorRepository.sumTotalDiariasByDate(tenantId, data);
                if (diariasVendedores == null) {
                    diariasVendedores = BigDecimal.ZERO;
                }
            }

            // Calculate maintenance expenses for this day
            BigDecimal manutencoes = manutencoesDia.stream()
                    .map(DespesaManutencao::getValor)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalDespesas = despesasOp.add(combustivel).add(comissoes).add(diariasVendedores).add(manutencoes);

            result.add(ReceitaDespesaDiaResponse.builder()
                    .data(data)
                    .receita(receita)
                    .despesasOperacionais(despesasOp)
                    .combustivel(combustivel)
                    .comissoes(comissoes)
                    .diariasVendedores(diariasVendedores)
                    .manutencoes(manutencoes)
                    .totalDespesas(totalDespesas)
                    .saldo(receita.subtract(totalDespesas))
                    .build());
        }

        return result;
    }

    /**
     * Get simplified DRE (Income Statement) for a month.
     */
    public DRESimplificadoResponse getDRESimplificado(UUID tenantId, int ano, int mes) {
        YearMonth yearMonth = YearMonth.of(ano, mes);
        LocalDate dataInicio = yearMonth.atDay(1);
        LocalDate dataFim = yearMonth.atEndOfMonth();

        // Try to get from monthly closure (use Optional to avoid transaction abort)
        Optional<FechamentoMensal> fechamentoMensalOpt = fechamentoService.buscarFechamentoMensalPorPeriodoOptional(tenantId, ano, mes);
        FechamentoMensal fechamentoMensal = fechamentoMensalOpt.orElse(null);

        BigDecimal receitaBruta;
        BigDecimal combustivel;
        BigDecimal comissoes;
        BigDecimal manutencoes;
        BigDecimal diariasVendedores;

        if (fechamentoMensal != null) {
            receitaBruta = fechamentoMensal.getTotalFaturado();
            combustivel = fechamentoMensal.getTotalCustos();
            comissoes = fechamentoMensal.getTotalComissoes();
            manutencoes = fechamentoMensal.getTotalManutencoes();
            diariasVendedores = fechamentoMensal.getTotalDiariasVendedores() != null ?
                    fechamentoMensal.getTotalDiariasVendedores() : BigDecimal.ZERO;
        } else {
            // Calculate from daily closures
            List<FechamentoDiario> fechamentosDiarios = fechamentoService.listarFechamentosDiarios(
                    tenantId, dataInicio, dataFim);

            receitaBruta = fechamentosDiarios.stream()
                    .map(FechamentoDiario::getTotalFaturado)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            combustivel = fechamentosDiarios.stream()
                    .map(FechamentoDiario::getTotalCombustivel)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            comissoes = fechamentosDiarios.stream()
                    .map(FechamentoDiario::getTotalComissoes)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Calculate maintenance from DespesaManutencaoService
            manutencoes = despesaManutencaoService.somarDespesasPeriodo(tenantId, dataInicio, dataFim);
            if (manutencoes == null) {
                manutencoes = BigDecimal.ZERO;
            }

            // Calculate diárias from presenca_vendedor for the period
            diariasVendedores = presencaVendedorRepository.sumTotalDiariasByTenantAndPeriodo(
                    tenantId, dataInicio, dataFim);
            if (diariasVendedores == null) {
                diariasVendedores = BigDecimal.ZERO;
            }
        }

        // Get expenses by category
        List<DespesaOperacional> despesas = despesaOperacionalService.listarPorPeriodo(
                tenantId, dataInicio, dataFim);

        BigDecimal despesasDiarias = sumByCategoria(despesas, CategoriaDespesa.DIARIA_FUNCIONARIO);
        BigDecimal despesasRefeicao = sumByCategoria(despesas, CategoriaDespesa.REFEICAO);
        BigDecimal despesasTransporte = sumByCategoria(despesas, CategoriaDespesa.TRANSPORTE);
        BigDecimal despesasLimpeza = sumByCategoria(despesas, CategoriaDespesa.LIMPEZA);

        BigDecimal outrasDespesas = despesas.stream()
                .filter(d -> d.getCategoria() != CategoriaDespesa.DIARIA_FUNCIONARIO &&
                            d.getCategoria() != CategoriaDespesa.REFEICAO &&
                            d.getCategoria() != CategoriaDespesa.TRANSPORTE &&
                            d.getCategoria() != CategoriaDespesa.LIMPEZA)
                .map(DespesaOperacional::getValor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDespesasOperacionais = despesasDiarias.add(diariasVendedores)
                .add(despesasRefeicao).add(despesasTransporte).add(despesasLimpeza).add(outrasDespesas);

        // Calculate DRE lines
        BigDecimal deducoes = BigDecimal.ZERO; // TODO: implement cancellations/discounts
        BigDecimal receitaLiquida = receitaBruta.subtract(deducoes);
        BigDecimal totalCustosVariaveis = combustivel.add(comissoes);
        BigDecimal lucroBruto = receitaLiquida.subtract(totalCustosVariaveis);
        BigDecimal resultadoLiquido = lucroBruto.subtract(totalDespesasOperacionais).subtract(manutencoes);

        // Calculate margin
        BigDecimal margemLiquida = BigDecimal.ZERO;
        if (receitaBruta.compareTo(BigDecimal.ZERO) > 0) {
            margemLiquida = resultadoLiquido
                    .multiply(new BigDecimal("100"))
                    .divide(receitaBruta, 2, RoundingMode.HALF_UP);
        }

        return DRESimplificadoResponse.builder()
                .ano(ano)
                .mes(mes)
                .receitaBruta(receitaBruta)
                .deducoes(deducoes)
                .receitaLiquida(receitaLiquida)
                .combustivel(combustivel)
                .comissoes(comissoes)
                .totalCustosVariaveis(totalCustosVariaveis)
                .lucroBruto(lucroBruto)
                .despesasDiarias(despesasDiarias)
                .diariasVendedores(diariasVendedores)
                .despesasRefeicao(despesasRefeicao)
                .despesasTransporte(despesasTransporte)
                .despesasLimpeza(despesasLimpeza)
                .outrasDesepsas(outrasDespesas)
                .totalDespesasOperacionais(totalDespesasOperacionais)
                .manutencoes(manutencoes)
                .resultadoLiquido(resultadoLiquido)
                .margemLiquida(margemLiquida)
                .build();
    }

    /**
     * Get pending records that need attention.
     */
    public RegistrosPendentesResponse getRegistrosPendentes(UUID tenantId) {
        // Pending commissions
        List<Comissao> comissoesPendentes = comissaoQueryService.findPendentesAprovacao(tenantId);
        List<Comissao> comissoesAguardando = comissaoQueryService.findAguardandoPagamento(tenantId);

        BigDecimal totalComissoesPendentes = BigDecimal.ZERO;
        List<RegistrosPendentesResponse.ComissaoPendenteItem> comissaoItems = new ArrayList<>();

        for (Comissao c : comissoesPendentes) {
            totalComissoesPendentes = totalComissoesPendentes.add(c.getValorComissao());
            comissaoItems.add(RegistrosPendentesResponse.ComissaoPendenteItem.builder()
                    .id(c.getId())
                    .vendedorId(c.getVendedorId())
                    .vendedorNome(null) // Would need join with vendedor table
                    .valor(c.getValorComissao())
                    .dtReferencia(c.getDataLocacao().atZone(ZoneId.systemDefault()).toLocalDate())
                    .build());
        }

        for (Comissao c : comissoesAguardando) {
            totalComissoesPendentes = totalComissoesPendentes.add(c.getValorComissao());
            comissaoItems.add(RegistrosPendentesResponse.ComissaoPendenteItem.builder()
                    .id(c.getId())
                    .vendedorId(c.getVendedorId())
                    .vendedorNome(null)
                    .valor(c.getValorComissao())
                    .dtReferencia(c.getDataLocacao().atZone(ZoneId.systemDefault()).toLocalDate())
                    .build());
        }

        // Pending expenses (awaiting approval)
        List<DespesaOperacional> despesasPendentes = despesaOperacionalService.listarPendentesAprovacao(tenantId);
        BigDecimal totalDespesasPendentes = despesasPendentes.stream()
                .map(DespesaOperacional::getValor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RegistrosPendentesResponse.DespesaPendenteItem> despesaItems = despesasPendentes.stream()
                .map(d -> RegistrosPendentesResponse.DespesaPendenteItem.builder()
                        .id(d.getId())
                        .dtReferencia(d.getDtReferencia())
                        .categoria(d.getCategoria().name())
                        .descricao(d.getDescricao())
                        .valor(d.getValor())
                        .build())
                .collect(Collectors.toList());

        // Approved expenses awaiting payment
        List<DespesaOperacional> despesasAguardando = despesaOperacionalService.listarAguardandoPagamento(tenantId);
        BigDecimal totalDespesasAguardando = despesasAguardando.stream()
                .map(DespesaOperacional::getValor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Days without closure (last 30 days)
        LocalDate hoje = LocalDate.now();
        LocalDate trintaDiasAtras = hoje.minusDays(30);
        List<FechamentoDiario> fechamentos = fechamentoService.listarFechamentosDiarios(
                tenantId, trintaDiasAtras, hoje);
        Map<LocalDate, FechamentoDiario> fechamentoMap = fechamentos.stream()
                .collect(Collectors.toMap(FechamentoDiario::getDtReferencia, f -> f));

        List<LocalDate> diasSemFechamento = new ArrayList<>();
        for (LocalDate data = trintaDiasAtras; !data.isAfter(hoje); data = data.plusDays(1)) {
            if (!fechamentoMap.containsKey(data)) {
                diasSemFechamento.add(data);
            }
        }

        // Open closures (status = aberto)
        List<RegistrosPendentesResponse.FechamentoAbertoItem> fechamentosAbertos = fechamentos.stream()
                .filter(f -> "aberto".equals(f.getStatus()))
                .map(f -> RegistrosPendentesResponse.FechamentoAbertoItem.builder()
                        .id(f.getId())
                        .dtReferencia(f.getDtReferencia())
                        .totalFaturado(f.getTotalFaturado())
                        .build())
                .collect(Collectors.toList());

        return RegistrosPendentesResponse.builder()
                .quantidadeComissoesPendentes(comissaoItems.size())
                .totalComissoesPendentes(totalComissoesPendentes)
                .comissoesPendentes(comissaoItems)
                .quantidadeDespesasPendentes(despesasPendentes.size())
                .totalDespesasPendentes(totalDespesasPendentes)
                .despesasPendentes(despesaItems)
                .quantidadeDespesasAguardandoPagamento(despesasAguardando.size())
                .totalDespesasAguardandoPagamento(totalDespesasAguardando)
                .quantidadeDiasSemFechamento(diasSemFechamento.size())
                .diasSemFechamento(diasSemFechamento)
                .quantidadeFechamentosAbertos(fechamentosAbertos.size())
                .fechamentosAbertos(fechamentosAbertos)
                .quantidadeManutencoesAbertas((int) despesaManutencaoService.contarPendentes(tenantId))
                .totalManutencoesAbertas(despesaManutencaoService.somarValorPendentes(tenantId))
                .build();
    }

    private BigDecimal sumByCategoria(List<DespesaOperacional> despesas, CategoriaDespesa categoria) {
        return despesas.stream()
                .filter(d -> d.getCategoria() == categoria)
                .map(DespesaOperacional::getValor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
