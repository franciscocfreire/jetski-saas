package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: vínculo de emissão delegada (EMISSAO_DELEGADA_SPEC §3.3, V048).
 *
 * Parceria operadora × EAMA emissora: a operadora emite CHA-MTE em nome da
 * EAMA. SEM tenant_id único — a RLS é DUPLA (cada lado enxerga a linha em que
 * participa). MVP: no máximo 1 vínculo não-terminal por operadora.
 *
 * Ciclo: CONVIDADO → ATIVO ⇄ BLOQUEADO (kill switch da EAMA) → REVOGADO.
 *
 * @author Jetski Team
 */
@Entity
@Table(name = "vinculo_emissao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VinculoEmissao {

    public enum Status { CONVIDADO, ATIVO, BLOQUEADO, REVOGADO }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_operador_id", nullable = false)
    private UUID tenantOperadorId;

    @Column(name = "tenant_emissor_id", nullable = false)
    private UUID tenantEmissorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private Status status = Status.CONVIDADO;

    /** Lado que convidou — o aceite tem que vir do OUTRO lado. */
    @Column(name = "convidado_por_tenant", nullable = false)
    private UUID convidadoPorTenant;

    @Column(name = "convidado_por")
    private UUID convidadoPor;

    @Column(name = "convidado_em", nullable = false)
    private Instant convidadoEm;

    @Column(name = "aceito_por")
    private UUID aceitoPor;

    @Column(name = "aceito_em")
    private Instant aceitoEm;

    @Column(name = "termo_aceite_em")
    private Instant termoAceiteEm;

    /** Snapshot do termo de responsabilidade aceito (§5.1). */
    @Column(name = "termo_texto")
    private String termoTexto;

    @Column(name = "bloqueado_em")
    private Instant bloqueadoEm;

    @Column(name = "revogado_por")
    private UUID revogadoPor;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (convidadoEm == null) {
            convidadoEm = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
