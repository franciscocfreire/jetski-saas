package com.jetski.locacoes.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity: Habilitação do condutor de uma reserva (1:1 com reserva).
 *
 * <p>Duas vias:
 * <ul>
 *   <li><b>CHA</b>: cliente já habilitado (Arrais/Motonauta/CHA) — anexa o documento.</li>
 *   <li><b>EMA</b>: emissão da CHA-MTA-E (videoaula + anexos 5-C/5-B/1-C + GRU).</li>
 * </ul>
 * GRU manual no v1. {@code resolvida} = CHA coletada OU GRU paga.
 */
@Entity
@Table(name = "reserva_habilitacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaHabilitacao {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Via via;

    // Via CHA
    @Column(name = "cha_categoria")
    private String chaCategoria;
    @Column(name = "cha_numero")
    private String chaNumero;
    @Column(name = "cha_validade")
    private LocalDate chaValidade;

    // Via EMA
    @Column(name = "videoaula_em")
    private Instant videoaulaEm;
    @Column(name = "anexo_saude", nullable = false)
    @Builder.Default
    private Boolean anexoSaude = false;
    @Column(name = "anexo_regras", nullable = false)
    @Builder.Default
    private Boolean anexoRegras = false;
    @Column(name = "anexo_residencia", nullable = false)
    @Builder.Default
    private Boolean anexoResidencia = false;

    // Instrutor (EAMA) que assina o Atestado de Demonstração (Anexo 5-B-1)
    @Column(name = "instrutor_id")
    private UUID instrutorId;

    // Autodeclaração de saúde (Anexo 5-C)
    @Column(name = "usa_lentes", nullable = false)
    @Builder.Default
    private Boolean usaLentes = false;
    @Column(name = "usa_aparelho", nullable = false)
    @Builder.Default
    private Boolean usaAparelho = false;

    // GRU (taxa da Marinha) — manual no v1
    @Column(name = "gru_numero")
    private String gruNumero;
    @Column(name = "gru_valor", precision = 10, scale = 2)
    private BigDecimal gruValor;
    @Column(name = "gru_pago", nullable = false)
    @Builder.Default
    private Boolean gruPago = false;
    @Column(name = "gru_pago_em")
    private Instant gruPagoEm;

    // GRU gerada automaticamente (robô HTTP Marinha/PagTesouro) — dados do PIX
    @Column(name = "gru_pix_copia_e_cola", columnDefinition = "text")
    private String gruPixCopiaECola;
    @Column(name = "gru_pix_expiracao")
    private Instant gruPixExpiracao;
    @Column(name = "gru_id_marinha")
    private String gruIdMarinha;
    @Column(name = "gru_gerada_em")
    private Instant gruGeradaEm;
    @Column(name = "gru_pdf_s3_key", columnDefinition = "text")
    private String gruPdfS3Key;

    @Column(nullable = false)
    @Builder.Default
    private Boolean resolvida = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum Via {
        CHA,
        EMA
    }
}
