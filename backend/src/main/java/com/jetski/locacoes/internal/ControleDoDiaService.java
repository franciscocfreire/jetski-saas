package com.jetski.locacoes.internal;

import com.jetski.comissoes.api.CommissionService;
import com.jetski.locacoes.api.FolioQueryService;
import com.jetski.locacoes.api.dto.AgendaReservaResponse;
import com.jetski.locacoes.api.dto.ControleDoDiaResponse;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.Locacao;
import com.jetski.locacoes.domain.LocacaoStatus;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaLancamento;
import com.jetski.locacoes.domain.Vendedor;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.JetskiRepository;
import com.jetski.locacoes.internal.repository.LocacaoRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.locacoes.internal.repository.ReservaLancamentoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.locacoes.internal.repository.VendedorRepository;
import com.jetski.tenant.TenantTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CONTROLE DO DIA — a prancheta digital do operador (visão consolidada).
 *
 * <p>Joins em memória, sem N+1 — mesmo padrão de {@link ReservaAgendaService}.
 *
 * <p>Regras de composição:
 * <ul>
 *   <li>Linhas LOCACAO = locações com check-in no dia ∪ TODAS as EM_CURSO
 *       (uma locação de ontem ainda na água precisa aparecer na prancheta de
 *       hoje).</li>
 *   <li>Linhas RESERVA = reservas CONFIRMADAS do dia (via
 *       {@link ReservaAgendaService}, que já resolve o trio de prontidão) —
 *       o que ainda vai virar check-in. Filtro EXPLÍCITO por tenant nas
 *       entidades (a busca da agenda confia na RLS; testes bypassam).</li>
 *   <li>Ordenação: EM_CURSO primeiro, por volta prevista (dataCheckIn +
 *       duracaoPrevista) ascendente — vencidas no topo; depois as reservas
 *       CONFIRMADAS por dataInicio asc; por fim as demais locações por
 *       dataCheckOut (fallback dataCheckIn) descendente.</li>
 *   <li>totalPorForma = regime de CAIXA (folio, data do lançamento, fuso do
 *       tenant) — mesma janela do fechamento diário.</li>
 *   <li>totalDia/totalPorVendedor = COMPETÊNCIA: soma de valorTotal das
 *       LOCAÇÕES não canceladas com check-in no dia (canceladas aparecem na
 *       lista, mas fora das somas; linhas RESERVA NUNCA entram nas somas).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControleDoDiaService {

    private final LocacaoRepository locacaoRepository;
    private final JetskiRepository jetskiRepository;
    private final ClienteRepository clienteRepository;
    private final VendedorRepository vendedorRepository;
    private final ModeloRepository modeloRepository;
    private final CommissionService commissionService;
    private final ReservaRepository reservaRepository;
    private final ReservaAgendaService reservaAgendaService;
    private final ReservaLancamentoRepository reservaLancamentoRepository;
    private final FolioQueryService folioQueryService;
    private final TenantTimeService tenantTimeService;

    @Transactional(readOnly = true)
    public ControleDoDiaResponse doDia(UUID tenantId, LocalDate data) {
        // 1. Universo de locações: check-ins do dia ∪ todas EM_CURSO (dedup por id)
        Map<UUID, Locacao> porId = new LinkedHashMap<>();
        locacaoRepository.findByTenantIdAndDateRange(
                tenantId, data.atStartOfDay(), data.plusDays(1).atStartOfDay())
            .forEach(l -> porId.put(l.getId(), l));
        locacaoRepository.findByTenantIdAndStatusOrderByDataCheckInDesc(tenantId, LocacaoStatus.EM_CURSO)
            .forEach(l -> porId.putIfAbsent(l.getId(), l));

        // 2. Ordenação: EM_CURSO por volta prevista asc (vencidas no topo);
        //    demais por dataCheckOut/dataCheckIn desc
        List<Locacao> emCurso = porId.values().stream()
            .filter(l -> l.getStatus() == LocacaoStatus.EM_CURSO)
            .sorted(Comparator.comparing(ControleDoDiaService::voltaPrevista))
            .toList();
        List<Locacao> demais = porId.values().stream()
            .filter(l -> l.getStatus() != LocacaoStatus.EM_CURSO)
            .sorted(Comparator.comparing(
                (Locacao l) -> l.getDataCheckOut() != null ? l.getDataCheckOut() : l.getDataCheckIn(),
                Comparator.reverseOrder()))
            .toList();
        List<Locacao> ordenadas = Stream.concat(emCurso.stream(), demais.stream()).toList();

        // 3. Reservas CONFIRMADAS do dia (a agenda já resolve nomes e o trio
        //    de prontidão em lote). A agenda não devolve vendedor nem tenant:
        //    buscamos as entidades em lote (1 query) para o filtro EXPLÍCITO
        //    por tenant e para resolver o vendedorNome — sem N+1.
        List<AgendaReservaResponse> agendaConfirmadas = reservaAgendaService.doPeriodo(data, data).stream()
            // PENDENTE também é futura saída (balcão em andamento / aguardando
            // pagamento) — o cliente vem hoje do mesmo jeito; só RASCUNHO e
            // terminais (cancelada/expirada/no-show) ficam fora da prancheta
            .filter(a -> Reserva.ReservaStatus.CONFIRMADA.name().equals(a.getStatus())
                || Reserva.ReservaStatus.PENDENTE.name().equals(a.getStatus()))
            .sorted(Comparator.comparing(AgendaReservaResponse::getDataInicio))
            .toList();
        Map<UUID, Reserva> reservasDoTenant = agendaConfirmadas.isEmpty() ? Map.of()
            : reservaRepository.findAllById(
                    agendaConfirmadas.stream().map(AgendaReservaResponse::getId).toList()).stream()
                .filter(r -> tenantId.equals(r.getTenantId()))
                .collect(Collectors.toMap(Reserva::getId, Function.identity()));
        agendaConfirmadas = agendaConfirmadas.stream()
            .filter(a -> reservasDoTenant.containsKey(a.getId()))
            .toList();

        // 4. Formas de pagamento por locação (lote no folio; só fatos de caixa
        //    tipo PAGAMENTO, distintas, em ordem cronológica)
        Map<UUID, List<String>> formasPorLocacao = porId.isEmpty() ? Map.of()
            : reservaLancamentoRepository.findByLocacaoIdIn(porId.keySet()).stream()
                .filter(l -> l.getTipo() == ReservaLancamento.Tipo.PAGAMENTO && l.getForma() != null)
                .sorted(Comparator.comparing(ReservaLancamento::getCreatedAt))
                .collect(Collectors.groupingBy(ReservaLancamento::getLocacaoId,
                    Collectors.collectingAndThen(
                        Collectors.mapping(l -> l.getForma().name(), Collectors.toList()),
                        formas -> formas.stream().distinct().toList())));

        // 5. Nomes resolvidos em lote (anti-N+1) — mesmo caminho do toResponse
        Map<UUID, Jetski> jetskis = jetskiRepository.findAllById(
                ordenadas.stream().map(Locacao::getJetskiId).distinct().toList()).stream()
            .collect(Collectors.toMap(Jetski::getId, Function.identity()));
        // modelo da locação vem via jetski (jetski → modelo); o da reserva já vem da agenda
        Map<UUID, String> modelos = modeloRepository.findAllById(
                jetskis.values().stream().map(Jetski::getModeloId)
                    .filter(Objects::nonNull).distinct().toList()).stream()
            .collect(Collectors.toMap(Modelo::getId, Modelo::getNome));
        Map<UUID, String> clientes = clienteRepository.findAllById(
                ordenadas.stream().map(Locacao::getClienteId)
                    .filter(Objects::nonNull).distinct().toList()).stream()
            .collect(Collectors.toMap(Cliente::getId, Cliente::getNome));
        // vendedores das locações E das reservas em um único lote
        Map<UUID, Vendedor> vendedores = vendedorRepository.findAllById(
                Stream.concat(
                        ordenadas.stream().map(Locacao::getVendedorId),
                        reservasDoTenant.values().stream().map(Reserva::getVendedorId))
                    .filter(Objects::nonNull).distinct().toList()).stream()
            .collect(Collectors.toMap(Vendedor::getId, v -> v));

        // 6. Linhas LOCACAO (trio de prontidão null — conceito de reserva)
        List<ControleDoDiaResponse.Linha> linhasEmCurso = emCurso.stream()
            .map(l -> linhaLocacao(l, jetskis, modelos, clientes, vendedores, formasPorLocacao))
            .toList();
        List<ControleDoDiaResponse.Linha> linhasDemais = demais.stream()
            .map(l -> linhaLocacao(l, jetskis, modelos, clientes, vendedores, formasPorLocacao))
            .toList();

        // 7. Linhas RESERVA (o que ainda vai chegar hoje): dataCheckIn = início
        //    previsto, duração derivada do intervalo, sem check-out/formas
        List<ControleDoDiaResponse.Linha> linhasReserva = agendaConfirmadas.stream()
            .map(a -> {
                UUID vendedorId = reservasDoTenant.get(a.getId()).getVendedorId();
                return new ControleDoDiaResponse.Linha(
                    "RESERVA",
                    null,
                    a.getId(),
                    a.getJetskiId(),
                    a.getJetskiSerie(),
                    a.getModeloId(),
                    a.getModeloNome(),
                    a.getClienteNome(),
                    vendedorId != null && vendedores.containsKey(vendedorId)
                        ? vendedores.get(vendedorId).getNome() : null,
                    a.getDataInicio(),
                    a.getDataInicio() != null && a.getDataFimPrevista() != null
                        ? (int) Duration.between(a.getDataInicio(), a.getDataFimPrevista()).toMinutes()
                        : null,
                    null,
                    a.getStatus(),
                    a.getValorTotal(),
                    List.of(),
                    a.isPagamentoOk(),
                    a.isHabilitacaoOk(),
                    a.isTermoOk(),
                    a.isProntaParaCheckin());
            })
            .toList();

        // 8. Concatenação estável: em curso → reservas do dia → finalizadas/demais
        //    (o front separa por tipo/status; só a ordem dentro do grupo importa)
        List<ControleDoDiaResponse.Linha> linhas = Stream.of(
                linhasEmCurso.stream(), linhasReserva.stream(), linhasDemais.stream())
            .flatMap(Function.identity())
            .toList();

        // 9. Totais por forma — REGIME DE CAIXA, janela do dia no fuso do
        //    tenant (mesma construção do fechamento diário)
        ZoneId zone = tenantTimeService.getZoneIdForTenant(tenantId);
        Instant inicioCaixa = data.atStartOfDay(zone).toInstant();
        Instant fimCaixa = data.plusDays(1).atStartOfDay(zone).toInstant();
        Map<String, BigDecimal> totalPorForma = new LinkedHashMap<>();
        for (FolioQueryService.TotalPorForma t
                : folioQueryService.totalRecebidoPorFormaNoDia(tenantId, inicioCaixa, fimCaixa)) {
            totalPorForma.merge(t.forma().name(), t.valor(), BigDecimal::add);
        }

        // 10. Produção do dia (competência): valorTotal (null-safe) das LOCAÇÕES
        //     não canceladas com check-in no dia — linhas RESERVA ficam fora
        List<Locacao> producaoDoDia = ordenadas.stream()
            .filter(l -> l.getStatus() != LocacaoStatus.CANCELADA)
            .filter(l -> l.getDataCheckIn() != null && data.equals(l.getDataCheckIn().toLocalDate()))
            .toList();

        BigDecimal totalDia = producaoDoDia.stream()
            .map(Locacao::getValorTotal)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, BigDecimal> porVendedor = new LinkedHashMap<>();
        // Expectativa de comissão: simulação RN04 por locação (sem persistir) —
        // produção bruta engana (comissão varia por vendedor e por venda)
        Map<UUID, BigDecimal> expectativaPorVendedor = new LinkedHashMap<>();
        Set<UUID> vendedorSemPolitica = new HashSet<>();
        for (Locacao l : producaoDoDia) {
            if (l.getVendedorId() != null && l.getValorTotal() != null) {
                porVendedor.merge(l.getVendedorId(), l.getValorTotal(), BigDecimal::add);
                Jetski jet = jetskis.get(l.getJetskiId());
                Integer duracao = l.getMinutosFaturaveis() != null ? l.getMinutosFaturaveis()
                    : l.getMinutosUsados() != null ? l.getMinutosUsados() : l.getDuracaoPrevista();
                try {
                    BigDecimal sim = commissionService.simularComissao(
                        tenantId, l.getVendedorId(),
                        jet != null ? jet.getModeloId() : null,
                        duracao != null ? duracao : 60,
                        l.getValorTotal(), l.getCombustivelCusto(), l.getValorBase());
                    if (sim == null) {
                        vendedorSemPolitica.add(l.getVendedorId());
                    } else {
                        expectativaPorVendedor.merge(l.getVendedorId(), sim, BigDecimal::add);
                    }
                } catch (RuntimeException e) {
                    // expectativa é best-effort — nunca derruba a prancheta
                    log.warn("Simulação de comissão falhou (locacao={}): {}", l.getId(), e.getMessage());
                    vendedorSemPolitica.add(l.getVendedorId());
                }
            }
        }
        List<ControleDoDiaResponse.TotalVendedor> totalPorVendedor = porVendedor.entrySet().stream()
            .map(e -> new ControleDoDiaResponse.TotalVendedor(
                e.getKey(),
                vendedores.containsKey(e.getKey()) ? vendedores.get(e.getKey()).getNome() : null,
                e.getValue(),
                // qualquer locação sem política → expectativa incompleta: omite
                vendedorSemPolitica.contains(e.getKey()) ? null : expectativaPorVendedor.get(e.getKey())))
            .sorted(Comparator.comparing(ControleDoDiaResponse.TotalVendedor::total).reversed())
            .toList();

        return new ControleDoDiaResponse(linhas, totalPorForma, totalPorVendedor, totalDia);
    }

    /** Monta a linha LOCACAO com os nomes resolvidos dos lotes (sem N+1). */
    private static ControleDoDiaResponse.Linha linhaLocacao(
            Locacao l,
            Map<UUID, Jetski> jetskis,
            Map<UUID, String> modelos,
            Map<UUID, String> clientes,
            Map<UUID, Vendedor> vendedores,
            Map<UUID, List<String>> formasPorLocacao) {
        Jetski jetski = jetskis.get(l.getJetskiId());
        return new ControleDoDiaResponse.Linha(
            "LOCACAO",
            l.getId(),
            null,
            l.getJetskiId(),
            jetski != null ? jetski.getSerie() : null,
            jetski != null ? jetski.getModeloId() : null,
            jetski != null && jetski.getModeloId() != null ? modelos.get(jetski.getModeloId()) : null,
            l.getClienteId() != null ? clientes.get(l.getClienteId()) : null,
            l.getVendedorId() != null && vendedores.containsKey(l.getVendedorId())
                ? vendedores.get(l.getVendedorId()).getNome() : null,
            l.getDataCheckIn(),
            l.getDuracaoPrevista(),
            l.getDataCheckOut(),
            l.getStatus() != null ? l.getStatus().name() : null,
            l.getValorTotal(),
            formasPorLocacao.getOrDefault(l.getId(), List.of()),
            null,
            null,
            null,
            null);
    }

    /** Volta prevista = dataCheckIn + duracaoPrevista (não há coluna de fim previsto). */
    private static LocalDateTime voltaPrevista(Locacao l) {
        return l.getDataCheckIn().plusMinutes(l.getDuracaoPrevista() != null ? l.getDuracaoPrevista() : 0);
    }
}
