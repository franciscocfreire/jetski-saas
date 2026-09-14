package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: instrutor da EAMA designado para atender uma parceria de emissão
 * delegada (V049). Designação obrigatória (§8.L, revista em 13/set/2026): a
 * operadora só vê e só emite com os instrutores da EAMA designados; sem linhas
 * para o vínculo, nenhum instrutor da EAMA — só os próprios aprovados (V070).
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
