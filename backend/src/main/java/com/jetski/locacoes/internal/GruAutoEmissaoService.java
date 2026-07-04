package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.ClienteNotificacao;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.ReservaHabilitacaoRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.reservas.domain.event.PagamentoConfirmadoEvent;
import org.springframework.transaction.support.TransactionTemplate;
import com.jetski.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

/**
 * Auto-emissão da GRU pelo TENANT (decisão de produto 04/07): quando o staff
 * confirma o pagamento (sinal ou total) de uma reserva do PORTAL, a loja emite
 * a GRU em nome do cliente — e a PAGA (QR/boleto no backoffice). O cliente
 * recebe apenas o número/status; nada a fazer da parte dele.
 *
 * Roda APÓS o commit e em thread própria: a geração chama a Marinha via RPA
 * (lenta) e jamais pode atrasar/derrubar a confirmação do pagamento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GruAutoEmissaoService {

    private final EntityManager entityManager;
    private final GruService gruService;
    private final ReservaRepository reservaRepository;
    private final ReservaHabilitacaoRepository habilitacaoRepository;
    private final ClienteNotificacaoService clienteNotificacaoService;
    private final TransactionTemplate transactionTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPagamentoConfirmado(PagamentoConfirmadoEvent event) {
        try {
            emitirSeAplicavel(event.tenantId(), event.reservaId());
        } catch (Exception e) {
            // best-effort: o pagamento já está confirmado; staff pode gerar manualmente
            log.warn("Auto-emissão de GRU falhou (reserva={}): {}", event.reservaId(), e.getMessage());
        }
    }

    /**
     * Núcleo reutilizável — também chamado quando o cliente completa os dados
     * pessoais DEPOIS da confirmação (retry que fecha o ciclo sem staff).
     * TransactionTemplate (não @Transactional): o listener chama via this e a
     * auto-invocação não passa pelo proxy — o set_config transaction-local
     * ficaria fora de transação e a RLS esconderia a reserva em prod.
     */
    public void emitirSeAplicavel(UUID tenantId, UUID reservaId) {
        transactionTemplate.executeWithoutResult(tx -> emitirCore(tenantId, reservaId));
    }

    private void emitirCore(UUID tenantId, UUID reservaId) {
        fixarTenant(tenantId);

        Optional<Reserva> reservaOpt = reservaRepository.findById(reservaId);
        if (reservaOpt.isEmpty() || reservaOpt.get().getCanal() != Reserva.Canal.PORTAL) {
            return; // balcão emite no próprio wizard do staff
        }
        Reserva reserva = reservaOpt.get();

        // GRU só faz sentido no caminho EMA e enquanto não paga/resolvida
        Optional<ReservaHabilitacao> hab = habilitacaoRepository.findByReservaId(reservaId);
        if (hab.isPresent()) {
            ReservaHabilitacao h = hab.get();
            boolean caminhoCha = h.getVia() == ReservaHabilitacao.Via.CHA;
            boolean jaResolvida = Boolean.TRUE.equals(h.getResolvida());
            boolean jaPaga = Boolean.TRUE.equals(h.getGruPago());
            boolean jaEmitida = h.getGruNumero() != null && !h.getGruNumero().isBlank();
            if (caminhoCha || jaResolvida || jaPaga || jaEmitida) {
                return;
            }
        }

        GruService.GruGeracao r = gruService.gerarGru(reservaId);
        if (r.sucesso()) {
            String numero = r.habilitacao() != null ? r.habilitacao().getGruNumero() : null;
            log.info("GRU auto-emitida pelo tenant: reserva={}, gru={}, reaproveitada={}",
                reservaId, numero, r.reaproveitada());
            clienteNotificacaoService.notificar(tenantId, reserva.getClienteId(),
                ClienteNotificacao.GRU_EMITIDA,
                "Taxa da Marinha emitida 📄",
                (numero != null ? "A GRU nº " + numero + " foi emitida em seu nome"
                    : "A taxa da Marinha foi emitida em seu nome")
                    + " e a própria loja fará o pagamento — você não paga nada por ela.",
                "/conta/reservas/" + reservaId + "/habilitacao");
        } else {
            log.warn("Auto-emissão de GRU sem sucesso (reserva={}): {} {}",
                reservaId, r.erroCodigo(), r.erroMensagem());
            clienteNotificacaoService.notificar(tenantId, reserva.getClienteId(),
                ClienteNotificacao.GRU_PENDENTE_DADOS,
                "Complete seus dados para a taxa da Marinha",
                "A loja tentou emitir sua GRU, mas faltam dados do seu cadastro "
                    + "(CPF, endereço…). Complete-os na aba Habilitação que a emissão sai sozinha.",
                "/conta/reservas/" + reservaId + "/habilitacao");
        }
    }

    /** Fixa app.tenant_id (transaction-local) — thread async nasce sem contexto. */
    private void fixarTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        TenantContext.setTenantId(tenantId);
    }
}
