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
 * Identidade GLOBAL do cliente do portal (V032): dados que seguem a pessoa
 * (CPF, RG, nascimento…) e hidratam o Cliente tenant-scoped de cada loja.
 * Endereço/telefones/anexos permanecem por loja.
 */
@Entity
@Table(name = "customer_profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfile {

    @Id
    @GeneratedValue
    private UUID id;



    /**
     * A PESSOA (identidade única, F0): o perfil civil deixa de ser raiz
     * própria e passa a apontar para usuario. Preenchido lazy (obter) quando
     * o sub já tem pessoa provisionada; re-key completo é a F3.
     */
    @Column(name = "usuario_id")
    private java.util.UUID usuarioId;

    @Column(name = "nome", length = 120)
    private String nome;

    /** Define-only pelo portal; único entre contas (índice parcial). */
    @Column(name = "cpf", length = 20)
    private String cpf;

    @Column(name = "rg", length = 30)
    private String rg;

    @Column(name = "orgao_emissor", length = 20)
    private String orgaoEmissor;

    @Column(name = "nacionalidade", length = 60)
    private String nacionalidade;

    @Column(name = "naturalidade", length = 80)
    private String naturalidade;

    @Column(name = "estrangeiro", nullable = false)
    @Builder.Default
    private Boolean estrangeiro = false;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
