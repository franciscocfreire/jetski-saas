package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.ReservaBuscaResponse;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Modelo;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.ModeloRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Módulo Reservas (busca staff): filtros server-side + nomes resolvidos,
 * limitado a 200 linhas — mesma abordagem do módulo GRUs.
 */
@Service
@RequiredArgsConstructor
public class ReservaBuscaService {

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final ModeloRepository modeloRepository;

    @Transactional(readOnly = true)
    public List<ReservaBuscaResponse> buscar(Reserva.ReservaStatus status, Reserva.Canal canal,
                                             UUID clienteId, LocalDateTime de, LocalDateTime ate) {
        List<Reserva> reservas = reservaRepository.buscar(
            status, canal, clienteId, de, ate, PageRequest.of(0, 200));
        if (reservas.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> clientes = clienteRepository.findAllById(
                reservas.stream().map(Reserva::getClienteId).distinct().toList()).stream()
            .collect(Collectors.toMap(Cliente::getId, Cliente::getNome));
        Map<UUID, String> modelos = modeloRepository.findAllById(
                reservas.stream().map(Reserva::getModeloId).distinct().toList()).stream()
            .collect(Collectors.toMap(Modelo::getId, Modelo::getNome));

        return reservas.stream().map(r -> ReservaBuscaResponse.builder()
            .id(r.getId())
            .clienteId(r.getClienteId())
            .clienteNome(clientes.get(r.getClienteId()))
            .modeloNome(modelos.get(r.getModeloId()))
            .dataInicio(r.getDataInicio())
            .dataFimPrevista(r.getDataFimPrevista())
            .status(r.getStatus() != null ? r.getStatus().name() : null)
            .canal(r.getCanal() != null ? r.getCanal().name() : null)
            .pagamentoStatus(r.getPagamentoStatus() != null ? r.getPagamentoStatus().name() : null)
            .valorTotal(r.getValorTotal())
            .documentoEmitido(r.getDocumentoEmitidoEm() != null)
            .build()).toList();
    }
}
