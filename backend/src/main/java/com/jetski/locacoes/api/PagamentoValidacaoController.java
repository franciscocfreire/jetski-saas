package com.jetski.locacoes.api;

import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaComprovante;
import com.jetski.locacoes.internal.repository.ReservaComprovanteRepository;
import com.jetski.shared.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fila de validação de pagamentos (staff): reservas com comprovante enviado
 * pelo cliente (EM_ANALISE) + download dos comprovantes. A confirmação/recusa
 * usa os endpoints existentes confirmar-sinal / recusar-pagamento.
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas — Validação de Pagamento", description = "Fila de sinais/pagamentos a validar (staff)")
public class PagamentoValidacaoController {

    private final EntityManager entityManager;
    private final ReservaComprovanteRepository comprovanteRepository;
    private final StorageService storageService;

    public record PagamentoPendente(
        UUID reservaId, String clienteNome, String modeloNome,
        LocalDateTime dataInicio, String pagamentoTipo,
        BigDecimal valorInformado, BigDecimal valorEstimadoTotal,
        Instant comprovanteEnviadoEm, String canal) {}

    public record ComprovanteDTO(UUID id, String tipo, Instant enviadoEm, String downloadUrl) {}

    @GetMapping("/pagamentos-pendentes")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'FINANCEIRO')")
    @Transactional(readOnly = true)
    @Operation(summary = "Reservas com pagamento EM_ANALISE (comprovante enviado)")
    public ResponseEntity<List<PagamentoPendente>> pendentes(@PathVariable UUID tenantId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT r.id, c.nome, m.nome, r.data_inicio, r.pagamento_tipo,
                       r.pagamento_valor_informado,
                       (m.preco_base_hora *
                        (EXTRACT(EPOCH FROM (r.data_fim_prevista - r.data_inicio)) / 3600.0)),
                       (SELECT max(rc.enviado_em) FROM reserva_comprovante rc
                         WHERE rc.reserva_id = r.id AND rc.ativo = true),
                       r.canal
                  FROM reserva r
                  JOIN cliente c ON c.id = r.cliente_id
                  JOIN modelo m ON m.id = r.modelo_id
                 WHERE r.pagamento_status = 'EM_ANALISE'
                   AND r.ativo = true
                 ORDER BY 8 NULLS LAST
                """)
            .getResultList();

        List<PagamentoPendente> pendentes = rows.stream().map(r -> new PagamentoPendente(
            (UUID) r[0], (String) r[1], (String) r[2],
            toLocalDateTime(r[3]),
            (String) r[4],
            (BigDecimal) r[5],
            r[6] != null ? new BigDecimal(r[6].toString()).setScale(2, java.math.RoundingMode.HALF_UP) : null,
            toInstant(r[7]),
            (String) r[8])).toList();

        return ResponseEntity.ok(pendentes);
    }

    // Hibernate 6 devolve java.time direto p/ timestamp(tz) em native query;
    // drivers antigos devolvem java.sql.Timestamp — aceita ambos.
    private static LocalDateTime toLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof Instant i) return LocalDateTime.ofInstant(i, java.time.ZoneId.systemDefault());
        throw new IllegalStateException("Tipo temporal inesperado: " + v.getClass());
    }

    private static Instant toInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (v instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        throw new IllegalStateException("Tipo temporal inesperado: " + v.getClass());
    }

    @GetMapping("/{id}/comprovantes")
    @PreAuthorize("hasAnyRole('ADMIN_TENANT', 'GERENTE', 'OPERADOR', 'FINANCEIRO')")
    @Operation(summary = "Comprovantes anexados à reserva (com URL de download temporária)")
    public ResponseEntity<List<ComprovanteDTO>> comprovantes(
            @PathVariable UUID tenantId,
            @PathVariable UUID id) {
        List<ComprovanteDTO> lista = comprovanteRepository
            .findByReservaIdAndAtivoTrueOrderByEnviadoEmDesc(id)
            .stream()
            .map(c -> new ComprovanteDTO(
                c.getId(), c.getTipo(), c.getEnviadoEm(),
                storageService.generatePresignedDownloadUrl(c.getS3Key(), 15).getUrl()))
            .toList();
        return ResponseEntity.ok(lista);
    }
}
