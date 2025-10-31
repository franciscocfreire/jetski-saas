package com.jetski.comissoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity: PoliticaComissao (Commission Policy)
 *
 * <p>Define regras hierárquicas de comissão baseadas em diferentes critérios.</p>
 *
 * <p><strong>Hierarquia (RN04 - primeiro match ganha):</strong></p>
 * <ol>
 *   <li>CAMPANHA - Promoção ativa com período de vigência</li>
 *   <li>MODELO - Específico por modelo de jet ski</li>
 *   <li>DURACAO - Por faixa de duração da locação</li>
 *   <li>VENDEDOR - Padrão do vendedor</li>
 * </ol>
 *
 * <p><strong>Tipos de comissão suportados:</strong></p>
 * <ul>
 *   <li>PERCENTUAL: percentual_comissao aplicado sobre valor comissionável</li>
 *   <li>VALOR_FIXO: valor_fixo por locação</li>
 *   <li>ESCALONADO: percentual_comissao até duracao_min_minutos, percentual_extra acima</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.7.0
 */
@Entity
@Table(name = "politica_comissao",
        indexes = {
                @Index(name = "idx_politica_tenant_nivel", columnList = "tenant_id, nivel"),
                @Index(name = "idx_politica_tenant_vendedor", columnList = "tenant_id, vendedor_id"),
                @Index(name = "idx_politica_tenant_modelo", columnList = "tenant_id, modelo_id"),
                @Index(name = "idx_politica_tenant_campanha", columnList = "tenant_id, codigo_campanha")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliticaComissao {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Nível hierárquico da política (determina prioridade)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false, length = 20)
    private NivelPolitica nivel;

    /**
     * Tipo de cálculo de comissão
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoComissao tipo;

    /**
     * Nome descritivo da política
     */
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    /**
     * Descrição detalhada
     */
    @Column(name = "descricao", length = 500)
    private String descricao;

    // ========== Filtros (critérios de aplicação) ==========

    /**
     * Vendedor (para nível VENDEDOR)
     */
    @Column(name = "vendedor_id")
    private UUID vendedorId;

    /**
     * Modelo de jet ski (para nível MODELO)
     */
    @Column(name = "modelo_id")
    private UUID modeloId;

    /**
     * Código da campanha (para nível CAMPANHA)
     */
    @Column(name = "codigo_campanha", length = 50)
    private String codigoCampanha;

    /**
     * Duração mínima em minutos (para nível DURACAO ou ESCALONADO)
     * Exemplo: 120 (aplica para locações >= 120min)
     */
    @Column(name = "duracao_min_minutos")
    private Integer duracaoMinMinutos;

    /**
     * Duração máxima em minutos (para nível DURACAO)
     * Exemplo: 240 (aplica para locações <= 240min)
     * Null = sem limite superior
     */
    @Column(name = "duracao_max_minutos")
    private Integer duracaoMaxMinutos;

    // ========== Valores de comissão ==========

    /**
     * Percentual de comissão (ex: 10.50 para 10,5%)
     * Usado para PERCENTUAL e ESCALONADO
     */
    @Column(name = "percentual_comissao", precision = 5, scale = 2)
    private BigDecimal percentualComissao;

    /**
     * Valor fixo de comissão (ex: 50.00 para R$ 50,00)
     * Usado para VALOR_FIXO
     */
    @Column(name = "valor_fixo", precision = 10, scale = 2)
    private BigDecimal valorFixo;

    /**
     * Percentual adicional para tipo ESCALONADO acima da duração mínima
     * Exemplo: percentual_comissao=10% até 120min, percentual_extra=12% acima 120min
     */
    @Column(name = "percentual_extra", precision = 5, scale = 2)
    private BigDecimal percentualExtra;

    // ========== Vigência (para CAMPANHA) ==========

    /**
     * Data/hora de início de vigência (para campanhas)
     */
    @Column(name = "vigencia_inicio")
    private Instant vigenciaInicio;

    /**
     * Data/hora de fim de vigência (para campanhas)
     */
    @Column(name = "vigencia_fim")
    private Instant vigenciaFim;

    // ========== Controle ==========

    /**
     * Política ativa/inativa
     */
    @Column(name = "ativa", nullable = false)
    @Builder.Default
    private Boolean ativa = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (ativa == null) {
            ativa = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Verifica se a política está vigente (para campanhas)
     */
    public boolean isVigente() {
        if (nivel != NivelPolitica.CAMPANHA) {
            return true; // Não-campanhas sempre vigentes (se ativas)
        }

        Instant now = Instant.now();
        boolean inicioOk = vigenciaInicio == null || !now.isBefore(vigenciaInicio);
        boolean fimOk = vigenciaFim == null || !now.isAfter(vigenciaFim);

        return inicioOk && fimOk;
    }

    /**
     * Verifica se aplica para uma determinada duração (em minutos)
     */
    public boolean aplicaParaDuracao(int duracaoMinutos) {
        if (duracaoMinMinutos != null && duracaoMinutos < duracaoMinMinutos) {
            return false;
        }
        if (duracaoMaxMinutos != null && duracaoMinutos > duracaoMaxMinutos) {
            return false;
        }
        return true;
    }
}
