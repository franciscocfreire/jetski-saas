package com.jetski.shared.audit.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity: Auditoria (Audit Log)
 *
 * <p>Records all significant business operations for audit trail and compliance.
 * Each audit entry captures who did what, when, and the state before/after the operation.
 *
 * <p><strong>Multi-tenant:</strong> All entries are isolated by tenant_id via PostgreSQL RLS.
 *
 * <p><strong>Event-Driven:</strong> Entries are created by {@code AuditEventListener}
 * that listens to domain events (CheckInEvent, CheckOutEvent, etc.).
 *
 * <p><strong>Use Cases:</strong>
 * <ul>
 *   <li>Track check-in/check-out operations</li>
 *   <li>Record jetski status changes</li>
 *   <li>Log maintenance order lifecycle</li>
 *   <li>Compliance and dispute resolution</li>
 *   <li>Security incident investigation</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.10.0
 */
@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auditoria {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    /**
     * Tenant that owns this audit record.
     * Filtered by RLS policy.
     */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /**
     * User who performed the action.
     * Extracted from SecurityContext.
     */
    @Column(name = "usuario_id")
    private UUID usuarioId;

    /**
     * Action performed (e.g., CHECK_IN, CHECK_OUT, STATUS_CHANGE).
     * Maps to domain event types.
     */
    @Column(name = "acao", nullable = false, length = 50)
    private String acao;

    /**
     * Entity type affected (e.g., LOCACAO, JETSKI, RESERVA).
     */
    @Column(name = "entidade", nullable = false, length = 50)
    private String entidade;

    /**
     * ID of the affected entity.
     */
    @Column(name = "entidade_id")
    private UUID entidadeId;

    /**
     * State before the operation (JSON snapshot).
     * null for creation events.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "dados_anteriores", columnDefinition = "JSONB")
    private Map<String, Object> dadosAnteriores;

    /**
     * State after the operation (JSON snapshot).
     * null for deletion events.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "dados_novos", columnDefinition = "JSONB")
    private Map<String, Object> dadosNovos;

    /**
     * Client IP address.
     * Extracted from request context.
     */
    @Column(name = "ip", length = 45)
    private String ip;

    /**
     * Client user agent string.
     * Useful for identifying device/app.
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /**
     * Correlation/trace ID for distributed tracing.
     * Links audit entry to request logs.
     */
    @Column(name = "trace_id", length = 100)
    private String traceId;

    /**
     * Timestamp when this audit entry was created.
     * Immutable.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    // ===================================================================
    // Factory Methods for Common Audit Actions
    // ===================================================================

    /**
     * Creates an audit entry for a check-in operation.
     */
    public static Auditoria checkIn(UUID tenantId, UUID usuarioId, UUID locacaoId,
                                     Map<String, Object> dadosNovos, String traceId) {
        return Auditoria.builder()
                .tenantId(tenantId)
                .usuarioId(usuarioId)
                .acao("CHECK_IN")
                .entidade("LOCACAO")
                .entidadeId(locacaoId)
                .dadosNovos(dadosNovos)
                .traceId(traceId)
                .build();
    }

    /**
     * Creates an audit entry for a check-out operation.
     */
    public static Auditoria checkOut(UUID tenantId, UUID usuarioId, UUID locacaoId,
                                      Map<String, Object> dadosAnteriores,
                                      Map<String, Object> dadosNovos, String traceId) {
        return Auditoria.builder()
                .tenantId(tenantId)
                .usuarioId(usuarioId)
                .acao("CHECK_OUT")
                .entidade("LOCACAO")
                .entidadeId(locacaoId)
                .dadosAnteriores(dadosAnteriores)
                .dadosNovos(dadosNovos)
                .traceId(traceId)
                .build();
    }

    /**
     * Creates a generic audit entry.
     */
    public static Auditoria of(UUID tenantId, UUID usuarioId, String acao,
                                String entidade, UUID entidadeId,
                                Map<String, Object> dadosAnteriores,
                                Map<String, Object> dadosNovos, String traceId) {
        return Auditoria.builder()
                .tenantId(tenantId)
                .usuarioId(usuarioId)
                .acao(acao)
                .entidade(entidade)
                .entidadeId(entidadeId)
                .dadosAnteriores(dadosAnteriores)
                .dadosNovos(dadosNovos)
                .traceId(traceId)
                .build();
    }
}
