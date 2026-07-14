package com.jetski.metering.internal;

import com.jetski.locacoes.event.DocumentoPreviewGeradoEvent;
import com.jetski.locacoes.event.GruEmitidaEvent;
import com.jetski.metering.domain.EmissaoUso;
import com.jetski.metering.domain.EmissaoUsoRepository;
import com.jetski.metering.domain.TipoEmissao;
import com.jetski.reservas.domain.event.DocumentosEmitidosEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Contabiliza fatos de uso por tenant a partir dos eventos de domínio.
 *
 * <p>Mesmo contrato do {@code AuditEventListener}: assíncrono, transação própria,
 * best-effort (falha de metering nunca quebra o fluxo de negócio; log de erro).
 * Idempotente via índice único (tipo, referencia_id, ocorrido_em) — reprocesso
 * do mesmo evento é no-op.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeteringEventListener {

    private final EmissaoUsoRepository repository;

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDocumentosEmitidos(DocumentosEmitidosEvent event) {
        registrar(event.tenantId(), TipoEmissao.DOCUMENTO, event.documentoId(),
            event.destinos(), event.occurredAt(), event.emissorTenantId());
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGruEmitida(GruEmitidaEvent event) {
        registrar(event.tenantId(), TipoEmissao.GRU, event.habilitacaoId(),
            event.meio(), event.geradaEm(), null);
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDocumentoPreviewGerado(DocumentoPreviewGeradoEvent event) {
        registrar(event.tenantId(), TipoEmissao.PREVIA, event.reservaId(),
            event.destino(), event.occurredAt(), null);
    }

    /** Visível ao pacote para testes de idempotência (invocação síncrona). */
    void registrar(UUID tenantId, TipoEmissao tipo, UUID referenciaId, String destinos, Instant ocorridoEm) {
        registrar(tenantId, tipo, referenciaId, destinos, ocorridoEm, null);
    }

    /** Variante com a dimensão do emissor delegado (V048). */
    void registrar(UUID tenantId, TipoEmissao tipo, UUID referenciaId, String destinos,
                   Instant ocorridoEm, UUID emissorTenantId) {
        try {
            if (tenantId == null || referenciaId == null || ocorridoEm == null) {
                log.warn("Metering ignorado por dados incompletos: tipo={}, tenant={}, ref={}",
                    tipo, tenantId, referenciaId);
                return;
            }
            if (repository.existsByTipoAndReferenciaIdAndOcorridoEm(tipo, referenciaId, ocorridoEm)) {
                return; // reprocesso do mesmo evento
            }
            repository.save(EmissaoUso.builder()
                .tenantId(tenantId)
                .tipo(tipo)
                .referenciaId(referenciaId)
                .destinos(destinos != null && destinos.length() > 60 ? destinos.substring(0, 60) : destinos)
                .ocorridoEm(ocorridoEm)
                .emissorTenantId(emissorTenantId)
                .build());
            log.info("Metering: {} contabilizado (tenant={}, ref={})", tipo, tenantId, referenciaId);
        } catch (DataIntegrityViolationException e) {
            log.debug("Metering: {} já contabilizado (ref={})", tipo, referenciaId);
        } catch (Exception e) {
            log.error("Metering: falha ao contabilizar {} (tenant={}, ref={}): {}",
                tipo, tenantId, referenciaId, e.getMessage(), e);
        }
    }
}
