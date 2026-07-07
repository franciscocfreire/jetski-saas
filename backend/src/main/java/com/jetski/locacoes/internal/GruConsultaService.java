package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.GruConsultaResponse;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.DocumentoEmitido;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.DocumentoEmitidoRepository;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Módulo GRUs (staff): ciclo das GRUs emitidas via EMA — inclusive as que
 * ainda não têm documentação emitida — com o estado do envio à Marinha.
 * Mesmo padrão de join em memória do {@link DocumentoConsultaService}
 * (≤200 linhas, RLS pelo tenant da requisição).
 */
@Service
@RequiredArgsConstructor
public class GruConsultaService {

    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final DocumentoEmitidoRepository documentoRepository;

    @Transactional(readOnly = true)
    public List<GruConsultaResponse> listar() {
        List<ReservaHabilitacao> habs = habilitacaoRepository.listarGrus(PageRequest.of(0, 200));
        if (habs.isEmpty()) {
            return List.of();
        }

        List<UUID> reservaIds = habs.stream().map(ReservaHabilitacao::getReservaId).distinct().toList();
        Map<UUID, Reserva> reservas = reservaRepository.findAllById(reservaIds).stream()
            .collect(Collectors.toMap(Reserva::getId, Function.identity()));
        Map<UUID, String> nomes = clienteRepository.findAllById(
                reservas.values().stream().map(Reserva::getClienteId).distinct().toList()).stream()
            .collect(Collectors.toMap(Cliente::getId, Cliente::getNome));
        // pode haver N documentos por reserva — vale o MAIS RECENTE (lista já desc)
        Map<UUID, DocumentoEmitido> docs = documentoRepository
            .findByReservaIdInOrderByEmitidoEmDesc(reservaIds).stream()
            .collect(Collectors.toMap(DocumentoEmitido::getReservaId,
                Function.identity(), (primeiro, ignorado) -> primeiro));

        return habs.stream().map(h -> {
            Reserva r = reservas.get(h.getReservaId());
            UUID clienteId = r != null ? r.getClienteId() : null;
            DocumentoEmitido doc = docs.get(h.getReservaId());
            return GruConsultaResponse.builder()
                .reservaId(h.getReservaId())
                .clienteId(clienteId)
                .clienteNome(clienteId != null ? nomes.get(clienteId) : null)
                .gruNumero(h.getGruNumero())
                .gruValor(h.getGruValor())
                .gruPago(h.getGruPago())
                .gruPagoEm(h.getGruPagoEm())
                .gruGeradaEm(h.getGruGeradaEm() != null ? h.getGruGeradaEm() : h.getCreatedAt())
                .documentoId(doc != null ? doc.getId() : null)
                .documentoEmitidoEm(r != null ? r.getDocumentoEmitidoEm() : null)
                .marinhaEnviadaEm(doc != null ? doc.getMarinhaEnviadoEm() : null)
                .marinhaConfirmadaEm(h.getMarinhaConfirmadaEm())
                .build();
        }).toList();
    }
}
