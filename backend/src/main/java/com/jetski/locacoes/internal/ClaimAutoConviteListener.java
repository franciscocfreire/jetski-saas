package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.event.PreContaCriadaEvent;
import com.jetski.locacoes.internal.repository.ClienteClaimTokenRepository;
import com.jetski.locacoes.internal.repository.ClienteIdentityProviderRepository;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.shared.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

/**
 * Auto-convite do cliente de balcão: quando uma pré-conta nasce (ou é
 * reaproveitada) COM e-mail, dispara o claim-token de ativação sem depender
 * de ação manual do staff — o cliente recebe o link para concluir o cadastro
 * e acompanhar as reservas no portal.
 *
 * <p>Roda APÓS o commit e em thread própria (padrão {@link GruAutoEmissaoService}):
 * o envio de e-mail jamais pode atrasar/derrubar a criação da pré-conta.
 * Best-effort — em falha, o staff usa o "reenviar convite" do backoffice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimAutoConviteListener {

    private final ClienteRepository clienteRepository;
    private final ClienteClaimTokenRepository tokenRepository;
    private final ClienteIdentityProviderRepository identityRepository;
    private final ClaimService claimService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPreContaCriada(PreContaCriadaEvent event) {
        try {
            // Thread async nasce sem contexto: o TenantAwareDataSource lê o
            // ThreadLocal a cada getConnection() para satisfazer a RLS.
            TenantContext.setTenantId(event.tenantId());
            TenantContext.setUsuarioId(event.criadoPor());
            enviarSeAplicavel(event.clienteId());
        } catch (Exception e) {
            // best-effort: a pré-conta JÁ está commitada
            log.warn("Auto-convite falhou (cliente={}): {}", event.clienteId(), e.getMessage());
        } finally {
            TenantContext.clear(); // thread de pool é reutilizada
        }
    }

    private void enviarSeAplicavel(UUID clienteId) {
        Optional<Cliente> clienteOpt = clienteRepository.findById(clienteId);
        if (clienteOpt.isEmpty()
                || clienteOpt.get().getEmail() == null
                || clienteOpt.get().getEmail().isBlank()) {
            log.info("Auto-convite: cliente {} sem e-mail — convite fica manual", clienteId);
            return;
        }
        Cliente cliente = clienteOpt.get();

        if (cliente.getStatusConta() == Cliente.StatusConta.ATIVA
                || identityRepository.existsByClienteId(clienteId)) {
            return; // já ativa/vinculada — nada a convidar
        }

        // Convite vigente na caixa de entrada? Não invalidar o link (gerar
        // desativa tokens anteriores); reenvio fica manual no backoffice.
        boolean conviteVigente = tokenRepository.findByClienteIdAndAtivoTrue(clienteId).stream()
            .anyMatch(t -> !t.isExpired());
        if (conviteVigente) {
            log.info("Auto-convite: cliente {} já tem convite vigente — não reenviado", clienteId);
            return;
        }

        claimService.gerar(clienteId, "email");
        log.info("Auto-convite enviado: cliente={}", clienteId);
    }
}
