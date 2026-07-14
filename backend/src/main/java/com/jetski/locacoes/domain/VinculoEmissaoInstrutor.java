package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: instrutor da EAMA designado para atender uma parceria de emissão
 * delegada (V049). Semântica opt-in: sem linhas para o vínculo, a operadora
 * vê TODOS os instrutores ativos da EAMA; com linhas, só os designados —
 * na listagem e na emissão.
 *
 * Sem tenant_id próprio: visibilidade herda do vínculo (RLS via subquery,
 * visível aos dois lados).
 *
 * @author Jetski Team
 */
@Entity
@Table(name = "vinculo_emissao_instrutor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VinculoEmissaoInstrutor {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "vinculo_id", nullable = false)
    private UUID vinculoId;

    @Column(name = "instrutor_id", nullable = false)
    private UUID instrutorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
