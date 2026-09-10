package com.jetski.locacoes.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity: Cliente (Customer)
 *
 * Represents a rental customer who can make reservations and rent jetskis.
 * Customers must accept liability terms before first rental.
 *
 * Examples:
 * - Maria Santos (CPF 123.456.789-00) - phone: (11) 98765-4321, email: maria@example.com
 * - Empresa ABC Ltda (CNPJ 12.345.678/0001-90) - corporate customer
 *
 * Business Rules:
 * - RF03.4: Customer must sign liability term (termoAceite) before check-in
 * - Contact information (email, telefone, whatsapp) optional but recommended for notifications
 * - LGPD: Customer data retention governed by tenant policy
 *
 * @author Jetski Team
 * @since 0.2.0
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    /**
     * Documento de identificação, sempre NORMALIZADO (dígitos para CPF/CNPJ,
     * alfanumérico maiúsculo para passaporte) — ver {@link Documentos}.
     * A pontuação é aplicada na exibição, nunca aqui.
     */
    @Column
    private String documento;

    /**
     * O que {@link #documento} é. Decide a busca, o rótulo no ofício à Capitania
     * e o nome do PDF anexo. {@code null} = documento malformado, ainda não
     * tipificado (a V066 preferiu deixar em branco a chutar).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "documento_tipo", length = 12)
    private DocumentoTipo documentoTipo;

    /** Identidade (RG) e órgão emissor — usados nos anexos NORMAM-212. */
    @Column(name = "rg")
    private String rg;

    @Column(name = "orgao_emissor")
    private String orgaoEmissor;

    /** Nacionalidade e naturalidade — Declaração de Residência (Anexo 1-C). */
    @Column(name = "nacionalidade")
    private String nacionalidade;

    @Column(name = "naturalidade")
    private String naturalidade;

    /** Locatário estrangeiro → emite também as versões em inglês do 5-B. */
    @Column(name = "estrangeiro", nullable = false)
    @Builder.Default
    private Boolean estrangeiro = false;

    /**
     * Birth date (optional)
     * Useful for age validation (e.g., minimum age for rental) and demographics
     */
    @Column(name = "data_nascimento")
    private java.time.LocalDate dataNascimento;

    /**
     * Gender (optional)
     * Possible values: MASCULINO, FEMININO, OUTRO, NAO_INFORMADO
     * Used for demographics and statistics
     */
    @Column(name = "genero")
    private String genero;

    /**
     * Email address (optional)
     * Validated with @Email annotation in DTOs
     */
    @Column(name = "email")
    private String email;

    /**
     * Phone number in E.164 international format (optional)
     * Example: +5511987654321 (Brazil), +14155552671 (USA)
     * Validated with @Pattern in DTOs
     */
    @Column(name = "telefone")
    private String telefone;

    /**
     * WhatsApp number in E.164 international format (optional)
     * Example: +5511987654321
     * Validated with @Pattern in DTOs
     */
    @Column(name = "whatsapp")
    private String whatsapp;

    /**
     * Address information (JSONB) - flexible structure for different countries
     *
     * Expected format (Brazil example):
     * {
     *   "cep": "01310-100",
     *   "logradouro": "Av. Paulista",
     *   "numero": "1000",
     *   "complemento": "10º andar",
     *   "bairro": "Bela Vista",
     *   "cidade": "São Paulo",
     *   "estado": "SP",
     *   "pais": "Brasil"
     * }
     */
    @Type(JsonBinaryType.class)
    @Column(name = "endereco", columnDefinition = "jsonb")
    private String enderecoJson;

    /**
     * Indicates if customer has accepted liability terms
     * Business rule RF03.4: Must be true before allowing check-in
     *
     * Note: Actual term signature is stored in Locacao entity
     * This flag indicates customer has accepted general terms
     */
    @Column(name = "termo_aceite")
    @Builder.Default
    private Boolean termoAceite = false;

    /**
     * Soft delete flag - inactive customers cannot make new rentals
     * but historical rentals are preserved (LGPD compliance)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Origem do cadastro: PORTAL (self-service), BALCAO (atendimento assistido)
     * ou LEAD (captado por um operador fora do balcão, ex.: na praia).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    @Builder.Default
    private Origem origem = Origem.PORTAL;

    /** Notas livres do staff sobre o cliente (visíveis só no backoffice). */
    @Column(name = "observacoes")
    private String observacoes;

    /**
     * Usuário do staff que registrou o lead/pré-conta — base para métrica de
     * conversão e comissão de captação (futuras). Null para cadastros do portal.
     */
    /**
     * A PESSOA (identidade única, F0): FK para usuario, preenchida quando o
     * cliente tem conta na plataforma (claim ativado ou reserva logada).
     * NULL = ficha de balcão/lead sem conta. Vira o vínculo canônico na F4;
     * até lá convive com cliente_identity_provider (dupla escrita).
     */
    @Column(name = "usuario_id")
    private java.util.UUID usuarioId;

    @Column(name = "capturado_por")
    private UUID capturadoPor;

    /**
     * Estado da conta do cliente (ciclo da pré-conta → ativa).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_conta", nullable = false, length = 20)
    @Builder.Default
    private StatusConta statusConta = StatusConta.SEM_LOGIN;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        normalizarDocumento();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        normalizarDocumento();
    }

    /**
     * Invariante de armazenamento: o documento é gravado normalizado e com tipo.
     *
     * <p>Fica no ciclo de vida da entidade, e não em um service, porque são
     * quatro caminhos que escrevem cliente (balcão, portal, perfil, merge de
     * CPF) e basta um esquecer para voltar a existir a mesma pessoa com duas
     * grafias — o defeito que a V066 corrigiu.
     */
    void normalizarDocumento() {
        if (documentoTipo == null) {
            documentoTipo = Documentos.inferirTipo(documento);
        }
        documento = Documentos.normalizar(documentoTipo, documento);
        // Só liga: estrangeiro residente tem CPF e continua estrangeiro.
        if (documentoTipo != null && documentoTipo.implicaEstrangeiro()) {
            estrangeiro = true;
        }
    }

    /**
     * Check if customer can rent (active and terms accepted)
     * Business rule RF03.4
     */
    public boolean canRent() {
        return ativo && Boolean.TRUE.equals(termoAceite);
    }

    /** Origem do cadastro do cliente. */
    public enum Origem {
        PORTAL,
        BALCAO,
        LEAD
    }

    /**
     * Estado da conta do cliente.
     * PRE_CONTA: criado no balcão sem login.
     * CONVIDADA: claim enviado.
     * ATIVA: cliente ativou a conta (login Keycloak vinculado).
     * SEM_LOGIN: cliente apenas como dado (sem intenção de login).
     */
    public enum StatusConta {
        PRE_CONTA,
        CONVIDADA,
        ATIVA,
        SEM_LOGIN
    }
}
