package com.jetski.signup.internal;

import com.jetski.shared.email.EmailService;
import com.jetski.signup.domain.SignupStatus;
import com.jetski.signup.internal.repository.TenantSignupRepository;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Edge case do aviso de aprovação: empresa aprovada ANTES do admin ativar o magic-link
 * ainda não tem Usuario/Membro — o e-mail do admin só existe em {@code tenant_signup}
 * (dono: este módulo). Se houver signup PENDING para o tenant aprovado, avisa esse e-mail.
 *
 * <p>Mutuamente exclusivo com o TenantStatusEmailListener (usuarios): signup PENDING
 * implica nenhum Membro ainda; após a ativação o signup vira ACTIVATED e quem avisa é
 * o listener de membros. Best-effort, após commit (mesmo padrão).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignupTenantApprovedListener {

    private final TenantSignupRepository tenantSignupRepository;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTenantStatusChanged(TenantStatusChangedEvent event) {
        if (!"TENANT_APPROVED".equals(event.acao())) {
            return;
        }
        try {
            tenantSignupRepository.findByTenantIdAndStatus(event.tenantId(), SignupStatus.PENDING)
                .ifPresent(signup -> {
                    emailService.sendTenantStatusNotification(
                        signup.getEmail(), event.acao(), event.razaoSocial(), null);
                    log.info("Aviso de aprovação enviado ao e-mail do signup pendente: tenant={}, to={}",
                        event.tenantId(), signup.getEmail());
                });
        } catch (Exception e) {
            // best-effort: a aprovação JÁ está commitada
            log.warn("Aviso de aprovação via signup falhou (best-effort): tenant={}, {}",
                event.tenantId(), e.getMessage());
        }
    }
}
