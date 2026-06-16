package com.jetski.audit.internal;

import com.jetski.audit.domain.Auditoria;
import com.jetski.audit.domain.AuditoriaRepository;
import com.jetski.locacoes.event.ClaimEnviadoEvent;
import com.jetski.locacoes.event.ContaAtivadaEvent;
import com.jetski.locacoes.event.PreContaCriadaEvent;
import com.jetski.reservas.domain.event.DocumentosEmitidosEvent;
import com.jetski.reservas.domain.event.PagamentoConfirmadoEvent;
import com.jetski.reservas.domain.event.PagamentoRecusadoEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * F1.F — testa os listeners de auditoria dos eventos de balcão/pagamento,
 * sem Spring (repositório mockado, captura da Auditoria salva).
 */
@DisplayName("AuditEventListener - eventos de balcão/pagamento (F1.F)")
class AuditEventListenerTest {

    private final AuditoriaRepository repo = mock(AuditoriaRepository.class);
    private final AuditEventListener listener = new AuditEventListener(repo);

    private Auditoria capturarSalva() {
        ArgumentCaptor<Auditoria> cap = ArgumentCaptor.forClass(Auditoria.class);
        verify(repo).save(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("PagamentoConfirmadoEvent → PAGAMENTO_CONFIRMADO/RESERVA")
    void pagamentoConfirmado() {
        UUID tenant = UUID.randomUUID(), reserva = UUID.randomUUID(), user = UUID.randomUUID();
        listener.onPagamentoConfirmado(
            PagamentoConfirmadoEvent.of(tenant, reserva, "TOTAL", new BigDecimal("900.00"), user));
        Auditoria a = capturarSalva();
        assertThat(a.getAcao()).isEqualTo("PAGAMENTO_CONFIRMADO");
        assertThat(a.getEntidade()).isEqualTo("RESERVA");
        assertThat(a.getEntidadeId()).isEqualTo(reserva);
        assertThat(a.getUsuarioId()).isEqualTo(user);
        assertThat(a.getTenantId()).isEqualTo(tenant);
    }

    @Test
    @DisplayName("PagamentoRecusadoEvent → PAGAMENTO_RECUSADO/RESERVA")
    void pagamentoRecusado() {
        UUID tenant = UUID.randomUUID(), reserva = UUID.randomUUID(), user = UUID.randomUUID();
        listener.onPagamentoRecusado(
            PagamentoRecusadoEvent.of(tenant, reserva, "Comprovante ilegível", user));
        Auditoria a = capturarSalva();
        assertThat(a.getAcao()).isEqualTo("PAGAMENTO_RECUSADO");
        assertThat(a.getEntidade()).isEqualTo("RESERVA");
        assertThat(a.getEntidadeId()).isEqualTo(reserva);
    }

    @Test
    @DisplayName("DocumentosEmitidosEvent → DOCUMENTOS_EMITIDOS/RESERVA")
    void documentosEmitidos() {
        UUID tenant = UUID.randomUUID(), reserva = UUID.randomUUID(), doc = UUID.randomUUID(), user = UUID.randomUUID();
        listener.onDocumentosEmitidos(
            DocumentosEmitidosEvent.of(tenant, reserva, doc, "marinha,cliente", user));
        Auditoria a = capturarSalva();
        assertThat(a.getAcao()).isEqualTo("DOCUMENTOS_EMITIDOS");
        assertThat(a.getEntidade()).isEqualTo("RESERVA");
        assertThat(a.getEntidadeId()).isEqualTo(reserva);
    }

    @Test
    @DisplayName("PreContaCriadaEvent → PRE_CONTA_CRIADA/CLIENTE")
    void preContaCriada() {
        UUID tenant = UUID.randomUUID(), cliente = UUID.randomUUID(), user = UUID.randomUUID();
        listener.onPreContaCriada(PreContaCriadaEvent.of(tenant, cliente, "BALCAO", user));
        Auditoria a = capturarSalva();
        assertThat(a.getAcao()).isEqualTo("PRE_CONTA_CRIADA");
        assertThat(a.getEntidade()).isEqualTo("CLIENTE");
        assertThat(a.getEntidadeId()).isEqualTo(cliente);
    }

    @Test
    @DisplayName("ClaimEnviadoEvent → CLAIM_ENVIADO/CLIENTE")
    void claimEnviado() {
        UUID tenant = UUID.randomUUID(), cliente = UUID.randomUUID(), user = UUID.randomUUID();
        listener.onClaimEnviado(ClaimEnviadoEvent.of(tenant, cliente, "email", user));
        Auditoria a = capturarSalva();
        assertThat(a.getAcao()).isEqualTo("CLAIM_ENVIADO");
        assertThat(a.getEntidade()).isEqualTo("CLIENTE");
    }

    @Test
    @DisplayName("ContaAtivadaEvent → CONTA_ATIVADA/CLIENTE")
    void contaAtivada() {
        UUID tenant = UUID.randomUUID(), cliente = UUID.randomUUID();
        listener.onContaAtivada(ContaAtivadaEvent.of(tenant, cliente, "sub-123"));
        Auditoria a = capturarSalva();
        assertThat(a.getAcao()).isEqualTo("CONTA_ATIVADA");
        assertThat(a.getEntidade()).isEqualTo("CLIENTE");
        assertThat(a.getEntidadeId()).isEqualTo(cliente);
    }
}
