package com.jetski.audit.internal;

import com.jetski.locacoes.event.CheckInEvent;
import com.jetski.locacoes.event.CheckOutEvent;
import com.jetski.locacoes.event.ClaimEnviadoEvent;
import com.jetski.locacoes.event.ContaAtivadaEvent;
import com.jetski.locacoes.event.DataCheckInAlteradaEvent;
import com.jetski.locacoes.event.LocacaoEditadaEvent;
import com.jetski.locacoes.event.PreContaCriadaEvent;
import com.jetski.reservas.domain.event.DocumentosEmitidosEvent;
import com.jetski.reservas.domain.event.PagamentoConfirmadoEvent;
import com.jetski.reservas.domain.event.PagamentoRecusadoEvent;
import com.jetski.reservas.domain.event.ReservationCancelledEvent;
import com.jetski.reservas.domain.event.ReservationConfirmedEvent;
import com.jetski.reservas.domain.event.ReservationCreatedEvent;
import com.jetski.audit.domain.Auditoria;
import com.jetski.audit.domain.AuditoriaRepository;
import com.jetski.shared.observability.MDCKeys;
import com.jetski.usuarios.domain.event.MemberActivatedEvent;
import com.jetski.usuarios.domain.event.MemberDeactivatedEvent;
import com.jetski.usuarios.domain.event.MemberInvitedEvent;
import com.jetski.usuarios.domain.event.MemberRolesChangedEvent;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized Event Listener for Audit Logging.
 *
 * <p>Listens to domain events and persists audit entries to the database.
 * This component enables loose coupling between business operations
 * and audit requirements.
 *
 * <p><strong>Features:</strong>
 * <ul>
 *   <li>Async processing with TenantContext propagation (via AsyncConfig TaskDecorator)</li>
 *   <li>REQUIRES_NEW transaction for isolation from main business transaction</li>
 *   <li>Automatic context capture (trace_id, IP, user agent)</li>
 *   <li>JSONB snapshots for before/after state</li>
 *   <li>Graceful error handling (audit failures don't break business flow)</li>
 * </ul>
 *
 * <p><strong>Supported Events:</strong>
 * <ul>
 *   <li>{@link CheckInEvent} - When a rental starts</li>
 *   <li>{@link CheckOutEvent} - When a rental completes</li>
 * </ul>
 *
 * <p><strong>Future Events (add listeners as needed):</strong>
 * <ul>
 *   <li>JetskiStatusChangedEvent</li>
 *   <li>MaintenanceOrderEvent</li>
 *   <li>ReservationCreatedEvent</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditoriaRepository auditoriaRepository;

    /**
     * Handles check-in events.
     *
     * <p>Captures the initial state when a rental begins, including:
     * <ul>
     *   <li>Jetski ID and initial hourmeter</li>
     *   <li>Customer and operator info</li>
     *   <li>Check-in type (reservation vs walk-in)</li>
     * </ul>
     *
     * @param event the check-in domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckIn(CheckInEvent event) {
        try {
            log.debug("Processing audit for check-in event: locacaoId={}", event.locacaoId());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("locacaoId", event.locacaoId().toString());
            dadosNovos.put("jetskiId", event.jetskiId().toString());
            dadosNovos.put("horimetroInicio", event.horimetroInicio());
            dadosNovos.put("dataCheckIn", event.dataCheckIn().toString());
            dadosNovos.put("tipo", event.tipo().name());

            if (event.reservaId() != null) {
                dadosNovos.put("reservaId", event.reservaId().toString());
            }
            if (event.clienteId() != null) {
                dadosNovos.put("clienteId", event.clienteId().toString());
            }

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.operadorId())
                    .acao("CHECK_IN")
                    .entidade("LOCACAO")
                    .entidadeId(event.locacaoId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for check-in: locacaoId={}, auditId={}",
                    event.locacaoId(), auditoria.getId());

        } catch (Exception e) {
            // Log but don't throw - audit failures should not break business flow
            log.error("Failed to create audit entry for check-in: locacaoId={}, error={}",
                    event.locacaoId(), e.getMessage(), e);
        }
    }

    /**
     * Handles check-out events.
     *
     * <p>Captures the final state when a rental completes, including:
     * <ul>
     *   <li>Final hourmeter and minutes used</li>
     *   <li>Total value charged</li>
     *   <li>Check-out timestamp</li>
     * </ul>
     *
     * @param event the check-out domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckOut(CheckOutEvent event) {
        try {
            log.debug("Processing audit for check-out event: locacaoId={}", event.locacaoId());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("locacaoId", event.locacaoId().toString());
            dadosNovos.put("jetskiId", event.jetskiId().toString());
            dadosNovos.put("horimetroFim", event.horimetroFim());
            dadosNovos.put("minutosUsados", event.minutosUsados());
            dadosNovos.put("valorTotal", event.valorTotal() != null ? event.valorTotal().toString() : null);
            dadosNovos.put("dataCheckOut", event.dataCheckOut().toString());

            if (event.clienteId() != null) {
                dadosNovos.put("clienteId", event.clienteId().toString());
            }

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.operadorId())
                    .acao("CHECK_OUT")
                    .entidade("LOCACAO")
                    .entidadeId(event.locacaoId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for check-out: locacaoId={}, auditId={}, valorTotal={}",
                    event.locacaoId(), auditoria.getId(), event.valorTotal());

        } catch (Exception e) {
            // Log but don't throw - audit failures should not break business flow
            log.error("Failed to create audit entry for check-out: locacaoId={}, error={}",
                    event.locacaoId(), e.getMessage(), e);
        }
    }

    /**
     * Handles check-in time change events.
     *
     * <p>Captures when the check-in time of an active rental is modified.
     *
     * @param event the data check-in altered domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDataCheckInAlterada(DataCheckInAlteradaEvent event) {
        try {
            log.debug("Processing audit for data check-in alterada event: locacaoId={}", event.locacaoId());

            Map<String, Object> dadosAnteriores = new HashMap<>();
            dadosAnteriores.put("dataCheckIn", event.dataAnterior() != null ? event.dataAnterior().toString() : null);

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("locacaoId", event.locacaoId().toString());
            dadosNovos.put("dataCheckIn", event.dataNova() != null ? event.dataNova().toString() : null);

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.operadorId())
                    .acao("DATA_CHECKIN_ALTERADA")
                    .entidade("LOCACAO")
                    .entidadeId(event.locacaoId())
                    .dadosAnteriores(dadosAnteriores)
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for data check-in alterada: locacaoId={}, auditId={}, de {} para {}",
                    event.locacaoId(), auditoria.getId(), event.dataAnterior(), event.dataNova());

        } catch (Exception e) {
            log.error("Failed to create audit entry for data check-in alterada: locacaoId={}, error={}",
                    event.locacaoId(), e.getMessage(), e);
        }
    }

    /**
     * Handles locacao edited events (finalized rentals edited before closure).
     *
     * <p>Captures the before/after state when a finalized rental is edited,
     * including all changed fields and the reason for the edit.
     *
     * @param event the locacao edited domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLocacaoEditada(LocacaoEditadaEvent event) {
        try {
            log.debug("Processing audit for locacao edited event: locacaoId={}", event.locacaoId());

            // Add edit reason to dadosNovos
            Map<String, Object> dadosNovos = new HashMap<>(event.dadosNovos());
            dadosNovos.put("motivoEdicao", event.motivoEdicao());

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.operadorId())
                    .acao("LOCACAO_FINALIZADA_EDITADA")
                    .entidade("LOCACAO")
                    .entidadeId(event.locacaoId())
                    .dadosAnteriores(event.dadosAnteriores())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for locacao edited: locacaoId={}, auditId={}, motivo={}",
                    event.locacaoId(), auditoria.getId(), event.motivoEdicao());

        } catch (Exception e) {
            log.error("Failed to create audit entry for locacao edited: locacaoId={}, error={}",
                    event.locacaoId(), e.getMessage(), e);
        }
    }

    // ===================================================================
    // Reservation Event Handlers
    // ===================================================================

    /**
     * Handles reservation created events.
     *
     * @param event the reservation created domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationCreated(ReservationCreatedEvent event) {
        try {
            log.debug("Processing audit for reservation created event: reservaId={}", event.reservaId());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("reservaId", event.reservaId().toString());
            dadosNovos.put("modeloId", event.modeloId().toString());
            dadosNovos.put("dataInicio", event.dataInicio().toString());
            dadosNovos.put("dataFimPrevista", event.dataFimPrevista().toString());
            dadosNovos.put("sinalPago", event.sinalPago());

            if (event.clienteId() != null) {
                dadosNovos.put("clienteId", event.clienteId().toString());
            }
            if (event.vendedorId() != null) {
                dadosNovos.put("vendedorId", event.vendedorId().toString());
            }

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.operadorId())
                    .acao("RESERVATION_CREATED")
                    .entidade("RESERVA")
                    .entidadeId(event.reservaId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for reservation created: reservaId={}, auditId={}",
                    event.reservaId(), auditoria.getId());

        } catch (Exception e) {
            log.error("Failed to create audit entry for reservation created: reservaId={}, error={}",
                    event.reservaId(), e.getMessage(), e);
        }
    }

    /**
     * Handles reservation confirmed events.
     *
     * @param event the reservation confirmed domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationConfirmed(ReservationConfirmedEvent event) {
        try {
            log.debug("Processing audit for reservation confirmed event: reservaId={}", event.reservaId());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("reservaId", event.reservaId().toString());
            dadosNovos.put("confirmedBy", event.confirmedBy() != null ? event.confirmedBy().toString() : null);

            if (event.clienteId() != null) {
                dadosNovos.put("clienteId", event.clienteId().toString());
            }

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.confirmedBy())
                    .acao("RESERVATION_CONFIRMED")
                    .entidade("RESERVA")
                    .entidadeId(event.reservaId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for reservation confirmed: reservaId={}, auditId={}",
                    event.reservaId(), auditoria.getId());

        } catch (Exception e) {
            log.error("Failed to create audit entry for reservation confirmed: reservaId={}, error={}",
                    event.reservaId(), e.getMessage(), e);
        }
    }

    // ===================== Balcão / Pagamento (Fase 1) =====================

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPagamentoConfirmado(PagamentoConfirmadoEvent event) {
        try {
            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("tipo", event.tipo());
            dadosNovos.put("valorPago", event.valorPago() != null ? event.valorPago().toString() : null);
            dadosNovos.put("confirmadoPor", event.confirmadoPor() != null ? event.confirmadoPor().toString() : null);

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.confirmadoPor())
                    .acao("PAGAMENTO_CONFIRMADO")
                    .entidade("RESERVA")
                    .entidadeId(event.reservaId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();
            auditoriaRepository.save(auditoria);
            log.info("Audit: PAGAMENTO_CONFIRMADO reservaId={}, auditId={}", event.reservaId(), auditoria.getId());
        } catch (Exception e) {
            log.error("Failed to audit PAGAMENTO_CONFIRMADO: reservaId={}, error={}", event.reservaId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPagamentoRecusado(PagamentoRecusadoEvent event) {
        try {
            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("motivo", event.motivo());
            dadosNovos.put("recusadoPor", event.recusadoPor() != null ? event.recusadoPor().toString() : null);

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.recusadoPor())
                    .acao("PAGAMENTO_RECUSADO")
                    .entidade("RESERVA")
                    .entidadeId(event.reservaId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();
            auditoriaRepository.save(auditoria);
            log.info("Audit: PAGAMENTO_RECUSADO reservaId={}, auditId={}", event.reservaId(), auditoria.getId());
        } catch (Exception e) {
            log.error("Failed to audit PAGAMENTO_RECUSADO: reservaId={}, error={}", event.reservaId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDocumentosEmitidos(DocumentosEmitidosEvent event) {
        try {
            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("documentoId", event.documentoId() != null ? event.documentoId().toString() : null);
            dadosNovos.put("destinos", event.destinos());
            dadosNovos.put("emitidoPor", event.emitidoPor() != null ? event.emitidoPor().toString() : null);

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.emitidoPor())
                    .acao("DOCUMENTOS_EMITIDOS")
                    .entidade("RESERVA")
                    .entidadeId(event.reservaId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();
            auditoriaRepository.save(auditoria);
            log.info("Audit: DOCUMENTOS_EMITIDOS reservaId={}, auditId={}", event.reservaId(), auditoria.getId());
        } catch (Exception e) {
            log.error("Failed to audit DOCUMENTOS_EMITIDOS: reservaId={}, error={}", event.reservaId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPreContaCriada(PreContaCriadaEvent event) {
        try {
            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("origem", event.origem());
            dadosNovos.put("criadoPor", event.criadoPor() != null ? event.criadoPor().toString() : null);

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.criadoPor())
                    .acao("PRE_CONTA_CRIADA")
                    .entidade("CLIENTE")
                    .entidadeId(event.clienteId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();
            auditoriaRepository.save(auditoria);
            log.info("Audit: PRE_CONTA_CRIADA clienteId={}, auditId={}", event.clienteId(), auditoria.getId());
        } catch (Exception e) {
            log.error("Failed to audit PRE_CONTA_CRIADA: clienteId={}, error={}", event.clienteId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onClaimEnviado(ClaimEnviadoEvent event) {
        try {
            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("canais", event.canais());
            dadosNovos.put("enviadoPor", event.enviadoPor() != null ? event.enviadoPor().toString() : null);

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.enviadoPor())
                    .acao("CLAIM_ENVIADO")
                    .entidade("CLIENTE")
                    .entidadeId(event.clienteId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();
            auditoriaRepository.save(auditoria);
            log.info("Audit: CLAIM_ENVIADO clienteId={}, auditId={}", event.clienteId(), auditoria.getId());
        } catch (Exception e) {
            log.error("Failed to audit CLAIM_ENVIADO: clienteId={}, error={}", event.clienteId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onContaAtivada(ContaAtivadaEvent event) {
        try {
            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("providerUserId", event.providerUserId());

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .acao("CONTA_ATIVADA")
                    .entidade("CLIENTE")
                    .entidadeId(event.clienteId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();
            auditoriaRepository.save(auditoria);
            log.info("Audit: CONTA_ATIVADA clienteId={}, auditId={}", event.clienteId(), auditoria.getId());
        } catch (Exception e) {
            log.error("Failed to audit CONTA_ATIVADA: clienteId={}, error={}", event.clienteId(), e.getMessage(), e);
        }
    }

    /**
     * Handles reservation cancelled events.
     *
     * @param event the reservation cancelled domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReservationCancelled(ReservationCancelledEvent event) {
        try {
            log.debug("Processing audit for reservation cancelled event: reservaId={}", event.reservaId());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("reservaId", event.reservaId().toString());
            dadosNovos.put("cancelledBy", event.cancelledBy() != null ? event.cancelledBy().toString() : null);

            if (event.clienteId() != null) {
                dadosNovos.put("clienteId", event.clienteId().toString());
            }
            if (event.reason() != null) {
                dadosNovos.put("reason", event.reason());
            }

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.cancelledBy())
                    .acao("RESERVATION_CANCELLED")
                    .entidade("RESERVA")
                    .entidadeId(event.reservaId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for reservation cancelled: reservaId={}, auditId={}",
                    event.reservaId(), auditoria.getId());

        } catch (Exception e) {
            log.error("Failed to create audit entry for reservation cancelled: reservaId={}, error={}",
                    event.reservaId(), e.getMessage(), e);
        }
    }

    // ===================================================================
    // Member Event Handlers
    // ===================================================================

    /**
     * Handles member invited events.
     *
     * @param event the member invited domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberInvited(MemberInvitedEvent event) {
        try {
            log.debug("Processing audit for member invited event: email={}", event.email());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("conviteId", event.conviteId().toString());
            dadosNovos.put("email", event.email());
            dadosNovos.put("nome", event.nome());
            dadosNovos.put("papeis", Arrays.asList(event.papeis()));

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.invitedBy())
                    .acao("MEMBER_INVITED")
                    .entidade("MEMBRO")
                    .entidadeId(event.conviteId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for member invited: email={}, auditId={}",
                    event.email(), auditoria.getId());

        } catch (Exception e) {
            log.error("Failed to create audit entry for member invited: email={}, error={}",
                    event.email(), e.getMessage(), e);
        }
    }

    /**
     * Handles member activated events.
     *
     * @param event the member activated domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberActivated(MemberActivatedEvent event) {
        try {
            log.debug("Processing audit for member activated event: usuarioId={}", event.usuarioId());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("usuarioId", event.usuarioId().toString());
            dadosNovos.put("email", event.email());
            dadosNovos.put("nome", event.nome());
            dadosNovos.put("papeis", Arrays.asList(event.papeis()));

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.usuarioId())
                    .acao("MEMBER_ACTIVATED")
                    .entidade("MEMBRO")
                    .entidadeId(event.usuarioId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for member activated: usuarioId={}, auditId={}",
                    event.usuarioId(), auditoria.getId());

        } catch (Exception e) {
            log.error("Failed to create audit entry for member activated: usuarioId={}, error={}",
                    event.usuarioId(), e.getMessage(), e);
        }
    }

    /**
     * Handles member roles changed events.
     *
     * @param event the member roles changed domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberRolesChanged(MemberRolesChangedEvent event) {
        try {
            log.debug("Processing audit for member roles changed event: usuarioId={}", event.usuarioId());

            Map<String, Object> dadosAnteriores = new HashMap<>();
            dadosAnteriores.put("papeis", Arrays.asList(event.previousRoles()));

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("usuarioId", event.usuarioId().toString());
            dadosNovos.put("email", event.email());
            dadosNovos.put("papeis", Arrays.asList(event.newRoles()));
            dadosNovos.put("changedBy", event.changedBy() != null ? event.changedBy().toString() : null);

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.changedBy())
                    .acao("MEMBER_ROLES_CHANGED")
                    .entidade("MEMBRO")
                    .entidadeId(event.usuarioId())
                    .dadosAnteriores(dadosAnteriores)
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for member roles changed: usuarioId={}, auditId={}",
                    event.usuarioId(), auditoria.getId());

        } catch (Exception e) {
            log.error("Failed to create audit entry for member roles changed: usuarioId={}, error={}",
                    event.usuarioId(), e.getMessage(), e);
        }
    }

    /**
     * Handles member deactivated events.
     *
     * @param event the member deactivated domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberDeactivated(MemberDeactivatedEvent event) {
        try {
            log.debug("Processing audit for member deactivated event: usuarioId={}", event.usuarioId());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("usuarioId", event.usuarioId().toString());
            dadosNovos.put("email", event.email());
            dadosNovos.put("deactivatedBy", event.deactivatedBy() != null ? event.deactivatedBy().toString() : null);

            if (event.reason() != null) {
                dadosNovos.put("reason", event.reason());
            }

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.deactivatedBy())
                    .acao("MEMBER_DEACTIVATED")
                    .entidade("MEMBRO")
                    .entidadeId(event.usuarioId())
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for member deactivated: usuarioId={}, auditId={}",
                    event.usuarioId(), auditoria.getId());

        } catch (Exception e) {
            log.error("Failed to create audit entry for member deactivated: usuarioId={}, error={}",
                    event.usuarioId(), e.getMessage(), e);
        }
    }

    /**
     * Handles tenant status changes by the platform super admin (approve/suspend/reactivate).
     *
     * @param event the tenant status changed domain event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTenantStatusChanged(TenantStatusChangedEvent event) {
        try {
            log.debug("Processing audit for tenant status change: tenant={}, acao={}",
                    event.tenantId(), event.acao());

            Map<String, Object> dadosAnteriores = new HashMap<>();
            dadosAnteriores.put("status", event.fromStatus());

            Map<String, Object> dadosNovos = new HashMap<>();
            dadosNovos.put("status", event.toStatus());
            dadosNovos.put("actor", event.actor() != null ? event.actor().toString() : null);
            if (event.motivo() != null) {
                dadosNovos.put("motivo", event.motivo());
            }

            Auditoria auditoria = Auditoria.builder()
                    .tenantId(event.tenantId())
                    .usuarioId(event.actor())
                    .acao(event.acao())
                    .entidade("TENANT")
                    .entidadeId(event.tenantId())
                    .dadosAnteriores(dadosAnteriores)
                    .dadosNovos(dadosNovos)
                    .traceId(getTraceId())
                    .ip(getRemoteIp())
                    .build();

            auditoriaRepository.save(auditoria);
            log.info("Audit entry created for tenant status change: tenant={}, acao={}, auditId={}",
                    event.tenantId(), event.acao(), auditoria.getId());

        } catch (Exception e) {
            log.error("Failed to create audit entry for tenant status change: tenant={}, error={}",
                    event.tenantId(), e.getMessage(), e);
        }
    }

    // ===================================================================
    // Context Capture Helpers
    // ===================================================================

    /**
     * Gets the trace ID from MDC (set by RequestCorrelationFilter).
     */
    private String getTraceId() {
        return MDC.get(MDCKeys.TRACE_ID);
    }

    /**
     * Gets the remote IP from MDC (set by RequestCorrelationFilter).
     */
    private String getRemoteIp() {
        return MDC.get(MDCKeys.REMOTE_IP);
    }
}
