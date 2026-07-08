package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.AgendaReservaResponse;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Jetski;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.JetskiRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.locacoes.internal.repository.ReservaAceiteRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Visão AGENDA do dia (grade por jetski): reservas do dia com nomes resolvidos
 * e o trio de PRONTIDÃO em lote (sem N+1) — mesma regra do checklist do
 * cliente: pagamento CONFIRMADO, habilitação resolvida, termo assinado.
 */
@Service
@RequiredArgsConstructor
public class ReservaAgendaService {

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final ModeloRepository modeloRepository;
    private final JetskiRepository jetskiRepository;
    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ReservaAceiteRepository aceiteRepository;

    @Transactional(readOnly = true)
    public List<AgendaReservaResponse> doDia(LocalDate data) {
        List<Reserva> reservas = reservaRepository.buscar(
                null, null, null,
                data.atStartOfDay(), data.plusDays(1).atStartOfDay(),
                PageRequest.of(0, 500)).stream()
            .filter(r -> r.getStatus() != Reserva.ReservaStatus.RASCUNHO)
            .sorted(Comparator.comparing(Reserva::getDataInicio))
            .toList();
        if (reservas.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = reservas.stream().map(Reserva::getId).toList();
        Map<UUID, String> clientes = clienteRepository.findAllById(
                reservas.stream().map(Reserva::getClienteId).distinct().toList()).stream()
            .collect(Collectors.toMap(Cliente::getId, Cliente::getNome));
        Map<UUID, String> modelos = modeloRepository.findAllById(
                reservas.stream().map(Reserva::getModeloId).distinct().toList()).stream()
            .collect(Collectors.toMap(Modelo::getId, Modelo::getNome));
        Map<UUID, String> jetskis = jetskiRepository.findAllById(
                reservas.stream().map(Reserva::getJetskiId)
                    .filter(java.util.Objects::nonNull).distinct().toList()).stream()
            .collect(Collectors.toMap(Jetski::getId, Jetski::getSerie));
        Map<UUID, ReservaHabilitacao> habs = habilitacaoRepository.findByReservaIdIn(ids).stream()
            .collect(Collectors.toMap(ReservaHabilitacao::getReservaId, Function.identity()));
        // aceites: fica o mais recente por reserva
        Map<UUID, ReservaAceite> aceites = aceiteRepository.findByReservaIdIn(ids).stream()
            .collect(Collectors.toMap(ReservaAceite::getReservaId, Function.identity(),
                (a, b) -> a.getAceitoEm().isAfter(b.getAceitoEm()) ? a : b));

        return reservas.stream().map(r -> {
            ReservaHabilitacao h = habs.get(r.getId());
            boolean pagamentoOk = r.getPagamentoStatus() == Reserva.PagamentoStatus.CONFIRMADO;
            boolean habilitacaoOk = h != null && Boolean.TRUE.equals(h.getResolvida());
            boolean termoOk = aceites.containsKey(r.getId());
            return AgendaReservaResponse.builder()
                .id(r.getId())
                .clienteId(r.getClienteId())
                .clienteNome(clientes.get(r.getClienteId()))
                .modeloId(r.getModeloId())
                .modeloNome(modelos.get(r.getModeloId()))
                .jetskiId(r.getJetskiId())
                .jetskiSerie(r.getJetskiId() != null ? jetskis.get(r.getJetskiId()) : null)
                .dataInicio(r.getDataInicio())
                .dataFimPrevista(r.getDataFimPrevista())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .canal(r.getCanal() != null ? r.getCanal().name() : null)
                .valorTotal(r.getValorTotal())
                .pagamentoOk(pagamentoOk)
                .habilitacaoOk(habilitacaoOk)
                .habilitacaoVia(h != null && h.getVia() != null ? h.getVia().name() : null)
                .termoOk(termoOk)
                .prontaParaCheckin(pagamentoOk && habilitacaoOk && termoOk)
                .build();
        }).toList();
    }
}
