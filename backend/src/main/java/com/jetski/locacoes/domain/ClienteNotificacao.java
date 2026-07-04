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
 * Notificação in-app do cliente do portal (V033): eventos do sistema
 * (pagamento, GRU, emissão, expiração) exibidos no sininho. Tenant-scoped;
 * o cliente agrega pelos vínculos, como reservas/locações.
 */
@Entity
@Table(name = "cliente_notificacao")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteNotificacao {

    /** Tipos usados hoje (string livre no banco p/ evoluir sem migration). */
    public static final String PAGAMENTO_CONFIRMADO = "PAGAMENTO_CONFIRMADO";
    public static final String PAGAMENTO_RECUSADO = "PAGAMENTO_RECUSADO";
    public static final String GRU_PAGA = "GRU_PAGA";
    public static final String GRU_EMITIDA = "GRU_EMITIDA";
    public static final String GRU_PENDENTE_DADOS = "GRU_PENDENTE_DADOS";
    public static final String DOCUMENTOS_EMITIDOS = "DOCUMENTOS_EMITIDOS";
    public static final String RESERVA_EXPIRADA = "RESERVA_EXPIRADA";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @Column(name = "mensagem", columnDefinition = "text")
    private String mensagem;

    /** Caminho relativo no portal (ex.: /conta/reservas/{id}). */
    @Column(name = "link", length = 300)
    private String link;

    @Column(name = "lida", nullable = false)
    @Builder.Default
    private Boolean lida = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
