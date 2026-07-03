package com.jetski.locacoes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Avaliação da locação pelo cliente (P4 do portal): nota 1-5 + comentário,
 * uma por locação FINALIZADA. A média por modelo alimenta a vitrine pública.
 */
@Entity
@Table(name = "avaliacao")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Avaliacao {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "locacao_id", nullable = false)
    private UUID locacaoId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "modelo_id", nullable = false)
    private UUID modeloId;

    /** Nota de 1 a 5 (CHECK no banco). */
    @Column(name = "nota", nullable = false)
    private Integer nota;

    @Column(name = "comentario", columnDefinition = "text")
    private String comentario;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
