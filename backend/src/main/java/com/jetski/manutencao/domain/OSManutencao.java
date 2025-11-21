package com.jetski.manutencao.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: OS Manutenção (Maintenance Order)
 *
 * <p>Represents a maintenance work order for a jetski unit.
 * Tracks preventive and corrective maintenance with costs, parts, and labor.
 *
 * <h3>Examples:</h3>
 * <ul>
 *   <li>Preventiva #001 - Troca de óleo e vela (50h odometer)</li>
 *   <li>Corretiva #002 - Reparo bomba d'água (falha operacional)</li>
 *   <li>Revisão #003 - Inspeção geral anual</li>
 * </ul>
 *
 * <h3>Business Rules:</h3>
 * <ul>
 *   <li><b>RN06</b>: When OS is ABERTA or EM_ANDAMENTO, jetski status → MANUTENCAO (blocked)</li>
 *   <li><b>RN06.1</b>: Jetski in MANUTENCAO status cannot be reserved</li>
 *   <li>When OS is CONCLUIDA or CANCELADA, jetski status → DISPONIVEL (if no other active OS)</li>
 *   <li>Parts costs tracked in pecas_json as JSONB array</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 1.0.0
 */
@Entity
@Table(name = "os_manutencao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OSManutencao {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Jetski unit under maintenance
     */
    @Column(name = "jetski_id", nullable = false)
    private UUID jetskiId;

    /**
     * Mechanic assigned to this OS (optional)
     */
    @Column(name = "mecanico_id")
    private UUID mecanicoId;

    /**
     * Type of maintenance
     */
    @Convert(converter = OSManutencaoTipoConverter.class)
    @Column(nullable = false, length = 20)
    private OSManutencaoTipo tipo;

    /**
     * Priority level (default: MEDIA)
     */
    @Convert(converter = OSManutencaoPrioridadeConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OSManutencaoPrioridade prioridade = OSManutencaoPrioridade.MEDIA;

    /**
     * Date when OS was opened
     */
    @Column(name = "dt_abertura", nullable = false)
    @Builder.Default
    private Instant dtAbertura = Instant.now();

    /**
     * Predicted start date for maintenance
     */
    @Column(name = "dt_prevista_inicio")
    private Instant dtPrevistaInicio;

    /**
     * Actual start date (when mechanic begins work)
     */
    @Column(name = "dt_inicio_real")
    private Instant dtInicioReal;

    /**
     * Predicted completion date
     */
    @Column(name = "dt_prevista_fim")
    private Instant dtPrevistaFim;

    /**
     * Actual completion date
     */
    @Column(name = "dt_conclusao")
    private Instant dtConclusao;

    /**
     * Problem description (mandatory)
     */
    @Column(name = "descricao_problema", nullable = false, columnDefinition = "TEXT")
    private String descricaoProblema;

    /**
     * Diagnosis (filled during work)
     */
    @Column(name = "diagnostico", columnDefinition = "TEXT")
    private String diagnostico;

    /**
     * Solution/work performed
     */
    @Column(name = "solucao", columnDefinition = "TEXT")
    private String solucao;

    /**
     * Parts used (JSONB)
     * Format: [{"nome": "Vela", "qtd": 2, "valor": 45.00}, ...]
     */
    @Type(JsonBinaryType.class)
    @Column(name = "pecas_json", columnDefinition = "jsonb")
    private String pecasJson;

    /**
     * Total cost of parts
     */
    @Column(name = "valor_pecas", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorPecas = BigDecimal.ZERO;

    /**
     * Labor cost
     */
    @Column(name = "valor_mao_obra", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorMaoObra = BigDecimal.ZERO;

    /**
     * Total cost (parts + labor)
     */
    @Column(name = "valor_total", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    /**
     * Odometer reading when OS was opened
     */
    @Column(name = "horimetro_abertura", precision = 10, scale = 2)
    private BigDecimal horimetroAbertura;

    /**
     * Odometer reading when OS was completed
     */
    @Column(name = "horimetro_conclusao", precision = 10, scale = 2)
    private BigDecimal horimetroConclusao;

    /**
     * Status workflow
     */
    @Convert(converter = OSManutencaoStatusConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OSManutencaoStatus status = OSManutencaoStatus.ABERTA;

    /**
     * Additional notes
     */
    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Check if OS is active (blocks jetski availability).
     *
     * @return true if status is ABERTA, EM_ANDAMENTO, or AGUARDANDO_PECAS
     */
    public boolean isActive() {
        return status == OSManutencaoStatus.ABERTA
            || status == OSManutencaoStatus.EM_ANDAMENTO
            || status == OSManutencaoStatus.AGUARDANDO_PECAS;
    }

    /**
     * Check if OS is finished (completed or cancelled).
     *
     * @return true if status is CONCLUIDA or CANCELADA
     */
    public boolean isFinished() {
        return status == OSManutencaoStatus.CONCLUIDA
            || status == OSManutencaoStatus.CANCELADA;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
