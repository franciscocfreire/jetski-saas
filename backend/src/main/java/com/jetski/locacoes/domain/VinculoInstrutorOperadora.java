package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: instrutor da OPERADORA submetido à aprovação da EAMA emissora da
 * parceria (V070, EMISSAO_DELEGADA_SPEC §8.N). Pela NORMAM-212 o instrutor é
 * cadastrado na EAMA, que responde por ele: sem aprovação, o instrutor da
 * operadora não assina emissão delegada.
 *
 * Sem tenant_id próprio: visibilidade herda do vínculo (RLS via subquery,
 * visível aos dois lados).
 *
 * @author Jetski Team
 */
@Entity
@Table(name = "vinculo_instrutor_operadora")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VinculoInstrutorOperadora {

    public enum Status { PENDENTE, APROVADO, REJEITADO, REMOVIDO }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "vinculo_id", nullable = false)
    private UUID vinculoId;

    @Column(name = "instrutor_id", nullable = false)
    private UUID instrutorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private Status status = Status.PENDENTE;

    @Column(name = "solicitado_em", nullable = false)
    private Instant solicitadoEm;

    @Column(name = "solicitado_por")
    private UUID solicitadoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    @Column(name = "decidido_por")
    private UUID decididoPor;

    @Column(length = 500)
    private String motivo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (solicitadoEm == null) solicitadoEm = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
