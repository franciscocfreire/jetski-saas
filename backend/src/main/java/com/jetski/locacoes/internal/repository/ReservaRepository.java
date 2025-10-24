package com.jetski.locacoes.internal.repository;

import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.Reserva.ReservaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository: ReservaRepository
 *
 * Handles database operations for reservations (Reserva).
 * RLS (Row Level Security) automatically filters queries by tenant_id.
 *
 * Core queries:
 * - List reservations by status and period
 * - Check for schedule conflicts (prevent overlapping reservations)
 * - Find reservations by jetski and date range
 * - Find reservations by customer
 * - Modelo-based availability checking (v0.3.0)
 * - Expiration and allocation queries (v0.3.0)
 *
 * Business Rules:
 * - RN06: Cannot reserve jetski in maintenance
 * - Schedule conflict detection: prevent overlapping bookings
 * - Modelo-based booking with optional deposit (v0.3.0)
 * - Controlled overbooking for reservations without deposit (v0.3.0)
 *
 * @author Jetski Team
 * @since 0.2.0
 * @version 0.3.0 - Added modelo-based queries
 */
@Repository
public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    /**
     * Find all active reservations for current tenant.
     * RLS policy automatically filters by tenant_id.
     * Active = not cancelled or finalized
     *
     * @return List of active Reserva records
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.ativo = true
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
        ORDER BY r.dataInicio ASC
    """)
    List<Reserva> findAllActive();

    /**
     * Find reservations by status.
     *
     * @param status Reservation status
     * @return List of reservations with specified status
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.status = :status
          AND r.ativo = true
        ORDER BY r.dataInicio ASC
    """)
    List<Reserva> findByStatus(@Param("status") ReservaStatus status);

    /**
     * Find reservations by jetski ID.
     * Used to list all reservations for a specific jetski.
     *
     * @param jetskiId Jetski UUID
     * @return List of reservations for the jetski
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.jetskiId = :jetskiId
          AND r.ativo = true
        ORDER BY r.dataInicio DESC
    """)
    List<Reserva> findByJetskiId(@Param("jetskiId") UUID jetskiId);

    /**
     * Find reservations by customer ID.
     * Used to list customer's reservation history.
     *
     * @param clienteId Customer UUID
     * @return List of customer's reservations
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.clienteId = :clienteId
          AND r.ativo = true
        ORDER BY r.dataInicio DESC
    """)
    List<Reserva> findByClienteId(@Param("clienteId") UUID clienteId);

    /**
     * Check for schedule conflicts with existing reservations.
     * Critical business rule: Prevent double-booking of jetski.
     *
     * A conflict exists if:
     * - Same jetski
     * - Status is PENDENTE or CONFIRMADA (not CANCELADA or FINALIZADA)
     * - Date ranges overlap:
     *   - New start < existing end AND new end > existing start
     *
     * @param jetskiId Jetski UUID
     * @param dataInicio Proposed start date/time
     * @param dataFimPrevista Proposed end date/time
     * @return List of conflicting reservations (should be empty if no conflict)
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.jetskiId = :jetskiId
          AND r.ativo = true
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
          AND r.dataInicio < :dataFimPrevista
          AND r.dataFimPrevista > :dataInicio
        ORDER BY r.dataInicio ASC
    """)
    List<Reserva> findConflictingReservations(
        @Param("jetskiId") UUID jetskiId,
        @Param("dataInicio") LocalDateTime dataInicio,
        @Param("dataFimPrevista") LocalDateTime dataFimPrevista
    );

    /**
     * Check for schedule conflicts excluding a specific reservation.
     * Used when updating existing reservation to avoid self-conflict.
     *
     * @param jetskiId Jetski UUID
     * @param dataInicio Proposed start date/time
     * @param dataFimPrevista Proposed end date/time
     * @param excludeReservaId Reservation ID to exclude from conflict check
     * @return List of conflicting reservations
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.jetskiId = :jetskiId
          AND r.id != :excludeReservaId
          AND r.ativo = true
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
          AND r.dataInicio < :dataFimPrevista
          AND r.dataFimPrevista > :dataInicio
        ORDER BY r.dataInicio ASC
    """)
    List<Reserva> findConflictingReservationsExcluding(
        @Param("jetskiId") UUID jetskiId,
        @Param("dataInicio") LocalDateTime dataInicio,
        @Param("dataFimPrevista") LocalDateTime dataFimPrevista,
        @Param("excludeReservaId") UUID excludeReservaId
    );

    /**
     * Find reservations for a jetski within a date range.
     * Used for calendar view and availability planning.
     *
     * @param jetskiId Jetski UUID
     * @param dataInicio Start of period
     * @param dataFim End of period
     * @return List of reservations in period
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.jetskiId = :jetskiId
          AND r.ativo = true
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
          AND r.dataInicio < :dataFim
          AND r.dataFimPrevista > :dataInicio
        ORDER BY r.dataInicio ASC
    """)
    List<Reserva> findByJetskiAndPeriod(
        @Param("jetskiId") UUID jetskiId,
        @Param("dataInicio") LocalDateTime dataInicio,
        @Param("dataFim") LocalDateTime dataFim
    );

    /**
     * Find pending reservations (awaiting confirmation).
     * Used for dashboard and notifications.
     *
     * @return List of pending reservations
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.status = 'PENDENTE'
          AND r.ativo = true
        ORDER BY r.createdAt DESC
    """)
    List<Reserva> findPendingReservations();

    /**
     * Find confirmed reservations for today.
     * Used for daily operations dashboard.
     *
     * @param startOfDay Start of current day
     * @param endOfDay End of current day
     * @return List of confirmed reservations for today
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.status = 'CONFIRMADA'
          AND r.ativo = true
          AND r.dataInicio >= :startOfDay
          AND r.dataInicio < :endOfDay
        ORDER BY r.dataInicio ASC
    """)
    List<Reserva> findTodayConfirmedReservations(
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("endOfDay") LocalDateTime endOfDay
    );

    /**
     * Count active reservations for current tenant.
     * Used for metrics and dashboard.
     *
     * @return Count of active reservations
     */
    @Query("""
        SELECT COUNT(r) FROM Reserva r
        WHERE r.ativo = true
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
    """)
    long countActive();

    /**
     * Count reservations by status.
     * Used for dashboard metrics.
     *
     * @param status Reservation status
     * @return Count of reservations with specified status
     */
    @Query("""
        SELECT COUNT(r) FROM Reserva r
        WHERE r.status = :status
          AND r.ativo = true
    """)
    long countByStatus(@Param("status") ReservaStatus status);

    // =====================================================
    // Modelo-based queries (v0.3.0)
    // =====================================================

    /**
     * Count ALL active reservations (PENDENTE or CONFIRMADA) for a modelo in a time period.
     * This includes BOTH guaranteed (with deposit) and non-guaranteed reservations.
     * Used to check overbooking limits.
     *
     * @param modeloId Modelo UUID
     * @param dataInicio Period start
     * @param dataFimPrevista Period end
     * @return Total count of active reservations for modelo in period
     */
    @Query("""
        SELECT COUNT(r) FROM Reserva r
        WHERE r.modeloId = :modeloId
          AND r.ativo = true
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
          AND r.dataInicio < :dataFimPrevista
          AND r.dataFimPrevista > :dataInicio
    """)
    long countActiveReservasForModelo(
        @Param("modeloId") UUID modeloId,
        @Param("dataInicio") LocalDateTime dataInicio,
        @Param("dataFimPrevista") LocalDateTime dataFimPrevista
    );

    /**
     * Count GUARANTEED reservations (with deposit, ALTA priority) for a modelo in a time period.
     * Guaranteed reservations block capacity - cannot exceed physical jetski count.
     * Used to ensure physical availability before accepting new guaranteed reservations.
     *
     * @param modeloId Modelo UUID
     * @param dataInicio Period start
     * @param dataFimPrevista Period end
     * @return Count of guaranteed reservations (ALTA priority with deposit)
     */
    @Query("""
        SELECT COUNT(r) FROM Reserva r
        WHERE r.modeloId = :modeloId
          AND r.ativo = true
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
          AND r.sinalPago = true
          AND r.prioridade = 'ALTA'
          AND r.dataInicio < :dataFimPrevista
          AND r.dataFimPrevista > :dataInicio
    """)
    long countReservasGarantidasForModelo(
        @Param("modeloId") UUID modeloId,
        @Param("dataInicio") LocalDateTime dataInicio,
        @Param("dataFimPrevista") LocalDateTime dataFimPrevista
    );

    /**
     * Find reservations by modelo ID within a date range.
     * Used for calendar view and availability planning.
     *
     * @param modeloId Modelo UUID
     * @param dataInicio Start of period
     * @param dataFim End of period
     * @return List of reservations in period for this modelo
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.modeloId = :modeloId
          AND r.ativo = true
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
          AND r.dataInicio < :dataFim
          AND r.dataFimPrevista > :dataInicio
        ORDER BY r.prioridade DESC, r.dataInicio ASC
    """)
    List<Reserva> findByModeloAndPeriod(
        @Param("modeloId") UUID modeloId,
        @Param("dataInicio") LocalDateTime dataInicio,
        @Param("dataFim") LocalDateTime dataFim
    );

    /**
     * Find reservations that should be automatically expired.
     * Criteria:
     * - expiraEm is in the past (no-show, grace period passed)
     * - NO deposit paid (BAIXA priority)
     * - Status is still PENDENTE or CONFIRMADA
     * - Is active
     *
     * Used by scheduled job to auto-expire no-show reservations.
     *
     * @param now Current timestamp
     * @return List of reservations to expire
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.expiraEm IS NOT NULL
          AND r.expiraEm <= :now
          AND r.sinalPago = false
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
          AND r.ativo = true
        ORDER BY r.expiraEm ASC
    """)
    List<Reserva> findReservasParaExpirar(@Param("now") LocalDateTime now);

    /**
     * Find confirmed reservations that need jetski allocation.
     * Criteria:
     * - Status is CONFIRMADA
     * - No jetski assigned yet (jetskiId is NULL)
     * - Is active
     * - Start time is approaching (within next N hours)
     *
     * Used by operators to allocate specific jetskis before check-in.
     * Can also be used by scheduled job to auto-allocate.
     *
     * @param dataInicioLimite Only return reservations starting before this time
     * @return List of reservations needing jetski allocation
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.jetskiId IS NULL
          AND r.status = 'CONFIRMADA'
          AND r.ativo = true
          AND r.dataInicio <= :dataInicioLimite
        ORDER BY r.prioridade DESC, r.dataInicio ASC
    """)
    List<Reserva> findReservasParaAlocar(@Param("dataInicioLimite") LocalDateTime dataInicioLimite);

    /**
     * Find reservations with deposit paid that are approaching expiration.
     * Used to notify operators for manual decision (refund policy).
     * These should NOT be auto-expired - require human intervention.
     *
     * @param notifyBefore Timestamp threshold for notification
     * @param now Current timestamp
     * @return List of guaranteed reservations approaching expiration
     */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.expiraEm IS NOT NULL
          AND r.expiraEm > :now
          AND r.expiraEm <= :notifyBefore
          AND r.sinalPago = true
          AND r.status IN ('PENDENTE', 'CONFIRMADA')
          AND r.ativo = true
        ORDER BY r.expiraEm ASC
    """)
    List<Reserva> findReservasGarantidasProximasExpiracao(
        @Param("notifyBefore") LocalDateTime notifyBefore,
        @Param("now") LocalDateTime now
    );
}
