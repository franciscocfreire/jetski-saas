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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Habilitação temporária (CHA-MTA-E) como dado GLOBAL do cliente (V043).
 *
 * <p>A habilitação pertence à pessoa (emitida pela Marinha; GRU paga pelo
 * cliente à União) — a loja é só o canal de emissão. Este registro nasce na
 * mesma transação da emissão ({@code CustomerHabilitacaoSyncService}) e
 * sobrevive a reset/exclusão da loja de origem, preservando o direito de
 * reuso do cliente em outras lojas.
 *
 * <p>Sem tenant e sem RLS (padrão {@link CustomerProfile}): acesso apenas
 * pelos endpoints self do portal e pelo sync interno. Chave humana = CPF
 * (só dígitos) — cliente de balcão pode não ter conta no portal;
 * provider/sub são preenchidos quando houver vínculo. Origem (tenant/
 * reserva/loja) é informativa, sem FK — a loja pode ser expurgada.
 */
@Entity
@Table(name = "customer_habilitacao")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerHabilitacao {

    @Id
    @GeneratedValue
    private UUID id;

    /** CPF normalizado (só dígitos). */
    @Column(name = "cpf", nullable = false, length = 14)
    private String cpf;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    /** Nº da GRU — identifica a habilitação (único). */
    @Column(name = "gru_numero", nullable = false, length = 60)
    private String gruNumero;

    @Column(name = "categoria", nullable = false, length = 40)
    @Builder.Default
    private String categoria = "CHA-MTA-E";

    @Column(name = "emitida_em", nullable = false)
    private Instant emitidaEm;

    @Column(name = "valida_ate", nullable = false)
    private LocalDate validaAte;

    /** Devolutiva da Marinha anexada — só confirmada é reusável. */
    @Column(name = "marinha_confirmada_em")
    private Instant marinhaConfirmadaEm;

    @Column(name = "loja_origem_nome", length = 200)
    private String lojaOrigemNome;

    @Column(name = "tenant_origem")
    private UUID tenantOrigem;

    @Column(name = "reserva_origem")
    private UUID reservaOrigem;

    /** Cópia da devolutiva no prefixo da PLATAFORMA (sobrevive ao expurgo da loja). */
    @Column(name = "pdf_s3_key")
    private String pdfS3Key;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
