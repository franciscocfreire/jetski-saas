package com.jetski.locacoes.internal;

import com.jetski.locacoes.api.dto.DocumentoConsultaResponse;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.DocumentoEmitido;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.DocumentoEmitidoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consulta dos documentos emitidos (PDF consolidado) das reservas — por cliente
 * ou geral. Gera uma URL de download presigned por item.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentoConsultaService {

    private final DocumentoEmitidoRepository documentoRepository;
    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final StorageService storageService;

    /** Documentos de UMA reserva (ficha da reserva), mais recente primeiro. */
    @Transactional(readOnly = true)
    public List<DocumentoConsultaResponse> listarPorReserva(UUID reservaId) {
        List<DocumentoEmitido> docs =
            documentoRepository.findByReservaIdOrderByEmitidoEmDesc(reservaId);
        return docs.stream().map(d -> DocumentoConsultaResponse.builder()
            .id(d.getId())
            .reservaId(d.getReservaId())
            .emitidoEm(d.getEmitidoEm())
            .hashSha256(d.getHashSha256())
            .downloadUrl(String.format("/v1/tenants/%s/documentos/%s/download",
                d.getTenantId(), d.getId()))
            .build()).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentoConsultaResponse> listar(UUID clienteId) {
        List<DocumentoEmitido> docs;
        if (clienteId != null) {
            List<UUID> reservaIds = reservaRepository.findByClienteId(clienteId).stream()
                .map(Reserva::getId).collect(Collectors.toList());
            docs = reservaIds.isEmpty() ? List.of()
                : documentoRepository.findByReservaIdInOrderByEmitidoEmDesc(reservaIds);
        } else {
            docs = documentoRepository.findTop200ByOrderByEmitidoEmDesc();
        }
        if (docs.isEmpty()) {
            return List.of();
        }

        // reservaId → reserva (p/ obter clienteId)
        List<UUID> reservaIds = docs.stream().map(DocumentoEmitido::getReservaId).distinct().toList();
        Map<UUID, Reserva> reservas = reservaRepository.findAllById(reservaIds).stream()
            .collect(Collectors.toMap(Reserva::getId, Function.identity()));
        // clienteId → nome
        List<UUID> clienteIds = reservas.values().stream().map(Reserva::getClienteId).distinct().toList();
        Map<UUID, String> nomes = clienteRepository.findAllById(clienteIds).stream()
            .collect(Collectors.toMap(Cliente::getId, Cliente::getNome));

        UUID tenant = docs.get(0).getTenantId();
        return docs.stream().map(d -> {
            Reserva r = reservas.get(d.getReservaId());
            UUID cid = r != null ? r.getClienteId() : null;
            return DocumentoConsultaResponse.builder()
                .id(d.getId())
                .reservaId(d.getReservaId())
                .clienteId(cid)
                .clienteNome(cid != null ? nomes.get(cid) : null)
                .emitidoEm(d.getEmitidoEm())
                .hashSha256(d.getHashSha256())
                // caminho da API (download autenticado via streaming — vale local e MinIO)
                .downloadUrl(String.format("/v1/tenants/%s/documentos/%s/download", tenant, d.getId()))
                .build();
        }).collect(Collectors.toList());
    }

    /** Conteúdo do PDF + nome de arquivo, lido do storage para streaming. */
    public record DocumentoArquivo(byte[] conteudo, String filename) {}

    @Transactional(readOnly = true)
    public DocumentoArquivo baixar(UUID id) {
        DocumentoEmitido d = documentoRepository.findById(id)
            .orElseThrow(() -> new com.jetski.shared.exception.NotFoundException("Documento não encontrado: " + id));
        byte[] bytes = storageService.getObject(d.getS3Key());
        String ref = d.getReservaId() != null ? d.getReservaId().toString().substring(0, 8) : "doc";
        return new DocumentoArquivo(bytes, "documento-" + ref + ".pdf");
    }
}
