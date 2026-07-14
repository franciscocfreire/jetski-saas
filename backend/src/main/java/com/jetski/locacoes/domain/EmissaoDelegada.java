package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: espelho, no tenant EMISSOR, de um documento emitido em nome dele
 * pela operadora parceira (EMISSAO_DELEGADA_SPEC §3.5, V048).
 *
 * É a trilha legal da EAMA: preservada no reset da operadora (FKs SET NULL /
 * sem FK), com hash e s3_key para reenvio à Capitania sem re-emissão.
 *
 * @author Jetski Team
 */
@Entity
@Table(name = "emissao_delegada")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmissaoDelegada {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Tenant EMISSOR (a EAMA em nome de quem o documento saiu). */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vinculo_id")
    private UUID vinculoId;

    @Column(name = "documento_id")
    private UUID documentoId;

    @Column(name = "documento_hash", length = 64)
    private String documentoHash;

    /** PDF da via da Marinha no storage (para reenvio/baixa). */
    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "operadora_tenant_id", nullable = false)
    private UUID operadoraTenantId;

    @Column(name = "operadora_nome", length = 200)
    private String operadoraNome;

    @Column(name = "condutor_nome", length = 200)
    private String condutorNome;

    @Column(name = "condutor_cpf", length = 20)
    private String condutorCpf;

    @Column(name = "instrutor_id")
    private UUID instrutorId;

    @Column(name = "instrutor_nome", length = 200)
    private String instrutorNome;

    @Column(name = "gru_numero", length = 40)
    private String gruNumero;

    @Column(name = "emitido_em", nullable = false)
    private Instant emitidoEm;

    @Column(name = "reenviado_em")
    private Instant reenviadoEm;

    @Column(name = "reenviado_para", length = 255)
    private String reenviadoPara;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
